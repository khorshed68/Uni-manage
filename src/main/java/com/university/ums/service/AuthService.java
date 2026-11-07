package com.university.ums.service;

import com.university.ums.dto.AdminResponse;
import com.university.ums.dto.ChangePasswordRequest;
import com.university.ums.dto.LoginRequest;
import com.university.ums.dto.LoginResponse;
import com.university.ums.dto.StudentResponse;
import com.university.ums.dto.TeacherResponse;
import com.university.ums.entity.RevokedToken;
import com.university.ums.entity.UserAccount;
import com.university.ums.exception.BadRequestException;
import com.university.ums.repository.RevokedTokenRepository;
import com.university.ums.repository.StudentRepository;
import com.university.ums.repository.TeacherRepository;
import com.university.ums.repository.UserAccountRepository;
import com.university.ums.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       PasswordEncoder passwordEncoder, UserAccountRepository userRepository,
                       TeacherRepository teacherRepository, StudentRepository studentRepository,
                       RevokedTokenRepository revokedTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    /** Verifies email + password (BCrypt) and returns a signed JWT. Throws BadCredentialsException (401) on failure. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        UserAccount user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        JwtService.IssuedToken token = jwtService.issue(user);
        log.info("User '{}' ({}) logged in", user.getUsername(), user.getRole());
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), token.expiresAt(),
                user.getUsername(), user.getRole());
    }

    /** Revokes the presented token so it can no longer be used, even before it expires. */
    @Transactional
    public void logout(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt() != null ? jwt.getExpiresAt() : Instant.now().plusSeconds(86_400);
        revokedTokenRepository.save(new RevokedToken(jwt.getId(), expiresAt));
        log.info("User '{}' logged out", jwt.getSubject());
    }

    /** Returns the caller's own profile; the shape depends on their role. */
    @Transactional(readOnly = true)
    public Object currentProfile(String username) {
        UserAccount user = loadUser(username);
        return switch (user.getRole()) {
            case ADMIN -> AdminResponse.from(user);
            case TEACHER -> teacherRepository.findByUserId(user.getId()).map(TeacherResponse::from)
                    .orElseThrow(() -> new IllegalStateException("Teacher profile missing for " + username));
            case STUDENT -> studentRepository.findByUserId(user.getId()).map(StudentResponse::from)
                    .orElseThrow(() -> new IllegalStateException("Student profile missing for " + username));
        };
    }

    /** Changes the caller's own password. All tokens issued before the change stop working. */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        UserAccount user = loadUser(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /** Housekeeping: revoked tokens only need to be remembered until they would have expired anyway. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void purgeExpiredRevokedTokens() {
        int removed = revokedTokenRepository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.debug("Purged {} expired revoked tokens", removed);
        }
    }

    private UserAccount loadUser(String username) {
        // The token validator already guarantees the user exists; this is just a safe fallback.
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }
}
