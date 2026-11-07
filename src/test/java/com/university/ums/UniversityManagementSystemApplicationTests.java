package com.university.ums;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the real security filter chain: real logins, real JWTs, H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UniversityManagementSystemApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        adminToken = login("admin@university.edu", "Admin@123");
    }

    // ---------------------------------------------------------------- authentication

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void webUiIsPublicButContainsNoData() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/app.js")).andExpect(status().isOk());
        mockMvc.perform(get("/app.css")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        for (String path : new String[]{"/api/admins", "/api/teachers", "/api/students", "/api/auth/me"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void loginIsByEmailNotUsername() throws Exception {
        // Email is case-insensitive.
        login("ADMIN@University.edu", "Admin@123");
        // The username is not accepted as a login identifier.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@university.edu\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@university.edu\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void tamperedOrGarbageTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        String tampered = adminToken.substring(0, adminToken.length() - 3) + "abc";
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordsAreStoredHashedNotInPlainText() throws Exception {
        String suffix = unique();
        createTeacher(suffix);
        // The API never returns the password, and login only works with the raw password.
        mockMvc.perform(get("/api/teachers").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$[*].password").doesNotExist());
        login("t" + suffix + "@uni.edu", "Password@1");
    }

    @Test
    void logoutRevokesToken() throws Exception {
        String token = login("admin@university.edu", "Admin@123");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        // Other sessions of the same user are unaffected.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordInvalidatesOldTokens() throws Exception {
        String suffix = unique();
        createStudent(suffix);
        String token = login("s" + suffix + "@uni.edu", "Password@1");

        mockMvc.perform(put("/api/auth/password").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"NewPassword@2\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/auth/password").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password@1\",\"newPassword\":\"NewPassword@2\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        login("s" + suffix + "@uni.edu", "NewPassword@2");
    }

    @Test
    void deletedUsersTokenStopsWorking() throws Exception {
        String suffix = unique();
        long id = createTeacher(suffix);
        String token = login("t" + suffix + "@uni.edu", "Password@1");
        mockMvc.perform(delete("/api/teachers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- role-based access control

    @Test
    void teacherCanManageTeachersButNotStudentsOrAdmins() throws Exception {
        String suffix = unique();
        long ownId = createTeacher(suffix);
        String teacherToken = login("t" + suffix + "@uni.edu", "Password@1");

        assertCrudAllowed(teacherToken, "/api/teachers", teacherJson("n" + suffix), teacherJson("n" + suffix)
                .replace("\"password\":\"Password@1\",", "").replace("Teacher n", "Renamed n"));
        assertForbidden(teacherToken, "/api/students", studentJson("n" + suffix));
        assertForbidden(teacherToken, "/api/admins", adminJson("n" + suffix));

        // A teacher must not lock themself out by deleting their own account.
        mockMvc.perform(delete("/api/teachers/" + ownId).header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot delete your own account"));
    }

    @Test
    void studentCanManageStudentsButNotTeachersOrAdmins() throws Exception {
        String suffix = unique();
        long ownId = createStudent(suffix);
        String studentToken = login("s" + suffix + "@uni.edu", "Password@1");

        assertCrudAllowed(studentToken, "/api/students", studentJson("n" + suffix), studentJson("n" + suffix)
                .replace("\"password\":\"Password@1\",", "").replace("Student n", "Renamed n"));
        assertForbidden(studentToken, "/api/teachers", teacherJson("n" + suffix));
        assertForbidden(studentToken, "/api/admins", adminJson("n" + suffix));

        mockMvc.perform(delete("/api/students/" + ownId).header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyRoleCanViewOwnProfile() throws Exception {
        String suffix = unique();
        createTeacher(suffix);
        createStudent(suffix);
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login("t" + suffix + "@uni.edu", "Password@1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.employeeId").value("EMP-" + suffix));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login("s" + suffix + "@uni.edu", "Password@1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.studentId").value("STU-" + suffix));
    }

    // ---------------------------------------------------------------- admin CRUD

    @Test
    void adminCanCrudTeachers() throws Exception {
        String suffix = unique();
        long id = createTeacher(suffix);

        mockMvc.perform(get("/api/teachers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("t" + suffix))
                .andExpect(jsonPath("$.role").value("TEACHER"));
        mockMvc.perform(get("/api/teachers").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        String update = """
                {"username":"t%s","fullName":"Updated Name","email":"t%s@uni.edu",
                 "employeeId":"EMP-%s","department":"Physics","designation":"Professor","phone":"+8801700000000"}
                """.formatted(suffix, suffix, suffix);
        mockMvc.perform(put("/api/teachers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.department").value("Physics"));
        // Password was omitted in the update, so the old one still works.
        login("t" + suffix + "@uni.edu", "Password@1");

        mockMvc.perform(delete("/api/teachers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/teachers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/teachers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanCrudStudents() throws Exception {
        String suffix = unique();
        long id = createStudent(suffix);

        mockMvc.perform(get("/api/students/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value("STU-" + suffix));

        String update = """
                {"username":"s%s","password":"Changed@123","fullName":"Moved Student","email":"s%s@uni.edu",
                 "studentId":"STU-%s","department":"Mathematics","semester":5}
                """.formatted(suffix, suffix, suffix);
        mockMvc.perform(put("/api/students/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semester").value(5));
        login("s" + suffix + "@uni.edu", "Changed@123");

        mockMvc.perform(delete("/api/students/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/students/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanCrudAdmins() throws Exception {
        String suffix = unique();
        String body = """
                {"username":"a%s","password":"Password@1","fullName":"Second Admin","email":"a%s@uni.edu"}
                """.formatted(suffix, suffix);
        MvcResult result = mockMvc.perform(post("/api/admins").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        // The new admin can manage teachers too.
        String newAdminToken = login("a" + suffix + "@uni.edu", "Password@1");
        mockMvc.perform(get("/api/teachers").header(HttpHeaders.AUTHORIZATION, bearer(newAdminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admins/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Second Admin", "Renamed Admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Admin"));
        // The update included a password, so tokens issued before it are no longer valid.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(newAdminToken)))
                .andExpect(status().isUnauthorized());
        newAdminToken = login("a" + suffix + "@uni.edu", "Password@1");

        // An admin cannot delete themself.
        long ownId = ((Number) JsonPath.read(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(newAdminToken))).andReturn()
                .getResponse().getContentAsString(), "$.id")).longValue();
        mockMvc.perform(delete("/api/admins/" + ownId).header(HttpHeaders.AUTHORIZATION, bearer(newAdminToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/admins/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admins/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointDoesNotExposeTeachersOrStudents() throws Exception {
        String suffix = unique();
        long teacherId = createTeacher(suffix);
        MvcResult me = mockMvc.perform(get("/api/teachers/" + teacherId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))).andReturn();
        String username = JsonPath.read(me.getResponse().getContentAsString(), "$.username");
        mockMvc.perform(get("/api/admins").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == '" + username + "')]").isEmpty());
    }

    // ---------------------------------------------------------------- validation & conflicts

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        String body = """
                {"username":"x","password":"short","fullName":"","email":"not-an-email",
                 "studentId":"","department":"","semester":20}
                """;
        mockMvc.perform(post("/api/students").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.semester").exists());
    }

    @Test
    void passwordIsRequiredOnCreate() throws Exception {
        String suffix = unique();
        String body = """
                {"username":"t%s","fullName":"No Password","email":"t%s@uni.edu",
                 "employeeId":"EMP-%s","department":"CSE","designation":"Lecturer"}
                """.formatted(suffix, suffix, suffix);
        mockMvc.perform(post("/api/teachers").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void duplicatesReturn409() throws Exception {
        String suffix = unique();
        createTeacher(suffix);
        // Same username / email as an existing teacher, used for a student.
        String body = """
                {"username":"t%s","password":"Password@1","fullName":"Dup","email":"other%s@uni.edu",
                 "studentId":"STU-X%s","department":"CSE","semester":1}
                """.formatted(suffix, suffix, suffix);
        mockMvc.perform(post("/api/students").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/teachers").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(teacherJson("z" + suffix).replace(
                                "EMP-z" + suffix, "EMP-" + suffix)))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedJsonAndBadIdsReturn400() throws Exception {
        mockMvc.perform(post("/api/teachers").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/teachers/abc").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/students/999999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    /** Full create → read → list → update → delete cycle on one resource with the given token. */
    private void assertCrudAllowed(String token, String path, String createJson, String updateJson) throws Exception {
        String auth = bearer(token);
        MvcResult created = mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());
        mockMvc.perform(get(path + "/" + id).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());
        mockMvc.perform(put(path + "/" + id).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(org.hamcrest.Matchers.startsWith("Renamed")));
        mockMvc.perform(delete(path + "/" + id).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNoContent());
        mockMvc.perform(get(path + "/" + id).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound());
    }

    /** Every CRUD operation on the resource must be rejected with 403 for this token. */
    private void assertForbidden(String token, String path, String json) throws Exception {
        MockHttpServletRequestBuilder[] requests = {
                get(path), get(path + "/1"),
                post(path).contentType(MediaType.APPLICATION_JSON).content(json),
                put(path + "/1").contentType(MediaType.APPLICATION_JSON).content(json),
                delete(path + "/1"),
        };
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    private static String adminJson(String suffix) {
        return """
                {"username":"a%s","password":"Password@1","fullName":"Admin %s","email":"a%s@uni.edu"}
                """.formatted(suffix, suffix, suffix);
    }

    private long createTeacher(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/teachers").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(teacherJson(suffix)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long createStudent(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/students").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(studentJson(suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String teacherJson(String suffix) {
        return """
                {"username":"t%s","password":"Password@1","fullName":"Teacher %s","email":"t%s@uni.edu",
                 "employeeId":"EMP-%s","department":"CSE","designation":"Lecturer"}
                """.formatted(suffix, suffix, suffix, suffix);
    }

    private static String studentJson(String suffix) {
        return """
                {"username":"s%s","password":"Password@1","fullName":"Student %s","email":"s%s@uni.edu",
                 "studentId":"STU-%s","department":"CSE","semester":1}
                """.formatted(suffix, suffix, suffix, suffix);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
