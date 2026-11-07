package com.university.ums.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.university.ums.entity.Role;
import com.university.ums.entity.UserAccount;
import com.university.ums.exception.ConflictException;
import com.university.ums.repository.UserAccountRepository;
import com.university.ums.service.AccountService;

/** Unit tests for {@link AccountService}: the repository is mocked, no Spring context or database. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(userRepository, passwordEncoder);
    }

    @Test
    void newAccountStoresBcryptHashNotPlainPassword() {
        when(userRepository.existsByUsernameIgnoreCase("karim")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("karim@university.edu")).thenReturn(false);

        UserAccount account = accountService.newAccount(
                "karim", "Karim@1234", "Karim Hossain", "Karim@University.edu", Role.TEACHER);

        assertNotEquals("Karim@1234", account.getPassword());
        assertTrue(account.getPassword().startsWith("$2a$"));
        assertTrue(passwordEncoder.matches("Karim@1234", account.getPassword()));
        assertEquals("karim@university.edu", account.getEmail());
    }

      @Test
    void duplicateEmailIsRejected() {
        when(userRepository.existsByUsernameIgnoreCase("karim")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("karim@university.edu")).thenReturn(true);

        assertThrows(ConflictException.class, () -> accountService.newAccount(
                "karim", "Karim@1234", "Karim Hossain", "karim@university.edu", Role.TEACHER));
    }
}
