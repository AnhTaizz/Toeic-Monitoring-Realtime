package vn.edu.toeic.client.monitoring.harness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import vn.edu.toeic.client.monitoring.trace.TraceJson;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

/** Small, versioned ground-truth file. Observation trace continues to use C3 reader/writer. */
public final class HarnessArtifacts {
    public static final Gson JSON=new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final int LIMIT=1024*1024;
    private static final Set<String> TRIAL_FIELDS=Set.of("kind","planned","queuedElapsedNanos","launchBeginElapsedNanos","startReturnedElapsedNanos",
            "registeredElapsedNanos","readyReceivedElapsedNanos","goSentElapsedNanos","exitObservedElapsedNanos","pid","processName","startInstant",
            "metadataQuality","status","exitCode","forcedCleanup","observedLifetimeNanos","observedHoldNanos");
    private HarnessArtifacts(){ }
    public static String writeGroundTruth(Path path,GroundTruth.Header header,List<GroundTruth.Trial> trials,String status) throws IOException {
        if(trials.size()!=header.config().perDuration()*3||status.equals("COMPLETE")&&trials.stream().anyMatch(t -> !t.status().equals("EXITED")))throw new IllegalArgumentException();
        var bytes=new java.io.ByteArrayOutputStream();var body=JSON.toJsonTree(header).getAsJsonObject();body.addProperty("kind","HEADER");append(bytes,body);
        for(var trial:trials) {
            body=JSON.toJsonTree(trial).getAsJsonObject();body.addProperty("kind","TRIAL");body.addProperty("observedLifetimeNanos",trial.observedLifetimeNanos());body.addProperty("observedHoldNanos",trial.observedHoldNanos());append(bytes,body);
        }
        String checksum=sha256(bytes.toByteArray());body=new JsonObject();body.addProperty("kind","FINAL");body.addProperty("status",status);body.addProperty("count",trials.size());body.addProperty("sha256",checksum);append(bytes,body);
        Files.write(path,bytes.toByteArray(),StandardOpenOption.CREATE_NEW);return checksum;
    }
    private static void append(java.io.ByteArrayOutputStream bytes,JsonObject value)throws IOException {
        byte[] line=(JSON.toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);if(line.length>8192||bytes.size()+line.length>LIMIT)throw new IOException("GROUND_TRUTH_LIMIT");bytes.write(line);
    }
    public static GroundTruth.Run readGroundTruth(Path path)throws IOException {
        int lineNumber=0;
        try {
            byte[] data=bounded(path);String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
            if(text.contains("\r")||!text.endsWith("\n"))throw new IOException("LF_OR_TRUNCATED");
            String[] lines=text.split("\n",-1);if(lines.length<4||lines.length>94)throw new IOException("RECORD_LIMIT");
            var hash=MessageDigest.getInstance("SHA-256");GroundTruth.Header header=null;var trials=new ArrayList<GroundTruth.Trial>();String status=null,checksum=null;
            for(int index=0;index<lines.length-1;index++) {
                lineNumber=index+1;if(lines[index].getBytes(StandardCharsets.UTF_8).length>8191)throw new IOException("LINE_LIMIT");var body=TraceJson.object(lines[index]);
                String kind=FullSnapshotPayload.text(body,"kind");
                if(index==0) {
                    TraceJson.fields(body,Set.of("kind","schemaVersion","runId","sourceLabel","clockDomain","collectorSessionId","traceStartElapsedNanos","config"));
                    if(!kind.equals("HEADER"))throw new IOException("HEADER_REQUIRED");
                    TraceJson.fields(body.getAsJsonObject("config"),Set.of("pollMillis","seed","perDuration","maxConcurrent","readyTimeoutMillis","exitSlackMillis","maxRunMillis","sourceSha"));
                    body.remove("kind");header=JSON.fromJson(body,GroundTruth.Header.class);
                } else if(index==lines.length-2) {
                    TraceJson.fields(body,Set.of("kind","status","count","sha256"));if(!kind.equals("FINAL"))throw new IOException("FINAL_REQUIRED");
                    status=FullSnapshotPayload.text(body,"status");checksum=HexFormat.of().formatHex(hash.digest());
                    if(!checksum.equals(FullSnapshotPayload.text(body,"sha256"))||FullSnapshotPayload.number(body,"count")!=trials.size())throw new IOException("CHECKSUM_OR_COUNT");
                    continue;
                } else {
                    TraceJson.fields(body,TRIAL_FIELDS);if(!kind.equals("TRIAL"))throw new IOException("TRIAL_REQUIRED");
                    TraceJson.fields(body.getAsJsonObject("planned"),Set.of("trialId","targetMillis","phaseMillis","plannedLaunchElapsedNanos"));
                    var lifetime=body.remove("observedLifetimeNanos");var hold=body.remove("observedHoldNanos");body.remove("kind");
                    var trial=JSON.fromJson(body,GroundTruth.Trial.class);
                    if(!JSON.toJsonTree(trial.observedLifetimeNanos()).equals(lifetime)||!JSON.toJsonTree(trial.observedHoldNanos()).equals(hold))throw new IOException("DERIVED_TIME");trials.add(trial);
                }
                hash.update((lines[index]+"\n").getBytes(StandardCharsets.UTF_8));
            }
            var expected=HarnessPlan.create(header.config());if(!trials.stream().map(GroundTruth.Trial::planned).toList().equals(expected))throw new IOException("PLAN_MISMATCH");
            if("COMPLETE".equals(status)&&trials.stream().anyMatch(t -> !t.status().equals("EXITED")))throw new IOException("FALSE_COMPLETE");
            return new GroundTruth.Run(header,trials,status,checksum);
        } catch(Exception invalid){throw new IOException("GROUND_TRUTH line "+Math.max(1,lineNumber)+": INVALID_OR_INCOMPLETE");}
    }
    public static void writeJson(Path path,Object value)throws IOException {
        byte[] bytes=(new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create().toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);
        if(bytes.length>LIMIT)throw new IOException("JSON_LIMIT");Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
    }
    public static JsonObject readJson(Path path)throws IOException{return TraceJson.object(new String(bounded(path),StandardCharsets.UTF_8));}
    private static byte[] bounded(Path path)throws IOException {
        try(var input=Files.newInputStream(path)){byte[] data=input.readNBytes(LIMIT+1);if(data.length>LIMIT)throw new IOException("FILE_LIMIT");return data;}
    }
    public static String sha256(byte[] bytes) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    public static String fileChecksum(Path path)throws IOException {
        try(var input=Files.newInputStream(path)) {
            var hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int count,total=0;
            while((count=input.read(buffer))!=-1){total+=count;if(total>16*1024*1024)throw new IOException("FILE_LIMIT");hash.update(buffer,0,count);}
            return HexFormat.of().formatHex(hash.digest());
        } catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static void manifest(Path directory,String status)throws IOException {
        var files=new JsonObject();for(String name:List.of("ground-truth.jsonl","observations.jsonl","metadata.json","join.json")) {
            Path file=directory.resolve(name);files.addProperty(name,Files.isRegularFile(file)?fileChecksum(file):null);
        }
        var body=new JsonObject();body.addProperty("schemaVersion","controlled-harness-manifest-v1");body.addProperty("status",status);body.add("files",files);writeJson(directory.resolve("manifest.json"),body);
    }
    public static boolean verifyManifest(Path directory) {
        try {
            var body=readJson(directory.resolve("manifest.json"));TraceJson.fields(body,Set.of("schemaVersion","status","files"));
            if(!FullSnapshotPayload.text(body,"schemaVersion").equals("controlled-harness-manifest-v1")||!FullSnapshotPayload.text(body,"status").equals("COMPLETE"))return false;
            var files=body.getAsJsonObject("files");TraceJson.fields(files,Set.of("ground-truth.jsonl","observations.jsonl","metadata.json","join.json"));
            for(var entry:files.entrySet())if(!fileChecksum(directory.resolve(entry.getKey())).equals(entry.getValue().getAsString()))return false;return true;
        } catch(Exception invalid){return false;}
    }
}
