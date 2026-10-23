package vn.edu.toeic.protocol.exam.rich;

public record JoinRoomRequest(String roomCode, String candidateCode, String pin, String requestId) {
    @Override public String toString() { return "JoinRoomRequest[credentials=<redacted>]"; }
}
