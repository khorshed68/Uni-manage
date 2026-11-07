package com.university.ums.dto;

import com.university.ums.entity.Role;
import com.university.ums.entity.Teacher;
import com.university.ums.entity.UserAccount;

import java.time.Instant;

public record TeacherResponse(
        Long id,
        String username,
        String fullName,
        String email,
        Role role,
        String employeeId,
        String department,
        String designation,
        String phone,
        Instant createdAt,
        Instant updatedAt
) {
    public static TeacherResponse from(Teacher teacher) {
        UserAccount user = teacher.getUser();
        return new TeacherResponse(teacher.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getRole(), teacher.getEmployeeId(), teacher.getDepartment(), teacher.getDesignation(),
                teacher.getPhone(), teacher.getCreatedAt(), teacher.getUpdatedAt());
    }
}
