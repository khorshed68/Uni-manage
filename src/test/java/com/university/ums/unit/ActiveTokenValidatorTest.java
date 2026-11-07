package com.university.ums.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.university.ums.repository.RevokedTokenRepository;
import com.university.ums.repository.UserAccountRepository;
import com.university.ums.security.ActiveTokenValidator;

/** Unit test for {@link ActiveTokenValidator}: a logged-out (revoked) token must be rejected. */
@ExtendWith(MockitoExtension.class)
class ActiveTokenValidatorTest {

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    void loggedOutTokenIsRejected() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("student1")
                .jti("revoked-id")
                .claim("role", "STUDENT")
                .claim("ver", 0)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(revokedTokenRepository.existsById("revoked-id")).thenReturn(true);

        ActiveTokenValidator validator = new ActiveTokenValidator(userRepository, revokedTokenRepository);

        assertTrue(validator.validate(jwt).hasErrors());
    }
}
