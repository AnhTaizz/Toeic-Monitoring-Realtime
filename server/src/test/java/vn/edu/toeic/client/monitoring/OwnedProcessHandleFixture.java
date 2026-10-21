package vn.edu.toeic.client.monitoring;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** TEST only: use the existing source's process supplier seam BEFORE metadata reads, never scan the host. */
public final class OwnedProcessHandleFixture implements ProcessSnapshotSource {
    private final long pid;
    private final ProcessHandleSnapshotSource delegate;
    public final AtomicInteger completed=new AtomicInteger();
    public volatile long lastQueryMillis;
    public volatile int lastReadingCount;
    public OwnedProcessHandleFixture(long pid){this.pid=pid;delegate=new ProcessHandleSnapshotSource(()->ProcessHandle.of(pid).stream());}
    @Override public List<ProcessReading> scan(){
        long start=System.nanoTime();var readings=delegate.scan();
        if(readings.size()>1||readings.stream().anyMatch(p->p.pid()!=pid))throw new IllegalStateException("TEST process scope mismatch");
        lastReadingCount=readings.size();lastQueryMillis=(System.nanoTime()-start)/1_000_000;completed.incrementAndGet();return readings;
    }
}
