package vn.edu.toeic.client;

public final class InvalidServerResponseException extends RuntimeException {
    public InvalidServerResponseException() {
        super("Phản hồi từ server không hợp lệ.");
    }
}
