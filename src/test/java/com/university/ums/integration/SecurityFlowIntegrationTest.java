package com.university.ums.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

/**
 * Integration tests: the whole application starts (security filter chain, controllers, services,
 * JPA and an H2 database) and real HTTP requests are sent with real JWTs from real logins.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanCreateTeacher() throws Exception {
        String token = login("admin@university.edu", "Admin@123");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"username":"it%s","password":"Password@1","fullName":"IT Teacher","email":"it%s@uni.edu",
                 "employeeId":"EMP-IT-%s","department":"CSE","designation":"Lecturer"}
                """.formatted(suffix, suffix, suffix);

        mockMvc.perform(post("/api/teachers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void teacherCannotAccessStudents() throws Exception {
        String token = login("teacher1@university.edu", "Teacher@123");

        mockMvc.perform(get("/api/students").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void tokenIsRejectedAfterLogout() throws Exception {
        String token = login("student1@university.edu", "Student@123");

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }
}
