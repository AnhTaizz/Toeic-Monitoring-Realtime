package vn.edu.toeic.server.auth;

import java.security.Principal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import vn.edu.toeic.protocol.Role;

/** Identity read from the server DB, never from client role/payload. */
public record AuthenticatedUser(long userId, String username, Role role, String roomAttemptId) implements Principal {
    public AuthenticatedUser(long userId, String username, Role role) { this(userId, username, role, null); }
    public static final String ATTRIBUTE = "vn.edu.toeic.server.auth.AuthenticatedUser";
    @JsonIgnore @Override public String getName() { return username; }
}
