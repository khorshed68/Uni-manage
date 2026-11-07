package com.university.ums.config;

import com.university.ums.dto.StudentRequest;
import com.university.ums.dto.TeacherRequest;
import com.university.ums.entity.Role;
import com.university.ums.entity.UserAccount;
import com.university.ums.repository.UserAccountRepository;
import com.university.ums.service.AccountService;
import com.university.ums.service.StudentService;
import com.university.ums.service.TeacherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin on an empty database (there is no public sign-up, so
 * someone has to exist to create everyone else). Optionally adds one demo
 * teacher and one demo student so the roles can be tried immediately.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserAccountRepository userRepository;
    private final AccountService accountService;
    private final TeacherService teacherService;
    private final StudentService studentService;

    @Value("${app.admin.username}")
    private String adminUsername;
    @Value("${app.admin.password}")
    private String adminPassword;
    @Value("${app.admin.email}")
    private String adminEmail;
    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    public DataInitializer(UserAccountRepository userRepository, AccountService accountService,
                           TeacherService teacherService, StudentService studentService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.teacherService = teacherService;
        this.studentService = studentService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.ADMIN) == 0) {
            UserAccount admin = accountService.newAccount(adminUsername, adminPassword, "System Administrator",
                    adminEmail, Role.ADMIN);
            userRepository.save(admin);
            log.info("Created initial admin account '{}'", adminUsername);
        }
        if (seedDemoData && !userRepository.existsByUsername("teacher1")) {
            teacherService.create(new TeacherRequest("teacher1", "Teacher@123", "Demo Teacher",
                    "teacher1@university.edu", "EMP-001", "Computer Science", "Lecturer", null));
            log.info("Created demo teacher 'teacher1'");
        }
        if (seedDemoData && !userRepository.existsByUsername("student1")) {
            studentService.create(new StudentRequest("student1", "Student@123", "Demo Student",
                    "student1@university.edu", "STU-001", "Computer Science", 1, null));
            log.info("Created demo student 'student1'");
        }
    }
}
