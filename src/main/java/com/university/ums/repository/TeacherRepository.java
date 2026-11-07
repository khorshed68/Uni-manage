package com.university.ums.repository;

import com.university.ums.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    List<Teacher> findAllByOrderByIdAsc();

    Optional<Teacher> findByUserId(Long userId);

    boolean existsByEmployeeIdIgnoreCase(String employeeId);

    boolean existsByEmployeeIdIgnoreCaseAndIdNot(String employeeId, Long id);
}
