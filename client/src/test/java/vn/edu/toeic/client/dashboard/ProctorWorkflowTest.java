package vn.edu.toeic.client.dashboard;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.audio.*;

/** MOCK gateway; technical state/lifecycle tests, not backend authorization or human review. */
class ProctorWorkflowTest {
    static final String MANIFEST="a".repeat(64);final Fake gateway=new Fake();Instant now=Instant.parse("2026-10-15T00:00:00Z");
    ProctorWorkflow model(){return new ProctorWorkflow(gateway,Runnable::run,()->now,view->{});}
    static SessionAudioReadinessResponse ready(String session,boolean all){return new SessionAudioReadinessResponse("1",session,MANIFEST,all,List.of(new SessionAudioReadinessResponse.Attempt("A",all)));}
    static ListeningStatusResponse status(String phase,boolean interrupted,String start){return new ListeningStatusResponse("1","request","A","S",start,MANIFEST,2,phase,null,null,null,interrupted,null,null);}
    @Test void readinessMustBeFreshAndSameSessionAndConfirmed(){
        var m=model();m.refresh("S");gateway.reads.getLast().complete(ready("OTHER",true));assertThat(m.snapshot().canStart()).isFalse();
        m.refresh("S");gateway.reads.getLast().complete(ready("S",false));assertThat(m.snapshot().canStart()).isFalse();
        m.refresh("S");gateway.reads.getLast().complete(ready("S",true));assertThat(m.snapshot().canStart()).isTrue();
        m.start(false);assertThat(gateway.starts).isEmpty();now=now.plusSeconds(6);assertThat(m.snapshot().canStart()).isFalse();m.close();
    }
    @Test void timeoutReadsAuthorityThenRetriesSameIntentAndDoubleClickDoesNothing(){
        var m=model();m.refresh("S");gateway.reads.getLast().complete(ready("S",true));m.start(true);m.start(true);assertThat(gateway.starts).hasSize(1);
        gateway.startResults.getLast().completeExceptionally(new java.io.IOException("MOCK timeout"));
        assertThat(gateway.statuses).hasSize(1);gateway.statuses.getLast().complete(status("PREPARING",false,null));assertThat(m.snapshot().canStart()).isFalse();
        m.refresh("S");gateway.reads.getLast().complete(ready("S",true));m.start(true);assertThat(gateway.starts.get(0)).isSameAs(gateway.starts.get(1));
        var intent=gateway.starts.getLast();gateway.startResults.getLast().complete(new ListeningStartResponse("1",intent.requestId(),"S",MANIFEST,"START",now));
        assertThat(m.snapshot().canStart()).isFalse();assertThat(m.snapshot().message()).contains("START");m.close();
    }
    @Test void lostStartResponseRecoveredReadNeverBroadcastsOrStartsAgain(){
        var m=model();m.refresh("S");gateway.reads.getLast().complete(ready("S",true));m.start(true);
        gateway.startResults.getLast().completeExceptionally(new java.io.IOException("MOCK lost"));gateway.statuses.getLast().complete(status("PLAYING",false,"persisted"));
        assertThat(m.snapshot().message()).contains("persisted");m.start(true);assertThat(gateway.starts).hasSize(1);m.close();
    }
    @Test void reorganizationRequiresInterruptedServerStateAndExplicitConfirmationRetainsRetry(){
        var m=model();m.refresh("S");gateway.reads.getLast().complete(ready("S",false));
        m.reorganize("A","NEW","reason",true);assertThat(gateway.reorgs).isEmpty();
        m.readStatus("A");gateway.statuses.getLast().complete(status("INTERRUPTED",true,"OLD"));
        m.reorganize("A","NEW","reason",false);assertThat(gateway.reorgs).isEmpty();
        m.reorganize("A","NEW","reason",true);gateway.reorgResults.getLast().completeExceptionally(new java.io.IOException("MOCK timeout"));
        m.reorganize("A","CHANGED","reason",true);assertThat(gateway.reorgs).hasSize(1);
        m.reorganize("A","NEW","reason",true);assertThat(gateway.reorgs.getFirst()).isSameAs(gateway.reorgs.getLast());
        var q=gateway.reorgs.getLast();gateway.reorgResults.getLast().complete(new ListeningReorganizeResponse("1",q.requestId(),"A","NEW-owner","NEW"));
        assertThat(m.snapshot().message()).contains("NEW-owner");assertThat(m.snapshot().listening()).isNull();m.close();
    }
    @Test void switchingSessionsNeverDropsUnresolvedStartIntentButWorksAfterCommittedAck(){
        var m=model();m.refresh("S");gateway.reads.getLast().complete(ready("S",true));m.start(true);
        gateway.startResults.getLast().completeExceptionally(new java.io.IOException("MOCK lost"));gateway.statuses.getLast().complete(status("PREPARING",false,null));
        int reads=gateway.reads.size();m.refresh("OTHER");assertThat(m.snapshot().session()).isEqualTo("S");assertThat(gateway.reads).hasSize(reads);
        m.refresh("S");gateway.reads.getLast().complete(ready("S",true));m.start(true);var intent=gateway.starts.getLast();
        gateway.startResults.getLast().complete(new ListeningStartResponse("1",intent.requestId(),"S",MANIFEST,"ACK",now));
        m.refresh("OTHER");gateway.reads.getLast().complete(ready("OTHER",true));assertThat(m.snapshot().canStart()).isTrue();m.close();
    }
    @Test void closedOwnerIgnoresPendingCallbacks(){
        var m=model();m.refresh("S");m.close();gateway.reads.getLast().complete(ready("S",true));assertThat(m.snapshot().readiness()).isNull();assertThat(m.snapshot().canStart()).isFalse();
    }
    static class Fake implements ProctorWorkflow.Gateway {
        final List<CompletableFuture<SessionAudioReadinessResponse>> reads=new ArrayList<>();final List<ListeningStartRequest> starts=new ArrayList<>();
        final List<CompletableFuture<ListeningStartResponse>> startResults=new ArrayList<>();final List<CompletableFuture<ListeningStatusResponse>> statuses=new ArrayList<>();
        final List<ListeningReorganizeRequest> reorgs=new ArrayList<>();final List<CompletableFuture<ListeningReorganizeResponse>> reorgResults=new ArrayList<>();
        public CompletableFuture<SessionAudioReadinessResponse> readiness(String s){var f=new CompletableFuture<SessionAudioReadinessResponse>();reads.add(f);return f;}
        public CompletableFuture<ListeningStartResponse> start(String s,ListeningStartRequest q){starts.add(q);var f=new CompletableFuture<ListeningStartResponse>();startResults.add(f);return f;}
        public CompletableFuture<ListeningStatusResponse> status(String a){var f=new CompletableFuture<ListeningStatusResponse>();statuses.add(f);return f;}
        public CompletableFuture<ListeningReorganizeResponse> reorganize(String a,ListeningReorganizeRequest q){reorgs.add(q);var f=new CompletableFuture<ListeningReorganizeResponse>();reorgResults.add(f);return f;}
    }
}
