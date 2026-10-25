package vn.edu.toeic.server.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.protocol.Role;

class SeedDataInitializerTest {
    private final UserAccountStore users=mock(UserAccountStore.class);
    private final PasswordEncoder passwords=mock(PasswordEncoder.class);
    @Test void demoAccountsAreOffByDefaultEvenWithASeedPassword() {
        new SeedDataInitializer(users,passwords,false,"MOCK-seed").run(null);
        verifyNoInteractions(users,passwords);
    }
    @Test void optInWithoutPasswordFailsWithoutInsertsBecauseThereIsNoDefault() {
        for(String missing:new String[]{null,""," "})
            assertThatThrownBy(()->new SeedDataInitializer(users,passwords,true,missing).run(null)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users,passwords);
    }
    @Test void explicitOptInCreatesOnlyAbsentDemoAccounts() {
        when(passwords.encode("MOCK-seed")).thenReturn("MOCK-bcrypt");
        new SeedDataInitializer(users,passwords,true,"MOCK-seed").run(null);
        verify(users).createIfAbsent("candidate1","Thí sinh 1",Role.CANDIDATE,"MOCK-bcrypt");
        verify(users).createIfAbsent("candidate2","Thí sinh 2",Role.CANDIDATE,"MOCK-bcrypt");
        verify(users).createIfAbsent("proctor1","Giám thị 1",Role.PROCTOR,"MOCK-bcrypt");
        verifyNoMoreInteractions(users);
    }
}
