package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Deterministic acceptance replay campaign; never launches a desktop, network, database or benchmark. */
public final class ReplayCampaign {
    public record Case(String workload,String mode,String scenario,String report,String reportSha256,String status,
            int resyncRequests,int recoveryPoints,String lastState,int missingBusinessEvents,boolean repeatIdentical,List<String> issues) { }
    public record Pair(String workload,String scenario,boolean businessEventsEqual,boolean deliveredEventsEqual,
            int comparableObservations,int expectedFaultDivergences,String comparisonStatus,String rule) { }
    public record Negative(String name,String report,String reportSha256,String expectedStatus,String actualStatus,boolean detected) { }
    public record Summary(String schemaVersion,String status,String sourceSha,String fixtureManifestSha256,long seed,String provenance,
            List<Case> cases,List<Pair> comparisons,List<Negative> negativeChecks,List<String> issues) { }
    private record Workload(String name,TraceData.Trace trace,int reconnectAt) { }
    private ReplayCampaign() { }
    public static Summary run(Path fixtures,Path output,long seed,String sourceSha) throws Exception {
        if(!sourceSha.matches("[a-f0-9]{40}"))throw new IllegalArgumentException("CAMPAIGN_SOURCE_SHA");
        var root=fixtures.toRealPath();var manifestPath=root.resolve("manifest.json");
        if(Files.size(manifestPath)>65536)throw new IllegalArgumentException("CAMPAIGN_MANIFEST_LIMIT");
        var manifest=TraceJson.object(Files.readString(manifestPath));
        TraceJson.fields(manifest,Set.of("schemaVersion","provenance","workloads"));
        if(!TraceJson.text(manifest,"schemaVersion").equals("monitoring-replay-fixtures-v1")||!TraceJson.text(manifest,"provenance").equals("MOCK_HANDWRITTEN"))
            throw new IllegalArgumentException("CAMPAIGN_MANIFEST_VERSION_OR_SOURCE");
        var workloads=new ArrayList<Workload>();var names=new java.util.HashSet<String>();
        if(!manifest.get("workloads").isJsonArray()||manifest.getAsJsonArray("workloads").size()!=3)throw new IllegalArgumentException("CAMPAIGN_THREE_WORKLOADS_REQUIRED");
        for(var item:manifest.getAsJsonArray("workloads")) {
            var entry=item.getAsJsonObject();TraceJson.fields(entry,Set.of("name","file","fileSha256","traceChecksum","reconnectAt"));
            String name=TraceJson.text(entry,"name"),file=TraceJson.text(entry,"file");
            if(!Set.of("low-change","high-change","reconnect").contains(name)||!names.add(name)||!file.matches("[a-z-]+\\.jsonl"))throw new IllegalArgumentException("CAMPAIGN_WORKLOAD");
            Path path=root.resolve(file).toRealPath();if(!path.startsWith(root)||!sha(path).equals(TraceJson.text(entry,"fileSha256")))throw new IllegalArgumentException("CAMPAIGN_FIXTURE_HASH_OR_PATH");
            var trace=TraceReader.read(path);
            if(!trace.header().schemaVersion().equals(TraceData.SCHEMA)||!trace.header().sourceLabel().equals("MOCK")||!trace.sha256().equals(TraceJson.text(entry,"traceChecksum")))
                throw new IllegalArgumentException("CAMPAIGN_FIXTURE_SCHEMA_OR_CHECKSUM");
            long reconnect=TraceJson.number(entry,"reconnectAt");if(reconnect<0||reconnect>9998)throw new IllegalArgumentException("CAMPAIGN_RECONNECT");
            workloads.add(new Workload(name,trace,(int)reconnect));
        }
        // Validate inputs before creating any output. Never overwrite an earlier campaign.
        Path parent=output.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);Files.createDirectory(output);
        var cases=new ArrayList<Case>();var pairs=new ArrayList<Pair>();var negatives=new ArrayList<Negative>();var issues=new ArrayList<String>();
        for(var workload:workloads)for(String scenario:FaultSchedule.SCENARIOS) {
            var results=new TreeMap<String,ReplayEngine.Result>();
            int reconnect=Set.of("drop-first","reconnect-old").contains(scenario)?6:workload.reconnectAt();
            var config=scenario.equals("seeded")?new FaultSchedule.Config(seed,45,15,65,reconnect,0,0):new FaultSchedule.Config(seed,0,0,0,reconnect,0,0);
            for(String mode:List.of("full","delta")) {
                var result=ReplayEngine.replay(workload.trace(),config,mode,0,scenario,0);
                String first=json(result),repeat=json(ReplayEngine.replay(workload.trace(),config,mode,0,scenario,0));
                boolean identical=first.equals(repeat);var caseIssues=new ArrayList<>(result.issues());
                if(!identical)caseIssues.add("NONDETERMINISTIC_REPORT");
                requireFaultCoverage(scenario,mode,result,caseIssues);
                String report=workload.name()+"-"+scenario+"-"+mode+".json";write(output.resolve(report),first);
                String status=caseIssues.isEmpty()?"PASS":"FAIL";
                cases.add(new Case(workload.name(),mode,scenario,report,sha(output.resolve(report)),status,result.resyncRequests().size(),
                        result.recoveryPoints().size(),result.lastDataState().status(),result.missingBusinessEvents().size(),identical,List.copyOf(caseIssues)));
                if(!status.equals("PASS"))issues.add(workload.name()+"/"+scenario+"/"+mode+":"+caseIssues);
                results.put(mode,result);
            }
            var full=results.get("full");var delta=results.get("delta");
            boolean business=full.businessEvents().equals(delta.businessEvents()),delivered=full.deliveredEvents().equals(delta.deliveredEvents());
            int comparable=0,divergent=0;
            var fullCheckpoints=checkpoints(full);var deltaCheckpoints=checkpoints(delta);
            for(var sample:workload.trace().samples())if(sample.type().equals("OBSERVATION")) {
                var f=fullCheckpoints.floorEntry(sample.index());var d=deltaCheckpoints.floorEntry(sample.index());
                if(f!=null&&d!=null&&f.getValue().status().equals("SYNCED")&&d.getValue().status().equals("SYNCED")
                        &&f.getValue().processes().equals(sample.productionProcesses())&&d.getValue().processes().equals(sample.productionProcesses()))comparable++;
                else divergent++; // Independently checked against each oracle; not forced equal while missing a base.
            }
            if(!business||!delivered||comparable==0&&!scenario.equals("seeded"))issues.add("CROSS_MODE "+workload.name()+"/"+scenario);
            pairs.add(new Pair(workload.name(),scenario,business,delivered,comparable,divergent,comparable==0?"NO_COMMON_SYNC_CHECKPOINT":"COMPARABLE_OBSERVATIONS_CHECKED",
                    "Compare raw observation identity+metadata only when both match that observation; sequence differs. Event decisions keyed by eventId; state schedules mode-specific. Each fault checkpoint independently checked."));
        }
        var low=workloads.stream().filter(w->w.name().equals("low-change")).findFirst().orElseThrow();
        negative(output,negatives,"wrong-full",ReplayEngine.replay(low.trace(),new FaultSchedule.Config(seed,0,0,0,0,0,1)),issues);
        negative(output,negatives,"wrong-delta",ReplayEngine.replay(low.trace(),FaultSchedule.Config.none(),"delta",2),issues);
        for(String mode:List.of("full","delta"))negative(output,negatives,"wrong-event-"+mode,
                ReplayEngine.replay(low.trace(),FaultSchedule.Config.none(),mode,0,"clean",1),issues);
        var summary=new Summary("monitoring-replay-campaign-v1",issues.isEmpty()?"PASS":"FAIL",sourceSha,sha(manifestPath),seed,
                "MOCK observations; production encoders/reducer; REPLAY data faults SIMULATED; baseline ACK enumeration only, resync recorded/no feedback; no retry/ACK-loss/network/auth/DB/presence/TTL/live gate/E1/E2",List.copyOf(cases),List.copyOf(pairs),List.copyOf(negatives),List.copyOf(issues));
        write(output.resolve("summary.json"),json(summary));return summary;
    }
    private static TreeMap<Long,ObservationOracle.State> checkpoints(ReplayEngine.Result result) {
        var checkpoints=new TreeMap<Long,ObservationOracle.State>();
        for(int i=0;i<result.faultSchedule().size();i++)checkpoints.put(result.faultSchedule().get(i).atRecord(),result.faultChecks().get(i).actualState());
        return checkpoints;
    }
    private static void requireFaultCoverage(String scenario,String mode,ReplayEngine.Result result,List<String> issues) {
        var checks=result.faultChecks();
        if(scenario.equals("duplicate")&&!checks.stream().anyMatch(c->c.actualOutcome().equals("DUPLICATE")))issues.add("NO_DUPLICATE_COVERAGE");
        if(scenario.startsWith("conflict-")&&!checks.stream().anyMatch(c->c.operation().startsWith("CONFLICT_")&&c.actualOutcome().equals("CONFLICT")))issues.add("NO_CONFLICT_COVERAGE");
        if(scenario.equals("event-drop")&&result.missingBusinessEvents().isEmpty())issues.add("NO_MISSING_EVENT_COVERAGE");
        if(scenario.equals("reconnect-old")&&!checks.stream().anyMatch(c->c.operation().equals("RELEASE_LATE")&&c.actualOutcome().equals("STALE")))issues.add("NO_OLD_EPOCH_COVERAGE");
        if(scenario.equals("late-after-full")&&!checks.stream().anyMatch(c->c.operation().equals("RELEASE_LATE")&&c.actualOutcome().equals("STALE")))issues.add("NO_LATE_VERSION_COVERAGE");
        if(mode.equals("delta")&&Set.of("drop-first","drop-state","reorder","retry-resync").contains(scenario)&&result.resyncRequests().isEmpty())issues.add("NO_RESYNC_COVERAGE");
        if(mode.equals("delta")&&scenario.equals("retry-resync")&&!checks.stream().anyMatch(c->c.operation().equals("RETRY_AFTER_RESYNC")&&c.actualOutcome().equals("DUPLICATE")&&c.actualState().status().equals("UNSYNCED")))issues.add("NO_RETRY_UNSYNCED_COVERAGE");
        if(!scenario.equals("seeded")&&!result.lastDataState().status().equals("SYNCED"))issues.add("NO_FINAL_RECOVERY");
    }
    private static void negative(Path output,List<Negative> negatives,String name,ReplayEngine.Result result,List<String> issues) throws IOException {
        String file="negative-"+name+".json";write(output.resolve(file),json(result));boolean detected=result.status().equals("FAIL")&&!result.issues().isEmpty();
        negatives.add(new Negative(name,file,sha(output.resolve(file)),"FAIL",result.status(),detected));if(!detected)issues.add("NEGATIVE_NOT_DETECTED:"+name);
    }
    static String json(Object value){return new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create().toJson(value)+"\n";}
    private static void write(Path path,String value) throws IOException {Files.writeString(path,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static String sha(Path path) throws IOException {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
