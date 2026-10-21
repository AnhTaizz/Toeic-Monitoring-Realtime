package vn.edu.toeic.server.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.stage.Stage;
import vn.edu.toeic.client.monitoring.*;
import vn.edu.toeic.client.monitoring.activity.*;
import vn.edu.toeic.client.monitoring.display.*;
import vn.edu.toeic.client.monitoring.focus.*;
import vn.edu.toeic.client.monitoring.window.*;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.monitoring.usb.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.Role;
import static vn.edu.toeic.server.monitoring.signal.MonitoringTimelinePostgresTest.*;

/** Composition TEST only. Production observers, collector, FULL, delivery and transport are reused unchanged. */
final class JointMonitoringRun implements AutoCloseable {
    final RealtimeClient realtime;
    final ScheduledExecutorService connectionWorker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"toeic-joint-realtime");t.setDaemon(true);return t;});
    final CandidateMonitoringSession process;
    final SignalRunContext run;
    final SignalDelivery delivery;
    final ObserverStatusEmitter statuses=new ObserverStatusEmitter();
    final long statusGeneration;
    final Stage stage;
    final List<AutoCloseable> observers=new ArrayList<>();
    final AtomicInteger polls=new AtomicInteger();
    final CountDownLatch collectorStopped=new CountDownLatch(1);
    UsbScanCoordinator usb;
    UsbScanCoordinator.Source usbSource;
    boolean closed;
    JointMonitoringRun(MonitoringTimelinePostgresTest test,String attempt,String token,Stage stage,long ownedPid)throws Exception {
        this.stage=stage;
        realtime=new RealtimeClient(new AuthenticatedWebSocketOpener(test.base,token),new RealtimeClient.Settings(Duration.ofMillis(100),Duration.ofMillis(100),Duration.ofSeconds(1),1,65536,500),connectionWorker);test.owned.add(realtime);
        var source=new OwnedProcessHandleFixture(ownedPid);
        // Transmit only the owned TEST PID. No process command line or unrelated readings enter evidence/server.
        var collector=new ProcessCollector(()->{polls.incrementAndGet();return source.scan();},Duration.ofMillis(250),new ObservationTrace(){
            @Override public void stopped(String id,long tick){collectorStopped.countDown();}
        });
        process=new CandidateMonitoringSession(collector,realtime,MonitoringDelivery.Settings.defaults(),ignored->{});
        test.owned.add(process);
        assertThatThrownBy(()->process.start(Role.PROCTOR,attempt,Set.of(attempt))).isInstanceOf(IllegalStateException.class);
        assertThat(polls).hasValue(0);
        realtime.connect(new RealtimeClient.Session(Set.of(attempt),null,null)).get(8,TimeUnit.SECONDS);
        String identity=process.start(Role.CANDIDATE,attempt,Set.of(attempt));
        assertThat(process.modeStatus().mode()).isEqualTo("FULL");
        var acknowledged=new CountDownLatch(1);
        try(var listener=realtime.onMessage(m->{if("ACK".equals(m.type())&&"HEARTBEAT".equals(m.payload().get("acknowledgedType").getAsString()))acknowledged.countDown();})){
            assertThat(acknowledged.await(8,TimeUnit.SECONDS)).isTrue();
        }
        realtime.confirmSignalExperiment(test.get("/api/v1/monitoring/attempts/"+attempt+"/signal-capabilities",token,200),realtime.connectionGeneration(),identity);
        run=new SignalRunContext(attempt,identity);test.owned.add(run);
        delivery=new SignalDelivery(run,realtime,new SignalDelivery.Settings(16,1048576,2,Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofMillis(25)),System::nanoTime,Clock.systemUTC(),true);test.owned.add(delivery);
        statusGeneration=statuses.start(run,delivery);
        test.owned.add(this);
        fx(()->{
            var focus=new FocusSignalAdapter();long f=focus.start(run,delivery);observers.add(FxFocusMonitor.attach(stage,focus,f,Clock.systemUTC(),System::nanoTime,realtime::observationOrigin));
            var window=new WindowSignalAdapter();long w=window.start(run,delivery);observers.add(FxWindowMonitor.attach(stage,window,w,Clock.systemUTC(),realtime::observationOrigin));
            var display=new DisplaySignalAdapter();long d=display.start(run,delivery);observers.add(FxDisplayMonitor.attach(display,d,statuses,statusGeneration,Clock.systemUTC(),realtime::observationOrigin));
            var activity=new ActivitySignalAdapter();long a=activity.start(run,delivery);observers.add(FxActivityMonitor.attach(stage,activity,a,Clock.systemUTC(),System::nanoTime,realtime::observationOrigin));
            return null;
        });
    }
    void startUsb(UsbScanCoordinator.Source source,UsbScanCoordinator.Audit audit)throws Exception {
        usbSource=source;
        usb=new UsbScanCoordinator(source,new UsbScanCoordinator.Settings(Duration.ofMillis(Integer.parseInt(required("TOEIC_USB_CADENCE_MILLIS"))),Duration.ofMillis(Integer.parseInt(required("TOEIC_USB_TIMEOUT_MILLIS"))),Duration.ofSeconds(Integer.parseInt(required("TOEIC_USB_DURATION_SECONDS")))),run,delivery,realtime,audit,statuses,statusGeneration);
        usb.start();
    }
    void stopUsb()throws Exception {
        if(usb!=null){usb.close();assertThat(usb.awaitStopped(Duration.ofSeconds(5))).isTrue();}
        if(usbSource!=null){assertThat(usbSource.status().childrenAlive()).isZero();assertThat(usbSource.status().readersTerminated()).isTrue();}
        assertThat(statuses.status().active()).as("USB close must not retire display status owner").isTrue();
    }
    void quiesceObservers()throws Exception {
        stopUsb();
        fx(()->{for(int i=observers.size()-1;i>=0;i--)observers.get(i).close();observers.clear();return null;});
    }
    @Override public void close()throws Exception {
        if(closed)return;closed=true;
        try{quiesceObservers();}finally{
            statuses.close();run.close();delivery.close();
            var stopped=process.stop();
            try{stopped.stopped().get(8,TimeUnit.SECONDS);assertThat(delivery.awaitStopped(Duration.ofSeconds(3))).isTrue();assertThat(collectorStopped.await(3,TimeUnit.SECONDS)).isTrue();}
            finally{process.close();realtime.close();assertThat(connectionWorker.awaitTermination(3,TimeUnit.SECONDS)).isTrue();}
        }
    }
    /** Harmless stdin-controlled cmd fixture with an allowlisted TEST filename; this is not a real browser. */
    static final class Workload implements AutoCloseable {
        final Process child;
        Workload(Path output)throws Exception {
            Path folder=Files.createDirectory(output.resolve("owned-process-"+UUID.randomUUID()));
            Path command=folder.resolve("chrome.exe");Files.copy(Path.of(System.getenv("SystemRoot"),"System32","cmd.exe"),command);
            child=new ProcessBuilder(command.toString(),"/d","/q").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            assertThat(child.isAlive()).isTrue();
        }
        @Override public void close()throws Exception {
            if(child.isAlive()){
                child.getOutputStream().write("exit\r\n".getBytes(StandardCharsets.US_ASCII));child.getOutputStream().close();
                boolean exited=child.waitFor(5,TimeUnit.SECONDS);
                if(!exited){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);}
                assertThat(exited).as("owned TEST workload exits by stdin, no broad process kill").isTrue();
            }
            assertThat(child.toHandle().isAlive()).isFalse();
        }
    }
}
