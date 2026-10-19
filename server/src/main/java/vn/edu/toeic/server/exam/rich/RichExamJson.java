package vn.edu.toeic.server.exam.rich;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.exam.rich.RichExamImportRequest;
import vn.edu.toeic.server.exam.ExamApiException;

/** Strict v2 boundary only; the v1 Spring message converters are unchanged. */
@Component
public final class RichExamJson {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().registerTypeAdapter(LocalDate.class,
            (JsonDeserializer<LocalDate>) (value, type, context) -> LocalDate.parse(value.getAsString())).create();

    public RichExamImportRequest read(InputStream input) throws IOException {
        return read(input, RichExamImportRequest.class);
    }

    public <T> T read(InputStream input, Class<T> type) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new ExamApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorCode.INVALID_INPUT, "Payload rich vượt 2 MiB", null);
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try (var reader = new JsonReader(new StringReader(text))) {
                reader.setStrictness(Strictness.STRICT);
                JsonElement tree = value(reader, type, 0);
                if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
                return JSON.fromJson(tree, type);
            }
        } catch (ExamApiException known) { throw known; }
        catch (IOException | RuntimeException malformed) { throw invalid(); }
    }

    private JsonElement value(JsonReader reader, Type type, int depth) throws IOException {
        if (depth > 12) throw invalid();
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            if (type instanceof Class<?> c && c.isPrimitive()) throw invalid();
            return JsonNull.INSTANCE;
        }
        if (type instanceof ParameterizedType list && list.getRawType() == List.class) {
            if (reader.peek() != JsonToken.BEGIN_ARRAY) throw invalid();
            reader.beginArray(); var values = new JsonArray();
            while (reader.hasNext()) {
                if (values.size() >= 2000) throw invalid();
                values.add(value(reader, list.getActualTypeArguments()[0], depth + 1));
            }
            reader.endArray(); return values;
        }
        Class<?> kind = (Class<?>) type;
        if (kind.isRecord()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw invalid();
            Map<String, RecordComponent> fields = new HashMap<>();
            for (var field : kind.getRecordComponents()) fields.put(field.getName(), field);
            reader.beginObject(); var object = new JsonObject(); var seen = new HashSet<String>();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!fields.containsKey(name) || !seen.add(name)) throw invalid();
                object.add(name, value(reader, fields.get(name).getGenericType(), depth + 1));
            }
            reader.endObject();
            for (var field : fields.values()) if (field.getType().isPrimitive() && !seen.contains(field.getName())) throw invalid();
            return object;
        }
        if (kind == int.class || kind == Integer.class || kind == long.class || kind == Long.class) {
            if (reader.peek() != JsonToken.NUMBER) throw invalid();
            String number = reader.nextString();
            // An integer wire field is not a string, float, exponent or silently truncated value.
            if (!number.matches("-?(0|[1-9][0-9]*)")) throw invalid();
            BigDecimal parsed = new BigDecimal(number);
            if (kind == int.class || kind == Integer.class) return new JsonPrimitive(parsed.intValueExact());
            return new JsonPrimitive(parsed.longValueExact());
        }
        if (reader.peek() != JsonToken.STRING) throw invalid();
        String text = reader.nextString();
        for (int i=0;i<text.length();i++) {
            char character=text.charAt(i);
            if (character==0) throw invalid();
            if (Character.isHighSurrogate(character)) {
                if (++i>=text.length() || !Character.isLowSurrogate(text.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(character)) throw invalid();
        }
        if (kind.isEnum() && Arrays.stream(kind.getEnumConstants()).noneMatch(e -> ((Enum<?>) e).name().equals(text))) throw invalid();
        if (kind == LocalDate.class) LocalDate.parse(text);
        return new JsonPrimitive(text);
    }
    private static ExamApiException invalid() { return ExamApiException.invalidInput("JSON rich không hợp lệ hoặc sai schema/type/field"); }
}
