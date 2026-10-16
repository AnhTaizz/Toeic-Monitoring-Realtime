package vn.edu.toeic.client.monitoring.harness;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;

/** Collector thread CPU and parent JVM CPU/heap, explicitly different scopes. No RSS claim. */
final class E1Resources implements AutoCloseable {
    private final long origin;
    private final List<String> samples=new ArrayList<>();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"toeic-e1-resource");t.setDaemon(true);return t;});
    E1Resources(long origin){this.origin=origin;samples.add("elapsedNanos,collectorThreadCpuNanos,parentProcessCpuNanos,parentHeapUsedBytes");worker.scheduleAtFixedRate(this::sample,0,500,TimeUnit.MILLISECONDS);}
    private void sample(){
        var threads=ManagementFactory.getThreadMXBean();long collector=-1;
        if(threads.isThreadCpuTimeSupported()&&threads.isThreadCpuTimeEnabled())for(long id:threads.getAllThreadIds()) {
            var info=threads.getThreadInfo(id);if(info!=null&&info.getThreadName().equals("toeic-process-collector"))collector=threads.getThreadCpuTime(id);
        }
        long process=ProcessHandle.current().info().totalCpuDuration().map(java.time.Duration::toNanos).orElse(-1L);
        long heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        synchronized(samples){samples.add((System.nanoTime()-origin)+","+collector+","+process+","+heap);}
    }
    String csv(){synchronized(samples){return String.join("\n",samples)+"\n";}}
    public void close(){worker.shutdownNow();try{if(!worker.awaitTermination(2,TimeUnit.SECONDS))throw new IllegalStateException("SAMPLER_CLEANUP");}catch(InterruptedException stopped){Thread.currentThread().interrupt();}}
}
