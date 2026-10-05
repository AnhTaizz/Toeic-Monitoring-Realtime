package vn.edu.toeic.server.exam;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;

class ExamValidationTest {

    private ExamValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ExamValidationService();
    }

    @Test
    void validExamPassesValidation() {
        ExamImportRequest request = new ExamImportRequest(
                "TEST-EXAM-01",
                "Đề thi TOEIC rút gọn số 1",
                "Đề thi thử nghiệm 10 câu",
                List.of(
                        new ExamQuestionImportDto(
                                "q1",
                                "LISTENING",
                                1,
                                null,
                                null,
                                "part1_audio.mp3",
                                "Look at the picture and choose the best description.",
                                "A",
                                1,
                                List.of(
                                        new ExamOptionImportDto("A", "The woman is typing on a laptop.", 1),
                                        new ExamOptionImportDto("B", "The woman is reading a book.", 2),
                                        new ExamOptionImportDto("C", "The woman is walking in the park.", 3),
                                        new ExamOptionImportDto("D", "The woman is driving a car.", 4)
                                )
                        ),
                        new ExamQuestionImportDto(
                                "q2",
                                "READING",
                                5,
                                null,
                                null,
                                null,
                                "Customer satisfaction is our highest _______.",
                                "B",
                                2,
                                List.of(
                                        new ExamOptionImportDto("A", "prioritize", 1),
                                        new ExamOptionImportDto("B", "priority", 2),
                                        new ExamOptionImportDto("C", "prior", 3),
                                        new ExamOptionImportDto("D", "prioritized", 4)
                                )
                        )
                )
        );

        assertThatCode(() -> validationService.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void duplicateQuestionIdThrowsInvalidExamException() {
        ExamImportRequest request = new ExamImportRequest(
                "TEST-EXAM-DUP",
                "Đề thi lỗi trùng câu hỏi",
                "Mô tả",
                List.of(
                        new ExamQuestionImportDto(
                                "q1", "READING", 5, null, null, null,
                                "Prompt 1", "A", 1,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2))
                        ),
                        new ExamQuestionImportDto(
                                "q1", "READING", 5, null, null, null,
                                "Prompt 2", "A", 2,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2))
                        )
                )
        );

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(InvalidExamException.class)
                .hasMessageContaining("Trùng lặp mã câu hỏi: q1");
    }

    @Test
    void correctOptionNotInOptionsThrowsInvalidExamException() {
        ExamImportRequest request = new ExamImportRequest(
                "TEST-EXAM-WRONG-ANSWER",
                "Đề thi lỗi đáp án không nằm trong lựa chọn",
                "Mô tả",
                List.of(
                        new ExamQuestionImportDto(
                                "q1", "READING", 5, null, null, null,
                                "Prompt 1", "D", 1,
                                List.of(
                                        new ExamOptionImportDto("A", "Opt A", 1),
                                        new ExamOptionImportDto("B", "Opt B", 2),
                                        new ExamOptionImportDto("C", "Opt C", 3)
                                )
                        )
                )
        );

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(InvalidExamException.class)
                .hasMessageContaining("không nằm trong danh sách lựa chọn");
    }

    @Test
    void inconsistentPassageInSameGroupThrowsInvalidExamException() {
        ExamImportRequest request = new ExamImportRequest(
                "TEST-EXAM-INCONSISTENT-GROUP",
                "Đề thi lỗi nhóm đoạn văn",
                "Mô tả",
                List.of(
                        new ExamQuestionImportDto(
                                "q1", "READING", 7, "group-1", "Passage text A", null,
                                "Question 1", "A", 1,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2))
                        ),
                        new ExamQuestionImportDto(
                                "q2", "READING", 7, "group-1", "Passage text DIFFERENT", null,
                                "Question 2", "B", 2,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2))
                        )
                )
        );

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(InvalidExamException.class)
                .hasMessageContaining("không nhất quán");
    }
}
