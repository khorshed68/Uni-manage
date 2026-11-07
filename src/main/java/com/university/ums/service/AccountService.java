package com.university.ums.service;

import com.university.ums.entity.Role;
import com.university.ums.entity.UserAccount;
import com.university.ums.exception.ConflictException;
import com.university.ums.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Shared logic for the login account behind every admin, teacher and student. */
@Service
public class AccountService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount newAccount(String username, String rawPassword, String fullName, String email, Role role) {
        String normalizedUsername = username.trim();
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ConflictException("Username '" + normalizedUsername + "' is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email '" + normalizedEmail + "' is already in use");
        }
        return new UserAccount(normalizedUsername, passwordEncoder.encode(rawPassword), fullName.trim(),
                normalizedEmail, role);
    }

    /** Applies a PUT to an account. A null/blank password keeps the current one. */
    public void updateAccount(UserAccount account, String username, String rawPassword, String fullName, String email) {
        String normalizedUsername = username.trim();
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, account.getId())) {
            throw new ConflictException("Username '" + normalizedUsername + "' is already taken");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, account.getId())) {
            throw new ConflictException("Email '" + normalizedEmail + "' is already in use");
        }
        account.setUsername(normalizedUsername);
        account.setFullName(fullName.trim());
        account.setEmail(normalizedEmail);
        if (rawPassword != null && !rawPassword.isBlank()) {
            account.changePassword(passwordEncoder.encode(rawPassword));
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
