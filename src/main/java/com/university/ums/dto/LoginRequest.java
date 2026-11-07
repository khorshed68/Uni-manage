package com.university.ums.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/auth/login. Users sign in with their email address and password. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
