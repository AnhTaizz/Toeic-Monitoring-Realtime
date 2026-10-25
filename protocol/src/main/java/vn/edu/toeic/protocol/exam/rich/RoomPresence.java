package vn.edu.toeic.protocol.exam.rich;

import java.time.Instant;
import java.util.List;

public record RoomPresence(String roomCode, String sessionId, String state, List<Member> members) {
    public RoomPresence { members = members == null ? null : List.copyOf(members); }
    public record Member(long userId, String username, String displayName, String attemptId, Instant joinedAt,
                         String connectionStatus, String connectionReason, Instant lastSeenAt) {
        public Member(long userId,String username,String displayName,String attemptId,Instant joinedAt) {
            this(userId,username,displayName,attemptId,joinedAt,"UNKNOWN","NOT_SEEN",null);
        }
    }
}
