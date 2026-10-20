package vn.edu.toeic.server.exam.rich;

import java.time.Instant;
import vn.edu.toeic.server.auth.AuthenticatedUser;

/** EX07 cấp context dưới khóa session -> attempt, trong transaction của caller.
 * Không có implementation nghĩa là mọi image download đều fail-closed.
 */
public interface RichImageSessionAuthority {
    Context lockAndAuthorize(AuthenticatedUser user, String attemptId);

    record Context(String examId, String sessionId, String attemptId, String sessionState,
            long writerEpoch, String preparationEpoch, String committedVisualManifestId,
            Instant deadlineAt) {}
}
