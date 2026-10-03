package vn.edu.toeic.server.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;

@Component
class SeedDataInitializer implements ApplicationRunner {
    private final UserAccountStore userAccountStore;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    SeedDataInitializer(
            UserAccountStore userAccountStore,
            PasswordEncoder passwordEncoder,
            @Value("${toeic.seed.password:ChangeMe123!}") String seedPassword) {
        this.userAccountStore = userAccountStore;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        create("candidate1", "Thí sinh 1", Role.CANDIDATE);
        create("candidate2", "Thí sinh 2", Role.CANDIDATE);
        create("proctor1", "Giám thị 1", Role.PROCTOR);
    }

    private void create(String username, String displayName, Role role) {
        userAccountStore.createIfAbsent(username, displayName, role, passwordEncoder.encode(seedPassword));
    }
}
