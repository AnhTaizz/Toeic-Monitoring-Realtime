package vn.edu.toeic.server.monitoring;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** Client-reported loss; this does not prove an interruption time or cheating. */
public record MonitoringGap(String gapId, String collectorSessionId, String reason, long droppedCount,
        Instant firstDroppedAt, Instant lastDroppedAt) {
    public static MonitoringGap parse(JsonObject body) {
        if (body == null || !body.keySet().equals(Set.of("gapId", "collectorSessionId", "reason", "droppedCount", "firstDroppedAt", "lastDroppedAt")))
            throw new IllegalArgumentException();
        String gap = id(body, "gapId"), collector = id(body, "collectorSessionId");
        if (!"QUEUE_OVERFLOW".equals(text(body, "reason"))) throw new IllegalArgumentException();
        JsonElement count = body.get("droppedCount");
        if (count == null || !count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        long dropped = count.getAsBigDecimal().longValueExact();
        Instant first = time(body, "firstDroppedAt"), last = time(body, "lastDroppedAt");
        if (dropped <= 0 || last.isBefore(first)) throw new IllegalArgumentException();
        return new MonitoringGap(gap, collector, "QUEUE_OVERFLOW", dropped, first, last);
    }
    private static String id(JsonObject body, String name) {
        String value = text(body, name);
        if (!value.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException(); return value;
    }
    private static String text(JsonObject body, String name) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    private static Instant time(JsonObject body, String name) {
        Instant value = Instant.parse(text(body, name)).truncatedTo(ChronoUnit.MICROS);
        if (value.isBefore(Instant.parse("0001-01-01T00:00:00Z")) || value.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) throw new IllegalArgumentException();
        return value;
    }
}
