package vn.edu.toeic.server.auth;

import java.util.Optional;
import vn.edu.toeic.protocol.Role;

public interface UserAccountStore {
    Optional<UserAccount> findByUsername(String username);

    void createIfAbsent(String username, String displayName, Role role, String passwordHash);
}
