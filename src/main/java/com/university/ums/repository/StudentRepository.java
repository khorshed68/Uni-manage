package com.university.ums.repository;

import com.university.ums.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findAllByOrderByIdAsc();

    Optional<Student> findByUserId(Long userId);

    boolean existsByStudentIdIgnoreCase(String studentId);

    boolean existsByStudentIdIgnoreCaseAndIdNot(String studentId, Long id);
}
