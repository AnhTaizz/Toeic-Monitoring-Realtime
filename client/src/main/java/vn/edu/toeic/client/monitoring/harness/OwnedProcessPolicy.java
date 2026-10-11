package vn.edu.toeic.client.monitoring.harness;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import vn.edu.toeic.client.monitoring.ProcessReading;
import vn.edu.toeic.client.monitoring.ProcessSelection;
import vn.edu.toeic.client.monitoring.trace.TraceData;

/** TEST only: enrolled root identities of one bounded run; no executable-name allowlist. */
public final class OwnedProcessPolicy implements ProcessSelection {
    private record Owner(Process process,Instant start) { }
    private final ConcurrentHashMap<Long,java.util.List<Owner>> roots=new ConcurrentHashMap<>();
    public void register(Process process,Instant start){roots.compute(process.pid(),(pid,old) -> {var values=new java.util.ArrayList<>(old==null?java.util.List.of():old);values.add(new Owner(process,start));return java.util.List.copyOf(values);});}
    public void unregister(Process process){roots.computeIfPresent(process.pid(),(pid,old) -> {var values=old.stream().filter(owner -> owner.process()!=process).toList();return values.isEmpty()?null:values;});}
    public int size(){return roots.values().stream().mapToInt(java.util.List::size).sum();}
    void clear(){roots.clear();}
    @Override public String version(){return TraceData.TEST_POLICY;}
    @Override public boolean includes(ProcessReading reading) {
        var owners=roots.get(reading.pid());
        return owners!=null&&owners.stream().anyMatch(owner -> owner.start()==null||reading.startInstant()==null||owner.start().equals(reading.startInstant()));
    }
}
