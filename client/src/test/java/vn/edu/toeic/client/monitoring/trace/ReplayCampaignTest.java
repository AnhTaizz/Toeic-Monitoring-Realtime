package vn.edu.toeic.client.monitoring.trace;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.monitoring.MonitoringReplayDriver;

class ReplayCampaignTest {
    @TempDir Path output;
    Path fixtures() throws Exception{return Path.of(getClass().getResource("/monitoring-traces/t3-c3/manifest.json").toURI()).getParent();}
    TraceData.Trace trace(String name) throws Exception{return TraceReader.read(fixtures().resolve(name+".jsonl"));}
    @ParameterizedTest @ValueSource(strings={"low-change","high-change","reconnect"})
    void oracleAndEncodersMatchHandwrittenPidsAndEventAppearanceTable(String name) throws Exception {
        var table=JsonParser.parseString(Files.readString(fixtures().resolve("handwritten-expected.json"))).getAsJsonObject().getAsJsonObject(name);
        var trace=trace(name);int reconnect=name.equals("reconnect")?6:0;
        var full=ReplayEngine.replay(trace,new FaultSchedule.Config(0,0,0,0,reconnect,0,0));
        var delta=ReplayEngine.replay(trace,new FaultSchedule.Config(0,0,0,0,reconnect,0,0),"delta",0);
        assertThat(full.status()).isEqualTo("PASS");assertThat(delta.status()).isEqualTo("PASS");
        assertThat(delta.businessEvents()).isEqualTo(full.businessEvents());
        var steps=delta.businessSteps().stream().filter(s->s.observation().equals("OBSERVATION")||s.observation().equals("SOURCE_FAILURE")).toList();
        for(int i=0;i<table.getAsJsonArray("pids").size();i++) {
            var pids=table.getAsJsonArray("pids").get(i).getAsJsonArray().asList().stream().map(v->v.getAsLong()).toList();
            var step=steps.get(i);assertThat(step.processKeys().stream().map(k->Long.parseLong(k.split("\\|")[1])).sorted().toList()).isEqualTo(pids);
            assertThat(step.newEventIds()).hasSize(table.getAsJsonArray("newEvents").get(i).getAsInt());
        }
        assertThat(delta.observationChecks()).allMatch(ReplayEngine.Check::match);
        var wire=MonitoringReplayDriver.encode(trace,reconnect,true);
        assertThat(wire).anyMatch(f->f.message().type().equals("MONITORING_FULL")&&f.scan()>30);
        assertThat(wire).anyMatch(f->f.message().type().equals("MONITORING_DELTA")&&f.message().payload().getAsJsonArray("added").isEmpty()&&f.message().payload().getAsJsonArray("removed").isEmpty());
        if(name.equals("high-change")) {
            assertThat(delta.observationChecks().stream().filter(c->c.record()==7).findFirst().orElseThrow().actualState().processes().getFirst().metadataQuality()).isEqualTo("UNREADABLE");
            assertThat(delta.observationChecks().stream().filter(c->c.record()==9).findFirst().orElseThrow().actualState().processes().getFirst().startInstant()).isNull();
        }
    }
    @Test void campaignRunsAllWorkloadsFaultsBothModesAndDeterministicReports() throws Exception {
        var summary=ReplayCampaign.run(fixtures(),output.resolve("campaign"),20261012,"6a4103161ad5bebde5277bc6a415b6c21a0cce6b");
        assertThat(summary.status()).as(summary.issues().toString()).isEqualTo("PASS");
        assertThat(summary.cases()).hasSize(72).allMatch(c->c.status().equals("PASS")&&c.repeatIdentical());
        assertThat(summary.comparisons()).hasSize(36).allMatch(p->p.businessEventsEqual()&&p.deliveredEventsEqual()&&(p.comparableObservations()>0||p.scenario().equals("seeded")&&p.comparisonStatus().equals("NO_COMMON_SYNC_CHECKPOINT")));
        assertThat(summary.negativeChecks()).hasSize(4).allMatch(ReplayCampaign.Negative::detected);
        assertThat(summary.cases()).anyMatch(c->c.mode().equals("delta")&&c.scenario().equals("retry-resync")&&c.resyncRequests()>0);
    }
    @ParameterizedTest @CsvSource({"drop-first,6","drop-state,0","reorder,0","retry-resync,0"})
    void missingBasePreservesLastKnownAndDoesNotRecoverByDuplicate(String scenario,int reconnect) throws Exception {
        var result=ReplayEngine.replay(trace("low-change"),new FaultSchedule.Config(1,0,0,0,reconnect,0,0),"delta",0,scenario,0);
        assertThat(result.status()).isEqualTo("PASS");assertThat(result.resyncRequests()).isNotEmpty();
        assertThat(result.lastDataState().status()).isEqualTo("SYNCED");
        for(int i=0;i<result.faultChecks().size();i++)if(result.faultChecks().get(i).actualOutcome().equals("RESYNC")) {
            var before=result.faultChecks().get(i-1).actualState();var after=result.faultChecks().get(i).actualState();
            assertThat(after.processes()).isEqualTo(before.processes());assertThat(after.sequence()).isEqualTo(before.sequence());
            assertThat(after.status()).isEqualTo("UNSYNCED");
        }
        if(scenario.equals("retry-resync"))assertThat(result.faultChecks()).anyMatch(c->c.operation().equals("RETRY_AFTER_RESYNC")&&c.actualOutcome().equals("DUPLICATE")&&c.actualState().status().equals("UNSYNCED"));
    }
    @ParameterizedTest @ValueSource(strings={"full","delta"})
    void conflictByIdAndAliasSequenceCoversFullDeltaEventWithoutChangingState(String mode) throws Exception {
        for(String scenario:List.of("conflict-id","conflict-sequence")) {
            var result=ReplayEngine.replay(trace("low-change"),FaultSchedule.Config.none(),mode,0,scenario,0);
            assertThat(result.status()).isEqualTo("PASS");
            assertThat(result.faultChecks().stream().filter(c->c.operation().startsWith("CONFLICT_")).map(ReplayEngine.Check::actualOutcome).toList())
                    .hasSize(mode.equals("delta")?3:2).containsOnly("CONFLICT");
        }
    }
    @Test void fullRecoveryCannotRepairDroppedHistoricalEvent() throws Exception {
        var result=ReplayEngine.replay(trace("low-change"),FaultSchedule.Config.none(),"delta",0,"event-drop",0);
        assertThat(result.status()).isEqualTo("PASS");assertThat(result.lastDataState().status()).isEqualTo("SYNCED");
        assertThat(result.missingBusinessEvents()).containsExactly("replay-event-1");
        assertThat(result.deliveredEvents()).isEqualTo(result.expectedDeliveredEvents());
    }
    @Test void epochAndLowVersionLateMessagesCannotOverwriteRecoveredState() throws Exception {
        for(String scenario:List.of("late-after-full","reconnect-old")) {
            var result=ReplayEngine.replay(trace("low-change"),new FaultSchedule.Config(0,0,0,0,scenario.equals("reconnect-old")?6:0,0,0),"delta",0,scenario,0);
            assertThat(result.status()).isEqualTo("PASS");assertThat(result.faultChecks()).anyMatch(c->c.operation().equals("RELEASE_LATE")&&c.actualOutcome().equals("STALE"));
        }
    }
    @Test void negativeDeltaMetadataAndEventContentAreDetectedIndependently() throws Exception {
        assertThat(ReplayEngine.replay(trace("low-change"),FaultSchedule.Config.none(),"delta",2).status()).isEqualTo("FAIL");
        for(String mode:List.of("full","delta"))assertThat(ReplayEngine.replay(trace("low-change"),FaultSchedule.Config.none(),mode,0,"clean",1).status()).isEqualTo("FAIL");
    }
    @Test void cliExitCodesHashValidationAndNoOverwrite() throws Exception {
        var console=new PrintStream(new ByteArrayOutputStream());String input=fixtures().resolve("low-change.jsonl").toString();
        assertThat(TraceCli.run(new String[]{"replay","--input",input,"--output",output.resolve("fail-event.json").toString(),"--mutate-event-at","1"},console)).isEqualTo(1);
        assertThat(TraceCli.run(new String[]{"replay","--input",input,"--output",output.resolve("invalid.json").toString(),"--scenario","invalid"},console)).isEqualTo(2);
        assertThat(TraceCli.run(new String[]{"replay","--input",input,"--output",output.resolve("invalid-combination.json").toString(),"--scenario","clean","--drop","5"},console)).isEqualTo(2);
        var copy=output.resolve("fixtures");Files.createDirectory(copy);
        try(var paths=Files.list(fixtures())){for(var p:paths.toList())Files.copy(p,copy.resolve(p.getFileName()));}
        Files.writeString(copy.resolve("low-change.jsonl"),"corrupt");
        assertThatThrownBy(()->ReplayCampaign.run(copy,output.resolve("bad"),1,"6a4103161ad5bebde5277bc6a415b6c21a0cce6b")).hasMessage("CAMPAIGN_FIXTURE_HASH_OR_PATH");
        assertThat(output.resolve("bad")).doesNotExist();
        assertThatThrownBy(()->ReplayCampaign.run(fixtures(),output,1,"6a4103161ad5bebde5277bc6a415b6c21a0cce6b")).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
    }
    @Test void lateEventUsesCurrentDeliveryConnectionNotOriginConnection() {
        var trace=TraceFixtures.hand();var frames=MonitoringReplayDriver.encode(trace,3,true);
        var server=new ReplayServer(true);var oracle=new ObservationOracle(trace,3);
        var event=frames.stream().filter(f->f.message().type().equals("PROCESS_OBSERVED")).findFirst().orElseThrow();
        for(var frame:frames)if(!frame.message().type().equals("PROCESS_OBSERVED")){server.apply(frame);oracle.apply(frame);}
        server.apply(event);oracle.apply(event);
        assertThat(server.events().get("replay-event-1").get("deliveryStatus").getAsString()).isEqualTo("PREVIOUS_CONNECTION");
        assertThat(server.events()).isEqualTo(oracle.events());
    }
}
