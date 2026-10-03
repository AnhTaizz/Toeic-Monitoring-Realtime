package vn.edu.toeic.server.auth;

import vn.edu.toeic.protocol.Role;

public record UserAccount(
        long id,
        String username,
        String displayName,
        Role role,
        String passwordHash,
        boolean enabled) {
}
