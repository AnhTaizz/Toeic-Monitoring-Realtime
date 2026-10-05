package vn.edu.toeic.server.exam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;

@Service
public class ExamValidationService {

    public void validate(ExamImportRequest request) {
        if (request == null) {
            throw new InvalidExamException("Yêu cầu import đề thi không được để trống");
        }
        if (request.examId() == null || request.examId().isBlank()) {
            throw new InvalidExamException("Mã đề thi (examId) không được để trống");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new InvalidExamException("Tiêu đề đề thi không được để trống");
        }
        if (request.questions() == null || request.questions().isEmpty()) {
            throw new InvalidExamException("Đề thi phải chứa ít nhất 1 câu hỏi");
        }

        Set<String> seenQuestionIds = new HashSet<>();
        Map<String, String> groupPassageMap = new HashMap<>();

        for (int i = 0; i < request.questions().size(); i++) {
            ExamQuestionImportDto q = request.questions().get(i);
            if (q == null) {
                throw new InvalidExamException("Câu hỏi tại vị trí " + (i + 1) + " bị null");
            }
            if (q.questionId() == null || q.questionId().isBlank()) {
                throw new InvalidExamException("Câu hỏi tại vị trí " + (i + 1) + " thiếu questionId");
            }
            if (!seenQuestionIds.add(q.questionId())) {
                throw new InvalidExamException("Trùng lặp mã câu hỏi: " + q.questionId());
            }
            if (q.section() == null || (!q.section().equalsIgnoreCase("LISTENING") && !q.section().equalsIgnoreCase("READING"))) {
                throw new InvalidExamException("Câu hỏi " + q.questionId() + " có section không hợp lệ (phải là LISTENING hoặc READING)");
            }
            if (q.part() < 1 || q.part() > 7) {
                throw new InvalidExamException("Câu hỏi " + q.questionId() + " có part không hợp lệ (" + q.part() + ", phải từ 1 đến 7)");
            }
            if (q.prompt() == null || q.prompt().isBlank()) {
                throw new InvalidExamException("Câu hỏi " + q.questionId() + " thiếu nội dung prompt");
            }
            if (q.options() == null || q.options().size() < 2) {
                throw new InvalidExamException("Câu hỏi " + q.questionId() + " phải có ít nhất 2 lựa chọn đáp án");
            }

            Set<String> optionIds = new HashSet<>();
            for (ExamOptionImportDto opt : q.options()) {
                if (opt == null || opt.optionId() == null || opt.optionId().isBlank()) {
                    throw new InvalidExamException("Câu hỏi " + q.questionId() + " chứa lựa chọn không hợp lệ");
                }
                if (!optionIds.add(opt.optionId())) {
                    throw new InvalidExamException("Câu hỏi " + q.questionId() + " chứa lựa chọn trùng lặp: " + opt.optionId());
                }
            }

            if (q.correctOption() == null || q.correctOption().isBlank()) {
                throw new InvalidExamException("Câu hỏi " + q.questionId() + " thiếu đáp án đúng (correctOption)");
            }
            if (!optionIds.contains(q.correctOption())) {
                throw new InvalidExamException("Câu hỏi " + q.questionId() + " có đáp án đúng '" + q.correctOption() + "' không nằm trong danh sách lựa chọn: " + optionIds);
            }

            if (q.groupId() != null && !q.groupId().isBlank()) {
                if (q.passageText() != null && !q.passageText().isBlank()) {
                    String existingPassage = groupPassageMap.putIfAbsent(q.groupId(), q.passageText());
                    if (existingPassage != null && !existingPassage.equals(q.passageText())) {
                        throw new InvalidExamException("Nhóm đoạn văn " + q.groupId() + " có nội dung passage không nhất quán giữa các câu hỏi");
                    }
                }
            }
        }
    }
}
