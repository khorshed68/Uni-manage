package com.university.ums;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UniversityManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniversityManagementSystemApplication.class, args);
    }
}
