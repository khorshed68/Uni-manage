package com.university.ums.dto;

import com.university.ums.entity.Role;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        String username,
        Role role
) {
}
