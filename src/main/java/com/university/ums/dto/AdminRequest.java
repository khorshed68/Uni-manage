package com.university.ums.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for POST/PUT /api/admins. On PUT the password is optional (blank = keep current). */
public record AdminRequest(
        @NotBlank @Pattern(regexp = ValidationRules.USERNAME_REGEX, message = ValidationRules.USERNAME_MESSAGE)
        String username,

        @NotBlank(groups = OnCreate.class) @Size(min = 8, max = 72)
        String password,

        @NotBlank @Size(max = 100)
        String fullName,

        @NotBlank @Email @Size(max = 120)
        String email
) {
}
