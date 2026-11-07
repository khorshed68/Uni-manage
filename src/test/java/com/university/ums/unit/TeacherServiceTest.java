package com.university.ums.unit;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.university.ums.entity.Role;
import com.university.ums.entity.Teacher;
import com.university.ums.entity.UserAccount;
import com.university.ums.exception.BadRequestException;
import com.university.ums.exception.ResourceNotFoundException;
import com.university.ums.repository.TeacherRepository;
import com.university.ums.service.AccountService;
import com.university.ums.service.TeacherService;

/** Unit tests for {@link TeacherService}: repository and account service are mocked. */
@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private TeacherService teacherService;

    @Test
    void teacherCannotDeleteOwnAccount() {
        UserAccount user = new UserAccount("teacher1", "hash", "Demo Teacher", "teacher1@university.edu", Role.TEACHER);
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(new Teacher(user)));

        assertThrows(BadRequestException.class, () -> teacherService.delete(1L, "teacher1"));
        verify(teacherRepository, never()).delete(any());
    }

   @Test
    void unknownTeacherIdThrowsNotFound() {
        when(teacherRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teacherService.findById(99L));
    }
}
