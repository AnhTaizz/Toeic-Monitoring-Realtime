package vn.edu.toeic.server.exam;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.AttemptCreationDto;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.CreateSessionResponse;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamImportResponse;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;

@Service
public class ExamService {

    private final JdbcClient jdbc;
    private final ExamValidationService validationService;
    private final AuthorizationService authorizationService;

    public ExamService(
            JdbcClient jdbc,
            ExamValidationService validationService,
            AuthorizationService authorizationService) {
        this.jdbc = jdbc;
        this.validationService = validationService;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public ExamImportResponse importExam(AuthenticatedUser proctor, ExamImportRequest request) {
        authorizationService.requireRole(proctor, Role.PROCTOR);
        validationService.validate(request);

        boolean exists = jdbc.sql("SELECT count(*) FROM exams WHERE exam_id = :examId")
                .param("examId", request.examId())
                .query(Long.class)
                .single() > 0;

        if (exists) {
            throw new InvalidExamException("Đề thi với mã '" + request.examId() + "' đã tồn tại");
        }

        jdbc.sql("""
                INSERT INTO exams (exam_id, title, description, total_questions, created_by)
                VALUES (:examId, :title, :description, :totalQuestions, :createdBy)
                """)
                .param("examId", request.examId())
                .param("title", request.title())
                .param("description", request.description())
                .param("totalQuestions", request.questions().size())
                .param("createdBy", proctor.userId())
                .update();

        for (int i = 0; i < request.questions().size(); i++) {
            ExamQuestionImportDto q = request.questions().get(i);
            int questionOrder = q.orderIndex() > 0 ? q.orderIndex() : (i + 1);

            jdbc.sql("""
                    INSERT INTO exam_questions (exam_id, question_id, section, part, group_id, passage_text, audio_file, prompt, correct_option, order_index)
                    VALUES (:examId, :questionId, :section, :part, :groupId, :passageText, :audioFile, :prompt, :correctOption, :orderIndex)
                    """)
                    .param("examId", request.examId())
                    .param("questionId", q.questionId())
                    .param("section", q.section().toUpperCase())
                    .param("part", q.part())
                    .param("groupId", q.groupId())
                    .param("passageText", q.passageText())
                    .param("audioFile", q.audioFile())
                    .param("prompt", q.prompt())
                    .param("correctOption", q.correctOption())
                    .param("orderIndex", questionOrder)
                    .update();

            for (int j = 0; j < q.options().size(); j++) {
                ExamOptionImportDto opt = q.options().get(j);
                int optionOrder = opt.orderIndex() > 0 ? opt.orderIndex() : (j + 1);

                jdbc.sql("""
                        INSERT INTO exam_options (exam_id, question_id, option_id, option_text, order_index)
                        VALUES (:examId, :questionId, :optionId, :optionText, :orderIndex)
                        """)
                        .param("examId", request.examId())
                        .param("questionId", q.questionId())
                        .param("optionId", opt.optionId())
                        .param("optionText", opt.optionText())
                        .param("orderIndex", optionOrder)
                        .update();
            }
        }

        return new ExamImportResponse(
                request.examId(),
                request.title(),
                request.questions().size(),
                "IMPORTED");
    }

    @Transactional(readOnly = true)
    public CandidateExamDto getCandidateExam(AuthenticatedUser user, String attemptId) {
        authorizationService.requireAttempt(user, attemptId);

        String examId = jdbc.sql("""
                SELECT COALESCE(a.exam_id, s.exam_id) FROM monitoring_attempts a
                LEFT JOIN exam_sessions s ON s.session_id = a.session_id
                WHERE a.attempt_id = :attemptId
                """)
                .param("attemptId", attemptId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new InvalidExamException("Không tìm thấy đề thi cho lượt thi: " + attemptId));

        if (examId == null || examId.isBlank()) {
            throw new InvalidExamException("Lượt thi chưa được gán đề thi: " + attemptId);
        }

        record ExamHeader(String title, String description, int totalQuestions) {}

        ExamHeader header = jdbc.sql("SELECT title, description, total_questions FROM exams WHERE exam_id = :examId")
                .param("examId", examId)
                .query((rs, rowNum) -> new ExamHeader(
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("total_questions")))
                .single();

        List<CandidateQuestionDto> questions = jdbc.sql("""
                SELECT q.question_id, q.section, q.part, q.group_id, q.passage_text, q.audio_file, q.prompt, q.order_index
                FROM exam_questions q
                WHERE q.exam_id = :examId
                ORDER BY q.order_index, q.id
                """)
                .param("examId", examId)
                .query((rs, rowNum) -> {
                    String qId = rs.getString("question_id");
                    String section = rs.getString("section");
                    int part = rs.getInt("part");
                    String groupId = rs.getString("group_id");
                    String passage = rs.getString("passage_text");
                    String audio = rs.getString("audio_file");
                    String prompt = rs.getString("prompt");
                    int orderIndex = rs.getInt("order_index");

                    List<CandidateOptionDto> options = jdbc.sql("""
                            SELECT option_id, option_text, order_index
                            FROM exam_options
                            WHERE exam_id = :examId AND question_id = :qId
                            ORDER BY order_index, id
                            """)
                            .param("examId", examId)
                            .param("qId", qId)
                            .query((ors, oRowNum) -> new CandidateOptionDto(
                                    ors.getString("option_id"),
                                    ors.getString("option_text"),
                                    ors.getInt("order_index")))
                            .list();

                    return new CandidateQuestionDto(
                            qId, section, part, groupId, passage, audio, prompt, orderIndex, options);
                })
                .list();

        return new CandidateExamDto(examId, header.title(), header.description(), header.totalQuestions(), questions);
    }

    @Transactional
    public CreateSessionResponse createSession(AuthenticatedUser proctor, CreateSessionRequest request) {
        authorizationService.requireRole(proctor, Role.PROCTOR);

        if (request.sessionId() == null || request.sessionId().isBlank()) {
            throw new InvalidExamException("sessionId không được để trống");
        }
        if (request.examId() == null || request.examId().isBlank()) {
            throw new InvalidExamException("examId không được để trống");
        }
        if (request.durationSeconds() <= 0) {
            throw new InvalidExamException("durationSeconds phải lớn hơn 0");
        }
        if (request.candidateUsernames() == null || request.candidateUsernames().isEmpty()) {
            throw new InvalidExamException("Danh sách thí sinh không được để trống");
        }

        boolean sessionExists = jdbc.sql("SELECT count(*) FROM exam_sessions WHERE session_id = :sessionId")
                .param("sessionId", request.sessionId())
                .query(Long.class)
                .single() > 0;

        if (sessionExists) {
            throw new InvalidExamException("Ca thi với mã '" + request.sessionId() + "' đã tồn tại");
        }

        boolean examExists = jdbc.sql("SELECT count(*) FROM exams WHERE exam_id = :examId")
                .param("examId", request.examId())
                .query(Long.class)
                .single() > 0;

        if (!examExists) {
            throw new InvalidExamException("Không tìm thấy đề thi với mã: " + request.examId());
        }

        jdbc.sql("""
                INSERT INTO exam_sessions (session_id, exam_id, title, duration_seconds, state, created_by)
                VALUES (:sessionId, :examId, :title, :duration, 'SCHEDULED', :createdBy)
                """)
                .param("sessionId", request.sessionId())
                .param("examId", request.examId())
                .param("title", request.title())
                .param("duration", request.durationSeconds())
                .param("createdBy", proctor.userId())
                .update();

        List<String> proctorList = new ArrayList<>(request.proctorUsernames());
        if (!proctorList.contains(proctor.username())) {
            proctorList.add(proctor.username());
        }

        List<Long> proctorUserIds = new ArrayList<>();
        for (String pUsername : proctorList) {
            Long pId = jdbc.sql("SELECT id FROM user_accounts WHERE username = :u AND role = 'PROCTOR'")
                    .param("u", pUsername)
                    .query(Long.class)
                    .optional()
                    .orElseThrow(() -> new InvalidExamException("Không tìm thấy giám thị: " + pUsername));
            proctorUserIds.add(pId);
            jdbc.sql("""
                    INSERT INTO exam_session_proctors (session_id, proctor_user_id)
                    VALUES (:sessionId, :proctorId)
                    ON CONFLICT DO NOTHING
                    """)
                    .param("sessionId", request.sessionId())
                    .param("proctorId", pId)
                    .update();
        }

        List<AttemptCreationDto> createdAttempts = new ArrayList<>();

        for (String cUsername : request.candidateUsernames()) {
            Long cId = jdbc.sql("SELECT id FROM user_accounts WHERE username = :u AND role = 'CANDIDATE'")
                    .param("u", cUsername)
                    .query(Long.class)
                    .optional()
                    .orElseThrow(() -> new InvalidExamException("Không tìm thấy thí sinh: " + cUsername));

            String attemptId = request.sessionId() + "-" + cUsername;

            jdbc.sql("""
                    INSERT INTO monitoring_attempts (attempt_id, candidate_user_id, session_id, exam_id, state, writer_epoch, saved_revision)
                    VALUES (:attemptId, :candidateId, :sessionId, :examId, 'ACTIVE', 1, 0)
                    ON CONFLICT (attempt_id) DO UPDATE
                    SET session_id = EXCLUDED.session_id, exam_id = EXCLUDED.exam_id, state = 'ACTIVE'
                    """)
                    .param("attemptId", attemptId)
                    .param("candidateId", cId)
                    .param("sessionId", request.sessionId())
                    .param("examId", request.examId())
                    .update();

            for (Long pId : proctorUserIds) {
                jdbc.sql("""
                        INSERT INTO monitoring_proctor_assignments (attempt_id, proctor_user_id)
                        VALUES (:attemptId, :proctorId)
                        ON CONFLICT DO NOTHING
                        """)
                        .param("attemptId", attemptId)
                        .param("proctorId", pId)
                        .update();
            }

            createdAttempts.add(new AttemptCreationDto(attemptId, cUsername, "ACTIVE"));
        }

        return new CreateSessionResponse(
                request.sessionId(),
                request.examId(),
                request.title(),
                request.durationSeconds(),
                "SCHEDULED",
                createdAttempts);
    }
}
