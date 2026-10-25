package vn.edu.toeic.protocol.exam.rich;

import vn.edu.toeic.protocol.auth.LoginResponse;

public record JoinedRoom(String roomCode, String attemptId, String state, LoginResponse login) {
    @Override public String toString() { return "JoinedRoom[roomCode=" + roomCode + ", state=" + state + ", login=<redacted>]"; }
}
