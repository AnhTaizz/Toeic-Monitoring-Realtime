package vn.edu.toeic.client.exam.rich;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Ảnh đề chỉ nằm trong RAM, không ghi đĩa. Tải, kiểm và decode trên worker riêng (không chạy trên
 * JavaFX thread). Cache theo {@link Scope}: đổi attempt/writer/epoch/manifest thì dữ liệu cũ bị bỏ.
 *
 * @param <T> kiểu ảnh đã decode; app dùng javafx Image, test dùng decoder giả.
 */
public final class RichImageStore<T> implements AutoCloseable {
    /** Ngữ cảnh hợp lệ của ảnh. Trước START: manifest + epoch + writer; sau START: visualManifestId đã chốt. */
    public record Scope(String attemptId, long writerEpoch, String preparationEpoch, String manifestId) {}
    /** Metadata server công bố trong manifest/DTO; bytes thật phải khớp hết. */
    public record Expected(String mediaId, String sha256, long sizeBytes, int widthPx, int heightPx, String mimeType) {}
    public record Loaded<T>(Expected expected, T image) {}

    private record Key(Scope scope, String mediaId, String sha256) {}

    private final RichExamApi api;
    private final Function<byte[], T> decoder;
    private final ExecutorService worker;
    private final Map<Key, CompletableFuture<Loaded<T>>> entries = new HashMap<>();
    private Scope scope;
    private boolean closed;

    public RichImageStore(RichExamApi api, Function<byte[], T> decoder) {
        this(api, decoder, Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "toeic-rich-image"); thread.setDaemon(true); return thread;
        }));
    }

    RichImageStore(RichExamApi api, Function<byte[], T> decoder, ExecutorService worker) {
        this.api = Objects.requireNonNull(api); this.decoder = Objects.requireNonNull(decoder); this.worker = Objects.requireNonNull(worker);
    }

    /** Đổi ngữ cảnh: xóa ảnh của ngữ cảnh khác và hủy các lần tải đang chạy của chúng. */
    public synchronized void useScope(Scope next) {
        if (Objects.equals(scope, next)) return;
        scope = next;
        // Tách khỏi map trước rồi mới hủy: cancel() chạy callback xóa entry ngay trên luồng này.
        List<CompletableFuture<Loaded<T>>> stale = new ArrayList<>();
        entries.entrySet().removeIf(entry -> {
            if (entry.getKey().scope().equals(next)) return false;
            stale.add(entry.getValue());
            return true;
        });
        stale.forEach(future -> future.cancel(true));
    }

    public synchronized Scope scope() { return scope; }
    public synchronized int size() { return entries.size(); }

    /**
     * Tải một lần cho mỗi (scope, mediaId, sha256). Lần thất bại bị xóa khỏi cache để người dùng thử lại.
     * manifestQuery là visualManifestId gửi kèm URL (bắt buộc trước START).
     */
    public synchronized CompletableFuture<Loaded<T>> load(Expected expected, String manifestQuery) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Kho ảnh đã đóng."));
        if (scope == null) return CompletableFuture.failedFuture(new IllegalStateException("Chưa có ngữ cảnh ảnh hợp lệ."));
        Scope owner = scope;
        Key key = new Key(owner, expected.mediaId(), expected.sha256());
        CompletableFuture<Loaded<T>> existing = entries.get(key);
        if (existing != null) return existing;
        CompletableFuture<Loaded<T>> future = CompletableFuture.supplyAsync(() -> {
            try {
                var downloaded = api.image(owner.attemptId(), expected.mediaId(), manifestQuery);
                verify(downloaded.bytes(), expected);
                T decoded = decoder.apply(downloaded.bytes());
                synchronized (this) {
                    if (closed || !owner.equals(scope)) throw new CancellationException("Ngữ cảnh ảnh đã đổi.");
                }
                return new Loaded<>(expected, decoded);
            } catch (RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new CompletionException(failure); }
        }, worker);
        entries.put(key, future);
        future.whenComplete((ignored, failure) -> {
            if (failure != null) synchronized (this) { entries.remove(key, future); }
        });
        return future;
    }

    /** Kiểm bytes thật: kích thước, SHA-256, chữ ký PNG/JPEG và kích thước pixel trong header. */
    public static void verify(byte[] bytes, Expected expected) throws IOException {
        if (bytes == null || bytes.length != expected.sizeBytes()) throw new IOException("Ảnh tải về sai kích thước file.");
        if (!sha256(bytes).equals(expected.sha256())) throw new IOException("Ảnh tải về sai SHA-256.");
        boolean png = bytes.length > 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean jpeg = bytes.length > 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8;
        if (!(png && "image/png".equals(expected.mimeType())) && !(jpeg && "image/jpeg".equals(expected.mimeType())))
            throw new IOException("Ảnh tải về không đúng định dạng đã công bố.");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? Collections.emptyIterator() : ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Không đọc được header ảnh.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) != expected.widthPx() || reader.getHeight(0) != expected.heightPx())
                    throw new IOException("Ảnh tải về sai kích thước pixel.");
            } finally { reader.dispose(); }
        }
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        List<CompletableFuture<Loaded<T>>> pending = new ArrayList<>(entries.values());
        entries.clear();
        pending.forEach(future -> future.cancel(true));
        worker.shutdownNow();
    }
}
