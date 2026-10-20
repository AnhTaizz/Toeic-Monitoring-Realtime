package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;

/** Strict, bounded JSON for the experimental signal lane only; legacy parsers are unchanged. */
public final class SignalJson {
    public static final long MAX_INTEGER=9_007_199_254_740_991L;
    private SignalJson() { }
    public static JsonObject parse(byte[] bytes) {
        if(bytes.length>8192)throw new IllegalArgumentException();
        try { return parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()); }
        catch(CharacterCodingException e){throw new IllegalArgumentException();}
    }
    public static JsonObject parse(String raw) {
        if(raw==null||raw.length()>8192)throw new IllegalArgumentException();
        unicode(raw);if(raw.getBytes(StandardCharsets.UTF_8).length>8192)throw new IllegalArgumentException();
        try(JsonReader r=new JsonReader(new StringReader(raw))){
            r.setStrictness(Strictness.STRICT);JsonElement result=read(r,1);
            if(!result.isJsonObject()||r.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException();
            return result.getAsJsonObject();
        }catch(IOException|IllegalStateException e){throw new IllegalArgumentException();}
    }
    private static JsonElement read(JsonReader r,int depth)throws IOException{
        if(depth>6)throw new IllegalArgumentException();
        return switch(r.peek()){
            case BEGIN_OBJECT->{r.beginObject();JsonObject o=new JsonObject();while(r.hasNext()){
                String k=r.nextName();unicode(k);if(o.has(k))throw new IllegalArgumentException();o.add(k,read(r,depth+1));}r.endObject();yield o;}
            case BEGIN_ARRAY->{r.beginArray();JsonArray a=new JsonArray();while(r.hasNext())a.add(read(r,depth+1));r.endArray();yield a;}
            case STRING->{String s=r.nextString();unicode(s);yield new JsonPrimitive(s);}
            case NUMBER->{String s=r.nextString();if(!s.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException();long n=Long.parseLong(s);if(n>MAX_INTEGER)throw new IllegalArgumentException();yield new JsonPrimitive(n);}
            case BOOLEAN->new JsonPrimitive(r.nextBoolean());
            case NULL->{r.nextNull();yield JsonNull.INSTANCE;}
            default->throw new IllegalArgumentException();
        };
    }
    /** After strict syntax validation, measure the original object spans, including whitespace/escapes.
     * Valid signal envelopes have exactly three object levels: envelope, payload, details.
     * This is only a size check; fields/types and malformed shapes still go through the typed parser.
     */
    static void wireObjectSizes(String raw){
        int[] starts=new int[6];int depth=0;boolean quoted=false,escaped=false;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;continue;}
            if(c=='"'){quoted=true;continue;}
            if(c=='{')starts[depth++]=i;
            else if(c=='}'){
                int start=starts[--depth];
                int limit=depth==1?4096:depth==2?1024:8192;
                if(raw.substring(start,i+1).getBytes(StandardCharsets.UTF_8).length>limit)throw new IllegalArgumentException();
            }
        }
    }
    public static void unicode(String s){
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){
            if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException();
        }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException();}
    }
    public static void fields(JsonObject o,Set<String> required,Set<String> optional){
        if(o==null||!o.keySet().containsAll(required))throw new IllegalArgumentException();
        for(String k:o.keySet())if(!required.contains(k)&&!optional.contains(k))throw new IllegalArgumentException();
        for(String k:required)if(o.get(k).isJsonNull())throw new IllegalArgumentException();
    }
    public static String text(JsonObject o,String key){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException();String s=v.getAsString();unicode(s);return s;}
    public static boolean bool(JsonObject o,String key){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();return v.getAsBoolean();}
    public static String id(String s){if(s==null||!s.matches("[A-Za-z0-9_.:-]{1,128}"))throw new IllegalArgumentException();return s;}
    public static long integer(JsonObject o,String key,long min,long max){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!v.toString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException();long n=Long.parseLong(v.toString());if(n<min||n>max)throw new IllegalArgumentException();return n;}
    public static Instant time(String s){if(!s.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]{1,6})?Z"))throw new IllegalArgumentException();return Instant.parse(s);}
    public static void size(JsonObject o,int limit){if(o.toString().getBytes(StandardCharsets.UTF_8).length>limit)throw new IllegalArgumentException();}
}
