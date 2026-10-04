package vn.edu.toeic.client.dashboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.dashboard.MonitoringData.Roster;

/** Explicit validation, shared by HTTP and push. No numeric coercion through double. */
public final class MonitoringJson {
    private MonitoringJson() { }
    public static String text(JsonObject body, String name, int limit) {
        JsonElement value = body == null ? null : body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid();
        String result = value.getAsString();
        if (result.isBlank() || result.length() > limit || result.chars().anyMatch(Character::isISOControl)) throw invalid();
        return result;
    }
    public static String id(JsonObject body, String name) {
        String value = text(body, name, 128);
        if (!value.matches("[A-Za-z0-9_.:-]{1,128}")) throw invalid();
        return value;
    }
    private static String nullableId(JsonObject body, String name) {
        if (!body.has(name)) throw invalid();
        return body.get(name).isJsonNull() ? null : id(body, name);
    }
    private static long number(JsonObject body, String name, long minimum) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid();
        try {
            long result = new BigDecimal(value.getAsString()).longValueExact();
            if (result < minimum) throw invalid();
            return result;
        } catch (RuntimeException ignored) { throw invalid(); }
    }
    private static Instant time(JsonObject body, String name, boolean nullable) {
        if (!body.has(name)) throw invalid();
        if (nullable && body.get(name).isJsonNull()) return null;
        try {
            Instant result = Instant.parse(text(body, name, 64));
            int year = result.atOffset(ZoneOffset.UTC).getYear();
            if (year < 1 || year > 9999) throw invalid();
            return result;
        } catch (RuntimeException ignored) { throw invalid(); }
    }
    private static void response(JsonObject body) {
        if (!"v0".equals(text(body, "protocolVersion", 8))) throw invalid();
        id(body, "traceId");
    }
    private static <T> List<T> items(JsonObject body, String field, int maximum, Function<JsonObject,T> reader) {
        JsonElement array = body.get(field);
        if (array == null || !array.isJsonArray() || array.getAsJsonArray().size() > maximum) throw invalid();
        List<T> result = new ArrayList<>();
        for (JsonElement item : array.getAsJsonArray()) {
            if (!item.isJsonObject()) throw invalid();
            result.add(reader.apply(item.getAsJsonObject()));
        }
        return List.copyOf(result);
    }
    private static <T> void unique(List<T> items, Function<T,String> key) {
        Set<String> seen = new HashSet<>();
        for (T item : items) if (!seen.add(key.apply(item))) throw invalid();
    }
    public static Roster roster(JsonObject body, int maximum) {
        response(body);
        List<Presence> result = items(body, "attempts", maximum, MonitoringJson::presence);
        unique(result, Presence::attemptId);
        return new Roster(time(body, "serverTime", false), result);
    }
    public static ScopeView scope(JsonObject body, int maximum) {
        response(body);
        JsonObject user = body.getAsJsonObject("user");
        Role role;
        try { role = Role.valueOf(text(user, "role", 32)); } catch (RuntimeException ignored) { throw invalid(); }
        JsonElement array = body.get("attemptScope");
        if (array == null || !array.isJsonArray() || array.getAsJsonArray().size() > maximum) throw invalid();
        Set<String> scope = new HashSet<>();
        for (JsonElement item : array.getAsJsonArray()) {
            JsonObject wrapper = new JsonObject(); wrapper.add("id", item);
            if (!scope.add(id(wrapper, "id"))) throw invalid();
        }
        return new ScopeView(role, scope);
    }
    public static Presence presence(JsonObject body) {
        String attempt = id(body, "attemptId"), name = text(body, "candidateDisplayName", 255);
        long user = number(body, "candidateUserId", 1), revision = number(body, "revision", 0);
        String status = text(body, "status", 16), reason = text(body, "reason", 32);
        String collector = nullableId(body, "collectorSessionId");
        Instant seen = time(body, "lastSeenAt", true), detected = time(body, "timeoutDetectedAt", true);
        if (status.equals("ONLINE")) {
            if (!reason.equals("HEARTBEAT") || revision == 0 || collector == null || seen == null || detected != null) throw invalid();
        } else if (status.equals("UNKNOWN")) {
            if (reason.equals("NOT_SEEN")) {
                if (revision != 0 || seen != null || detected != null || collector != null) throw invalid();
            } else {
                if (!Set.of("HEARTBEAT_TIMEOUT","ACCESS_REVOKED","SERVER_RESTART").contains(reason)
                        || revision == 0 || seen == null || collector == null
                        || (reason.equals("HEARTBEAT_TIMEOUT") != (detected != null))) throw invalid();
            }
        } else throw invalid();
        return new Presence(attempt, user, name, status, reason, revision, collector, seen, detected);
    }
    public static Event event(JsonObject body) {
        String name = text(body, "processName", 255), quality = text(body, "metadataQuality", 32);
        if (!name.matches("[A-Za-z0-9_.-]{1,255}") || name.equals(".") || name.equals("..")
                || !Set.of("COMPLETE","UNREADABLE").contains(quality)) throw invalid();
        return new Event(id(body,"eventId"), id(body,"attemptId"), name, quality, text(body,"policyVersion",128),
                time(body,"observedAt",false), time(body,"receivedAt",false));
    }
    public static Interruption interruption(JsonObject body) {
        if (!"HEARTBEAT_TIMEOUT".equals(text(body,"reason",32))) throw invalid();
        return new Interruption(id(body,"gapId"), id(body,"attemptId"), id(body,"collectorSessionId"), "HEARTBEAT_TIMEOUT",
                time(body,"lastSeenAt",false), time(body,"timeoutDetectedAt",false), time(body,"recoveredAt",true));
    }
    public static List<Event> events(JsonObject body, String attempt, int maximum) {
        response(body); if (!id(body,"attemptId").equals(attempt)) throw invalid();
        List<Event> result = items(body,"events",maximum, MonitoringJson::event);
        if (result.stream().anyMatch(item -> !item.attemptId().equals(attempt))) throw invalid();
        unique(result, Event::eventId); return result;
    }
    public static List<Interruption> interruptions(JsonObject body, String attempt, int maximum) {
        response(body); if (!id(body,"attemptId").equals(attempt)) throw invalid();
        List<Interruption> result = items(body,"interruptions",maximum, MonitoringJson::interruption);
        if (result.stream().anyMatch(item -> !item.attemptId().equals(attempt))) throw invalid();
        unique(result, Interruption::gapId); return result;
    }
    public static void push(MessageEnvelope<JsonObject> message) {
        if (!"v0".equals(message.protocolVersion()) || message.requestId() != null || message.attemptId() == null) throw invalid();
        JsonObject identity = new JsonObject(); identity.addProperty("messageId",message.messageId()); identity.addProperty("traceId",message.traceId()); identity.addProperty("attemptId",message.attemptId());
        id(identity,"messageId"); id(identity,"traceId"); id(identity,"attemptId");
        String payloadAttempt;
        if (message.type().equals("MONITOR_PRESENCE")) payloadAttempt = presence(message.payload()).attemptId();
        else if (message.type().equals("MONITOR_WARNING")) payloadAttempt = event(message.payload()).attemptId();
        else throw invalid();
        if (!payloadAttempt.equals(message.attemptId())) throw invalid();
    }
    private static InvalidServerResponseException invalid() { return new InvalidServerResponseException(); }
}
