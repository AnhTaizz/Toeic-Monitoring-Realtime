package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static vn.edu.toeic.dataset.Json.*;

/** ETL chủ động một lần, không cookie/auth/redirect/retry; không dùng trong runtime thí sinh. */
public final class MediaDownloader implements AutoCloseable {
    record Response(int status, String mime, byte[] bytes) {}
    @FunctionalInterface interface Fetcher { Response get(URI uri, long limit) throws Exception; }
    private final HttpClient client;
    private final Fetcher fetcher;
    public MediaDownloader() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        fetcher = (uri, limit) -> {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                    .header("User-Agent", "TOEIC-offline-ETL/1.0").header("Accept", "image/jpeg,image/png,audio/mpeg,audio/wav,audio/mp4,application/octet-stream").GET().build();
            var future = client.sendAsync(request, info -> new LimitedBody(limit));
            try {
                var result = future.get(45, TimeUnit.SECONDS);
                return new Response(result.statusCode(), result.headers().firstValue("Content-Type").orElse(""), result.body());
            } catch (Exception error) { future.cancel(true); throw error; }
        };
    }
    MediaDownloader(Fetcher fetcher) { this.fetcher = fetcher; client = null; }
    public JsonArray manifest(JsonObject dataset, JsonObject grant) {
        var result = new JsonArray(); String examId = str(dataset.getAsJsonObject("exam"), "examId");
        for (var v : dataset.getAsJsonArray("media")) {
            var m = v.getAsJsonObject(); String reason = MediaPermissions.blockedReason(dataset, grant, m);
            result.add(obj("mediaId", str(m, "mediaId"), "sourceUrl", str(m, "sourceUrl"), "mediaType", str(m, "mediaType"),
                    "localFile", str(m, "localFile"), "relativeStoragePath", examId + "/" + str(m, "localFile"),
                    "uses", m.has("uses") ? m.get("uses").deepCopy() : new JsonArray(), "permissionStatus", reason == null ? "USER_CONFIRMED" : "BLOCKED",
                    "downloadStatus", reason == null ? "PLANNED" : "BLOCKED", "blockedReason", reason,
                    "httpStatus", null, "serverMimeType", null));
        }
        return result;
    }
    public JsonArray download(JsonObject dataset, JsonObject grant, Path audioRoot, Path imageRoot, Path receipts) throws IOException {
        JsonArray manifest = manifest(dataset, grant); String examId = str(dataset.getAsJsonObject("exam"), "examId");
        if (!safeFile(examId)) throw new IOException("Unsafe examId");
        for (int i = 0; i < manifest.size(); i++) {
            JsonObject row = manifest.get(i).getAsJsonObject(), media = dataset.getAsJsonArray("media").get(i).getAsJsonObject();
            if (str(row, "blockedReason") != null) continue;
            Path staged = null;
            try {
                String filename = str(media, "localFile"), id = str(media, "mediaId");
                if (!safeFile(filename) || !safeFile(id)) throw new IOException("UNSAFE_LOCAL_NAME");
                Path root = "AUDIO".equals(str(media, "mediaType")) ? audioRoot : imageRoot;
                if (root == null) throw new IOException("MEDIA_ROOT_REQUIRED");
                Files.createDirectories(root); Path canonicalRoot = root.toRealPath();
                Path directory = canonicalRoot.resolve(examId); Files.createDirectories(directory);
                if (Files.isSymbolicLink(directory) || !directory.toRealPath().equals(directory.toAbsolutePath().normalize())) throw new IOException("UNSAFE_EXAM_DIRECTORY");
                Path destination = directory.resolve(filename), receiptPath = receipts.resolve(examId).resolve(id + ".json");
                String permissionHash = hash(GSON.toJson(grant).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || !destination.toRealPath().equals(destination.toAbsolutePath().normalize())
                            || !Files.isRegularFile(receiptPath)) throw new IOException("EXISTING_FILE_WITHOUT_VALID_RECEIPT");
                    var receipt = read(receiptPath);
                    if (!str(media, "sourceUrl").equals(str(receipt, "sourceUrl")) || !permissionHash.equals(str(receipt, "permissionSha256"))
                            || !str(dataset.getAsJsonObject("source"), "sha256").equals(str(receipt, "sourceSha256"))
                            || !hash(destination).equals(str(receipt, "sha256"))) throw new IOException("EXISTING_FILE_RECEIPT_MISMATCH");
                    row.addProperty("downloadStatus", "CACHED"); row.add("httpStatus", receipt.get("httpStatus")); row.add("serverMimeType", receipt.get("serverMimeType"));
                    continue;
                }
                long limit = "IMAGE".equals(str(media, "mediaType")) ? 8L * 1024 * 1024 : 64L * 1024 * 1024;
                Response response = fetcher.get(URI.create(str(media, "sourceUrl")), limit);
                row.addProperty("httpStatus", response.status()); row.addProperty("serverMimeType", response.mime());
                if (response.status() != 200) throw new IOException("HTTP_" + response.status() + "_NO_AUTH_OR_REDIRECT_BYPASS");
                if (response.bytes().length == 0 || response.bytes().length > limit) throw new IOException("MEDIA_SIZE_LIMIT");
                String mime = response.mime().split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                if (!(mime.startsWith("image/") || mime.startsWith("audio/") || mime.equals("application/octet-stream"))) throw new IOException("HTTP_CONTENT_TYPE_NOT_MEDIA");
                staged = Files.createTempDirectory(canonicalRoot, "etl-staging-");
                Path stagedDir = staged.resolve(examId); Files.createDirectory(stagedDir);
                Path stagedFile = stagedDir.resolve(filename); Files.write(stagedFile, response.bytes(), StandardOpenOption.CREATE_NEW);
                JsonObject probeMedia = media.deepCopy();
                JsonObject probe = obj("exam", dataset.get("exam"), "media", arr(probeMedia));
                var inspection = new LocalMediaInspector().inspect(probe, staged, staged).get(0).getAsJsonObject();
                probeMedia = probe.getAsJsonArray("media").get(0).getAsJsonObject();
                if (!"BYTES_VERIFIED".equals(str(inspection, "state"))) throw new IOException("LOCAL_VERIFY_FAILED: " + str(inspection, "reason"));
                String measuredMime = str(probeMedia, "mimeType");
                if (!mime.equals("application/octet-stream") && !mime.equals(measuredMime)) throw new IOException("HTTP_MIME_SIGNATURE_MISMATCH");
                // Chỉ publish file đã kiểm; không ghi đè media operator đã có.
                Files.move(stagedFile, destination, StandardCopyOption.ATOMIC_MOVE);
                row.addProperty("downloadStatus", "DOWNLOADED");
                write(receiptPath, obj("mediaId", id, "sourceUrl", str(media, "sourceUrl"), "sourceSha256", str(dataset.getAsJsonObject("source"), "sha256"),
                        "permissionSha256", permissionHash, "sha256", str(probeMedia, "sha256"), "sizeBytes", response.bytes().length,
                        "httpStatus", response.status(), "serverMimeType", response.mime()));
            } catch (Exception error) {
                row.addProperty("downloadStatus", "BLOCKED");
                // Chỉ ghi mã/nguyên nhân, không ghi URL redirect hay dữ liệu trả về có thể chứa login/token.
                String reason = error.getMessage(); row.addProperty("blockedReason", reason == null ? error.getClass().getSimpleName() : reason);
            } finally {
                if (staged != null) try (var paths = Files.walk(staged)) {
                    for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                }
            }
            System.out.println(examId + " " + str(media, "mediaId") + " " + str(row, "downloadStatus"));
        }
        return manifest;
    }
    @Override public void close() { if (client != null) client.close(); }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        final long limit; long received; Flow.Subscription subscription;
        LimitedBody(long limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var b : buffers) received += b.remaining();
            if (received > limit) { subscription.cancel(); delegate.onError(new IOException("MEDIA_SIZE_LIMIT")); }
            else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }
}
