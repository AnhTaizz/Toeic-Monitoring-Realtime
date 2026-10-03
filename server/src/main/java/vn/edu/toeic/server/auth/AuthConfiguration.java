package vn.edu.toeic.server.auth;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class AuthConfiguration {
    @Bean
    @ConditionalOnMissingBean(AttemptScopeAuthorizer.class)
    AttemptScopeAuthorizer attemptScopeAuthorizer() {
        return (user, attemptId) -> false; // No production attempt schema/assignment exists at T1-A2.
    }
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
