package com.university.ums.service;

import com.university.ums.dto.TeacherRequest;
import com.university.ums.dto.TeacherResponse;
import com.university.ums.entity.Role;
import com.university.ums.entity.Teacher;
import com.university.ums.entity.UserAccount;
import com.university.ums.exception.BadRequestException;
import com.university.ums.exception.ConflictException;
import com.university.ums.exception.ResourceNotFoundException;
import com.university.ums.repository.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final AccountService accountService;

    public TeacherService(TeacherRepository teacherRepository, AccountService accountService) {
        this.teacherRepository = teacherRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<TeacherResponse> findAll() {
        return teacherRepository.findAllByOrderByIdAsc().stream().map(TeacherResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TeacherResponse findById(Long id) {
        return TeacherResponse.from(load(id));
    }

    public TeacherResponse create(TeacherRequest request) {
        String employeeId = request.employeeId().trim();
        if (teacherRepository.existsByEmployeeIdIgnoreCase(employeeId)) {
            throw new ConflictException("Employee ID '" + employeeId + "' is already in use");
        }
        UserAccount account = accountService.newAccount(request.username(), request.password(), request.fullName(),
                request.email(), Role.TEACHER);
        Teacher teacher = new Teacher(account);
        applyProfile(teacher, request, employeeId);
        return TeacherResponse.from(teacherRepository.saveAndFlush(teacher));
    }

    public TeacherResponse update(Long id, TeacherRequest request) {
        Teacher teacher = load(id);
        String employeeId = request.employeeId().trim();
        if (teacherRepository.existsByEmployeeIdIgnoreCaseAndIdNot(employeeId, id)) {
            throw new ConflictException("Employee ID '" + employeeId + "' is already in use");
        }
        accountService.updateAccount(teacher.getUser(), request.username(), request.password(), request.fullName(),
                request.email());
        applyProfile(teacher, request, employeeId);
        return TeacherResponse.from(teacherRepository.saveAndFlush(teacher));
    }

    public void delete(Long id, String currentUsername) {
        Teacher teacher = load(id);
        if (teacher.getUser().getUsername().equals(currentUsername)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        // Cascades to the teacher's login account, so their tokens stop working too.
        teacherRepository.delete(teacher);
    }

    private void applyProfile(Teacher teacher, TeacherRequest request, String employeeId) {
        teacher.setEmployeeId(employeeId);
        teacher.setDepartment(request.department().trim());
        teacher.setDesignation(request.designation().trim());
        teacher.setPhone(AccountService.blankToNull(request.phone()));
    }

    private Teacher load(Long id) {
        return teacherRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Teacher", id));
    }
}
