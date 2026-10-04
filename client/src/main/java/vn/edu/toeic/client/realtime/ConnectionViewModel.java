package vn.edu.toeic.client.realtime;

/** Pure model: the JavaFX caller marshals updates with Platform.runLater. */
public record ConnectionViewModel(String status, boolean networkLocked) {
    public static ConnectionViewModel from(ConnectionState state) {
        return switch (state) {
            case CONNECTED -> new ConnectionViewModel("Đã kết nối tới server", false);
            case CONNECTING -> new ConnectionViewModel("Đang kết nối tới server…", true);
            case RECONNECTING -> new ConnectionViewModel("Mất kết nối tới server. Đang thử kết nối lại…", true);
            case DISCONNECTED -> new ConnectionViewModel("Mất kết nối tới server", true);
            case FAILED -> new ConnectionViewModel("Không kết nối được. Cần kiểm tra hoặc đăng nhập lại.", true);
        };
    }
}
