package vn.edu.toeic.protocol.exam;

import java.time.Instant;
import java.util.List;

/** Read-only committed progress; never contains answers, writer credentials or correct options. */
public record ProctorProgressPage(Instant readAt, List<ProctorProgressRow> attempts, String nextAfterAttemptId) {
    public ProctorProgressPage { attempts = List.copyOf(attempts); }
}
