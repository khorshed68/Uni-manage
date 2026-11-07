package com.university.ums.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings bound from {@code app.jwt.*}.
 *
 * @param secret     HMAC-SHA256 signing key, at least 32 characters (set JWT_SECRET in production)
 * @param expiration how long an access token stays valid
 * @param issuer     value written to and required in the {@code iss} claim
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration, String issuer) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 bytes long");
        }
        if (expiration == null) {
            expiration = Duration.ofHours(1);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "university-management-system";
        }
    }
}
