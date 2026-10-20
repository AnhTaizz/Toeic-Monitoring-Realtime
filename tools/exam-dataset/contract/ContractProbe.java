import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.exam.rich.RichExamValidator;

/** Chạy với DTO/validator thật export read-only vào target; không phải HTTP/PG test. */
public final class ContractProbe {
    static int records;
    public static void main(String[] args) throws Exception {
        JsonElement json = JsonParser.parseString(Files.readString(Path.of(args[0])));
        shape(json, RichExamImportRequest.class, "$");
        Gson gson = new GsonBuilder().registerTypeAdapter(LocalDate.class,
                (JsonDeserializer<LocalDate>) (j, t, c) -> LocalDate.parse(j.getAsString())).create();
        RichExamImportRequest request = gson.fromJson(json, RichExamImportRequest.class);
        boolean rejected = false;
        try { new RichExamValidator().validate(request); }
        catch (IllegalArgumentException expected) { rejected = true; System.out.println("DRAFT_REJECTED: " + expected.getMessage()); }
        if (!rejected) throw new AssertionError("Draft must not pass EX03 verification gate");
        System.out.println("DTO_SHAPE_PASS records=" + records + " schemaVersion=" + request.schemaVersion());
        System.out.println("HTTP_POSTGRES_NOT_RUN");
    }
    static void shape(JsonElement json, Type type, String path) {
        if (json.isJsonNull()) {
            if (type instanceof Class<?> c && c.isPrimitive()) throw new AssertionError(path + " primitive null");
            return;
        }
        if (type instanceof ParameterizedType p && p.getRawType() == List.class) {
            for (JsonElement e : json.getAsJsonArray()) shape(e, p.getActualTypeArguments()[0], path + "[]");
            return;
        }
        Class<?> c = (Class<?>) type;
        if (c.isRecord()) {
            records++; var expected = new HashSet<String>(); for (var r : c.getRecordComponents()) expected.add(r.getName());
            if (!expected.equals(json.getAsJsonObject().keySet())) throw new AssertionError(path + " fields differ from " + c.getSimpleName());
            for (var r : c.getRecordComponents()) shape(json.getAsJsonObject().get(r.getName()), r.getGenericType(), path + "." + r.getName());
        } else if (c.isEnum()) {
            if (Arrays.stream(c.getEnumConstants()).noneMatch(v -> ((Enum<?>) v).name().equals(json.getAsString()))) throw new AssertionError(path + " unknown enum");
        } else if (c == String.class || c == LocalDate.class) {
            if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) throw new AssertionError(path + " not string");
            if (c == LocalDate.class) LocalDate.parse(json.getAsString());
        } else if (Set.of(int.class, long.class, Integer.class, Long.class).contains(c)) {
            if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber() || !json.toString().matches("-?[0-9]+")) throw new AssertionError(path + " not integer");
        } else throw new AssertionError("Unexpected type " + type);
    }
}
