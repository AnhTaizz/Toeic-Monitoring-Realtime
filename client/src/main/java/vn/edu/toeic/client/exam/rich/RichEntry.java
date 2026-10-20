package vn.edu.toeic.client.exam.rich;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import vn.edu.toeic.client.exam.ExamApiException;

/**
 * Chọn luồng màn thi theo phiên bản ca do server lưu (contract §4): GET preparation trả 200 nghĩa là
 * ca PROCTORED_V2 đang SCHEDULED/IN_PROGRESS; 409 INVALID_STATE nghĩa là ca legacy hoặc lượt V2 đã
 * kết thúc, khi đó luồng legacy đọc status v1 và hiện kết quả. Client không tự suy version.
 */
public final class RichEntry {
    private RichEntry() {}

    public static CompletableFuture<Boolean> probe(String serverUrl, String token, String attemptId) {
        return CompletableFuture.supplyAsync(() -> probe(new RichExamApiClient(serverUrl, token), attemptId));
    }

    static boolean probe(RichExamApi api, String attemptId) {
        try (api) {
            api.preparation(attemptId);
            return true;
        } catch (ExamApiException rejected) {
            if (rejected.statusCode() == 409 && "INVALID_STATE".equals(rejected.code())) return false;
            throw rejected;
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new CompletionException(failure);
        }
    }
}
