package vn.edu.toeic.client.dashboard;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import vn.edu.toeic.protocol.monitoring.signal.*;

/** Strict read-side projection of the existing, ACTIVE-only signal history contract. */
public final class SignalHistory {
    private SignalHistory() { }
    public record Entry(String attemptId, long sequence, Instant receivedAt, String delivery, Signal signal) { }
    public record Page(long after, long through, Long next, boolean more, List<Entry> items) {
        public Page { items = List.copyOf(items); }
    }
    public static Page parse(JsonObject body, String attempt, long after, Long requestedThrough, int limit) {
        SignalJson.size(body, 512 * 1024);
        SignalJson.fields(body, Set.of("protocolVersion","schemaVersion","traceId","attemptId","items",
                "afterSequence","throughSequence","hasMore"), Set.of("nextAfterSequence"));
        if (!body.has("nextAfterSequence")) throw new IllegalArgumentException(); // required key, nullable on the final page
        if (!"v0".equals(SignalJson.text(body,"protocolVersion")) || !attempt.equals(SignalJson.text(body,"attemptId"))) throw new IllegalArgumentException();
        SignalJson.integer(body,"schemaVersion",1,1); SignalJson.id(SignalJson.text(body,"traceId"));
        long start = SignalJson.integer(body,"afterSequence",0,SignalJson.MAX_INTEGER);
        long through = SignalJson.integer(body,"throughSequence",start,SignalJson.MAX_INTEGER);
        if (start != after || (requestedThrough != null && through != requestedThrough)) throw new IllegalArgumentException();
        if (!body.get("items").isJsonArray() || body.getAsJsonArray("items").size() > limit) throw new IllegalArgumentException();
        boolean more = SignalJson.bool(body,"hasMore");
        Long next = body.get("nextAfterSequence").isJsonNull() ? null : SignalJson.integer(body,"nextAfterSequence",start+1,through);
        var rows = new ArrayList<Entry>(); var ids = new HashSet<String>(); long sequence = start;
        for (var element : body.getAsJsonArray("items")) {
            JsonObject p = element.getAsJsonObject().deepCopy();
            if (!attempt.equals(SignalJson.text(p,"attemptId"))) throw new IllegalArgumentException();
            long n = SignalJson.integer(p,"timelineSequence",1,through);
            if (n != ++sequence) throw new IllegalArgumentException();
            Instant received = SignalJson.time(SignalJson.text(p,"receivedAt"));
            String delivery = SignalJson.text(p,"deliveryStatus");
            if (!Set.of("LIVE","BUFFERED_OFFLINE","PREVIOUS_CONNECTION","UNSPECIFIED").contains(delivery)) throw new IllegalArgumentException();
            for (String field : List.of("attemptId","timelineSequence","receivedAt","deliveryStatus")) p.remove(field);
            Signal signal = Signal.parse(p);
            if (!ids.add(signal.eventId())) throw new IllegalArgumentException();
            rows.add(new Entry(attempt,n,received,delivery,signal));
        }
        if (more ? rows.size()!=limit || next==null || next!=sequence || sequence>=through : next!=null || sequence!=through) throw new IllegalArgumentException();
        return new Page(start,through,next,more,rows);
    }
}
