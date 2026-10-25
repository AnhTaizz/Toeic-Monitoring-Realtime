package vn.edu.toeic.monitoring.usb;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Identifies only helpers spawned here; never suppresses all processes with the same executable name. */
public final class OwnedUsbProcesses {
    private record Identity(long pid,Instant start) { }
    private static final Map<Identity,ProcessHandle> RECENT=new LinkedHashMap<>();
    private OwnedUsbProcesses() { }
    static synchronized Process start(List<String> command)throws IOException {
        // The same lock covers launch and lookup, so a collector cannot observe an unregistered helper.
        var process=new ProcessBuilder(command).start();var handle=process.toHandle();Instant began=null;
        try{began=handle.info().startInstant().orElse(null);}catch(RuntimeException unavailable){ }
        RECENT.put(new Identity(handle.pid(),began),handle);
        while(RECENT.size()>128)RECENT.remove(RECENT.keySet().iterator().next());
        return process;
    }
    public static synchronized boolean contains(long pid,Instant start) {
        for(var entry:RECENT.entrySet())if(entry.getKey().pid()==pid){
            Instant known=entry.getKey().start();
            if(known!=null&&start!=null){if(known.equals(start))return true;}
            else if(entry.getValue().isAlive())return true;
        }
        return false;
    }
}
