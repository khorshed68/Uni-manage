package com.university.ums.controller;

import com.university.ums.dto.ChangePasswordRequest;
import com.university.ums.dto.LoginRequest;
import com.university.ums.dto.LoginResponse;
import com.university.ums.dto.MessageResponse;
import com.university.ums.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login / logout / own account. Available to every role (login is public). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt);
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.currentProfile(jwt.getSubject()));
    }

    @PutMapping("/password")
    public ResponseEntity<MessageResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(jwt.getSubject(), request);
        return ResponseEntity.ok(new MessageResponse("Password changed. Please log in again with the new password."));
    }
}
