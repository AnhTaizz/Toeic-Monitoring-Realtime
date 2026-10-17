package vn.edu.toeic.client.proctor;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;

/** MOCK gateway + controlled latches, real bounded controller workers. */
class ProctorSessionControllerTest {
    private final Fake api=new Fake();private final AtomicInteger expired=new AtomicInteger();
    private final BlockingQueue<ProctorSessionController.Snapshot> states=new LinkedBlockingQueue<>();
    private final ProctorSessionController controller=new ProctorSessionController(api,states::add,expired::incrementAndGet,()->{});
    @AfterEach void close(){api.release.countDown();controller.close();}
    private ProctorSessionController.Snapshot await(java.util.function.Predicate<ProctorSessionController.Snapshot> predicate)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(System.nanoTime()<end){var s=states.poll(100,TimeUnit.MILLISECONDS);if(s!=null&&predicate.test(s))return s;}
        throw new AssertionError("No expected controller state");
    }
    @Test void missingReadinessAndStaleRefreshCannotStart()throws Exception{
        controller.start();assertThat(api.starts).isEmpty();controller.loadSession("S");await(s->!s.readyStale());
        api.failRead=true;controller.loadSession("S");var stale=await(s->!s.readyLoading()&&s.readyStale());
        assertThat(stale.readiness()).isNotNull();controller.start();assertThat(api.starts).isEmpty();
    }
    @Test void startResponseLostRetriesWholeRequestAndDoubleClickDoesNotSendTwice()throws Exception{
        controller.loadSession("S");await(s->!s.readyStale());api.blockStart=true;controller.start();assertThat(api.entered.await(3,TimeUnit.SECONDS)).isTrue();
        controller.start();assertThat(api.starts).hasSize(1);api.release.countDown();var failed=await(s->!s.busy()&&s.pendingStart()!=null);
        var sent=failed.pendingStart();api.blockStart=false;controller.start();await(s->!s.busy()&&s.pendingStart()==null);
        assertThat(api.starts).containsExactly(sent,sent);
    }
    @Test void reorganizationUncertaintyKeepsRequestPayloadEvenWhenFormChanges()throws Exception{
        controller.loadAttempt("OLD");await(s->!s.resultStale());api.loseReorganize=true;
        controller.reorganize("NEW","Thi lại");var failed=await(s->!s.busy()&&s.pendingReorganization()!=null);
        api.loseReorganize=false;controller.reorganize("OTHER","changed");var done=await(s->s.replacement()!=null);
        assertThat(api.reorganizations).containsExactly(failed.pendingReorganization(),failed.pendingReorganization());
        assertThat(done.replacement().newSessionId()).isEqualTo("NEW");assertThat(done.knownAttempts()).contains("OLD","NEW-candidate1");
    }
    @Test void lateSessionResponseCannotOverwriteNewSelection()throws Exception{
        api.blockRead=true;controller.loadSession("A");assertThat(api.entered.await(3,TimeUnit.SECONDS)).isTrue();
        controller.loadSession("B");await(s->"B".equals(s.session())&&!s.readyStale());api.release.countDown();
        assertThat(api.finished.await(3,TimeUnit.SECONDS)).isTrue();
        assertThat(controller.snapshot().session()).isEqualTo("B");assertThat(controller.snapshot().readiness().sessionId()).isEqualTo("B");
    }
    @Test void changedAttemptRejectsOldResultAndCloseSuppressesCallback()throws Exception{
        api.blockResult=true;controller.loadAttempt("A");assertThat(api.entered.await(3,TimeUnit.SECONDS)).isTrue();
        controller.loadAttempt("B");await(s->"B".equals(s.attempt())&&!s.resultStale());controller.close();states.clear();api.release.countDown();
        assertThat(api.finished.await(3,TimeUnit.SECONDS)).isTrue();assertThat(states).isEmpty();assertThat(api.closed).isTrue();
    }
    @Test void unauthorizedExpiresSessionForbiddenDoesNot()throws Exception{
        api.reject=403;controller.loadSession("S");await(s->!s.readyLoading());assertThat(expired).hasValue(0);
        api.reject=401;controller.loadSession("S");await(s->!s.readyLoading());assertThat(expired).hasValue(1);
    }
    @Test void noInterruptionCannotReorganize()throws Exception{
        controller.reorganize("NEW","Thi lại");assertThat(api.reorganizations).isEmpty();api.interrupted=false;
        controller.loadAttempt("OLD");await(s->!s.resultStale());controller.reorganize("NEW","Thi lại");assertThat(api.reorganizations).isEmpty();
    }
    @Test void readyAgeUsesMonotonicTimeAndSuccessfulSessionCannotStartAgain()throws Exception{
        var clock=new AtomicLong(100);var q=new LinkedBlockingQueue<ProctorSessionController.Snapshot>();
        try(var c=new ProctorSessionController(api,q::add,()->{},()->{},clock::get)){
            c.loadSession("S");poll(q,x->!x.readyStale());assertThat(c.snapshot().canStart()).isTrue();
            clock.addAndGet(TimeUnit.SECONDS.toNanos(5));assertThat(c.snapshot().canStart()).isFalse();c.start();assertThat(api.starts).isEmpty();
            q.clear();c.loadSession("S");poll(q,x->!x.readyLoading()&&!x.readyStale());c.start();poll(q,x->!x.busy()&&x.notice().startsWith("Server xác nhận"));
            q.clear();c.loadSession("S");poll(q,x->!x.readyLoading()&&!x.readyStale());assertThat(c.snapshot().canStart()).isFalse();assertThat(api.starts).hasSize(1);
        }
    }
    @Test void uncertainStartCannotChangeSessionAndServerStatusSettlesCommittedStart()throws Exception{
        controller.loadSession("S");await(x->!x.readyStale());api.blockStart=true;api.proveStarted=true;
        controller.start();assertThat(api.entered.await(3,TimeUnit.SECONDS)).isTrue();controller.loadSession("OTHER");
        assertThat(controller.snapshot().session()).isEqualTo("S");api.release.countDown();
        var known=await(x->x.notice().startsWith("Server ghi nhận ca đã start"));
        assertThat(known.pendingStart()).isNull();assertThat(known.canStart()).isFalse();assertThat(api.starts).hasSize(1);
        controller.loadSession("OTHER");await(x->"OTHER".equals(x.session())&&!x.readyStale());
    }
    @Test void legacyRawAttemptReadNeverCallsMonitoringOnlyListeningRoute()throws Exception{
        String raw="  cũ/đề% + ";controller.loadAttempt(raw);var s=await(x->!x.resultStale());
        assertThat(s.result().attemptId()).isEqualTo(raw);assertThat(s.listening()).isNull();assertThat(api.listeningReads).hasValue(0);
    }
    private static ProctorSessionController.Snapshot poll(BlockingQueue<ProctorSessionController.Snapshot> q,java.util.function.Predicate<ProctorSessionController.Snapshot> accept)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(System.nanoTime()<end){var s=q.poll(100,TimeUnit.MILLISECONDS);if(s!=null&&accept.test(s))return s;}
        throw new AssertionError("Missing expected state");
    }
    private static final class Fake implements ProctorSessionApi {
        volatile boolean failRead,blockStart,loseReorganize,blockRead,blockResult,closed,interrupted=true;volatile int reject;volatile boolean proveStarted;final AtomicInteger listeningReads=new AtomicInteger();
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        final List<ListeningStartRequest> starts=new CopyOnWriteArrayList<>();final List<ListeningReorganizeRequest> reorganizations=new CopyOnWriteArrayList<>();
        @Override public SessionAudioReadinessResponse readiness(String s)throws Exception{
            if(blockRead&&s.equals("A")){entered.countDown();release.await();finished.countDown();}
            if(reject!=0)throw new ProctorSessionApiClient.Rejected(reject,"FORBIDDEN");if(failRead)throw new IOException();
            return new SessionAudioReadinessResponse("v0",s,"a".repeat(64),true,List.of(new SessionAudioReadinessResponse.Attempt(s+"-candidate1",true)));
        }
        @Override public ListeningStartResponse start(String s,ListeningStartRequest r)throws Exception{starts.add(r);if(blockStart){entered.countDown();release.await();throw new IOException();}return new ListeningStartResponse("v0",r.requestId(),s,r.manifestId(),"START",Instant.now());}
        @Override public CandidateAttemptStatusResponse result(String a)throws Exception{
            if(blockResult&&a.equals("A")){entered.countDown();try{release.await();}catch(InterruptedException ignored){}finished.countDown();}
            return new CandidateAttemptStatusResponse(a,"S","E",9007199254740993L,9007199254740994L,"TIMED_OUT",Instant.now(),null,1,1,1,0,990,Map.of("L","A"),interrupted);
        }
        @Override public ListeningStatusResponse listening(String a){listeningReads.incrementAndGet();return new ListeningStatusResponse("v0",null,a,"S",a.equals("S-candidate1")&&!proveStarted?null:"START","a".repeat(64),1,interrupted?"INTERRUPTED":"PLAYING",null,null,null,interrupted,null,interrupted?"MEDIA_ERROR":null);}
        @Override public ListeningReorganizeResponse reorganize(String a,ListeningReorganizeRequest r)throws Exception{reorganizations.add(r);if(loseReorganize)throw new IOException();return new ListeningReorganizeResponse("v0",r.requestId(),a,r.newSessionId()+"-candidate1",r.newSessionId());}
        @Override public ExamImportResponse importExam(ExamImportRequest r){throw new UnsupportedOperationException();}
        @Override public CreateSessionResponse createSession(CreateSessionRequest r){throw new UnsupportedOperationException();}
        @Override public void close(){closed=true;}
    }
}
