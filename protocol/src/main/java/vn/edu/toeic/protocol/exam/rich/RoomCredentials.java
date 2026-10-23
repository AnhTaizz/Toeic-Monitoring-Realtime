package vn.edu.toeic.protocol.exam.rich;

/** Displayed once to the proctor for distribution to this candidate. */
public record RoomCredentials(long userId, String username, String displayName, String attemptId, String pin) {
    @Override public String toString() { return "RoomCredentials[userId=" + userId + ", pin=<redacted>]"; }
}
