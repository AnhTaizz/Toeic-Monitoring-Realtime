package vn.edu.toeic.server.exam;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "exam.timeout.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ExamTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(ExamTimeoutService.class);

    private final ExamService examService;

    public ExamTimeoutService(ExamService examService) {
        this.examService = examService;
    }

    @Scheduled(fixedDelayString = "${exam.timeout.scan-interval-ms:1000}")
    public int processTimeouts() {
        List<String> expiredAttemptIds = examService.findExpiredActiveAttemptIds();
        int count = 0;
        for (String attemptId : expiredAttemptIds) {
            try {
                boolean timedOut = examService.timeoutAttempt(attemptId);
                if (timedOut) {
                    count++;
                    log.info("Attempt {} timed out and scored successfully", attemptId);
                }
            } catch (Exception e) {
                log.error("Failed to process timeout for attempt {}", attemptId, e);
            }
        }
        return count;
    }
}
