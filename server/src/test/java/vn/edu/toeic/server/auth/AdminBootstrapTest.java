package vn.edu.toeic.server.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.protocol.Role;

class AdminBootstrapTest {
    private final UserAccountStore users=mock(UserAccountStore.class);
    private final PasswordEncoder passwords=mock(PasswordEncoder.class);
    @Test void bootstrapIsOffByDefaultAndNeverUsesSeedPassword() {
        new AdminBootstrap(users,passwords,"","").run(null);
        verifyNoInteractions(users,passwords);
    }
    @Test void explicitBootstrapUsesEncoderAndNeverOverwritesExistingIdentity() {
        String secret="MOCK-independent-256bit-secret";
        when(passwords.encode(secret)).thenReturn("MOCK-bcrypt");
        when(users.findByUsername("admin")).thenReturn(Optional.of(new UserAccount(1,"admin","MOCK",Role.ADMIN,"existing-hash",true)));
        new AdminBootstrap(users,passwords,"admin",secret).run(null);
        verify(users).createIfAbsent("admin","Quản trị viên",Role.ADMIN,"MOCK-bcrypt");
    }
    @Test void shortUnicodeOverBcryptLimitAndPartialConfigurationAreRejectedWithoutInserts() {
        for(String secret:new String[]{"", "ChangeMe123!", " ".repeat(30), "Đ".repeat(40)})
            assertThatThrownBy(()->new AdminBootstrap(users,passwords,"admin",secret).run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new AdminBootstrap(users,passwords,"","x".repeat(30)).run(null)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(users,passwords);
    }
    @Test void bootstrapCannotPromoteAnExistingProctor() {
        when(users.findByUsername("proctor1")).thenReturn(Optional.of(new UserAccount(1,"proctor1","MOCK",Role.PROCTOR,"existing-hash",true)));
        assertThatThrownBy(()->new AdminBootstrap(users,passwords,"proctor1","x".repeat(30)).run(null)).isInstanceOf(IllegalStateException.class);
        verify(users).createIfAbsent(eq("proctor1"),anyString(),eq(Role.ADMIN),nullable(String.class));
    }
}
