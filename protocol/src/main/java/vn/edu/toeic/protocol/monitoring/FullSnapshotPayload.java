package vn.edu.toeic.protocol.monitoring;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One canonical observation set, shared by full encoding and the server reducer. */
public record FullSnapshotPayload(String collectorSessionId, String syncEpoch, long sequence,
        String policyVersion, List<Process> processes) {
    public static final int MAX_PROCESSES = 128;
    public static final String POLICY = "process-policy-v1";
    public static final Set<String> EXECUTABLES = Set.of("chrome.exe", "msedge.exe", "firefox.exe",
            "zalo.exe", "teams.exe", "discord.exe", "anydesk.exe", "teamviewer.exe");
    public record Process(long pid, String processName, String startInstant, String metadataQuality) {
        public Process {
            if (pid < 1 || processName == null || metadataQuality == null) throw new IllegalArgumentException();
            processName = processName.toLowerCase(Locale.ROOT);
            if (!EXECUTABLES.contains(processName) || !Set.of("COMPLETE", "UNREADABLE").contains(metadataQuality))
                throw new IllegalArgumentException();
            if (startInstant != null) startInstant = Instant.parse(startInstant).toString();
            if (startInstant == null && metadataQuality.equals("COMPLETE")) throw new IllegalArgumentException();
        }
    }
    public FullSnapshotPayload {
        id(collectorSessionId); id(syncEpoch);
        if (sequence < 1 || !POLICY.equals(policyVersion)) throw new IllegalArgumentException();
        processes = canonical(processes);
    }
    public static List<Process> canonical(List<Process> values) {
        if (values == null || values.size() > MAX_PROCESSES) throw new IllegalArgumentException();
        Set<String> identities = new HashSet<>();
        for (Process value : values) {
            if (value == null || !identities.add(value.pid() + ":" + value.startInstant())) throw new IllegalArgumentException();
        }
        return values.stream().sorted(Comparator.comparingLong(Process::pid)
                .thenComparing(p -> p.startInstant() == null ? "" : p.startInstant())).toList();
    }
    public static FullSnapshotPayload parse(JsonObject body) {
        fields(body, Set.of("collectorSessionId", "syncEpoch", "sequence", "policyVersion", "processes"));
        return new FullSnapshotPayload(text(body,"collectorSessionId"), text(body,"syncEpoch"), number(body,"sequence"),
                text(body,"policyVersion"), parseProcesses(body));
    }
    public static List<Process> parseProcesses(JsonObject body) {
        JsonElement raw = body.get("processes");
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().size() > MAX_PROCESSES) throw new IllegalArgumentException();
        List<Process> values = new ArrayList<>();
        for (JsonElement item : raw.getAsJsonArray()) {
            if (!item.isJsonObject()) throw new IllegalArgumentException();
            JsonObject value = item.getAsJsonObject();
            fields(value, Set.of("pid", "processName", "startInstant", "metadataQuality"));
            values.add(new Process(number(value,"pid"), text(value,"processName"), nullable(value,"startInstant"), text(value,"metadataQuality")));
        }
        return canonical(values);
    }
    public static void fields(JsonObject body, Set<String> fields) {
        if (body == null || !fields.containsAll(body.keySet())) throw new IllegalArgumentException();
    }
    public static String text(JsonObject body, String name) {
        JsonElement value = body == null ? null : body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank() || value.getAsString().length() > 128) throw new IllegalArgumentException();
        return value.getAsString();
    }
    public static String nullable(JsonObject body, String name) {
        JsonElement value = body.get(name);
        return value == null || value.isJsonNull() ? null : text(body,name);
    }
    public static long number(JsonObject body, String name) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        return value.getAsBigDecimal().longValueExact();
    }
    public static String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException();
        return value;
    }
}
