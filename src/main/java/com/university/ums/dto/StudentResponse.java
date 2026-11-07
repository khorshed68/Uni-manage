package com.university.ums.dto;

import com.university.ums.entity.Role;
import com.university.ums.entity.Student;
import com.university.ums.entity.UserAccount;

import java.time.Instant;

public record StudentResponse(
        Long id,
        String username,
        String fullName,
        String email,
        Role role,
        String studentId,
        String department,
        Integer semester,
        String phone,
        Instant createdAt,
        Instant updatedAt
) {
    public static StudentResponse from(Student student) {
        UserAccount user = student.getUser();
        return new StudentResponse(student.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getRole(), student.getStudentId(), student.getDepartment(), student.getSemester(),
                student.getPhone(), student.getCreatedAt(), student.getUpdatedAt());
    }
}
