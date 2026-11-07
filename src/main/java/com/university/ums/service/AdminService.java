package com.university.ums.service;

import com.university.ums.dto.AdminRequest;
import com.university.ums.dto.AdminResponse;
import com.university.ums.entity.Role;
import com.university.ums.entity.UserAccount;
import com.university.ums.exception.BadRequestException;
import com.university.ums.exception.ConflictException;
import com.university.ums.exception.ResourceNotFoundException;
import com.university.ums.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AdminService {

    private final UserAccountRepository userRepository;
    private final AccountService accountService;

    public AdminService(UserAccountRepository userRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<AdminResponse> findAll() {
        return userRepository.findAllByRoleOrderByIdAsc(Role.ADMIN).stream().map(AdminResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AdminResponse findById(Long id) {
        return AdminResponse.from(load(id));
    }

    public AdminResponse create(AdminRequest request) {
        UserAccount admin = accountService.newAccount(request.username(), request.password(), request.fullName(),
                request.email(), Role.ADMIN);
        return AdminResponse.from(userRepository.saveAndFlush(admin));
    }

    public AdminResponse update(Long id, AdminRequest request) {
        UserAccount admin = load(id);
        accountService.updateAccount(admin, request.username(), request.password(), request.fullName(), request.email());
        return AdminResponse.from(userRepository.saveAndFlush(admin));
    }

    public void delete(Long id, String currentUsername) {
        UserAccount admin = load(id);
        if (admin.getUsername().equals(currentUsername)) {
            throw new BadRequestException("You cannot delete your own admin account");
        }
        if (userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new ConflictException("Cannot delete the last remaining admin");
        }
        userRepository.delete(admin);
    }

    private UserAccount load(Long id) {
        return userRepository.findByIdAndRole(id, Role.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", id));
    }
}
