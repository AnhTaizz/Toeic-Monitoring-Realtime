package vn.edu.toeic.client.monitoring.usb;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.*;

/** Owns one USB source/adapter and two workers; borrows the authorized signal lane. */
public final class UsbScanCoordinator implements AutoCloseable {
    public record Settings(Duration cadence, Duration queryTimeout, Duration duration, boolean examSession) {
        public Settings(Duration cadence, Duration queryTimeout, Duration duration) {
            this(cadence, queryTimeout, duration, false);
        }
        public Settings {
            Objects.requireNonNull(cadence); Objects.requireNonNull(queryTimeout); Objects.requireNonNull(duration);
            if (cadence.toMillis()<250 || cadence.toMillis()>10000 || queryTimeout.toMillis()<100 || queryTimeout.toMillis()>10000
                    || (examSession ? !duration.isZero() : duration.toMillis()<1000 || duration.toMillis()>120000))
                throw new IllegalArgumentException("Invalid USB settings");
        }
        public static Settings defaults(){return new Settings(Duration.ofSeconds(1),Duration.ofSeconds(5),Duration.ofSeconds(15));}
        /** Session owner supplies the end: stop, terminal attempt, revoke, logout, or app close. */
        public static Settings forExam(Duration cadence, Duration queryTimeout){return new Settings(cadence,queryTimeout,Duration.ZERO,true);}
        public static Settings configuredForExam(){
            return forExam(Duration.ofMillis(Long.parseLong(System.getProperty("toeic.monitoring.usb-cadence-millis","1000"))),
                    Duration.ofMillis(Long.parseLong(System.getProperty("toeic.monitoring.usb-query-timeout-millis","5000"))));
        }
    }
    public record Reading(UsbSignalAdapter.Snapshot snapshot, Reason failureReason, long queryMillis) {
        public Reading { Objects.requireNonNull(snapshot); if(queryMillis<0 || !Set.of(Reason.NO_DATA,Reason.API_UNAVAILABLE,Reason.PERMISSION_DENIED,Reason.SCAN_FAILED).contains(failureReason))throw new IllegalArgumentException(); }
    }
    public record SourceStatus(int childrenAlive, boolean readersTerminated) { }
    public interface Source extends AutoCloseable {
        Reading scan(Duration timeout);
        SourceStatus status();
        @Override void close();
    }
    /** Observer must be bounded/RAM-only. Receives frozen messages, never CIM objects or disk slot keys. */
    public interface Audit { void emission(SignalMessage message, boolean queued); default void observed(Status status){ } }
    public record Status(boolean active,long generation,long scans,UsbSignalAdapter.Health health,Integer storageCount,
                         long queryMillis,String stoppedReason,boolean scanWorkerStopped,boolean controlWorkerStopped) { }
    private final Object lock=new Object();
    private final Source source;private final Settings settings;private final SignalRunContext run;
    private final SignalDelivery delivery;private final MonitoringTransport transport;private final Clock clock;private final Audit audit;
    private final UsbSignalAdapter adapter=new UsbSignalAdapter();private final ObserverStatusEmitter statuses;
    private final boolean ownsStatuses;
    // A non-daemon control worker keeps the JVM alive until bounded owned-source cleanup completes.
    private final ScheduledThreadPoolExecutor scanner=executor("toeic-usb-scan",true),control=executor("toeic-usb-control",false);
    private final AutoCloseable connections;
    private boolean started,active,cleanupScheduled;private long generation,scanGeneration,statusGeneration,scans;
    private volatile long socket;
    private ScheduledFuture<?> scanTask,controlTask;
    private UsbSignalAdapter.Health health=UsbSignalAdapter.Health.STOPPED;private Integer count;private long queryMillis;
    private String stoppedReason="NOT_STARTED";private State previous;private Reason readyReason=Reason.INITIAL;
    public UsbScanCoordinator(Source source,Settings settings,SignalRunContext run,SignalDelivery delivery,MonitoringTransport transport,Audit audit){
        this(source,settings,run,delivery,transport,Clock.systemUTC(),System::nanoTime,audit);
    }
    public UsbScanCoordinator(Source source,Settings settings,SignalRunContext run,SignalDelivery delivery,MonitoringTransport transport,Clock clock,LongSupplier ticker,Audit audit){
        this(source,settings,run,delivery,transport,clock,audit,new ObserverStatusEmitter(ticker),0,true);
    }
    /** Borrow the single status owner already shared with display; do not stop it. */
    public UsbScanCoordinator(Source source,Settings settings,SignalRunContext run,SignalDelivery delivery,MonitoringTransport transport,Audit audit,ObserverStatusEmitter statuses,long statusGeneration){
        this(source,settings,run,delivery,transport,Clock.systemUTC(),audit,statuses,statusGeneration,false);
    }
    private UsbScanCoordinator(Source source,Settings settings,SignalRunContext run,SignalDelivery delivery,MonitoringTransport transport,Clock clock,Audit audit,ObserverStatusEmitter statuses,long statusGeneration,boolean ownsStatuses){
        this.source=Objects.requireNonNull(source);this.settings=Objects.requireNonNull(settings);this.run=Objects.requireNonNull(run);
        this.delivery=Objects.requireNonNull(delivery);this.transport=Objects.requireNonNull(transport);this.clock=Objects.requireNonNull(clock);this.audit=Objects.requireNonNull(audit);
        this.statuses=Objects.requireNonNull(statuses);this.statusGeneration=statusGeneration;this.ownsStatuses=ownsStatuses;
        if(!delivery.usesContext(run))throw new IllegalArgumentException("Shared context delivery required");
        if(!ownsStatuses&&!statuses.usesContext(run,delivery,statusGeneration))throw new IllegalArgumentException("Fresh shared status owner required");
        connections=transport.onConnectionState(state->{if(state!=ConnectionState.CONNECTED){
            if(settings.examSession())pause("CONNECTION_CHANGED");else requestStop("CONNECTION_CHANGED");
        }});
    }
    private static ScheduledThreadPoolExecutor executor(String name,boolean daemon){var e=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,name);t.setDaemon(daemon);return t;});e.setRemoveOnCancelPolicy(true);e.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);return e;}
    public long start(){
        boolean ready=transport.signalExperimentReady(run.attemptId());long current=transport.connectionGeneration();
        synchronized(lock){
            if(started||cleanupScheduled||!ready||!run.active()||!delivery.status().active())throw new IllegalStateException("Fresh authorized lane required");
            if(!ownsStatuses&&!statuses.usesContext(run,delivery,statusGeneration))throw new IllegalStateException("Shared status owner stopped");
            started=true;scanGeneration=adapter.start(run,delivery);if(ownsStatuses)statusGeneration=statuses.start(run,delivery);
            activate(current);
            if(!settings.examSession())control.schedule(()->requestStop("DURATION"),settings.duration().toMillis(),TimeUnit.MILLISECONDS);
            return generation;
        }
    }
    /** Owner calls only after refreshing and confirming this socket's capability and heartbeat. */
    public void resume(){
        boolean ready=transport.signalExperimentReady(run.attemptId());long current=transport.connectionGeneration();
        synchronized(lock){
            if(!settings.examSession()||!started||cleanupScheduled||!ready||!run.active()||!delivery.status().active()
                    ||!ownsStatuses&&!statuses.usesContext(run,delivery,statusGeneration))throw new IllegalStateException("Current authorized exam lane required");
            if(active&&socket==current)return;
            cancelSchedules();activate(current);
        }
    }
    private void activate(long current){
        socket=current;active=true;stoppedReason="NONE";generation++;long expected=generation;
        scanTask=scanner.scheduleWithFixedDelay(()->scan(expected),0,settings.cadence().toMillis(),TimeUnit.MILLISECONDS);
        controlTask=control.scheduleWithFixedDelay(()->flush(expected),100,100,TimeUnit.MILLISECONDS);
    }
    private boolean laneReady(){return transport.signalExperimentReady(run.attemptId())&&transport.connectionGeneration()==socket&&run.active()&&delivery.status().active()
            &&(ownsStatuses||statuses.usesContext(run,delivery,statusGeneration));}
    private void laneLost(long expected){
        if(settings.examSession()&&run.active()&&delivery.status().active()
                &&(ownsStatuses||statuses.usesContext(run,delivery,statusGeneration)))pause("LANE_NOT_READY",expected);
        else requestStop("LANE_NOT_READY",expected);
    }
    private void scan(long expected){
        if(!laneReady()){laneLost(expected);return;}
        synchronized(lock){if(!active||generation!=expected)return;}
        Reading reading;
        try{reading=Objects.requireNonNull(source.scan(settings.queryTimeout()));}
        catch(RuntimeException unavailable){reading=new Reading(new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.ERROR,List.of()),Reason.SCAN_FAILED,0);}
        if(!laneReady()){laneLost(expected);return;}
        var origin=transport.observationOrigin();Instant time=clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<UsbSignalAdapter.Emission> usb;List<ObserverStatusEmitter.Emission> status;
        synchronized(lock){
            if(!active||generation!=expected)return;
            scans++;queryMillis=reading.queryMillis();var result=adapter.accept(scanGeneration,reading.snapshot(),time,origin);
            health=result.health();count=health==UsbSignalAdapter.Health.READY?reading.snapshot().storageSlots().size():null;
            State state=switch(health){case READY->State.READY;case UNSUPPORTED->State.UNSUPPORTED;default->reading.snapshot().state()==UsbSignalAdapter.SourceState.ERROR?State.ERROR:State.UNKNOWN;};
            Reason reason;
            if(state==State.READY){if(previous!=null&&previous!=State.READY)readyReason=Reason.RECOVERED;reason=readyReason;}
            else reason=switch(state){case UNSUPPORTED->Reason.API_UNAVAILABLE;case ERROR->reading.failureReason()==Reason.PERMISSION_DENIED?Reason.PERMISSION_DENIED:Reason.SCAN_FAILED;default->Reason.NO_DATA;};
            previous=state;usb=result.emissions();status=statuses.observe(statusGeneration,ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,state,reason,time,origin);
        }
        try{for(var e:usb)audit.emission(e.message(),e.queued());for(var e:status)audit.emission(e.message(),e.queued());audit.observed(status());}
        catch(RuntimeException failed){requestStop("AUDIT_LIMIT_OR_FAILURE",expected);}
    }
    private void flush(long expected){
        if(!laneReady()){laneLost(expected);return;}
        List<ObserverStatusEmitter.Emission> emitted;
        synchronized(lock){if(!active||generation!=expected)return;emitted=statuses.flush(statusGeneration);}
        try{for(var e:emitted)audit.emission(e.message(),e.queued());}catch(RuntimeException failed){requestStop("AUDIT_LIMIT_OR_FAILURE",expected);}
    }
    /** Invalidate query callbacks without releasing/reclaiming the one adapter binding or baseline. */
    public void pause(String reason){pause(reason,-1);}
    private void pause(String reason,long expected){
        if(!settings.examSession()||!Set.of("CONNECTION_CHANGED","LANE_NOT_READY").contains(reason))throw new IllegalArgumentException("Exam pause code required");
        List<ObserverStatusEmitter.Emission> emitted;
        synchronized(lock){
            if(!started||cleanupScheduled||!active||expected>=0&&generation!=expected)return;
            active=false;generation++;stoppedReason=reason;cancelSchedules();health=UsbSignalAdapter.Health.UNKNOWN;count=null;
            Instant time=clock.instant().truncatedTo(ChronoUnit.MICROS);
            adapter.accept(scanGeneration,new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.UNKNOWN,List.of()),time,EventOrigin.OFFLINE);
            previous=State.UNKNOWN;
            emitted=statuses.observe(statusGeneration,ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,State.UNKNOWN,Reason.NO_DATA,time,EventOrigin.OFFLINE);
        }
        try{for(var e:emitted)audit.emission(e.message(),e.queued());}catch(RuntimeException failed){requestStop("AUDIT_LIMIT_OR_FAILURE");}
    }
    private void cancelSchedules(){if(scanTask!=null)scanTask.cancel(false);if(controlTask!=null)controlTask.cancel(false);}
    public void requestStop(String reason){requestStop(reason,-1);}
    private void requestStop(String reason,long expected){
        if(!Set.of("STOP","DURATION","CONNECTION_CHANGED","LANE_NOT_READY","AUDIT_LIMIT_OR_FAILURE","CANCELLED").contains(reason))throw new IllegalArgumentException("Safe stop code required");
        synchronized(lock){
            if(cleanupScheduled||expected>=0&&(!active||generation!=expected))return;
            if(!started && reason.equals("CONNECTION_CHANGED"))return;
            active=false;generation++;stoppedReason=reason;cleanupScheduled=true;cancelSchedules();adapter.stop();if(ownsStatuses)statuses.stop();scanner.shutdown();
            control.execute(this::cleanup);
        }
    }
    private void cleanup(){
        try{source.close();}finally{
            scanner.shutdownNow();
            try{scanner.awaitTermination(4,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            try{connections.close();}catch(Exception ignored){ }
            control.shutdown();
        }
    }
    public Status status(){synchronized(lock){return new Status(active,generation,scans,health,count,queryMillis,stoppedReason,scanner.isTerminated(),control.isTerminated());}}
    public boolean awaitStopped(Duration timeout)throws InterruptedException{
        if(Platform.isFxApplicationThread())throw new IllegalStateException("Wait for USB shutdown off JavaFX thread");
        long deadline=System.nanoTime()+timeout.toNanos();
        return scanner.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS)
                &&control.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
    }
    @Override public void close(){
        if(Platform.isFxApplicationThread())throw new IllegalStateException("Close USB source off JavaFX thread");
        requestStop("STOP");try{awaitStopped(Duration.ofSeconds(8));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
}
