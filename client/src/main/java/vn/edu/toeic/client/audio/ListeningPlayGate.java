package vn.edu.toeic.client.audio;

/**
 * Cổng trước MediaPlayer.play() cho một đoạn Listening. Gọi trên JavaFX thread khi Media đã onReady.
 * Ca legacy dùng {@link #IMMEDIATE}; ca V2 chỉ mở khi ảnh Part 1 của đoạn đã hiển thị thật trong scene.
 * Đúng một trong hai callback được gọi; fail làm player dừng và báo MEDIA_ERROR như lỗi media khác.
 */
@FunctionalInterface
public interface ListeningPlayGate {
    void await(ListeningPlayback.Segment segment, Runnable open, Runnable fail);

    ListeningPlayGate IMMEDIATE = (segment, open, fail) -> open.run();
}
