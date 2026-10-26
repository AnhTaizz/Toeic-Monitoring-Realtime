package vn.edu.toeic.server.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;

/** Explicit operator opt-in; never derives an Admin password from the demo seed password. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserAccountStore users;
    private final PasswordEncoder passwords;
    private final String username, password;
    public AdminBootstrap(UserAccountStore users, PasswordEncoder passwords,
            @Value("${toeic.admin.bootstrap.username:}") String username,
            @Value("${toeic.admin.bootstrap.password:}") String password) {
        this.users=users; this.passwords=passwords; this.username=username; this.password=password;
    }
    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        if (username.isBlank() && password.isBlank()) return;
        AccountProvisioner.validate(username,"Quản trị viên");
        if (password.isBlank() || password.length()<20 || password.length()>72 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("Admin bootstrap requires a separate 20–72 byte password supplied by the operator.");
        users.createIfAbsent(username.strip(),"Quản trị viên",Role.ADMIN,passwords.encode(password));
        if (users.findByUsername(username.strip()).orElseThrow().role()!=Role.ADMIN)
            throw new IllegalStateException("Admin bootstrap username already belongs to a non-Admin account; no identity was changed.");
    }
}
