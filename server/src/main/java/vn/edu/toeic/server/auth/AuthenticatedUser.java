package vn.edu.toeic.server.auth;

import java.security.Principal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import vn.edu.toeic.protocol.Role;

/** Identity read from the server DB, never from client role/payload. */
public record AuthenticatedUser(long userId, String username, Role role) implements Principal {
    public static final String ATTRIBUTE = AuthenticatedUser.class.getName();
    @JsonIgnore @Override public String getName() { return username; }
}
