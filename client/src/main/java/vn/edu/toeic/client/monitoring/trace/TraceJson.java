package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.Strictness;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

final class TraceJson {
    static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private TraceJson() { }
    static JsonObject object(String line) throws IOException {
        try(var reader=new JsonReader(new StringReader(line))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value=read(reader,0);
            if(!value.isJsonObject()||reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("JSON_OBJECT");
            return value.getAsJsonObject();
        } catch(RuntimeException invalid) { throw new IOException("JSON_INVALID"); }
    }
    private static JsonElement read(JsonReader reader,int depth) throws IOException {
        if(depth>8) throw new IOException("JSON_DEPTH");
        return switch(reader.peek()) {
            case BEGIN_OBJECT -> {
                var object=new JsonObject();Set<String> fields=new HashSet<>();reader.beginObject();
                while(reader.hasNext()) {String name=reader.nextName();if(!fields.add(name))throw new IOException("DUPLICATE_FIELD");object.add(name,read(reader,depth+1));}
                reader.endObject();yield object;
            }
            case BEGIN_ARRAY -> {
                var array=new JsonArray();reader.beginArray();while(reader.hasNext()) {if(array.size()>=128)throw new IOException("ARRAY_LIMIT");array.add(read(reader,depth+1));}
                reader.endArray();yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {reader.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new IOException("JSON_VALUE");
        };
    }
    static void fields(JsonObject object,Set<String> names) {
        if(!object.keySet().equals(names)) throw new IllegalArgumentException();
    }
    static TraceData.Header header(JsonObject body) {
        fields(body,Set.of("kind","schemaVersion","sourceLabel","pollMillis","policyVersion","wallOrigin"));
        if(!text(body,"kind").equals("HEADER"))throw new IllegalArgumentException();
        return new TraceData.Header(text(body,"schemaVersion"),text(body,"sourceLabel"),number(body,"pollMillis"),text(body,"policyVersion"),text(body,"wallOrigin"));
    }
    static TraceData.Sample sample(JsonObject body) {
        fields(body,Set.of("kind","index","type","elapsedNanos","collectorSessionId","policyVersion","scanDurationNanos","processes"));
        if(!text(body,"kind").equals("RECORD"))throw new IllegalArgumentException();
        return new TraceData.Sample(number(body,"index"),text(body,"type"),number(body,"elapsedNanos"),text(body,"collectorSessionId"),
                text(body,"policyVersion"),number(body,"scanDurationNanos"),FullSnapshotPayload.parseProcesses(body));
    }
    static JsonObject record(TraceData.Sample sample) {var body=JSON.toJsonTree(sample).getAsJsonObject();body.addProperty("kind","RECORD");return body;}
    static JsonObject header(TraceData.Header header) {var body=JSON.toJsonTree(header).getAsJsonObject();body.addProperty("kind","HEADER");return body;}
    static String text(JsonObject body,String field) {return FullSnapshotPayload.text(body,field);}
    static long number(JsonObject body,String field) {return FullSnapshotPayload.number(body,field);}
}
