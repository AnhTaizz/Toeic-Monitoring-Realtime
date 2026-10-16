package vn.edu.toeic.client.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;

/** MOCK DTO: kiểm việc giữ nguyên kết quả server, không chứng nhận tích hợp. */
class ExamResultMappingTest {
    private static final Instant FINAL_AT = Instant.parse("2026-10-16T08:00:00Z");

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void submitPreservesInterruptionAndEveryServerResultField(boolean interrupted) {
        var response = new SubmitExamResponse("MOCK-request", "MOCK-attempt", "SUBMITTED",
                10, 7, 3, 4, 735, FINAL_AT, FINAL_AT.plusSeconds(1), 42, interrupted);
        verify(ExamSession.Result.of(response), "SUBMITTED", interrupted);
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED,true", "SUBMITTED,false", "TIMED_OUT,true", "TIMED_OUT,false"})
    void finalStatusPreservesInterruptionWithoutInferringItFromTimeoutOrScore(String state, boolean interrupted) {
        var response = new CandidateAttemptStatusResponse("MOCK-attempt", "MOCK-session", "MOCK-exam",
                5, 42, state, FINAL_AT.minusSeconds(1), FINAL_AT, 10, 7, 3, 4, 735, Map.of(), interrupted);
        verify(ExamSession.Result.of(response), state, interrupted);
    }

    private static void verify(ExamSession.Result result, String state, boolean interrupted) {
        // Property extraction lets this regression run against the original record missing the flag (F01).
        assertThat(result).extracting("listeningInterrupted").isEqualTo(interrupted);
        assertThat(result.state()).isEqualTo(state);
        assertThat(result.totalQuestions()).isEqualTo(10);
        assertThat(result.correctCount()).isEqualTo(7);
        assertThat(result.listeningCorrect()).isEqualTo(3);
        assertThat(result.readingCorrect()).isEqualTo(4);
        assertThat(result.score()).isEqualTo(735);
        assertThat(result.decidedAt()).isEqualTo(FINAL_AT);
        assertThat(result.savedRevision()).isEqualTo(42);
    }
}
