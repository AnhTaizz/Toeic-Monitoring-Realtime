package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

final class Json {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();
    static JsonObject obj(Object... pairs) {
        var result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) result.add((String) pairs[i], GSON.toJsonTree(pairs[i + 1]));
        return result;
    }
    static JsonArray arr(Object... values) {
        var result = new JsonArray();
        for (var value : values) result.add(GSON.toJsonTree(value));
        return result;
    }
    static String str(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null; }
    static int num(JsonObject o, String key) { return o.get(key).getAsInt(); }
    static JsonObject read(Path path) throws IOException { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    static void write(Path path, JsonElement value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, GSON.toJson(value) + "\n");
    }
    static String hash(Path path) throws IOException { return hash(Files.readAllBytes(path)); }
    static boolean safeFile(String name) {
        return name != null && name.length() <= 255 && name.matches("[A-Za-z0-9][A-Za-z0-9._-]*") && !name.contains("..") && !name.endsWith(".")
                && !name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?");
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private Json() {}
}
