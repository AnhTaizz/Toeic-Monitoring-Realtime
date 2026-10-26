package vn.edu.toeic.protocol.exam.rich;

public record JoinPinRoomRequest(String pin, String requestId) {
    @Override public String toString() { return "JoinPinRoomRequest[credentials=<redacted>]"; }
}
