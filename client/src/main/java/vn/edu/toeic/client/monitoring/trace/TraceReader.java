package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Set;

/** Strict UTF-8/LF, bounded read. SHA256 covers original HEADER+RECORD bytes including every LF. */
public final class TraceReader {
    public static final int MAX_RECORDS=10000,MAX_LINE_BYTES=65536,MAX_FILE_BYTES=16*1024*1024;
    private TraceReader() { }
    public static TraceData.Trace read(Path path) throws IOException {
        if(Files.size(path)>MAX_FILE_BYTES) throw new IOException("TRACE_FILE_LIMIT");
        var samples=new ArrayList<TraceData.Sample>();TraceData.Header header=null;boolean finished=false;
        MessageDigest hash=sha256();String checksum=null;int lineNumber=0,total=0;
        try(InputStream input=new BufferedInputStream(Files.newInputStream(path))) {
            byte[] bytes;
            while((bytes=line(input))!=null) {
                lineNumber++;total+=bytes.length;if(total>MAX_FILE_BYTES)throw new IOException("FILE_LIMIT");
                if(finished)throw new IOException("AFTER_FINAL");
                var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
                JsonObject body=TraceJson.object(decoder.decode(ByteBuffer.wrap(bytes,0,bytes.length-1)).toString());
                if(lineNumber==1) {header=TraceJson.header(body);hash.update(bytes);continue;}
                if(TraceJson.text(body,"kind").equals("FINAL")) {
                    TraceJson.fields(body,Set.of("kind","status","attempted","written","dropped","sha256"));
                    checksum=HexFormat.of().formatHex(hash.digest());
                    if(!"COMPLETE".equals(TraceJson.text(body,"status"))||TraceJson.number(body,"dropped")!=0
                            ||TraceJson.number(body,"written")!=samples.size()||TraceJson.number(body,"attempted")!=samples.size()
                            ||!checksum.equals(TraceJson.text(body,"sha256"))) throw new IOException("CHECKSUM_OR_INCOMPLETE");
                    finished=true;continue;
                }
                if(samples.size()>=MAX_RECORDS)throw new IOException("RECORD_LIMIT");
                var sample=TraceJson.sample(body);
                if(sample.index()!=samples.size()+1L)throw new IOException("RECORD_SEQUENCE");
                if(samples.isEmpty()) {if(!sample.type().equals("START")||sample.elapsedNanos()!=0)throw new IOException("START_REQUIRED");}
                else {var last=samples.getLast();if(last.type().equals("STOP")||sample.type().equals("START")
                            ||sample.elapsedNanos()<last.elapsedNanos()||!sample.collectorSessionId().equals(last.collectorSessionId()))throw new IOException("LIFECYCLE_OR_TIME");}
                samples.add(sample);hash.update(bytes);
            }
            if(!finished||header==null||samples.size()<2||!samples.getLast().type().equals("STOP"))throw new IOException("MISSING_FINAL_OR_STOP");
            return new TraceData.Trace(header,samples,checksum);
        } catch(IOException|RuntimeException invalid) {
            throw new IOException("TRACE line "+Math.max(1,lineNumber)+": "+(invalid instanceof IOException?invalid.getMessage():"INVALID_RECORD"));
        }
    }
    private static byte[] line(InputStream input) throws IOException {
        var bytes=new ByteArrayOutputStream();int value;
        while((value=input.read())!=-1) {
            if(value=='\r')throw new IOException("LF_REQUIRED");
            bytes.write(value);if(bytes.size()>MAX_LINE_BYTES)throw new IOException("LINE_LIMIT");
            if(value=='\n')return bytes.toByteArray();
        }
        if(bytes.size()>0)throw new IOException("TRUNCATED_LINE");return null;
    }
    static MessageDigest sha256() {try{return MessageDigest.getInstance("SHA-256");}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}
