package com.university.ums.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for POST/PUT /api/students. On PUT the password is optional (blank = keep current). */
public record StudentRequest(
        @NotBlank @Pattern(regexp = ValidationRules.USERNAME_REGEX, message = ValidationRules.USERNAME_MESSAGE)
        String username,

        @NotBlank(groups = OnCreate.class) @Size(min = 8, max = 72)
        String password,

        @NotBlank @Size(max = 100)
        String fullName,

        @NotBlank @Email @Size(max = 120)
        String email,

        @NotBlank @Size(max = 30)
        String studentId,

        @NotBlank @Size(max = 100)
        String department,

        @NotNull @Min(1) @Max(12)
        Integer semester,

        @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
        String phone
) {
}
