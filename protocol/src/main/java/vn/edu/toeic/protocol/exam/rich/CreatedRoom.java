package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record CreatedRoom(String roomCode, CreateRichSessionResponse session, List<RoomCredentials> candidates) {
    public CreatedRoom { candidates = candidates == null ? null : List.copyOf(candidates); }
    @Override public String toString() { return "CreatedRoom[roomCode=" + roomCode + ", credentials=<redacted>]"; }
}
