package com.university.ums.security;

import com.university.ums.entity.UserAccount;
import com.university.ums.repository.RevokedTokenRepository;
import com.university.ums.repository.UserAccountRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Rejects a correctly signed, unexpired JWT when it should no longer be honoured:
 * it was logged out, its user was deleted, its role no longer matches, or the
 * user's password changed after it was issued.
 */
public class ActiveTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final UserAccountRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public ActiveTokenValidator(UserAccountRepository userRepository, RevokedTokenRepository revokedTokenRepository) {
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getId() == null || revokedTokenRepository.existsById(jwt.getId())) {
            return failure("Token has been revoked (logged out)");
        }
        Optional<UserAccount> user = Optional.ofNullable(jwt.getSubject()).flatMap(userRepository::findByUsername);
        if (user.isEmpty()) {
            return failure("User no longer exists");
        }
        if (!user.get().getRole().name().equals(jwt.getClaimAsString(JwtService.ROLE_CLAIM))) {
            return failure("Token role does not match user");
        }
        Object version = jwt.getClaims().get(JwtService.VERSION_CLAIM);
        if (!(version instanceof Number number) || number.intValue() != user.get().getTokenVersion()) {
            return failure("Token was issued before the password was changed");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
