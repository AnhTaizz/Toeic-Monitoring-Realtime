package vn.edu.toeic.client.integration;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.*;
import vn.edu.toeic.client.*;
import vn.edu.toeic.client.exam.*;
import vn.edu.toeic.client.proctor.redesign.ProctorIntegrationProbe;
import vn.edu.toeic.protocol.exam.*;

/** REAL production Application, JavaFX Media, HTTP/WS, JAR and isolated PostgreSQL schema.
 * Fixture exam/WAV are TEST data; clicking speaker confirmation is SIMULATED human hearing.
 * Server process restart is REAL. This is one-host automation, not human or two-machine LAN acceptance.
 */
public final class ReadingListeningPostgresGuiSmoke {
    private final String run = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final String schema = "integration_gui_" + run;
    private final Path output = Path.of(System.getProperty("integration.output", "target/integration-gui"), run).toAbsolutePath();
    private final Gson json = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private Process server;
    private Process lifecycleEdge;
    private int port, passed;
    private String origin, candidateToken, proctorToken;
    private Stage candidateStage, proctorStage;
    private ToeicClientApplication candidateApp, proctorApp;
    private ExamApiClient candidateApi, proctorApi;

    public static void main(String[] args) {
        ReadingListeningPostgresGuiSmoke smoke = new ReadingListeningPostgresGuiSmoke();
        int exit = 0;
        try { smoke.run(); }
        catch (Throwable failure) {
            System.out.println("FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage()); exit = 1;
            try { if (smoke.proctorStage != null) smoke.screenshot(smoke.proctorStage, "failed-proctor.png"); }
            catch (Exception ignored) { }
        }
        finally { smoke.cleanup(); }
        System.out.println("GUI_INTEGRATION passed=" + smoke.passed + " exit=" + exit);
        System.exit(exit);
    }

    private void run() throws Exception {
        Files.createDirectories(output);
        Class.forName("org.postgresql.Driver");
        try (Connection db = db(); Statement sql = db.createStatement()) { sql.execute("CREATE SCHEMA " + schema); }
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        origin = "http://127.0.0.1:" + port;
        String readingSession = "GUI-R-" + run, listeningSession = "GUI-L-" + run;
        String reading = readingSession + "-candidate1", listening = listeningSession + "-candidate1";
        Path audioRoot = output.resolve("audio");
        createWave(audioRoot.resolve("GUI-MIXED-" + run).resolve("bài nghe.wav"), 30);
        System.setProperty("toeic.audio.cacheDirectory", output.resolve("Bộ nhớ audio có dấu").toString());
        System.setProperty("toeic.exam.autosaveDebounceMillis", "100");
        startServer(audioRoot);
        try (LoginApiClient login = new LoginApiClient()) {
            candidateToken = login.login(origin, "candidate1", required("TOEIC_SEED_PASSWORD")).get(20, TimeUnit.SECONDS).token();
            proctorToken = login.login(origin, "proctor1", required("TOEIC_SEED_PASSWORD")).get(20, TimeUnit.SECONDS).token();
        }
        candidateApi = new ExamApiClient(origin, candidateToken); proctorApi = new ExamApiClient(origin, proctorToken);
        proctorApi.importExam(new ExamImportRequest("GUI-READING-" + run, "TEST Reading", "Owned GUI fixture",
                List.of(question("R1", "READING", null, 1)))).get(20, TimeUnit.SECONDS);
        proctorApi.importExam(new ExamImportRequest("GUI-MIXED-" + run, "TEST Reading + Listening", "Owned GUI fixture",
                List.of(question("L1", "LISTENING", "bài nghe.wav", 1), question("R1", "READING", null, 2)))).get(20, TimeUnit.SECONDS);
        proctorApi.createSession(session(readingSession, "GUI-READING-" + run)).get(20, TimeUnit.SECONDS);
        proctorApi.createSession(session(listeningSession, "GUI-MIXED-" + run)).get(20, TimeUnit.SECONDS);
        CompletableFuture<Void> started = new CompletableFuture<>();
        if(Boolean.getBoolean("integration.manualDesktop")){manualDesktop();return;}
        Platform.startup(() -> { Platform.setImplicitExit(false); started.complete(null); }); started.get(20, TimeUnit.SECONDS);
        if (Boolean.getBoolean("integration.fullLifecycle")) { fullLifecycle(readingSession, reading); return; }
        candidateApp = fx(ToeicClientApplication::new); candidateStage = fx(Stage::new);
        fx(() -> { candidateApp.start(candidateStage); return null; }); login(candidateStage, "candidate1");
        enter(reading); clickId(candidateStage, "option-R1-A");
        until(() -> "A".equals(status(reading).answers().get("R1")), "Reading autosave ACK");
        long readingWriter = status(reading).writerEpoch();
        pass("REAL Reading GUI/autosave/timer; authoritative writer=" + readingWriter); screenshot(candidateStage, "01-reading-saved.png");
        restartServer(audioRoot);
        until(() -> enabled(candidateStage, "option-R1-B"), "Reading reconnect");
        check(status(reading).writerEpoch() == readingWriter, "Reconnect does not takeover Reading");
        clickId(candidateStage, "option-R1-B"); until(() -> "B".equals(status(reading).answers().get("R1")), "Reading revision after reconnect");
        pass("REAL server restart/Reading reconnect keeps writer and authoritative answers");
        Platform.runLater(() -> ((Button) candidateStage.getScene().lookup("#exam-submit")).fire());
        until(() -> confirmSubmit(), "Submit confirmation");
        until(() -> "SUBMITTED".equals(status(reading).state()), "GUI submission");
        pass("GUI submit/server final result"); screenshot(candidateStage, "02-reading-submitted.png");
        fx(() -> { for (Window window : List.copyOf(Window.getWindows())) if (window != candidateStage) window.hide(); return null; });
        if (fx(() -> candidateStage.getScene().lookup("#exam-exit") != null)) clickId(candidateStage, "exam-exit");
        until(() -> fx(() -> candidateStage.getScene().lookup("#candidate-exam-tabs") != null), "Candidate portal");
        fx(() -> { ((TabPane) candidateStage.getScene().lookup("#candidate-exam-tabs")).getSelectionModel().select(1); return null; });
        clickId(candidateStage, "candidate-history-refresh");
        until(() -> fx(() -> ((TableView<?>) candidateStage.getScene().lookup("#candidate-history")).getItems().stream()
                .anyMatch(row -> row instanceof CandidateAttemptSummary summary && reading.equals(summary.attemptId()))), "Production history");
        check(status(reading).writerEpoch() == readingWriter, "History never takes over final attempt");
        pass("Production history discovers submitted result without changing writer"); screenshot(candidateStage, "03-history.png");
        fx(() -> { ((TabPane) candidateStage.getScene().lookup("#candidate-exam-tabs")).getSelectionModel().select(0); return null; });
        proctorApp = fx(ToeicClientApplication::new); proctorStage = fx(Stage::new);
        fx(() -> { proctorApp.start(proctorStage); return null; }); login(proctorStage, "proctor1");
        selectProctorTab("Tiến độ đã lưu");
        until(()->fx(()->ProctorIntegrationProbe.savedProgress(proctorStage.getScene().getRoot(),reading,"SUBMITTED",1)),"Committed final progress rendered");
        pass("REAL proctor progress shows committed finalized row without answers or writer acquisition");screenshot(proctorStage,"07-progress.png");
        enter(listening); clickId(candidateStage, "option-L1-A");
        until(() -> "A".equals(status(listening).answers().get("L1")), "Listening answer save");
        long sharedWriter = status(listening).writerEpoch();
        clickText(candidateStage, "Tải / chuẩn bị lại audio");
        until(() -> enabledText(candidateStage, "Thử loa"), "Audio checksum/download");
        check(status(listening).writerEpoch() == sharedWriter, "Audio preparation reuses Reading writer");
        clickText(candidateStage, "Thử loa"); until(() -> enabledText(candidateStage, "Tôi nghe được"), "Real speaker sample Media end");
        clickText(candidateStage, "Tôi nghe được"); clickText(candidateStage, "Xác nhận sẵn sàng");
        until(() -> response("/api/v1/sessions/" + listeningSession + "/audio/readiness", proctorToken, null).toString().contains("\"allReady\":true"), "READY ACK");
        String manifest = response("/api/v1/attempts/" + listening + "/audio/manifest", candidateToken, null).get("manifestId").getAsString();
        selectProctorTab("Điều khiển ca / kết quả");
        fx(()->{((TextField)proctorStage.getScene().lookup("#proctor-session-id")).setText(listeningSession);return null;});
        clickId(proctorStage,"proctor-load-ready");
        until(()->enabled(proctorStage,"proctor-start"),"Server READY enables UI start");
        ensureVisible(proctorStage,"proctor-start");
        Platform.runLater(()->((Button)proctorStage.getScene().lookup("#proctor-start")).fire());
        until(ReadingListeningPostgresGuiSmoke::confirmOk,"Explicit proctor start confirmation");
        until(()->fx(()->((Label)proctorStage.getScene().lookup("#proctor-operation-status")).getText().contains("Server xác nhận startId=")),"UI waits server start ACK");
        ensureVisible(proctorStage,"proctor-operation-status");
        pass("REAL proctor session UI READY/start confirmation and committed ACK");screenshot(proctorStage,"08-session-start.png");
        until(() -> "PLAYING".equals(response("/api/v1/attempts/" + listening + "/listening/status", candidateToken, null).get("phase").getAsString()), "Real Media/STARTED");
        check(status(listening).writerEpoch() == sharedWriter, "Start uses exact shared writer");
        until(() -> !enabledText(candidateStage, "Tải / chuẩn bị lại audio"), "Preparation disabled while Media plays");
        pass("Reading + Listening same Application; REAL WAV/READY/STARTED with one writer; speaker confirmation SIMULATED");
        clickId(candidateStage, "palette-R1"); clickId(candidateStage, "option-R1-A");
        until(() -> "A".equals(status(listening).answers().get("R1")), "Reading during Listening");
        response("/api/v1/sessions/" + listeningSession + "/listening/start", proctorToken, Map.of("requestId", "GUI-START-" + run, "manifestId", manifest));
        check(status(listening).writerEpoch() == sharedWriter, "Duplicate start never takes over writer");
        pass("Reading autosave during real Listening; duplicate server start remains idempotent"); screenshot(candidateStage, "04-reading-listening.png");
        restartServer(audioRoot);
        until(() -> status(listening).listeningInterrupted(), "Authoritative interruption after real restart");
        until(() -> enabled(candidateStage, "option-R1-B"), "Reading recovers after Listening interruption");
        clickId(candidateStage, "option-R1-B"); until(() -> "B".equals(status(listening).answers().get("R1")), "Reading save with frozen Listening");
        check("A".equals(status(listening).answers().get("L1")), "Listening answers preserved");
        clickId(candidateStage, "palette-L1");
        check(!enabled(candidateStage, "option-L1-B"), "Interrupted Listening controls locked");
        check("INTERRUPTED".equals(response("/api/v1/attempts/" + listening + "/listening/status", candidateToken, null).get("phase").getAsString()), "Listening never resumes");
        check(status(listening).writerEpoch() == sharedWriter, "Listening reconnect does not takeover");
        pass("REAL server restart stops/locks Listening without replay; Reading continues with frozen Listening answers"); screenshot(candidateStage, "05-interrupted.png");

        selectProctorTab("Process hiện tại");
        until(() -> "SYNCED".equals(response("/api/v1/monitoring/attempts/" + listening + "/state", proctorToken, null).get("status").getAsString()), "FULL state after restart");
        check(!response("/api/v1/monitoring/attempts", proctorToken, null).toString().contains(reading), "Final attempt stays outside monitoring scope");
        until(() -> fx(() -> ProctorIntegrationProbe.rosterContains(proctorStage.getScene().getRoot(),listening)),"Proctor roster rendered");
        clickId(proctorStage,"candidates-detail-"+listening);
        until(() -> fx(() -> ProctorIntegrationProbe.currentProcessSynced(proctorStage.getScene().getRoot(),listening)),
                "Proctor full observation rendered without stale/loading flags");
        pass("REAL proctor Application/HTTP roster + candidate FULL state; final attempt excluded"); screenshot(proctorStage, "06-proctor.png");
        var oldStatus=status(listening);
        selectProctorTab("Điều khiển ca / kết quả");
        fx(()->{((ComboBox<?>)proctorStage.getScene().lookup("#proctor-attempt-id")).getEditor().setText(listening);return null;});
        clickId(proctorStage,"proctor-read-attempt");
        until(()->enabled(proctorStage,"proctor-reorganize"),"Interrupted status enables explicit reorg");
        String replacement="GUI-REORG-"+run;
        fx(()->{((TextField)proctorStage.getScene().lookup("#proctor-replacement-session")).setText(replacement);((TextField)proctorStage.getScene().lookup("#proctor-replacement-title")).setText("TEST controlled reorganization");return null;});
        ensureVisible(proctorStage,"proctor-reorganize");
        Platform.runLater(()->((Button)proctorStage.getScene().lookup("#proctor-reorganize")).fire());
        until(ReadingListeningPostgresGuiSmoke::confirmOk,"Explicit reorg confirmation");
        until(()->fx(()->((Label)proctorStage.getScene().lookup("#proctor-operation-status")).getText().contains("Lượt cũ ")),"Reorg server ACK");
        var afterReorg=status(listening);
        check(oldStatus.answers().equals(afterReorg.answers())&&oldStatus.deadlineAt().equals(afterReorg.deadlineAt())&&oldStatus.savedRevision()==afterReorg.savedRevision(),"Reorg preserves old answers/deadline/revision");
        check(status(replacement+"-candidate1").savedRevision()==0,"Replacement starts fresh");
        ensureVisible(proctorStage,"proctor-operation-status");
        pass("REAL explicit UI reorganization creates new attempt, old authoritative data immutable");screenshot(proctorStage,"09-reorganization.png");
        JsonObject result = new JsonObject(); result.addProperty("passed", passed); result.addProperty("schema", schema);
        result.addProperty("readingWriter", readingWriter); result.addProperty("sharedWriter", sharedWriter);
        result.addProperty("labels", "REAL Application/Media/JAR/PG/HTTP/WS/restart; TEST exam/WAV; SIMULATED speaker confirmation; LAN/human NOT RUN");
        Files.writeString(output.resolve("result.json"), json.toJson(result));
    }

    /** Production Application + JAR, programmatic FX controls; NOT desktop input or human acceptance. */
    private void fullLifecycle(String readingSession, String reading) throws Exception {
        Set<Long> fixtureThreads=Thread.getAllStackTraces().keySet().stream().map(Thread::threadId).collect(java.util.stream.Collectors.toSet());
        proctorApp=fx(ToeicClientApplication::new); proctorStage=fx(Stage::new);
        fx(()->{proctorApp.start(proctorStage);return null;}); login(proctorStage,"proctor1");
        until(()->fx(()->proctorStage.getScene().lookup("#dashboard-roster")!=null),"Proctor dashboard");
        check(monitoringThreads().isEmpty(),"Proctor never creates candidate monitoring workers");
        candidateApp=fx(ToeicClientApplication::new); candidateStage=fx(Stage::new);
        fx(()->{candidateApp.start(candidateStage);return null;}); login(candidateStage,"candidate1");
        selectAttempt(reading); clickText(candidateStage,"Bắt đầu giám sát");
        until(()->state(reading).get("status").getAsString().equals("SYNCED"),"First FULL snapshot");
        until(()->fx(()->candidateStage.getScene().getRoot().lookupAll(".label").stream()
                .filter(Label.class::isInstance).map(Label.class::cast).anyMatch(l->l.getText().contains("Chế độ FULL"))),"FULL GUI mode");
        check(monitoringThreads().size()==3,"One collector/delivery/full worker");
        Path edge=Path.of(System.getenv("ProgramFiles(x86)"),"Microsoft/Edge/Application/msedge.exe");
        check(Files.isRegularFile(edge),"Owned Edge TEST executable available");
        lifecycleEdge=new ProcessBuilder(edge.toString(),"--headless=new","--disable-gpu","--no-first-run",
                "--no-default-browser-check","--disable-background-networking","--remote-debugging-port=0",
                "--user-data-dir="+output.resolve("owned-edge-profile"),"about:blank")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        until(()->state(reading).getAsJsonArray("processes").asList().stream().anyMatch(p->
                p.getAsJsonObject().get("pid").getAsLong()==lifecycleEdge.pid()
                && p.getAsJsonObject().get("startInstant").getAsString().equals(lifecycleEdge.info().startInstant().orElseThrow().toString())),
                "FULL PID/start matches independently owned ProcessHandle");
        until(()->response("/api/v1/monitoring/attempts/"+reading+"/events",proctorToken,null)
                .getAsJsonArray("events").asList().stream().anyMatch(e->e.getAsJsonObject().get("processName").getAsString().equals("msedge.exe")),
                "Durable process history via assigned proctor API");
        until(()->fx(()->((TableView<?>)proctorStage.getScene().lookup("#dashboard-roster")).getItems().stream()
                .anyMatch(p->p instanceof vn.edu.toeic.client.dashboard.MonitoringData.Presence row&&row.attemptId().equals(reading))),"Proctor rendered assigned attempt");
        fx(()->{var table=(TableView<?>)proctorStage.getScene().lookup("#dashboard-roster");
            for(int i=0;i<table.getItems().size();i++)if(table.getItems().get(i) instanceof vn.edu.toeic.client.dashboard.MonitoringData.Presence row
                    &&row.attemptId().equals(reading)){table.getSelectionModel().select(i);break;}return null;});
        until(()->fx(()->((TableView<?>)proctorStage.getScene().lookup("#dashboard-current-processes")).getItems().stream()
                .anyMatch(p->p instanceof vn.edu.toeic.protocol.monitoring.FullSnapshotPayload.Process row&&row.pid()==lifecycleEdge.pid())),"Proctor current process table");
        selectProctorTab("Cảnh báo process");
        until(()->fx(()->((TableView<?>)proctorStage.getScene().lookup("#dashboard-events")).getItems().stream()
                .anyMatch(p->p instanceof vn.edu.toeic.client.dashboard.MonitoringData.Event row
                    &&row.attemptId().equals(reading)&&row.processName().equals("msedge.exe"))),"Proctor history table");
        screenshot(proctorStage,"A-proctor-history.private.png");
        JsonObject first=state(reading); lifecyclePoint("A-login-start","PASS",first);

        Set<Long> oldWorkers=monitoringThreads();
        clickText(candidateStage,"Dừng giám sát");
        until(()->monitoringThreads().isEmpty(),"Stop completes all monitoring workers");
        JsonObject stopped=state(reading); lifecyclePoint("B-stop","PASS",stopped);
        // CLOSE state and heartbeat timeout are separate contracts; do not require immediate UNKNOWN.
        clickText(candidateStage,"Bắt đầu giám sát");
        until(()->state(reading).get("status").getAsString().equals("SYNCED")
                && !state(reading).get("syncEpoch").equals(first.get("syncEpoch")),"Fresh epoch after restart");
        check(Collections.disjoint(oldWorkers,monitoringThreads())&&monitoringThreads().size()==3,"No old or overlapping monitoring worker");
        lifecyclePoint("B-restart","PASS",state(reading));

        clickText(candidateStage,"Đăng xuất");
        until(()->monitoringThreads().isEmpty(),"Logout worker cleanup");
        until(()->fx(()->!candidateStage.getScene().getRoot().lookupAll(".password-field").isEmpty()),"Logout login screen");
        login(candidateStage,"candidate2");
        String secondSession="GUI-C2-"+run;
        proctorApi.createSession(new CreateSessionRequest(secondSession,"GUI-READING-"+run,"TEST second candidate",600,
                List.of("proctor1"),List.of("candidate2"))).get(20,TimeUnit.SECONDS);
        clickText(candidateStage,"🔄 Làm mới danh sách ca thi");
        until(()->fx(()->((ComboBox<?>)candidateStage.getScene().lookup("#candidate-attempts")).getItems()
                .contains(secondSession+"-candidate2")),"Fresh candidate2 scope");
        selectAttempt(secondSession+"-candidate2"); clickText(candidateStage,"Bắt đầu giám sát");
        until(()->state(secondSession+"-candidate2").get("status").getAsString().equals("SYNCED"),"Candidate2 FULL state");
        check(!state(secondSession+"-candidate2").get("collectorSessionId").equals(first.get("collectorSessionId")),"Cross-account collector identity isolation");
        lifecyclePoint("C-other-account","PASS",state(secondSession+"-candidate2"));
        clickText(candidateStage,"Đăng xuất"); until(()->monitoringThreads().isEmpty(),"Candidate2 logout cleanup");
        login(candidateStage,"candidate1"); selectAttempt(reading);
        clickId(candidateStage,"candidate-enter-exam");
        until(()->enabled(candidateStage,"exam-submit"),"Real exam entry");
        until(()->state(reading).get("status").getAsString().equals("SYNCED"),"Exam monitoring active");
        lifecyclePoint("D-before-submit","PASS",state(reading));
        Platform.runLater(()->((Button)candidateStage.getScene().lookup("#exam-submit")).fire());
        until(ReadingListeningPostgresGuiSmoke::confirmSubmit,"Real submit confirmation");
        until(()->"SUBMITTED".equals(status(reading).state()),"Real HTTP submit COMMIT");
        until(()->monitoringThreads().isEmpty(),"Final submit monitoring workers stopped");
        lifecyclePoint("D-submitted","PASS",null);
        closeResult();
        until(()->fx(()->candidateStage.getScene().lookup("#candidate-attempts")!=null),"Portal after result");

        String timeoutSession="GUI-T-"+run, timed=timeoutSession+"-candidate1";
        proctorApi.createSession(new CreateSessionRequest(timeoutSession,"GUI-READING-"+run,"TEST natural deadline",8,
                List.of("proctor1"),List.of("candidate1"))).get(20,TimeUnit.SECONDS);
        clickText(candidateStage,"🔄 Làm mới danh sách ca thi");
        until(()->fx(()->((ComboBox<?>)candidateStage.getScene().lookup("#candidate-attempts")).getItems().contains(timed)),"Short attempt assigned");
        selectAttempt(timed); clickId(candidateStage,"candidate-enter-exam");
        until(()->enabled(candidateStage,"exam-submit"),"Short exam entered");
        until(()->state(timed).get("status").getAsString().equals("SYNCED"),"Short exam FULL baseline");
        lifecyclePoint("E-before-deadline","PASS",state(timed));
        until(()->"TIMED_OUT".equals(status(timed).state()),"Server natural timeout without SQL transitions");
        until(()->monitoringThreads().isEmpty(),"Natural timeout monitoring worker cleanup");
        lifecyclePoint("E-timed-out","PASS",null); closeResult();
        until(()->fx(()->candidateStage.getScene().lookup("#candidate-attempts")!=null),"Portal after timeout");

        selectAttempt("GUI-L-"+run+"-candidate1"); clickText(candidateStage,"Bắt đầu giám sát");
        until(()->monitoringThreads().size()==3,"Active monitoring before app stop");
        lifecyclePoint("F-before-exit","PASS",null);
        // Fixture APIs can lazily create identically named workers after the initial census.
        // Close these owned test resources before asserting application executor cleanup.
        candidateApi.close(); proctorApi.close();
        fx(()->{candidateApp.stop();candidateStage.close();proctorApp.stop();proctorStage.close();return null;});
        until(()->monitoringThreads().isEmpty(),"Application.stop cleanup");
        until(()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()
                &&t.getName().startsWith("toeic-")&&!fixtureThreads.contains(t.threadId())),"Owned application executors terminate");
        check(!fx(()->candidateStage.isShowing()||proctorStage.isShowing()),"Both stages closed");
        lifecyclePoint("F-application-stop","PASS",null);
        pass("FULL lifecycle A-F: production Application controls, REAL HTTP/WS/PG; desktop/human NOT RUN");
    }
    private void selectAttempt(String attempt)throws Exception {
        until(()->fx(()->candidateStage.getScene().lookup("#candidate-attempts") instanceof ComboBox<?> box
                && !box.isDisabled()&&box.getItems().contains(attempt)),"Selectable assigned attempt");
        fx(()->{((ComboBox<String>)candidateStage.getScene().lookup("#candidate-attempts")).setValue(attempt);return null;});
    }
    private void manualDesktop()throws Exception {
        Path jar=Path.of("client/target/client-0.1.0-SNAPSHOT-all.jar").toAbsolutePath();
        List<Process> apps=new ArrayList<>();
        try {
            for(String role:List.of("candidate","proctor"))apps.add(new ProcessBuilder(
                    Path.of(System.getProperty("java.home"),"bin","javaw.exe").toString(),
                    "-Dtoeic.server.url="+origin,"-Dtoeic.realtime.delta-experimental=false",
                    "-Duser.home="+output.resolve(role+"-profile"),"-jar",jar.toString())
                    .redirectOutput(output.resolve(role+".private.log").toFile())
                    .redirectError(output.resolve(role+".private.err").toFile()).start());
            Files.writeString(output.resolve("manual-credentials.private.txt"),"TEST loopback only\nServer: "+origin
                    +"\nCandidate: candidate1\nProctor: proctor1\nPassword: "+required("TOEIC_SEED_PASSWORD")
                    +"\nUse GUI-R-"+run+"-candidate1 for FULL lifecycle. No real credentials.\n");
            JsonObject ready=new JsonObject();ready.addProperty("candidatePid",apps.get(0).pid());
            ready.addProperty("proctorPid",apps.get(1).pid());ready.addProperty("serverPid",server.pid());
            ready.addProperty("source",System.getProperty("integration.runtimeSource","UNVERSIONED"));
            ready.addProperty("desktopAcceptance","NOT_RUN until human action and API/worker evidence");
            Files.writeString(output.resolve("manual-ready.json"),json.toJson(ready));
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(240);
            while(System.nanoTime()<end&&!Files.exists(output.resolve("manual-done.flag"))
                    &&apps.stream().anyMatch(Process::isAlive))Thread.sleep(200);
            ready.addProperty("candidateExitedBeforeTeardown",!apps.get(0).isAlive());
            ready.addProperty("proctorExitedBeforeTeardown",!apps.get(1).isAlive());
            Files.writeString(output.resolve("manual-end.json"),json.toJson(ready));
        } finally {
            for(Process app:apps)if(app.isAlive()){app.destroy();if(!app.waitFor(5,TimeUnit.SECONDS)){app.destroyForcibly();app.waitFor(5,TimeUnit.SECONDS);}}
            Files.writeString(output.resolve("manual-cleanup.json"),"{\"ownedAppsAlive\":"+apps.stream().filter(Process::isAlive).count()+"}");
        }
    }
    private void closeResult()throws Exception {
        until(()->fx(()->{for(Window w:List.copyOf(Window.getWindows()))
            if(w instanceof Stage stage && stage!=candidateStage&&stage!=proctorStage&&stage.getScene()!=null) {
                for(var node:stage.getScene().getRoot().lookupAll(".button"))if(node instanceof Button b
                    && b.getText().equals("Hoàn Tất & Thoát")&&!b.isDisabled()){b.fire();return true;}
            }return false;}),"Result dialog closed");
    }
    private JsonObject state(String attempt)throws Exception {
        return response("/api/v1/monitoring/attempts/"+attempt+"/state",proctorToken,null);
    }
    private static Set<Long> monitoringThreads() {
        Set<Long> ids=new HashSet<>();
        for(Thread t:Thread.getAllStackTraces().keySet())if(t.isAlive()&&Set.of("toeic-process-collector","toeic-monitoring-delivery","toeic-full-snapshot").contains(t.getName()))ids.add(t.threadId());
        return ids;
    }
    private void lifecyclePoint(String id,String status,JsonObject state)throws Exception {
        JsonObject point=new JsonObject(); point.addProperty("case",id); point.addProperty("status",status);
        point.addProperty("input","PROGRAMMATIC production controls; desktop/human NOT_RUN");
        point.addProperty("at",Instant.now().toString()); point.addProperty("ownedJvmPid",ProcessHandle.current().pid());
        point.addProperty("monitoringWorkers",monitoringThreads().size());
        if(state!=null){point.addProperty("state",state.get("status").getAsString());point.add("sequence",state.get("sequence"));}
        Files.writeString(output.resolve(id+".json"),json.toJson(point));
        if(candidateStage!=null&&fx(()->candidateStage.isShowing()))screenshot(candidateStage,id+".private.png");
        Process dump=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","jcmd.exe").toString(),
                Long.toString(ProcessHandle.current().pid()),"Thread.print").redirectErrorStream(true)
                .redirectOutput(output.resolve(id+".threads.private.txt").toFile()).start();
        if(!dump.waitFor(10,TimeUnit.SECONDS)){dump.destroyForcibly();throw new IllegalStateException("Owned jcmd timeout");}
        check(dump.exitValue()==0,"Owned jcmd exit"); System.out.println("LIFECYCLE "+json.toJson(point));
    }

    private static void ensureVisible(Stage stage,String id)throws Exception {
        fx(()->{
            var node=stage.getScene().lookup("#"+id);if(node==null)throw new IllegalStateException("Missing control "+id);
            javafx.scene.Node ancestor=node;
            while(ancestor!=null&&!(ancestor instanceof ScrollPane))ancestor=ancestor.getParent();
            if(ancestor instanceof ScrollPane scroll){
                var bounds=scroll.getContent().sceneToLocal(node.localToScene(node.getBoundsInLocal()));
                double maximum=scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight();
                scroll.setVvalue(maximum<=0?0:Math.max(0,Math.min(1,(bounds.getMinY()-scroll.getViewportBounds().getHeight()*.25)/maximum)));
                scroll.applyCss();scroll.layout();
            }
            return null;
        });
        fx(()->{
            var node=stage.getScene().lookup("#"+id);if(!node.isVisible()||node.localToScreen(node.getBoundsInLocal())==null)throw new IllegalStateException("Control not visible "+id);
            javafx.scene.Node ancestor=node.getParent();
            while(ancestor!=null){if(ancestor instanceof ScrollPane scroll){
                var bounds=node.localToScene(node.getBoundsInLocal());var viewport=scroll.lookup(".viewport");
                if(viewport==null||!viewport.localToScene(viewport.getBoundsInLocal()).intersects(bounds))throw new IllegalStateException("Control clipped "+id);
                break;
            }ancestor=ancestor.getParent();}
            return null;
        });
    }

    private void selectProctorTab(String title)throws Exception {
        String nav=switch(title){
            case "Tiến độ đã lưu","Process hiện tại" -> "nav-candidates";
            case "Cảnh báo process" -> "nav-history";
            case "Điều khiển ca / kết quả" -> "nav-session-controls";
            default -> throw new IllegalArgumentException("Unknown proctor destination: "+title);
        };
        clickId(proctorStage,nav);
        if(nav.equals("nav-session-controls"))fx(()->{
            var tabs=(TabPane)proctorStage.getScene().lookup("#session-controls-tabs");
            tabs.getSelectionModel().select(tabs.getTabs().stream().filter(t->"tab-v1".equals(t.getId())).findFirst().orElseThrow());return null;
        });
    }
    private static boolean confirmOk()throws Exception{return fx(()->{for(Window window:List.copyOf(Window.getWindows()))if(window.getScene()!=null&&window.getScene().getRoot() instanceof DialogPane pane&&pane.getButtonTypes().contains(ButtonType.OK)){((Button)pane.lookupButton(ButtonType.OK)).fire();return true;}return false;});}

    private void enter(String attempt) throws Exception {
        until(() -> fx(() -> candidateStage.getScene().lookup("#candidate-enter-exam") != null), "Exam portal");
        fx(() -> { ((ComboBox<String>) candidateStage.getScene().lookup("#candidate-attempts")).setValue(attempt); return null; });
        clickId(candidateStage, "candidate-enter-exam");
        until(() -> enabled(candidateStage, attempt.startsWith("GUI-L") ? "option-L1-A" : "option-R1-A"), "Editable exam");
    }
    private void login(Stage stage, String user) throws Exception {
        fx(() -> {
            ((TextField) stage.getScene().lookup("#login-server-url")).setText(origin);
            ((TextField) stage.getScene().lookup("#login-username")).setText(user);
            ((PasswordField) stage.getScene().lookup("#login-password")).setText(required("TOEIC_SEED_PASSWORD"));
            button(stage, "Đăng nhập").fire(); return null;
        });
        until(() -> fx(() -> stage.getScene().getRoot().lookupAll(".password-field").isEmpty()), "Production login " + user);
    }
    private CandidateAttemptStatusResponse status(String attempt) throws Exception { return candidateApi.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS); }
    private JsonObject response(String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token);
        if (body == null) builder.GET(); else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.toJson(body)));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode() + " at " + path);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private static ExamQuestionImportDto question(String id, String section, String audio, int order) {
        return new ExamQuestionImportDto(id, section, audio == null ? 5 : 1, null, null, audio, "TEST " + id, "A", order,
                List.of(new ExamOptionImportDto("A", "TEST A", 1), new ExamOptionImportDto("B", "TEST B", 2)));
    }
    private static CreateSessionRequest session(String id, String exam) { return new CreateSessionRequest(id, exam, "TEST GUI", 600, List.of("proctor1"), List.of("candidate1")); }
    private Connection db() throws Exception { return DriverManager.getConnection("jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME"), required("DB_USER"), required("DB_PASSWORD")); }
    private void startServer(Path audioRoot) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), "-jar", "server/target/server-0.1.0-SNAPSHOT.jar",
                "--server.address=127.0.0.1", "--server.port=" + port,
                "--spring.datasource.url=jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME") + "?currentSchema=" + schema,
                "--spring.flyway.default-schema=" + schema, "--spring.flyway.schemas=" + schema, "--logging.level.root=WARN", "--toeic.audio.root=" + audioRoot,
                "--toeic.state.delta-enabled=false","--toeic.monitoring.signal-experiment-enabled=false");
        server = process.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(output.resolve("owned-server.log").toFile())).start();
        until(() -> {
            if (!server.isAlive()) throw new IllegalStateException("Owned server exited");
            try {
                if (http.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/me")).timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() != 401) return false;
                // Tomcat can accept requests before the seed ApplicationRunner finishes.
                try (Connection db = db(); Statement sql = db.createStatement(); ResultSet rows = sql.executeQuery(
                        "SELECT count(*) FROM " + schema + ".user_accounts WHERE username IN ('candidate1','candidate2','proctor1')")) {
                    return rows.next() && rows.getInt(1) == 3;
                }
            } catch (java.io.IOException | SQLException pending) { return false; }
        }, "Owned Spring server");
    }
    private void restartServer(Path root) throws Exception { stopServer(); startServer(root); }
    private void stopServer() throws Exception { if (server != null && server.isAlive()) { server.destroy(); if (!server.waitFor(10, TimeUnit.SECONDS)) { server.destroyForcibly(); server.waitFor(10, TimeUnit.SECONDS); } } }
    private static void createWave(Path file, int seconds) throws Exception {
        Files.createDirectories(file.getParent()); int rate = 22050, bytes = rate * 2 * seconds;
        ByteBuffer wave = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
        wave.put(new byte[]{'R','I','F','F'}).putInt(36 + bytes).put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1)
                .putInt(rate).putInt(rate * 2).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(bytes);
        for (int i = 0; i < rate * seconds; i++) wave.putShort((short)(2000 * Math.sin(2 * Math.PI * 440 * i / rate)));
        Files.write(file, wave.array());
    }
    private static <T> T fx(Callable<T> task) throws Exception { CompletableFuture<T> result = new CompletableFuture<>(); Platform.runLater(() -> { try { result.complete(task.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } }); return result.get(15, TimeUnit.SECONDS); }
    private static Button button(Stage stage, String text) { return stage.getScene().getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
    private static void clickText(Stage stage, String text) throws Exception { until(() -> enabledText(stage, text), "Enabled " + text); fx(() -> { button(stage, text).fire(); return null; }); }
    private static boolean enabledText(Stage stage, String text) throws Exception { return fx(() -> { try { return !button(stage, text).isDisabled(); } catch (NoSuchElementException pending) { return false; } }); }
    private static boolean enabled(Stage stage, String id) throws Exception { return fx(() -> stage.getScene().lookup("#" + id) instanceof ButtonBase button && !button.isDisabled()); }
    private static void clickId(Stage stage, String id) throws Exception { until(() -> enabled(stage, id), "Enabled " + id); fx(() -> { ((ButtonBase)stage.getScene().lookup("#" + id)).fire(); return null; }); }
    private static boolean confirmSubmit() throws Exception { return fx(() -> { for (Window window : List.copyOf(Window.getWindows())) if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane dialog && dialog.getButtonTypes().contains(ButtonType.YES)) { ((Button) dialog.lookupButton(ButtonType.YES)).fire(); return true; } return false; }); }
    private static void until(Callable<Boolean> condition, String label) throws Exception { long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60); while (!condition.call()) { if (System.nanoTime() >= deadline) throw new IllegalStateException("Timed out: " + label); Thread.sleep(50); } }
    private void check(boolean value, String label) { if (!value) throw new IllegalStateException(label); }
    private void pass(String label) { passed++; System.out.println("PASS " + label); }
    private void screenshot(Stage stage, String name) throws Exception {
        WritableImage image = fx(() -> stage.getScene().snapshot(null)); int w = (int)image.getWidth(), h = (int)image.getHeight();
        var bitmap = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[w*h]; image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, w);
        bitmap.setRGB(0, 0, w, h, pixels, 0, w); javax.imageio.ImageIO.write(bitmap, "png", output.resolve(name).toFile());
    }
    private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment " + key); return value; }
    private void cleanup() {
        if(lifecycleEdge!=null){
            List<ProcessHandle> owned=new ArrayList<>(lifecycleEdge.descendants().toList());owned.add(lifecycleEdge.toHandle());
            for(ProcessHandle process:owned)if(process.isAlive())process.destroy();
            for(ProcessHandle process:owned)try{process.onExit().get(5,TimeUnit.SECONDS);}catch(Exception pending){process.destroyForcibly();}
            System.out.println("CLEANUP owned Edge alive="+owned.stream().filter(ProcessHandle::isAlive).count());
        }
        try { fx(() -> { if (candidateApp != null) candidateApp.stop(); if (proctorApp != null) proctorApp.stop(); for (Window window : List.copyOf(Window.getWindows())) window.hide(); return null; }); } catch (Exception ignored) { }
        if (candidateApi != null) candidateApi.close(); if (proctorApi != null) proctorApi.close();
        try { stopServer(); } catch (Exception ignored) { }
        try (Connection db = db(); Statement sql = db.createStatement()) { if (!schema.matches("integration_gui_[a-f0-9]{12}")) throw new IllegalStateException("Unsafe owned schema"); sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); System.out.println("CLEANUP owned server/schema " + schema); }
        catch (Exception failure) { System.out.println("CLEANUP FAIL " + failure.getClass().getSimpleName()); }
        Platform.exit(); http.close();
    }
}
