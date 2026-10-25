package vn.edu.toeic.server.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;

/**
 * Development/test demo accounts candidate1, candidate2, proctor1. Off unless toeic.seed.demo-accounts=true;
 * there is no default password, and accounts that already exist are never changed or removed.
 */
@Component
class SeedDataInitializer implements ApplicationRunner {
    private final UserAccountStore userAccountStore;
    private final PasswordEncoder passwordEncoder;
    private final boolean demoAccounts;
    private final String seedPassword;

    SeedDataInitializer(
            UserAccountStore userAccountStore,
            PasswordEncoder passwordEncoder,
            @Value("${toeic.seed.demo-accounts:false}") boolean demoAccounts,
            @Value("${toeic.seed.password:}") String seedPassword) {
        this.userAccountStore = userAccountStore;
        this.passwordEncoder = passwordEncoder;
        this.demoAccounts = demoAccounts;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!demoAccounts) return;
        if (seedPassword == null || seedPassword.isBlank())
            throw new IllegalStateException("toeic.seed.demo-accounts=true requires TOEIC_SEED_PASSWORD; there is no default demo password.");
        create("candidate1", "Thí sinh 1", Role.CANDIDATE);
        create("candidate2", "Thí sinh 2", Role.CANDIDATE);
        create("proctor1", "Giám thị 1", Role.PROCTOR);
    }

    private void create(String username, String displayName, Role role) {
        userAccountStore.createIfAbsent(username, displayName, role, passwordEncoder.encode(seedPassword));
    }
}
