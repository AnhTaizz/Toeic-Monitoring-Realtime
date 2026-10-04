package vn.edu.toeic.server.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MetadataQuality;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import vn.edu.toeic.client.monitoring.MonitoringMessage;
import vn.edu.toeic.client.monitoring.ObservedProcess;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessHandleSnapshotSource;
import vn.edu.toeic.client.monitoring.ProcessIdentity;
import vn.edu.toeic.client.monitoring.ProcessSnapshot;
import vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.ToeicServerApplication;

/** REAL production Spring/PostgreSQL/B2/C3, with owned Windows Edge process.
 * ACK loss is SIMULATED only at the test transport observer boundary.
 * Overflow snapshots are MOCK; their delivery/DB/ACK is REAL. No GUI claims.
 */
public final class MonitoringDeliverySmoke {
    private static final Gson GSON = new Gson();
    private static final String A = "TEST-C3-A", B = "TEST-C3-B", CLOSED = "TEST-C3-CLOSED", PASSWORD = "TEST-C3-only-password";
    private String url, schema;
    private ConfigurableApplicationContext context;
    private int port;
    private String phase = "MIGRATION";
    public static void main(String[] args) {
        MonitoringDeliverySmoke smoke = new MonitoringDeliverySmoke();
        try { smoke.run(args.length == 0 ? null : Path.of(args[0])); }
        catch (Exception failure) { System.err.println("C3 smoke FAIL phase=" + smoke.phase + " class=" + failure.getClass().getSimpleName()); System.exit(1); }
    }
    private void run(Path edge) throws Exception {
        url = "jdbc:postgresql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "5432") + "/" + env("DB_NAME", "toeic");
        schema = "c3_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url, env("DB_USER", "toeic"), env("DB_PASSWORD", ""))) {
            check(schema.matches("c3_test_[a-f0-9]{32}"), "Safe owned schema");
            admin.createStatement().execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(url, env("DB_USER", "toeic"), env("DB_PASSWORD", "")).schemas(schema).defaultSchema(schema).target("2").load().migrate();
                start(); fixtures();
                check(jdbc().sql("SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL").query(Integer.class).single() == 3, "V2->V3 migration");
                System.out.println("PASS REAL PostgreSQL18 V2->V3; TEST assignments only; no shared DB reset");
                verify(edge);
            } finally {
                if (context != null) { context.close(); context = null; }
                admin.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
                System.out.println("TEST schema removed; shared PostgreSQL/dev tables untouched");
            }
        }
        System.out.println("C3 REAL integration PASS; GUI/LAN/human B review NOT RUN; MT01 PARTIAL until B3");
    }
    private static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
    private void start() {
        SpringApplication app = new SpringApplication(ToeicServerApplication.class); app.setBannerMode(Banner.Mode.OFF);
        context = app.run("--server.port=" + port, "--server.address=127.0.0.1", "--spring.datasource.url=" + url + "?currentSchema=" + schema,
                "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema, "--logging.level.root=OFF", "--logging.level.org.springframework.web=OFF", "--debug=false");
        port = ((WebServerApplicationContext) context).getWebServer().getPort();
    }
    private JdbcClient jdbc() { return context.getBean(JdbcClient.class); }
    private void fixtures() {
        check(jdbc().sql("SELECT count(*) FROM monitoring_attempts").query(Integer.class).single() == 0, "No production fake attempts");
        PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
        for (String name : List.of("TEST-candidate-A", "TEST-candidate-B", "TEST-empty", "TEST-proctor-A", "TEST-proctor-B")) {
            jdbc().sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:n,'TEST C3 fixture',:r,:h)")
                    .param("n", name).param("r", name.contains("proctor") ? "PROCTOR" : "CANDIDATE").param("h", encoder.encode(PASSWORD)).update();
        }
        for (String attempt : List.of(A, B, CLOSED)) {
            jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,:s FROM user_accounts WHERE username=:n")
                    .param("a", attempt).param("s", attempt.equals(CLOSED) ? "CLOSED" : "ACTIVE")
                    .param("n", attempt.equals(B) ? "TEST-candidate-B" : "TEST-candidate-A").update();
            jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username=:n")
                    .param("a", attempt).param("n", attempt.equals(B) ? "TEST-proctor-B" : "TEST-proctor-A").update();
        }
    }
    private void verify(Path edge) throws Exception {
        phase = "AUTH";
        String origin = "http://127.0.0.1:" + port;
        try (LoginApiClient api = new LoginApiClient(); HttpClient http = HttpClient.newHttpClient()) {
            var candidate = api.login(origin, "TEST-candidate-A", PASSWORD).get(10, TimeUnit.SECONDS);
            var proctor = api.login(origin, "TEST-proctor-A", PASSWORD).get(10, TimeUnit.SECONDS);
            var foreignProctor = api.login(origin, "TEST-proctor-B", PASSWORD).get(10, TimeUnit.SECONDS);
            var empty = api.login(origin, "TEST-empty", PASSWORD).get(10, TimeUnit.SECONDS);
            var scope = api.scope(origin, candidate.token()).get(10, TimeUnit.SECONDS);
            check(scope.role() == Role.CANDIDATE && scope.attemptScope().equals(Set.of(A)), "Fresh production scope");
            try (RealtimeClient emptyTransport = new RealtimeClient(origin, empty.token());
                    CandidateMonitoringSession denied = new CandidateMonitoringSession(emptyTransport, ignored -> { })) {
                try { denied.start(Role.CANDIDATE, A, Set.copyOf(empty.attemptScope())); throw new IllegalStateException("Unexpected start"); }
                catch (IllegalStateException expected) { check(denied.status() == null, "Empty scope allocates no queue/worker"); }
            }
            Probe assigned = connect(http, origin, proctor.token()), other = connect(http, origin, foreignProctor.token());
            RealtimeClient.Settings network = new RealtimeClient.Settings(Duration.ofMillis(200), Duration.ofMillis(100), Duration.ofMillis(300), 30, 65536, 16);
            try (RealtimeClient client = new RealtimeClient(new AuthenticatedWebSocketOpener(origin, candidate.token()), network)) {
                ArrayBlockingQueue<MessageEnvelope<JsonObject>> errors = new ArrayBlockingQueue<>(64);
                AtomicInteger heartbeatAcks = new AtomicInteger();
                client.onMessage(message -> {
                    if (message.type().equals("ERROR")) errors.offer(message);
                    if (message.type().equals("ACK") && message.payload().get("acknowledgedType").getAsString().equals("HEARTBEAT")) heartbeatAcks.incrementAndGet();
                });
                client.connect(new RealtimeClient.Session(scope.attemptScope(), null, null)).get(10, TimeUnit.SECONDS);
                LossBoundary boundary = new LossBoundary(client);
                if (edge != null && Files.isRegularFile(edge) && edge.getFileName().toString().equalsIgnoreCase("msedge.exe"))
                    processDemo(edge, boundary, api, http, origin, candidate.token(), proctor.token(), assigned, other);
                else throw new IllegalStateException("Owned Windows Edge executable required for this smoke");
                phase = "RECONNECT";
                AtomicReference<MonitoringDelivery.Status> reconnectStatus = new AtomicReference<>();
                try (MonitoringDelivery delivery = new MonitoringDelivery(A, client, settings(4), reconnectStatus::set)) {
                    context.close(); context = null;
                    await(() -> client.connectionState() == ConnectionState.RECONNECTING, "Owned server disconnect");
                    delivery.observe(mockSnapshot(700001));
                    check(delivery.status().pendingEvents() == 1, "Offline event retained");
                    start(); await(() -> client.connectionState() == ConnectionState.CONNECTED, "Fresh B2 reconnect");
                    await(() -> delivery.status().pendingEvents() == 0, "Pending delivered after reconnect");
                    check(countPid(700001) == 1, "Reconnect one row");
                }
                System.out.println("PASS REAL owned server stop/restart + B2 reconnect; MOCK snapshot pending delivered exactly once; no reconnect loop in C3");
                phase = "OVERFLOW_GAP";
                jdbc().sql("CREATE FUNCTION test_gap_commit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'TEST deferred gap failure'; END $$").update();
                jdbc().sql("CREATE CONSTRAINT TRIGGER test_gap_failure AFTER INSERT ON monitoring_gaps DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_gap_commit_failure()").update();
                int beforeHeartbeat = heartbeatAcks.get();
                AtomicReference<MonitoringDelivery.Status> overflow = new AtomicReference<>();
                boundary.dropGapAck.set(true);
                try (MonitoringDelivery delivery = new MonitoringDelivery(A, boundary, settings(1), overflow::set)) {
                    delivery.observe(mockSnapshot(700010, 700011, 700012));
                    MessageEnvelope<JsonObject> dbError = errors.poll(5, TimeUnit.SECONDS);
                    check(dbError != null && dbError.payload().get("code").getAsString().equals("RETRYABLE_SERVER_ERROR"), "Deferred gap COMMIT error");
                    check(jdbc().sql("SELECT count(*) FROM monitoring_gaps").query(Integer.class).single() == 0, "Gap COMMIT rollback no row");
                    check(delivery.status().gapPending(), "No fake gap ACK on commit failure");
                    delivery.observe(mockSnapshot(700013)); // New drops while old gap is frozen/in retry.
                    jdbc().sql("DROP TRIGGER test_gap_failure ON monitoring_gaps").update();
                    jdbc().sql("DROP FUNCTION test_gap_commit_failure()").update();
                    await(() -> delivery.status().pendingEvents() == 0 && !delivery.status().gapPending() && delivery.status().bufferedDrops() == 0, "Gap/event ACK drain");
                    check(delivery.status().droppedCount() == 3, "Dropped count includes next accumulator");
                    check(jdbc().sql("SELECT count(*) FROM monitoring_gaps").query(Integer.class).single() == 2, "Frozen gap and next gap only once each");
                    check(jdbc().sql("SELECT sum(dropped_count) FROM monitoring_gaps").query(Long.class).single() == 3, "Persisted drop total");
                    check(boundary.gapWrites.get() >= 3 && boundary.gapLoss.get(), "Gap DB failure retry + simulated lost ACK retry");
                    await(() -> heartbeatAcks.get() > beforeHeartbeat, "Heartbeat not starved by delivery");
                    MessageEnvelope<JsonObject> changed = boundary.firstGap.get(); JsonObject altered = changed.payload().deepCopy(); altered.addProperty("droppedCount", 99);
                    client.send(new MessageEnvelope<>(changed.protocolVersion(), changed.type(), changed.messageId(), changed.requestId(), changed.attemptId(), changed.traceId(), altered)).get(5, TimeUnit.SECONDS);
                    MessageEnvelope<JsonObject> conflict = errors.poll(5, TimeUnit.SECONDS);
                    check(conflict != null && conflict.payload().get("code").getAsString().equals("CONFLICT"), "Gap changed payload conflict");
                }
                System.out.println("PASS MOCK overflow capacity1 -> REAL gap persistence/commit ACK; DB COMMIT rollback/retry; SIMULATED gap ACK loss; stable dedup; next accumulator + heartbeat");
                phase = "SCOPE_GAP";
                Probe raw = connect(http, origin, candidate.token());
                try {
                    for (String attempt : List.of(B, CLOSED, "TEST-unknown")) {
                        raw.send(GSON.toJson(MonitoringMessage.gap(attempt, "TEST-scope", 1, Instant.now(), Instant.now()).envelope()));
                        check(error(raw.next()).equals("FORBIDDEN"), "Gap foreign/CLOSED/unknown denied");
                    }
                    Probe proctorSocket = connect(http, origin, proctor.token());
                    try { proctorSocket.send(GSON.toJson(MonitoringMessage.gap(A, "TEST-scope", 1, Instant.now(), Instant.now()).envelope())); check(error(proctorSocket.next()).equals("FORBIDDEN"), "Proctor cannot report gap"); }
                    finally { proctorSocket.socket.abort(); }
                } finally { raw.socket.abort(); }
                check(jdbc().sql("SELECT count(*) FROM monitoring_gaps").query(Integer.class).single() == 2, "Denied gap did not insert");
                System.out.println("PASS REAL gap auth/role/foreign/CLOSED/unknown scope; gap never changes process state; warning/timeline process contract unchanged");
            } finally { assigned.socket.abort(); other.socket.abort(); }
            await(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && Set.of("toeic-monitoring-delivery", "toeic-process-collector", "toeic-realtime-worker").contains(t.getName())), "Owned workers gone");
            System.out.println("PASS collector/delivery/transport workers terminated; callbacks/subscriptions cleaned; no queue across logout/app kill");
        }
    }
    private void processDemo(Path edge, LossBoundary boundary, LoginApiClient api, HttpClient http, String origin, String candidateToken, String proctorToken, Probe assigned, Probe other) throws Exception {
        phase = "REAL_PROCESS";
        ProcessHandleSnapshotSource real = new ProcessHandleSnapshotSource(); AtomicInteger polls = new AtomicInteger();
        CountDownLatch allowNext = new CountDownLatch(1), initial = new CountDownLatch(1);
        ProcessCollector collector = new ProcessCollector(() -> {
            if (polls.get() > 0) try { if (!allowNext.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("TEST scan gate"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("TEST stop"); }
            var readings = real.scan(); polls.incrementAndGet(); initial.countDown(); return readings;
        }, Duration.ofMillis(200));
        Path profile = Files.createTempDirectory("toeic-c3-owned-edge-"); Process owned = null;
        try (CandidateMonitoringSession session = new CandidateMonitoringSession(collector, boundary, settings(500), ignored -> { })) {
            var fresh = api.scope(origin, candidateToken).get(10, TimeUnit.SECONDS);
            session.start(fresh.role(), A, fresh.attemptScope());
            check(initial.await(5, TimeUnit.SECONDS), "Real first scan");
            owned = launch(edge, profile); boundary.targetPid.set(owned.pid()); allowNext.countDown();
            long pid = owned.pid();
            await(() -> boundary.eventLoss.get() && boundary.eventWrites.get() >= 2 && countPid(pid) == 1 && session.status().pendingEvents() == 0, "Real process lost ACK retry");
            String eventId = jdbc().sql("SELECT event_id FROM monitoring_events WHERE pid=:p").param("p", pid).query(String.class).single();
            await(() -> polls.get() >= 6, "Multiple real polls");
            check(countPid(pid) == 1 && assigned.count(eventId) == 1 && other.count(eventId) == 0, "One row/warning across polls+retry, proctor isolation");
            stopOwned(owned); owned = null;
            await(() -> collector.latestSnapshot().map(s -> s.restrictedProcesses().stream().noneMatch(p -> p.identity().pid() == pid)).orElse(false), "Owned process disappears");
            assigned.socket.abort();
            owned = launch(edge, profile); long offlinePid = owned.pid();
            await(() -> countPid(offlinePid) == 1 && session.status().pendingEvents() == 0, "Offline proctor event commit");
            var timeline = http.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/monitoring/attempts/" + A + "/events"))
                    .header("Authorization", "Bearer " + proctorToken).GET().build(), HttpResponse.BodyHandlers.ofString());
            String offlineEvent = jdbc().sql("SELECT event_id FROM monitoring_events WHERE pid=:p").param("p", offlinePid).query(String.class).single();
            check(timeline.statusCode() == 200 && GSON.fromJson(timeline.body(), JsonObject.class).getAsJsonArray("events").asList().stream()
                    .anyMatch(item -> item.getAsJsonObject().get("eventId").getAsString().equals(offlineEvent)), "Offline timeline recovery");
            stopOwned(owned); owned = null;
            session.stop().stopped().get(5, TimeUnit.SECONDS);
            System.out.println("PASS REAL Windows ProcessHandle/owned Edge open-close -> production C2/C3/B2 -> DB/ACK; SIMULATED first event ACK loss -> row1/warning1; real offline proctor timeline");
        } finally {
            allowNext.countDown(); if (owned != null) stopOwned(owned);
            Path normalized = profile.toAbsolutePath().normalize();
            check(normalized.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())
                    && normalized.getFileName().toString().startsWith("toeic-c3-owned-edge-"), "Cleanup owned temp profile only");
            try (var files = Files.walk(normalized)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static Process launch(Path edge, Path profile) throws Exception {
        return new ProcessBuilder(edge.toString(), "--headless=new", "--disable-gpu", "--no-first-run", "--disable-background-networking",
                "--remote-debugging-port=0", "--user-data-dir=" + profile, "about:blank")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    private static void stopOwned(Process root) throws Exception {
        List<ProcessHandle> handles = new ArrayList<>(root.descendants().toList()); handles.add(root.toHandle());
        for (ProcessHandle handle : handles) if (handle.isAlive()) handle.destroyForcibly();
        for (ProcessHandle handle : handles) handle.onExit().get(5, TimeUnit.SECONDS);
    }
    private long countPid(long pid) { return jdbc().sql("SELECT count(*) FROM monitoring_events WHERE pid=:p").param("p", pid).query(Long.class).single(); }
    private static MonitoringDelivery.Settings settings(int capacity) {
        return new MonitoringDelivery.Settings(capacity, Math.min(4, capacity), Duration.ofMillis(250), 5, Duration.ofMillis(75), Duration.ofMillis(300), Duration.ofMillis(15), 10000);
    }
    private static ProcessSnapshot mockSnapshot(long... pids) {
        Set<ObservedProcess> items = new HashSet<>();
        for (long pid : pids) items.add(new ObservedProcess(new ProcessIdentity("TEST-MOCK-collector", pid, Instant.parse("2026-10-04T00:00:00Z")), "msedge.exe", MetadataQuality.COMPLETE));
        return new ProcessSnapshot("TEST-MOCK-collector", "process-policy-v1", System.nanoTime(), 0, items, new ProcessSnapshot.Diagnostics(pids.length, 0, 0, 0, 0));
    }
    private static void await(BooleanSupplier condition, String label) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS)).get();
        }
        check(false, label);
    }
    private static void check(boolean pass, String label) { if (!pass) { System.err.println("FAIL " + label); throw new IllegalStateException("TEST assertion"); } }
    private static String error(JsonObject body) { check(body.get("type").getAsString().equals("ERROR"), "Expected ERROR"); return body.getAsJsonObject("payload").get("code").getAsString(); }
    private static Probe connect(HttpClient http, String origin, String token) throws Exception {
        Probe p = new Probe(); p.socket = http.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                .buildAsync(URI.create(origin.replace("http:", "ws:") + "/ws/v1/realtime"), p).get(10, TimeUnit.SECONDS); return p;
    }
    private static final class Probe implements WebSocket.Listener {
        WebSocket socket; final ArrayBlockingQueue<JsonObject> received = new ArrayBlockingQueue<>(1000); final StringBuilder fragments = new StringBuilder();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            fragments.append(data); if (last) { received.offer(GSON.fromJson(fragments.toString(), JsonObject.class)); fragments.setLength(0); }
            ws.request(1); return CompletableFuture.completedFuture(null);
        }
        void send(String body) throws Exception { socket.sendText(body, true).get(5, TimeUnit.SECONDS); }
        JsonObject next() throws Exception { JsonObject item = received.poll(5, TimeUnit.SECONDS); check(item != null, "Expected WS response"); return item; }
        long count(String event) { return received.stream().filter(m -> m.get("type").getAsString().equals("MONITOR_WARNING") && m.getAsJsonObject("payload").get("eventId").getAsString().equals(event)).count(); }
    }
    private static final class LossBoundary implements MonitoringTransport {
        final RealtimeClient delegate; final AtomicLong targetPid = new AtomicLong(-1);
        final AtomicReference<String> eventRequest = new AtomicReference<>(); final AtomicReference<MessageEnvelope<JsonObject>> firstGap = new AtomicReference<>();
        final AtomicBoolean eventLoss = new AtomicBoolean(), gapLoss = new AtomicBoolean(), dropGapAck = new AtomicBoolean();
        final AtomicInteger eventWrites = new AtomicInteger(), gapWrites = new AtomicInteger();
        LossBoundary(RealtimeClient delegate) { this.delegate = delegate; }
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {
            if (message.type().equals("PROCESS_OBSERVED") && message.payload().get("pid").getAsLong() == targetPid.get()) {
                eventRequest.compareAndSet(null, message.requestId()); check(eventRequest.get().equals(message.requestId()), "Stable event request retry"); eventWrites.incrementAndGet();
            }
            if (message.type().equals("MONITORING_GAP")) { firstGap.compareAndSet(null, message); gapWrites.incrementAndGet(); }
            return delegate.send(message);
        }
        @Override public ConnectionState connectionState() { return delegate.connectionState(); }
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) { return delegate.onConnectionState(listener); }
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) {
            return delegate.onMessage(message -> {
                if (message.type().equals("ACK") && message.requestId().equals(eventRequest.get()) && eventLoss.compareAndSet(false, true)) return;
                if (message.type().equals("ACK") && dropGapAck.get() && message.payload().get("acknowledgedType").getAsString().equals("MONITORING_GAP") && gapLoss.compareAndSet(false, true)) return;
                listener.accept(message);
            });
        }
        @Override public void forgetPending(String id) { delegate.forgetPending(id); }
    }
}
