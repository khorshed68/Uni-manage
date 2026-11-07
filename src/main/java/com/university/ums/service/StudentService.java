package com.university.ums.service;

import com.university.ums.dto.StudentRequest;
import com.university.ums.dto.StudentResponse;
import com.university.ums.entity.Role;
import com.university.ums.entity.Student;
import com.university.ums.entity.UserAccount;
import com.university.ums.exception.BadRequestException;
import com.university.ums.exception.ConflictException;
import com.university.ums.exception.ResourceNotFoundException;
import com.university.ums.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final AccountService accountService;

    public StudentService(StudentRepository studentRepository, AccountService accountService) {
        this.studentRepository = studentRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> findAll() {
        return studentRepository.findAllByOrderByIdAsc().stream().map(StudentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse findById(Long id) {
        return StudentResponse.from(load(id));
    }

    public StudentResponse create(StudentRequest request) {
        String studentId = request.studentId().trim();
        if (studentRepository.existsByStudentIdIgnoreCase(studentId)) {
            throw new ConflictException("Student ID '" + studentId + "' is already in use");
        }
        UserAccount account = accountService.newAccount(request.username(), request.password(), request.fullName(),
                request.email(), Role.STUDENT);
        Student student = new Student(account);
        applyProfile(student, request, studentId);
        return StudentResponse.from(studentRepository.saveAndFlush(student));
    }

    public StudentResponse update(Long id, StudentRequest request) {
        Student student = load(id);
        String studentId = request.studentId().trim();
        if (studentRepository.existsByStudentIdIgnoreCaseAndIdNot(studentId, id)) {
            throw new ConflictException("Student ID '" + studentId + "' is already in use");
        }
        accountService.updateAccount(student.getUser(), request.username(), request.password(), request.fullName(),
                request.email());
        applyProfile(student, request, studentId);
        return StudentResponse.from(studentRepository.saveAndFlush(student));
    }

    public void delete(Long id, String currentUsername) {
        Student student = load(id);
        if (student.getUser().getUsername().equals(currentUsername)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        // Cascades to the student's login account, so their tokens stop working too.
        studentRepository.delete(student);
    }

    private void applyProfile(Student student, StudentRequest request, String studentId) {
        student.setStudentId(studentId);
        student.setDepartment(request.department().trim());
        student.setSemester(request.semester());
        student.setPhone(AccountService.blankToNull(request.phone()));
    }

    private Student load(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }
}
