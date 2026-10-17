package vn.edu.toeic.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import vn.edu.toeic.client.monitoring.*;
import vn.edu.toeic.client.monitoring.trace.TraceRecorder;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Owned child JVM: production Application login/buttons/scope/factory. Optional TEST observer/source fault seams.
 * Normal/default child has no seam. Instrumented readings delegate to REAL ProcessHandle; faults SIMULATED.
 */
public final class LiveCandidateApp {
    private static final Gson GSON=new Gson();
    private static final String REPLY="GATE_REPLY ";
    private final ControlSource source=new ControlSource();
    private final boolean instrumented=Boolean.getBoolean("toeic.test.gate.instrumented");
    private final Path output=Path.of(System.getProperty("toeic.test.gate.output"));
    private ToeicClientApplication app;private Stage stage;private FaultTransport faults;
    private final AtomicInteger requestNumber=new AtomicInteger();
    public static void main(String[] args) throws Exception {
        var child=new LiveCandidateApp();int result=0;
        try{child.run();}catch(Exception error){System.err.println("TEST_GATE_CHILD_FAIL class="+error.getClass().getSimpleName());result=1;}
        finally{child.source.resume();if(child.faults!=null)child.faults.close();try{fx(()->{if(child.app!=null)child.app.stop();if(child.stage!=null)child.stage.close();return null;});}catch(Exception ignored){ }Platform.exit();}
        System.exit(result);
    }
    private void run() throws Exception {
        Files.createDirectories(output);Platform.startup(()->{});Platform.setImplicitExit(false);
        fx(()->{
            app=instrumented?new ToeicClientApplication((transport,listener)->{
                faults=new FaultTransport(transport,source,output.resolve("fault-wire.jsonl"));
                Duration poll=Duration.ofMillis(Long.getLong("toeic.monitoring.pollMillis",1000));
                ObservationTrace trace=new ObservationTrace(){ObservationTrace writer=ObservationTrace.NONE;
                    @Override public void started(String c,String p,long n){writer=TraceRecorder.configured(c,poll,true);writer.started(c,p,n);}
                    @Override public void observed(ProcessSnapshot s){writer.observed(s);}
                    @Override public void failed(String c,String p,long n){writer.failed(c,p,n);}
                    @Override public void stopped(String c,long n){writer.stopped(c,n);}
                };
                return new CandidateMonitoringSession(new ProcessCollector(source,poll,trace),faults,MonitoringDelivery.Settings.configured(),listener);
            }):new ToeicClientApplication();
            stage=new Stage();app.start(stage);stage.setTitle("TEST T3-C4 candidate · "+(instrumented?"REAL source / SIMULATED faults":"production default factory"));stage.setHeight(800);
            var fields=nodes(stage.getScene().getRoot()).stream().filter(n->n instanceof TextField&&!(n instanceof PasswordField)).map(n->(TextField)n).toList();
            fields.get(0).setText(System.getProperty("toeic.server.url"));fields.get(1).setText(System.getenv("TEST_GATE_USERNAME"));
            ((PasswordField)nodes(stage.getScene().getRoot()).stream().filter(n->n instanceof PasswordField).findFirst().orElseThrow()).setText(System.getenv("TEST_GATE_PASSWORD"));
            button("Đăng nhập").fire();return null;
        });
        System.out.println("GATE_READY instrumented="+instrumented+" source=REAL_PROCESS_HANDLE faults="+(instrumented?"SIMULATED":"NONE"));
        try(var input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while((line=input.readLine())!=null) {
                var command=JsonParser.parseString(line).getAsJsonObject();String id=command.get("id").getAsString(),action=command.get("action").getAsString();
                var reply=new JsonObject();reply.addProperty("id",id);
                try {
                    switch(action) {
                        case "STATUS" -> { }
                        case "START" -> fx(()->{combo().setValue(command.get("attempt").getAsString());button("Bắt đầu giám sát").fire();return null;});
                        case "STOP" -> fx(()->{button("Dừng giám sát").fire();return null;});
                        case "LOGOUT" -> fx(()->{button("Đăng xuất").fire();return null;});
                        case "SCREENSHOT" -> screenshot(command.get("name").getAsString());
                        case "PAUSE" -> source.pause();
                        case "RESUME" -> {source.failed=false;source.resume();}
                        case "FAIL_SCAN" -> {source.failed=true;source.resume();}
                        case "ACK_DROP_ONCE" -> {requiredFaults();faults.arm="DROP_ONCE";}
                        case "ACK_DROP_ALL" -> {requiredFaults();faults.arm="DROP_ALL";}
                        case "FUTURE_BASE" -> {requiredFaults();faults.arm="FUTURE_BASE";}
                        case "ALLOW_ACK" -> {requiredFaults();faults.dropAll=false;}
                        case "RELEASE_OLD_ACK" -> {requiredFaults();if(faults.held!=null)faults.broadcast(faults.held);}
                        case "REPLAY_STATE" -> {requiredFaults();if(faults.accepted==null)throw new IllegalStateException("No accepted state");faults.send(faults.accepted).get(5,TimeUnit.SECONDS);}
                        case "CLOSE" -> {
                            source.resume();fx(()->{app.stop();stage.close();return null;});
                            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                            while(System.nanoTime()<until&&workers()>0)Thread.sleep(25);
                            reply.addProperty("ok",true);reply.addProperty("workers",workers());System.out.println(REPLY+reply);return;
                        }
                        default -> throw new IllegalArgumentException("Unknown test action");
                    }
                    reply.addProperty("ok",true);reply.add("status",status());
                }catch(Exception error){reply.addProperty("ok",false);reply.addProperty("errorClass",error.getClass().getSimpleName());reply.addProperty("errorMessage",String.valueOf(error.getMessage()));}
                System.out.println(REPLY+reply);
            }
        }
    }
    private void requiredFaults(){if(faults==null)throw new IllegalStateException("Fault seam disabled");}
    private JsonObject status() throws Exception {
        return fx(()->{
            var result=new JsonObject();var session=(CandidateMonitoringSession)field(app,"monitoring");var client=(RealtimeClient)field(app,"realtimeClient");
            result.addProperty("loginReady",nodes(stage.getScene().getRoot()).stream().anyMatch(n->n instanceof Button b&&b.getText().equals("Đăng xuất")));
            result.addProperty("startEnabled",nodes(stage.getScene().getRoot()).stream().anyMatch(n->n instanceof Button b&&b.getText().equals("Bắt đầu giám sát")&&!b.isDisabled()));
            result.addProperty("connection",client==null?"NONE":client.connectionState().name());result.addProperty("capability",client==null?"UNKNOWN":client.deltaCapability().name());
            result.addProperty("active",session!=null&&session.isActive());result.add("mode",session==null?null:GSON.toJsonTree(session.modeStatus()));
            result.add("state",session==null?null:GSON.toJsonTree(session.fullStatus()));result.add("events",session==null?null:GSON.toJsonTree(session.status()));
            result.addProperty("sourcePaused",source.paused);result.addProperty("sourceEntered",source.entered);result.addProperty("sourceFailed",source.failed);
            result.addProperty("scans",source.scans.get());result.addProperty("workers",workers());
            if(faults!=null){result.add("acceptedState",faults.accepted==null?null:GSON.toJsonTree(faults.accepted));result.add("sends",GSON.toJsonTree(faults.sends.stream().skip(Math.max(0,faults.sends.size()-12)).toList()));result.add("replies",GSON.toJsonTree(faults.replies.stream().skip(Math.max(0,faults.replies.size()-12)).toList()));}
            result.add("labels",GSON.toJsonTree(nodes(stage.getScene().getRoot()).stream().filter(n->n instanceof Label).map(n->((Label)n).getText()).toList()));return result;
        });
    }
    private static Object field(Object o,String name) throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private Button button(String text){return (Button)nodes(stage.getScene().getRoot()).stream().filter(n->n instanceof Button b&&b.getText().equals(text)).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") private ComboBox<String> combo(){return (ComboBox<String>)nodes(stage.getScene().getRoot()).stream().filter(n->n instanceof ComboBox).findFirst().orElseThrow();}
    private static List<Node> nodes(Node root){var result=new ArrayList<Node>();result.add(root);if(root instanceof Parent p)for(var n:p.getChildrenUnmodifiable())result.addAll(nodes(n));return result;}
    private void screenshot(String name) throws Exception {
        if(!name.matches("[a-z0-9-]+\\.png"))throw new IllegalArgumentException();
        fx(()->{WritableImage image=stage.getScene().snapshot(null);var bitmap=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(bitmap,"png",output.resolve(name).toFile());return null;});
    }
    private static int workers(){return (int)Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&List.of("toeic-process-collector","toeic-full-snapshot","toeic-monitoring-delivery","toeic-trace-writer","toeic-realtime-worker","toeic-measurement-writer").contains(t.getName())).count();}
    private static <T>T fx(java.util.concurrent.Callable<T> action) throws Exception {var f=new CompletableFuture<T>();Platform.runLater(()->{try{f.complete(action.call());}catch(Exception e){f.completeExceptionally(e);}});return f.get(10,TimeUnit.SECONDS);}
    private static final class ControlSource implements ProcessSnapshotSource {
        final ProcessHandleSnapshotSource real=new ProcessHandleSnapshotSource();final AtomicInteger scans=new AtomicInteger();volatile boolean failed,paused,entered;
        @Override public List<ProcessReading> scan() {
            scans.incrementAndGet();synchronized(this){while(paused){entered=true;try{wait();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("TEST interrupted");}}}
            if(failed)throw new IllegalStateException("SIMULATED source failure");return real.scan();
        }
        synchronized void pause(){entered=false;paused=true;}
        synchronized void resume(){paused=false;entered=false;notifyAll();}
    }
    private static final class FaultTransport implements MonitoringTransport,AutoCloseable {
        final MonitoringTransport actual;final ControlSource source;final Path log;final AutoCloseable bridge;
        final List<Consumer<MessageEnvelope<JsonObject>>> listeners=new CopyOnWriteArrayList<>();
        final List<MessageEnvelope<JsonObject>> sends=new CopyOnWriteArrayList<>(),replies=new CopyOnWriteArrayList<>();
        final java.util.Map<String,MessageEnvelope<JsonObject>> sentById=java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());
        volatile String arm="NONE",target;volatile boolean dropAll;volatile MessageEnvelope<JsonObject> held,accepted;
        FaultTransport(MonitoringTransport actual,ControlSource source,Path log) {
            this.actual=actual;this.source=source;this.log=log;bridge=actual.onMessage(m->{
                record("RECEIVED",m);replies.add(m);while(replies.size()>48)replies.removeFirst();
                if(m.type().equals("ACK")&&m.payload().has("acknowledgedType")&&List.of("MONITORING_FULL","MONITORING_DELTA").contains(m.payload().get("acknowledgedType").getAsString()))accepted=sentById.get(m.requestId());
                if(m.type().equals("ACK")&&m.requestId()!=null&&m.requestId().equals(target)&&(held==null||dropAll)){held=m;record("SIMULATED_ACK_OBSERVER_DROP",m);return;}
                broadcast(m);
            });
        }
        void broadcast(MessageEnvelope<JsonObject> m){List.copyOf(listeners).forEach(l->l.accept(m));}
        private MessageEnvelope<JsonObject> plan(MessageEnvelope<JsonObject> m) {
            if(m.type().equals("MONITORING_DELTA")&&!arm.equals("NONE")) {
                String action=arm;arm="NONE";source.pause();
                if(action.equals("FUTURE_BASE")){var body=m.payload().deepCopy();body.addProperty("baseSequence",body.get("baseSequence").getAsLong()+1);body.addProperty("sequence",body.get("sequence").getAsLong()+1);m=new MessageEnvelope<>(m.protocolVersion(),m.type(),m.messageId(),m.requestId(),m.attemptId(),m.traceId(),body);record("SIMULATED_FUTURE_BASE",m);}
                else {target=m.requestId();held=null;dropAll=action.equals("DROP_ALL");}
            }
            record("SEND",m);sends.add(m);while(sends.size()>48)sends.removeFirst();synchronized(sentById){sentById.put(m.requestId(),m);if(sentById.size()>128)sentById.remove(sentById.keySet().iterator().next());}return m;
        }
        synchronized void record(String action,MessageEnvelope<JsonObject> m){try{var row=new JsonObject();row.addProperty("operation",action);row.add("message",GSON.toJsonTree(m));Files.writeString(log,row+"\n",StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}catch(Exception e){throw new IllegalStateException("TEST raw log failed");}}
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){return actual.send(plan(m));}
        @Override public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> m,long g,java.util.function.BooleanSupplier valid){return actual.sendForGeneration(plan(m),g,valid);}
        @Override public DeltaCapability deltaCapability(){return actual.deltaCapability();}
        @Override public boolean supportsMonitoringDelta(){return actual.supportsMonitoringDelta();}
        @Override public ConnectionState connectionState(){return actual.connectionState();}
        @Override public long connectionGeneration(){return actual.connectionGeneration();}
        @Override public vn.edu.toeic.protocol.monitoring.EventOrigin observationOrigin(){return actual.observationOrigin();}
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> l){listeners.add(l);return ()->listeners.remove(l);}
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> l){return actual.onConnectionState(l);}
        @Override public AutoCloseable onMonitoringAccessRejected(Consumer<MonitoringAccessRejection> l){return actual.onMonitoringAccessRejected(l);}
        @Override public AutoCloseable monitoringHeartbeat(String a,String c){return actual.monitoringHeartbeat(a,c);}
        @Override public void forgetPending(String id){actual.forgetPending(id);}
        @Override public void close(){try{bridge.close();}catch(Exception ignored){ }}
    }
}
