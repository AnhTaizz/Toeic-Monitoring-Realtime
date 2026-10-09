package vn.edu.toeic.client.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

/** MOCK đề và gateway giả; kiểm việc gom nhóm, từ chối đề sai và bước xin quyền ghi khi vào phòng thi. */
class ExamPaperAndEntryTest {
    private static final String ATTEMPT = "MOCK-ATTEMPT";
    private static final Instant DEADLINE = Instant.parse("2026-10-09T10:00:00Z");

    private static CandidateQuestionDto question(String id, String groupId, String passage, String... optionIds) {
        List<CandidateOptionDto> options = java.util.Arrays.stream(optionIds)
                .map(option -> new CandidateOptionDto(option, "MOCK " + option, 1)).toList();
        return new CandidateQuestionDto(id, "READING", 7, groupId, passage, null, "MOCK prompt " + id, 1, options);
    }

    private static CandidateExamDto exam(CandidateQuestionDto... questions) {
        return new CandidateExamDto("MOCK-EXAM", "MOCK title", "MOCK", questions.length, List.of(questions));
    }

    @Test
    void questionsSharingAGroupAreShownTogetherWithOnePassage() {
        ExamPaper paper = ExamPaper.from(exam(
                question("r1", null, null, "A", "B"),
                question("r2", "G1", "MOCK passage", "A", "B"),
                question("r3", "G1", "MOCK passage", "A", "B"),
                question("r4", null, null, "A", "B")));

        assertThat(paper.groups()).hasSize(3);
        assertThat(paper.groups().get(1).passageText()).isEqualTo("MOCK passage");
        assertThat(paper.groups().get(1).questions()).extracting(CandidateQuestionDto::questionId).containsExactly("r2", "r3");
        assertThat(paper.groups().get(0).questions()).hasSize(1);
        assertThat(paper.groupIndexOf("r3")).isEqualTo(1);
        assertThat(paper.groupIndexOf("r4")).isEqualTo(2);
        assertThat(paper.allowedOptions()).containsEntry("r1", Set.of("A", "B"));
    }

    @Test
    void candidateDtoHasNoFieldForTheCorrectAnswer() {
        assertThat(CandidateQuestionDto.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .noneMatch(name -> name.toLowerCase().contains("correct"));
    }

    @Test
    void malformedExamIsRejectedWithAMessageInsteadOfCrashingTheScreen() {
        assertThatThrownBy(() -> ExamPaper.from(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExamPaper.from(exam(question("r1", null, null, "A", "B"), question("r1", null, null, "A", "B"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trùng");
        assertThatThrownBy(() -> ExamPaper.from(exam(question("r1", null, null, "A"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ít hơn hai");
        assertThatThrownBy(() -> ExamPaper.from(exam(question("r1", null, null, "A", "A"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trùng mã");
    }

    @Test
    void enteringAnActiveAttemptClaimsANewWriterEpochAndStartsFromTheServerState() {
        FakeExamGateway gateway = new FakeExamGateway(ATTEMPT);
        CompletableFuture<ExamEntry.Outcome> opening = ExamEntry.open(gateway, ATTEMPT);

        gateway.replyStatus(0, gateway.active(4, 40, Map.of("r1", "A"), DEADLINE));
        assertThat(gateway.takeovers).as("chưa kiểm tra đề thì chưa đổi epoch").isEmpty();
        gateway.exams.get(0).complete(exam(question("r1", null, null, "A", "B")));
        assertThat(gateway.takeovers).hasSize(1);
        gateway.takeovers.get(0).reply().complete(new TakeoverWriterResponse(
                gateway.takeovers.get(0).request().requestId(), ATTEMPT, 5, 40, "ACTIVE", DEADLINE, Map.of("r1", "A")));

        ExamEntry.Ready ready = (ExamEntry.Ready) opening.join();
        assertThat(ready.start().writerEpoch()).as("dùng epoch server vừa cấp, không dùng epoch đọc từ status").isEqualTo(5);
        assertThat(ready.start().savedRevision()).isEqualTo(40);
        assertThat(ready.start().answers()).isEqualTo(Map.of("r1", "A"));
        assertThat(ready.start().deadlineAt()).isEqualTo(DEADLINE);
        assertThat(ready.paper().questions()).hasSize(1);
    }

    @Test
    void enteringADecidedAttemptReturnsTheServerResultWithoutTakeover() {
        FakeExamGateway gateway = new FakeExamGateway(ATTEMPT);
        CompletableFuture<ExamEntry.Outcome> opening = ExamEntry.open(gateway, ATTEMPT);

        gateway.replyStatus(0, gateway.decided("TIMED_OUT", 12, Map.of("r1", "A")));

        ExamEntry.Finished finished = (ExamEntry.Finished) opening.join();
        assertThat(finished.result().state()).isEqualTo("TIMED_OUT");
        assertThat(finished.result().correctCount()).isEqualTo(6);
        assertThat(gateway.exams).isEmpty();
        assertThat(gateway.takeovers).isEmpty();
    }

    @Test
    void malformedExamFailsEntryBeforeAnyWriterEpochIsClaimed() {
        FakeExamGateway gateway = new FakeExamGateway(ATTEMPT);
        CompletableFuture<ExamEntry.Outcome> opening = ExamEntry.open(gateway, ATTEMPT);

        gateway.replyStatus(0, gateway.active(1, 0, Map.of(), null));
        gateway.exams.get(0).complete(exam(question("r1", null, null, "A", "B"), question("r1", null, null, "A", "B")));

        assertThatThrownBy(opening::join).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(gateway.takeovers).isEmpty();
    }

    @Test
    void attemptDecidedBetweenStatusAndTakeoverStillEndsWithTheServerResult() {
        FakeExamGateway gateway = new FakeExamGateway(ATTEMPT);
        CompletableFuture<ExamEntry.Outcome> opening = ExamEntry.open(gateway, ATTEMPT);

        gateway.replyStatus(0, gateway.active(1, 3, Map.of(), DEADLINE));
        gateway.exams.get(0).complete(exam(question("r1", null, null, "A", "B")));
        gateway.takeovers.get(0).reply().completeExceptionally(FakeExamGateway.rejected("INVALID_STATE"));
        gateway.replyStatus(1, gateway.decided("SUBMITTED", 3, Map.of()));

        assertThat(((ExamEntry.Finished) opening.join()).result().state()).isEqualTo("SUBMITTED");
    }

    @Test
    void failedTakeoverFailsEntryWithTheServerRejection() {
        FakeExamGateway gateway = new FakeExamGateway(ATTEMPT);
        CompletableFuture<ExamEntry.Outcome> opening = ExamEntry.open(gateway, ATTEMPT);

        gateway.replyStatus(0, gateway.active(1, 3, Map.of(), DEADLINE));
        gateway.exams.get(0).complete(exam(question("r1", null, null, "A", "B")));
        gateway.takeovers.get(0).reply().completeExceptionally(new ExamApiException(403, "FORBIDDEN", false));

        assertThatThrownBy(opening::join).hasCauseInstanceOf(ExamApiException.class);
    }
}
