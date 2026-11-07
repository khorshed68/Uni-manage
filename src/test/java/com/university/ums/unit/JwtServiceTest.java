package com.university.ums.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.university.ums.entity.Role;
import com.university.ums.entity.UserAccount;
import com.university.ums.security.JwtProperties;
import com.university.ums.security.JwtService;

/** Unit test for {@link JwtService}: issues a token and decodes it with the same key, no Spring context. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef0123";

    @Test
    void issuedTokenContainsUsernameRoleAndExpiry() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1), "test-issuer");
        JwtService jwtService = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties);
        UserAccount user = new UserAccount("teacher1", "hash", "Demo Teacher", "teacher1@university.edu", Role.TEACHER);

        JwtService.IssuedToken token = jwtService.issue(user);
        Jwt jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token.value());

        assertEquals("teacher1", jwt.getSubject());
        assertEquals("TEACHER", jwt.getClaimAsString(JwtService.ROLE_CLAIM));
        assertEquals("test-issuer", jwt.getClaimAsString("iss"));
        assertNotNull(jwt.getId());
        assertEquals(3600, token.expiresInSeconds());
    }
}
