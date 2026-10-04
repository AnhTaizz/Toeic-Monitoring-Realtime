package vn.edu.toeic.server.auth;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.server.monitoring.JdbcAttemptScopeStore;

@Configuration
class AuthConfiguration {
    @Bean
    @ConditionalOnMissingBean(AttemptScopeAuthorizer.class)
    AttemptScopeAuthorizer attemptScopeAuthorizer(JdbcClient jdbc) {
        return new JdbcAttemptScopeStore(jdbc);
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
