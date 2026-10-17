package vn.edu.toeic.client.dashboard;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.protocol.exam.*;

/** MOCK committed pages/faults, deterministic owner callbacks; no production integration claim. */
class ProctorProgressControllerTest {
    private final List<String> requested=new ArrayList<>();
    private final List<CompletableFuture<ProctorProgressPage>> replies=new ArrayList<>();
    private final List<ProctorProgressController.Snapshot> states=new ArrayList<>();
    private final ArrayDeque<Runnable> owner=new ArrayDeque<>();
    private final AtomicInteger expired=new AtomicInteger();
    private final ProctorProgressController c=new ProctorProgressController(cursor->{requested.add(cursor);var f=new CompletableFuture<ProctorProgressPage>();replies.add(f);return f;},owner::add,states::add,expired::incrementAndGet);
    private void drain(){while(!owner.isEmpty())owner.remove().run();}
    private static ProctorProgressPage page(String id,String next){return new ProctorProgressPage(Instant.parse("2026-10-15T00:00:00Z"),List.of(new ProctorProgressRow(id,"S","candidate1","TEST","ACTIVE",42,1,2,null,null,null,null)),next);}
    @Test void rowsOnlyAdvanceAfterCommittedReplyOnOwnerThreadAndReadsNeverOverlap(){
        c.refresh();c.refresh();c.firstPage();assertThat(requested).containsExactly((String)null);assertThat(c.snapshot().rows()).isEmpty();
        replies.getFirst().complete(page("A","A"));assertThat(c.snapshot().rows()).isEmpty();drain();
        assertThat(c.snapshot().rows().getFirst().savedRevision()).isEqualTo(42);assertThat(c.snapshot().stale()).isFalse();c.close();
    }
    @Test void failedNavigationKeepsConfirmedPageCursorAndReadTimeUntilRetrySucceeds(){
        c.refresh();replies.getFirst().complete(page("  cũ/đề% + ","  cũ/đề% + "));drain();var old=c.snapshot();
        c.nextPage();assertThat(requested.get(1)).isEqualTo(old.nextCursor());replies.get(1).completeExceptionally(new java.io.IOException());drain();
        assertThat(c.snapshot().rows()).isEqualTo(old.rows());assertThat(c.snapshot().readAt()).isEqualTo(old.readAt());assertThat(c.snapshot().cursor()).isNull();assertThat(c.snapshot().stale()).isTrue();assertThat(c.snapshot().canNext()).isFalse();
        c.refresh();assertThat(requested.get(2)).isNull();replies.get(2).complete(page("A","A"));drain();c.nextPage();
        replies.get(3).complete(page("B",null));drain();assertThat(c.snapshot().cursor()).isEqualTo("A");assertThat(c.snapshot().rows().getFirst().attemptId()).isEqualTo("B");c.close();
    }
    @Test void unauthorizedExpiresButForbiddenOnlyMarksStale(){
        c.refresh();replies.getFirst().completeExceptionally(new vn.edu.toeic.client.exam.ExamApiException(403,"FORBIDDEN",false));drain();assertThat(expired).hasValue(0);
        c.refresh();replies.get(1).completeExceptionally(new vn.edu.toeic.client.exam.ExamApiException(401,"UNAUTHORIZED",false));drain();assertThat(expired).hasValue(1);assertThat(c.snapshot().stale()).isTrue();c.close();
    }
    @Test void closeDiscardsQueuedCompletionAndStopsFurtherRequests(){
        c.refresh();replies.getFirst().complete(page("A",null));int before=states.size();c.close();drain();c.refresh();c.nextPage();
        assertThat(states).hasSize(before);assertThat(c.snapshot().rows()).isEmpty();assertThat(requested).hasSize(1);
    }
    @Test void malformedSuccessCannotReplaceLastConfirmedRows(){
        c.refresh();replies.getFirst().complete(page("A",null));drain();var old=c.snapshot().rows();c.refresh();
        replies.get(1).complete(new ProctorProgressPage(null,List.of(),null));drain();assertThat(c.snapshot().rows()).isEqualTo(old);assertThat(c.snapshot().stale()).isTrue();c.close();
    }
}
