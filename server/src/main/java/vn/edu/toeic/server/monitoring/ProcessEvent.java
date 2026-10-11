package vn.edu.toeic.server.monitoring;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

public record ProcessEvent(String eventId, String collectorSessionId, String policyVersion, long pid,
        String processName, Instant startInstant, String metadataQuality, Instant observedAt, EventOrigin origin) {
    public ProcessEvent(String eventId,String collectorSessionId,String policyVersion,long pid,String processName,
            Instant startInstant,String metadataQuality,Instant observedAt) {
        this(eventId,collectorSessionId,policyVersion,pid,processName,startInstant,metadataQuality,observedAt,EventOrigin.UNSPECIFIED);
    }
    private static final Set<String> FIELDS = Set.of("eventId", "collectorSessionId", "policyVersion", "pid",
            "processName", "startInstant", "metadataQuality", "observedAt", "observationContext", "observationConnectionId");

    public static ProcessEvent parse(JsonObject payload) {
        if (payload == null || !FIELDS.containsAll(payload.keySet())) throw new IllegalArgumentException();
        String eventId = identifier(payload, "eventId");
        String collector = identifier(payload, "collectorSessionId");
        String policy = text(payload, "policyVersion");
        if (policy.length() > 128 || policy.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
        JsonElement pidValue = payload.get("pid");
        if (pidValue == null || !pidValue.isJsonPrimitive() || !pidValue.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        long pid = pidValue.getAsBigDecimal().longValueExact();
        if (pid <= 0) throw new IllegalArgumentException();
        String name = text(payload, "processName");
        if (!name.matches("[A-Za-z0-9_.-]{1,255}") || name.equals(".") || name.equals("..")) throw new IllegalArgumentException();
        JsonElement startValue = payload.get("startInstant");
        Instant start = startValue == null || startValue.isJsonNull() ? null : instant(payload, "startInstant");
        String quality = text(payload, "metadataQuality");
        if (!Set.of("COMPLETE", "UNREADABLE").contains(quality) || (start == null && quality.equals("COMPLETE"))) throw new IllegalArgumentException();
        return new ProcessEvent(eventId, collector, policy, pid, name, start, quality, instant(payload, "observedAt"),EventOrigin.parse(payload));
    }

    private static Instant instant(JsonObject body, String field) {
        Instant value = Instant.parse(text(body, field)).truncatedTo(ChronoUnit.MICROS);
        // Keep dates in a predictable range shared by Java/JDBC/PostgreSQL.
        if (value.isBefore(Instant.parse("0001-01-01T00:00:00Z")) || value.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) throw new IllegalArgumentException();
        return value;
    }
    private static String identifier(JsonObject body, String field) {
        String value = text(body, field);
        if (!value.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException();
        return value;
    }
    private static String text(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) throw new IllegalArgumentException();
        return value.getAsString();
    }
}
