package vn.edu.toeic.server.monitoring;

public final class EventConflictException extends RuntimeException {
    public EventConflictException() { super("eventId đã được dùng với nội dung khác"); }
}
