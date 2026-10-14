package vn.edu.toeic.client.audio;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;

/** Bearer HTTP client with bounded bodies, no redirects and strict correlated event responses. */
public final class ListeningApiClient implements ListeningApi {
    private static final int MAX_JSON = 2 * 1024 * 1024;
    private final URI base;
    private volatile String token;
    private final HttpClient http;
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    public ListeningApiClient(String serverUrl, String token) {
        base = URI.create(serverUrl.trim().replaceAll("/+$", ""));
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || token == null || token.isBlank()) throw new IllegalArgumentException("Địa chỉ server không hợp lệ.");
        this.token = token;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static String attemptPath(String attempt) {
        if (!ListeningMessages.identifier(attempt)) throw new IllegalArgumentException("Lượt thi không hợp lệ.");
        return "/api/v1/attempts/" + AudioApiClient.segment(attempt);
    }

    private JsonObject request(String path, Object payload) throws Exception {
        String credential = token;
        if (credential == null) throw new IOException("Phiên Listening đã đóng.");
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + credential);
        if (payload == null) builder.GET();
        else builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toJson(payload), StandardCharsets.UTF_8));
        HttpResponse<InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(MAX_JSON + 1);
            if (bytes.length > MAX_JSON) throw new IOException("Phản hồi Listening quá lớn.");
            if (response.statusCode() != 200) throw new IOException(switch (response.statusCode()) {
                case 401 -> "Phiên hết hiệu lực. Hãy đăng nhập lại.";
                case 403 -> "Không có quyền dùng Listening cho lượt này.";
                case 409 -> "Server từ chối trạng thái Listening hiện tại.";
                default -> "Không gọi được Listening (HTTP " + response.statusCode() + ").";
            });
            try {
                JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) throw new IllegalArgumentException();
                return parsed.getAsJsonObject();
            } catch (RuntimeException malformed) { throw new IOException("Phản hồi Listening không hợp lệ."); }
        }
    }

    @Override public CandidateExamDto exam(String attempt) throws Exception {
        JsonObject body = request(attemptPath(attempt) + "/exam", null);
        try {
            ListeningMessages.fields(body, Set.of("examId", "title", "description", "totalQuestions", "questions"));
            JsonArray questionsJson = ListeningMessages.array(body, "questions");
            if (questionsJson.isEmpty() || questionsJson.size() > 500) throw new IllegalArgumentException();
            List<CandidateQuestionDto> questions = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonElement element : questionsJson) {
                JsonObject q = element.getAsJsonObject();
                ListeningMessages.fields(q, Set.of("questionId", "section", "part", "groupId", "passageText", "audioFile", "prompt", "orderIndex", "options"));
                List<CandidateOptionDto> options = new ArrayList<>();
                JsonArray optionsJson = ListeningMessages.array(q, "options");
                if (optionsJson.isEmpty() || optionsJson.size() > 10) throw new IllegalArgumentException();
                for (JsonElement optionElement : optionsJson) {
                    JsonObject o = optionElement.getAsJsonObject();
                    ListeningMessages.fields(o, Set.of("optionId", "optionText", "orderIndex"));
                    options.add(new CandidateOptionDto(ListeningMessages.string(o,"optionId"),
                            ListeningMessages.string(o,"optionText"), ListeningMessages.integer(o,"orderIndex")));
                }
                String id = ListeningMessages.string(q,"questionId");
                if (!ids.add(id)) throw new IllegalArgumentException();
                questions.add(new CandidateQuestionDto(id, ListeningMessages.string(q,"section"),
                        ListeningMessages.integer(q,"part"), ListeningMessages.nullableString(q,"groupId"),
                        ListeningMessages.nullableString(q,"passageText"), ListeningMessages.nullableString(q,"audioFile"),
                        ListeningMessages.string(q,"prompt"), ListeningMessages.integer(q,"orderIndex"), options));
            }
            int total = ListeningMessages.integer(body,"totalQuestions");
            if (total != questions.size()) throw new IllegalArgumentException();
            return new CandidateExamDto(ListeningMessages.string(body,"examId"), ListeningMessages.string(body,"title"),
                    ListeningMessages.nullableString(body,"description"), total, questions);
        } catch (RuntimeException malformed) { throw new IOException("Đề thi candidate không hợp lệ."); }
    }

    @Override public ListeningStatusResponse event(String attempt, ListeningEventRequest sent) throws Exception {
        JsonObject body = request(attemptPath(attempt) + "/listening/events", sent);
        try {
            ListeningStatusResponse status = ListeningMessages.status(body);
            if (!attempt.equals(status.attemptId()) || !sent.requestId().equals(status.requestId())
                    || !sent.startId().equals(status.startId()) || sent.writerEpoch() != status.writerEpoch())
                throw new IllegalArgumentException();
            return status;
        } catch (RuntimeException malformed) { throw new IOException("Server chưa xác nhận event Listening hợp lệ."); }
    }

    @Override public ListeningStatusResponse status(String attempt) throws Exception {
        JsonObject body = request(attemptPath(attempt) + "/listening/status", null);
        try {
            ListeningStatusResponse status = ListeningMessages.status(body);
            if (!attempt.equals(status.attemptId()) || status.requestId() != null) throw new IllegalArgumentException();
            return status;
        } catch (RuntimeException malformed) { throw new IOException("Trạng thái Listening từ server không hợp lệ."); }
    }

    @Override public void close() { token = null; http.shutdownNow(); }
}
