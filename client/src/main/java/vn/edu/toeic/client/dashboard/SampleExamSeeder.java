package vn.edu.toeic.client.dashboard;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.CreateSessionResponse;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;

/**
 * MOCK: đề mẫu 10 câu và ca thi mẫu cho giám thị bấm tạo khi phát triển/demo. Dữ liệu này có
 * đáp án đúng vì endpoint import cần nó; nó chỉ được gửi lên server bằng tài khoản PROCTOR và
 * không bao giờ đi vào màn thí sinh. Không dùng làm đề thi thật.
 */
final class SampleExamSeeder {
    static final String EXAM_ID = "EXAM-TOEIC-SAMPLE-10";

    private SampleExamSeeder() {
    }

    /** Thời lượng ca mẫu, đổi bằng {@code -Dtoeic.sample.durationSeconds=...} để thử hết giờ. */
    static int durationSeconds() {
        return Integer.getInteger("toeic.sample.durationSeconds", 45 * 60);
    }

    static CompletableFuture<CreateSessionResponse> seed(ExamApiClient api, String sessionId) {
        CreateSessionRequest session = new CreateSessionRequest(sessionId, EXAM_ID, "Ca thi mẫu TOEIC 10 câu (MOCK)",
                durationSeconds(), List.of("proctor1"), List.of("candidate1", "candidate2"));
        return api.importExam(sampleExam()).thenCompose(imported -> api.createSession(session));
    }

    private static ExamImportRequest sampleExam() {
        String notice = "NOTICE: Annual Maintenance Schedule\nPlease be advised that the main elevator will undergo routine maintenance this Saturday from 8:00 AM to 2:00 PM. Please use the emergency stairs during this period.";
        String email = "From: Support Team <support@toeic.edu.vn>\nTo: Candidate\nSubject: Exam Session Instructions\n\nDear candidate, your exam duration is 45 minutes. Answers are autosaved automatically. Make sure to click Submit before the deadline timer expires.";
        return new ExamImportRequest(EXAM_ID, "TOEIC 10-Question Benchmark Exam",
                "Đề thi mẫu gồm 5 câu Listening và 5 câu Reading",
                List.of(
                        question("L1", "LISTENING", 1, null, null, "audio_part1_1.mp3",
                                "Look at the picture and choose the best statement.", "A", 1,
                                "The woman is typing on a laptop.", "The woman is writing in a notebook.",
                                "The woman is talking on the phone.", "The woman is looking out the window."),
                        question("L2", "LISTENING", 1, null, null, "audio_part1_2.mp3",
                                "Where is the conference being held?", "B", 2,
                                "At the main auditorium on the second floor.", "In meeting room B downstairs.",
                                "Next Thursday at 10 AM.", "Yes, with all team members."),
                        question("L3", "LISTENING", 2, null, null, "audio_part2_1.mp3",
                                "Who is responsible for the financial report this quarter?", "C", 3,
                                "By Friday afternoon.", "It was quite detailed.",
                                "Ms. Rodriguez from Accounting.", "Yes, in the conference room."),
                        question("L4", "LISTENING", 3, "GROUP-L1", null, "audio_part3_1.mp3",
                                "What problem does the woman mention?", "A", 4,
                                "The printer is out of paper and ink.", "The shipment has been delayed.",
                                "The flight was cancelled.", "The client refused the offer."),
                        question("L5", "LISTENING", 3, "GROUP-L1", null, "audio_part3_1.mp3",
                                "What will the man probably do next?", "D", 5,
                                "Call the technician.", "Order more supplies online.",
                                "Schedule a meeting.", "Check the storage cabinet in the hallway."),
                        question("R1", "READING", 5, null, null, null,
                                "All employees are required to submit their expense reports _______ the end of the month.", "B", 6,
                                "until", "before", "during", "between"),
                        question("R2", "READING", 5, null, null, null,
                                "The new software update has _______ improved system processing speed across all departments.", "A", 7,
                                "significantly", "significant", "significance", "signify"),
                        question("R3", "READING", 6, "GROUP-R1", notice, null,
                                "When will the maintenance take place?", "C", 8,
                                "Friday morning", "Sunday afternoon", "Saturday morning and early afternoon", "All weekend long"),
                        question("R4", "READING", 7, "GROUP-R2", email, null,
                                "What should candidates do before time expires?", "D", 9,
                                "Restart their computer", "Log out of the system", "Call the proctor", "Click the Submit button"),
                        question("R5", "READING", 7, "GROUP-R2", email, null,
                                "How are answers saved during the test?", "A", 10,
                                "Autosaved automatically to server", "Saved only at the very end",
                                "Stored on a USB drive", "Transmitted via email")));
    }

    private static ExamQuestionImportDto question(String id, String section, int part, String groupId, String passage,
                                                  String audio, String prompt, String correct, int order,
                                                  String a, String b, String c, String d) {
        return new ExamQuestionImportDto(id, section, part, groupId, passage, audio, prompt, correct, order,
                List.of(new ExamOptionImportDto("A", a, 1), new ExamOptionImportDto("B", b, 2),
                        new ExamOptionImportDto("C", c, 3), new ExamOptionImportDto("D", d, 4)));
    }
}
