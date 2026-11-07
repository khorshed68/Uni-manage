package com.university.ums.dto;

import com.university.ums.entity.Role;
import com.university.ums.entity.UserAccount;

import java.time.Instant;

public record AdminResponse(
        Long id,
        String username,
        String fullName,
        String email,
        Role role,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminResponse from(UserAccount user) {
        return new AdminResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
