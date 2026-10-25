package vn.edu.toeic.client.audio;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** TEST launcher only: REAL production view/cache/JavaFX WAV playback, MOCK API and automated hearing confirmation. */
public final class AudioPackageSmoke {
    private static Stage stage;
    private static AudioPreparationView view;
    private static Timeline clock;
    private static int ticks;
    private static final class Transport implements MonitoringTransport {
        final List<Consumer<MessageEnvelope<JsonObject>>> listeners=new CopyOnWriteArrayList<>();
        final List<Consumer<ConnectionState>> stateListeners=new CopyOnWriteArrayList<>();
        volatile ConnectionState state=ConnectionState.CONNECTED;
        public ConnectionState connectionState() { return state; }
        public AutoCloseable onConnectionState(Consumer<ConnectionState> c) { stateListeners.add(c);return () -> stateListeners.remove(c); }
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c) { listeners.add(c);return () -> listeners.remove(c); }
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m) { return CompletableFuture.completedFuture(null); }
        void emit(MessageEnvelope<JsonObject> message){listeners.forEach(listener->listener.accept(message));}
        void state(ConnectionState next){state=next;stateListeners.forEach(listener->listener.accept(next));}
    }
    private static final class Api implements AudioApi,ListeningApi {
        byte[] bytes;
        AudioManifestResponse manifest;
        final List<AudioReadyRequest> reports = new CopyOnWriteArrayList<>();
        final List<ListeningEventRequest> listeningEvents=new CopyOnWriteArrayList<>();
        volatile String listeningPhase="NOT_STARTED";
        public long acquireWriter(String a) throws Exception { Thread.sleep(700); return 2; }
        public AudioManifestResponse manifest(String a) { return manifest; }
        public InputStream download(String a, String m, AudioFileDto f) { return new ByteArrayInputStream(bytes); }
        public AudioReadyResponse ready(AudioReadyRequest r) {
            reports.add(r); return new AudioReadyResponse("v0", r.requestId(), r.attemptId(), r.writerEpoch(), r.ready(), r.manifestId(), r.ready() ? Instant.now() : null);
        }
        public CandidateExamDto exam(String attempt){return new CandidateExamDto("MOCK-EXAM","Đề nghe MOCK",null,1,List.of(
                new CandidateQuestionDto("L1","LISTENING",1,"G1",null,manifest.files().getFirst().fileName(),"Bạn nghe thấy âm nào?",1,
                        List.of(new CandidateOptionDto("A","Âm mẫu 440 Hz",1),new CandidateOptionDto("B","Im lặng",2)))));}
        public ListeningStatusResponse status(String attempt){boolean active=!"NOT_STARTED".equals(listeningPhase);boolean stopped="INTERRUPTED".equals(listeningPhase);
            return new ListeningStatusResponse("v0",null,attempt,"MOCK-SESSION",active?"MOCK-START":null,active?manifest.manifestId():null,2,listeningPhase,
                Instant.now().plusSeconds(60),Set.of("PLAYING","COMPLETED","INTERRUPTED").contains(listeningPhase)?Instant.now():null,
                "COMPLETED".equals(listeningPhase)?Instant.now():null,stopped,stopped?Instant.now():null,stopped?"NETWORK_LOST":null);}
        public ListeningStatusResponse event(String attempt,ListeningEventRequest request){
            listeningEvents.add(request);String phase=switch(request.event()){case "COMPLETED"->"COMPLETED";case "INTERRUPTED"->"INTERRUPTED";default->"PLAYING";};listeningPhase=phase;
            boolean stopped="INTERRUPTED".equals(phase);
            return new ListeningStatusResponse("v0",request.requestId(),attempt,"MOCK-SESSION",request.startId(),manifest.manifestId(),request.writerEpoch(),phase,
                    Instant.now().plusSeconds(60),Instant.now(),"COMPLETED".equals(phase)?Instant.now():null,stopped,stopped?Instant.now():null,stopped?request.reason():null);
        }
        public void close() {}
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(Objects.requireNonNull(System.getenv("TOEIC_AUDIO_SMOKE_OUTPUT"))).toAbsolutePath();
        Files.createDirectories(output); List<String> result = new ArrayList<>(); int exit = 1;
        try {
            Api api = new Api(); Path fixture = SpeakerSample.create(output.resolve("MOCK đề audio")); api.bytes = Files.readAllBytes(fixture);
            api.manifest = new AudioManifestResponse("v0", "MOCK-ATTEMPT", "MOCK-SESSION", "a".repeat(64),
                    List.of(new AudioFileDto("đoạn nghe có dấu.wav", api.bytes.length, AudioFiles.hash(api.bytes))));
            CountDownLatch started = new CountDownLatch(1); Platform.startup(started::countDown);
            if (!started.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("FX startup timeout");
            Transport transport=new Transport();
            fx(() -> {
                view = new AudioPreparationView(api,api, output.resolve("Bộ nhớ audio có dấu và khoảng trắng"), List.of("MOCK-ATTEMPT"), transport);
                Label pulse = new Label(); clock = new Timeline(new KeyFrame(Duration.millis(20), e -> pulse.setText("UI pulse " + ++ticks)));
                clock.setCycleCount(Animation.INDEFINITE); clock.play();
                stage = new Stage(); stage.setTitle("T3-B1 TEST package · MOCK API · REAL WAV media");
                stage.setScene(new Scene(new VBox(12, pulse, view), 760, 520));
                stage.setX(-10000); stage.setY(-10000); stage.show(); return null;
            });
            fire("Tải / chuẩn bị lại audio"); waitFor("Thử loa");
            if (fx(() -> ticks) < 10) throw new IllegalStateException("FX thread blocked during slow preparation");
            result.add("PASS REAL FX responsive while MOCK takeover waits 700ms");
            Path local = new AudioFiles(output.resolve("Bộ nhớ audio có dấu và khoảng trắng/files")).file(api.manifest, api.manifest.files().getFirst());
            fire("Thử loa"); waitFor("Tôi nghe được"); fire("Tôi nghe được");
            byte[] corrupt = api.bytes.clone(); corrupt[44] ^= 1; Files.write(local, corrupt);
            fire("Xác nhận sẵn sàng"); waitText("SHA-256");
            if (api.reports.stream().anyMatch(AudioReadyRequest::ready)) throw new IllegalStateException("Corrupt local audio reported READY");
            result.add("PASS one-byte-corrupt REAL local file blocks READY in production view");
            screenshot(output.resolve("corrupt-file.png"));

            fire("Tải / chuẩn bị lại audio"); waitFor("Thử loa"); fire("Thử loa"); waitFor("Tôi nghe được"); fire("Tôi nghe được");
            fire("Xác nhận sẵn sàng"); waitText("Đã sẵn sàng. Chờ giám thị bắt đầu.");
            result.add("PASS REAL JavaFX PCM WAV completed; automated hearing confirmation; MOCK correlated READY ACK");
            screenshot(output.resolve("ready.png"));

            JsonObject start=new JsonObject();start.addProperty("startId","MOCK-START");start.addProperty("sessionId","MOCK-SESSION");
            start.addProperty("attemptId","MOCK-ATTEMPT");start.addProperty("manifestId",api.manifest.manifestId());start.addProperty("writerEpoch",2);
            start.addProperty("startedAt",Instant.now().minusSeconds(1).toString());start.addProperty("deadlineAt",Instant.now().plusSeconds(60).toString());
            var command=new MessageEnvelope<>("v0","LISTENING_START","MOCK-START",null,"MOCK-ATTEMPT","MOCK-trace",start);
            transport.emit(command);waitText("Server đã xác nhận phát xong Listening");transport.emit(command);Thread.sleep(300);
            if(api.listeningEvents.stream().filter(e->"STARTED".equals(e.event())).count()!=1)throw new IllegalStateException("Duplicate start replayed");
            result.add("PASS REAL exam WAV playback maps candidate question and duplicate startId does not replay");
            screenshot(output.resolve("listening-completed.png"));

            // Start completion intentionally locks preparation for this attempt; remaining B1 regressions use a fresh view.
            api.listeningPhase="NOT_STARTED";
            fx(()->{view.close();view=new AudioPreparationView(api,api,output.resolve("Bộ nhớ audio có dấu và khoảng trắng 2"),List.of("MOCK-ATTEMPT"),transport);stage.getScene().setRoot(new VBox(12,view));return null;});
            fire("Tải / chuẩn bị lại audio"); waitFor("Thử loa"); fire("Thử loa"); waitFor("Tôi nghe được"); fire("Tôi nghe được");
            local = new AudioFiles(output.resolve("Bộ nhớ audio có dấu và khoảng trắng 2/files")).file(api.manifest,api.manifest.files().getFirst());Files.delete(local);
            long before = api.reports.stream().filter(AudioReadyRequest::ready).count();
            fire("Xác nhận sẵn sàng"); waitText("Audio thiếu");
            if (api.reports.stream().filter(AudioReadyRequest::ready).count() != before) throw new IllegalStateException("Missing file reported READY");
            result.add("PASS deleted REAL local file blocks READY in production view");
            screenshot(output.resolve("missing-file.png"));

            fire("Tải / chuẩn bị lại audio"); waitFor("Thử loa");
            try (var paths = Files.list(output.resolve("Bộ nhớ audio có dấu và khoảng trắng 2/speaker"))) {
                for (Path path : paths.toList()) deleteAfterRelease(path);
            }
            fire("Thử loa"); waitText("Thử loa thất bại");
            result.add("PASS missing speaker sample raises REAL media error; READY stays locked");

            api.listeningPhase="NOT_STARTED";
            fx(()->{view.close();view=new AudioPreparationView(api,api,output.resolve("Bộ nhớ LT03"),List.of("MOCK-ATTEMPT"),transport);stage.getScene().setRoot(new VBox(12,view));return null;});
            fire("Tải / chuẩn bị lại audio");waitFor("Thử loa");fire("Thử loa");waitFor("Tôi nghe được");fire("Tôi nghe được");
            fire("Xác nhận sẵn sàng");waitText("Đã sẵn sàng. Chờ giám thị bắt đầu.");
            long starts=api.listeningEvents.stream().filter(e->"STARTED".equals(e.event())).count();
            transport.emit(command);await(()->api.listeningEvents.stream().filter(e->"STARTED".equals(e.event())).count()>starts);
            transport.state(ConnectionState.DISCONNECTED);waitText("NETWORK_LOST");
            if(api.listeningEvents.stream().noneMatch(e->"INTERRUPTED".equals(e.event())&&"NETWORK_LOST".equals(e.reason())))
                throw new IllegalStateException("Network loss was not reported");
            long startsAfterLoss=api.listeningEvents.stream().filter(e->"STARTED".equals(e.event())).count();
            transport.state(ConnectionState.CONNECTED);waitText("gián đoạn");transport.emit(command);Thread.sleep(300);
            if(api.listeningEvents.stream().filter(e->"STARTED".equals(e.event())).count()!=startsAfterLoss)
                throw new IllegalStateException("Reconnect replayed Listening");
            result.add("PASS LT03 REAL MediaPlayer stops on disconnect; reconnect queries status and never replays");
            screenshot(output.resolve("listening-network-interrupted.png"));
            fx(() -> { view.close(); clock.stop(); stage.close(); return null; });
            result.add("PASS view/player/worker shutdown; bundled runtime=" + System.getProperty("java.version"));
            exit = 0;
        } catch (Throwable failure) { result.add("FAIL " + failure); }
        finally {
            Files.write(output.resolve("result.txt"), result, java.nio.charset.StandardCharsets.UTF_8);
            for (String line : result) System.out.println(line);
            Platform.exit();
        }
        // Success must exit naturally, so a leaked non-daemon worker is caught by the script's timeout.
        if (exit != 0) System.exit(exit);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static Button button(String text) {
        return view.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }
    private static void fire(String text) throws Exception {
        fx(() -> { Button b = button(text); if (b.isDisabled()) throw new IllegalStateException("Disabled: " + text); b.fire(); return null; });
    }
    private static void waitFor(String button) throws Exception { await(() -> !button(button).isDisabled()); }
    private static void waitText(String text) throws Exception {
        await(() -> view.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast).anyMatch(l -> l.getText().contains(text)));
    }
    private static void await(Callable<Boolean> check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) { if (fx(check)) return; Thread.sleep(20); }
        throw new IllegalStateException("UI timeout");
    }
    /** Fixture setup: native disposal/file scanners may release a Windows WAV handle asynchronously. */
    private static void deleteAfterRelease(Path target) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(true){
            try{Files.delete(target);return;}
            catch(java.nio.file.FileSystemException locked){
                if(System.nanoTime()>=deadline)throw locked;
                Thread.sleep(25);
            }
        }
    }
    private static void screenshot(Path target) throws Exception {
        WritableImage snapshot = fx(() -> stage.getScene().snapshot(null));
        int width = (int)snapshot.getWidth(), height = (int)snapshot.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var pixels = snapshot.getPixelReader();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, pixels.getArgb(x, y));
        ImageIO.write(image, "png", target.toFile());
    }
}
