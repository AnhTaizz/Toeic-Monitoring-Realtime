package vn.edu.toeic.client.exam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;

/**
 * Đề thi đã kiểm tra và gom theo nhóm để hiển thị: các câu chung groupId đi với một đoạn văn.
 * Đề sai dữ liệu bị từ chối ở {@link #from} bằng IllegalArgumentException có thông báo, trước khi
 * mở màn thi.
 */
public record ExamPaper(String examId, String title, List<Group> groups, List<CandidateQuestionDto> questions) {
    /** groupId null nghĩa là câu đứng riêng. */
    public record Group(String groupId, String passageText, String audioFile, List<CandidateQuestionDto> questions) {
    }

    public static ExamPaper from(CandidateExamDto exam) {
        if (exam == null || exam.questions() == null || exam.questions().isEmpty()) {
            throw new IllegalArgumentException("Đề thi không có câu hỏi.");
        }
        Set<String> seenQuestions = new HashSet<>();
        Map<String, List<CandidateQuestionDto>> byGroup = new LinkedHashMap<>();
        int single = 0;
        for (CandidateQuestionDto question : exam.questions()) {
            if (question == null || question.questionId() == null || question.questionId().isBlank()) {
                throw new IllegalArgumentException("Đề thi có câu hỏi thiếu mã.");
            }
            if (!seenQuestions.add(question.questionId())) {
                throw new IllegalArgumentException("Đề thi có mã câu hỏi trùng: " + question.questionId());
            }
            if (question.options() == null || question.options().size() < 2) {
                throw new IllegalArgumentException("Câu " + question.questionId() + " có ít hơn hai lựa chọn.");
            }
            Set<String> seenOptions = new HashSet<>();
            for (CandidateOptionDto option : question.options()) {
                if (option == null || option.optionId() == null || !seenOptions.add(option.optionId())) {
                    throw new IllegalArgumentException("Câu " + question.questionId() + " có lựa chọn thiếu hoặc trùng mã.");
                }
            }
            boolean grouped = question.groupId() != null && !question.groupId().isBlank();
            // Khóa riêng cho câu đứng lẻ để nó không bị gộp với câu lẻ khác.
            String key = grouped ? "G:" + question.groupId() : "S:" + single++;
            byGroup.computeIfAbsent(key, ignored -> new ArrayList<>()).add(question);
        }
        List<Group> groups = new ArrayList<>();
        for (List<CandidateQuestionDto> members : byGroup.values()) {
            CandidateQuestionDto first = members.getFirst();
            groups.add(new Group(first.groupId(), firstNonBlank(members, true), firstNonBlank(members, false),
                    List.copyOf(members)));
        }
        return new ExamPaper(exam.examId(), exam.title(), List.copyOf(groups), List.copyOf(exam.questions()));
    }

    /** Mã câu → các mã lựa chọn hợp lệ; ExamSession dùng để từ chối ID không thuộc đề. */
    public Map<String, Set<String>> allowedOptions() {
        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        for (CandidateQuestionDto question : questions) {
            Set<String> options = new HashSet<>();
            for (CandidateOptionDto option : question.options()) {
                options.add(option.optionId());
            }
            allowed.put(question.questionId(), Set.copyOf(options));
        }
        return Map.copyOf(allowed);
    }

    public int groupIndexOf(String questionId) {
        for (int i = 0; i < groups.size(); i++) {
            for (CandidateQuestionDto question : groups.get(i).questions()) {
                if (question.questionId().equals(questionId)) return i;
            }
        }
        return -1;
    }

    private static String firstNonBlank(List<CandidateQuestionDto> members, boolean passage) {
        for (CandidateQuestionDto question : members) {
            String value = passage ? question.passageText() : question.audioFile();
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
