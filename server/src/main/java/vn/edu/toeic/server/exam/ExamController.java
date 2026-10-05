package vn.edu.toeic.server.exam;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.CreateSessionResponse;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamImportResponse;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping("/exams/import")
    public ResponseEntity<ExamImportResponse> importExam(
            HttpServletRequest httpRequest,
            @RequestBody ExamImportRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) httpRequest.getUserPrincipal();
        if (user == null) {
            throw AccessDeniedException.unauthorized();
        }
        ExamImportResponse response = examService.importExam(user, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions")
    public ResponseEntity<CreateSessionResponse> createSession(
            HttpServletRequest httpRequest,
            @RequestBody CreateSessionRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) httpRequest.getUserPrincipal();
        if (user == null) {
            throw AccessDeniedException.unauthorized();
        }
        CreateSessionResponse response = examService.createSession(user, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attempts/{attemptId}/exam")
    public ResponseEntity<CandidateExamDto> getCandidateExam(
            HttpServletRequest httpRequest,
            @PathVariable String attemptId) {
        AuthenticatedUser user = (AuthenticatedUser) httpRequest.getUserPrincipal();
        if (user == null) {
            throw AccessDeniedException.unauthorized();
        }
        CandidateExamDto response = examService.getCandidateExam(user, attemptId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/attempts/{attemptId}/answers")
    public ResponseEntity<vn.edu.toeic.protocol.exam.AutosaveAnswersResponse> autosaveAnswers(
            HttpServletRequest httpRequest,
            @PathVariable String attemptId,
            @RequestBody vn.edu.toeic.protocol.exam.AutosaveAnswersRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) httpRequest.getUserPrincipal();
        if (user == null) {
            throw AccessDeniedException.unauthorized();
        }
        if (!attemptId.equals(request.attemptId())) {
            throw new ExamApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    vn.edu.toeic.protocol.ErrorCode.INVALID_INPUT,
                    "attemptId trong URL không khớp với request body",
                    request.requestId());
        }
        vn.edu.toeic.protocol.exam.AutosaveAnswersResponse response = examService.autosaveAnswers(user, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<vn.edu.toeic.protocol.exam.SubmitExamResponse> submitExam(
            HttpServletRequest httpRequest,
            @PathVariable String attemptId,
            @RequestBody vn.edu.toeic.protocol.exam.SubmitExamRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) httpRequest.getUserPrincipal();
        if (user == null) {
            throw AccessDeniedException.unauthorized();
        }
        if (!attemptId.equals(request.attemptId())) {
            throw new ExamApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    vn.edu.toeic.protocol.ErrorCode.INVALID_INPUT,
                    "attemptId trong URL không khớp với request body",
                    request.requestId());
        }
        vn.edu.toeic.protocol.exam.SubmitExamResponse response = examService.submitExam(user, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/attempts/{attemptId}/takeover")
    public ResponseEntity<vn.edu.toeic.protocol.exam.TakeoverWriterResponse> takeoverWriter(
            HttpServletRequest httpRequest,
            @PathVariable String attemptId,
            @RequestBody vn.edu.toeic.protocol.exam.TakeoverWriterRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) httpRequest.getUserPrincipal();
        if (user == null) {
            throw AccessDeniedException.unauthorized();
        }
        if (!attemptId.equals(request.attemptId())) {
            throw new ExamApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    vn.edu.toeic.protocol.ErrorCode.INVALID_INPUT,
                    "attemptId trong URL không khớp với request body",
                    request.requestId());
        }
        vn.edu.toeic.protocol.exam.TakeoverWriterResponse response = examService.takeoverWriter(user, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attempts/{attemptId}/status")
    public ResponseEntity<vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse> getAttemptStatus(
            HttpServletRequest httpRequest,
            @PathVariable String attemptId) {
        AuthenticatedUser user = (AuthenticatedUser) httpRequest.getUserPrincipal();
        if (user == null) {
            throw AccessDeniedException.unauthorized();
        }
        vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse response = examService.getAttemptStatus(user, attemptId);
        return ResponseEntity.ok(response);
    }
}


