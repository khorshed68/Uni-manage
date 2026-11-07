# University Management System

[![CI](https://github.com/khorshed68/University-management-system/actions/workflows/ci.yml/badge.svg)](https://github.com/khorshed68/University-management-system/actions/workflows/ci.yml)

A web application built with **Java Spring Boot**. It manages three kinds of university users (**Admins, Teachers and Students**) through a secured **REST API**, and adds a small browser interface on top. Access is protected by **Spring Security** with login, logout, BCrypt password hashing and role-based access control (RBAC). The whole system is containerized with **Docker** and runs together with a **PostgreSQL** database using **Docker Compose**.

---

## Table of contents

1. [Project summary](#1-project-summary)
2. [Requirements and how each one is met](#2-requirements-and-how-each-one-is-met)
3. [Technology stack](#3-technology-stack)
4. [System architecture](#4-system-architecture)
5. [Data model](#5-data-model)
6. [Roles and permissions (RBAC)](#6-roles-and-permissions-rbac)
7. [CRUD on every resource, by role](#7-crud-on-every-resource-by-role)
8. [Authentication and authorization design](#8-authentication-and-authorization-design)
9. [REST API reference](#9-rest-api-reference)
10. [Validation and business rules](#10-validation-and-business-rules)
11. [HTTP status codes and error format](#11-http-status-codes-and-error-format)
12. [Web interface](#12-web-interface)
13. [Dockerization](#13-dockerization)
14. [How to run the project](#14-how-to-run-the-project)
15. [Default accounts](#15-default-accounts)
16. [Testing and verification](#16-testing-and-verification)
17. [Project structure](#17-project-structure)
18. [Configuration](#18-configuration)
19. [Limitations and future work](#19-limitations-and-future-work)
20. [Troubleshooting](#20-troubleshooting)

---

## 1. Project summary

| Item | Value |
|---|---|
| Project name | University Management System (UMS) |
| Type | Web application with a REST API and a browser interface |
| Language and framework | Java 21, Spring Boot 4.1.1 |
| User roles | Admin, Teacher, Student |
| Resources managed | Admins, Teachers, Students |
| Security | Spring Security, JWT (Bearer token), BCrypt password hashing, RBAC |
| Database | PostgreSQL 17 (in Docker); H2 in-memory for quick local runs and tests |
| Deployment | Dockerfile and `compose.yaml` (app + database) |
| Application URL | http://localhost:9090 |
| Sign-in | Email address and password |

**Access rule from the assignment brief:** admins manage all three resources, teachers manage only teachers, and students manage only students.

---

## 2. Requirements and how each one is met

| # | Requirement from the brief | How it is implemented | Verified |
|---|---|---|:-:|
| 1 | Spring Boot web application with three roles | Spring Boot 4.1.1. Every account has a role: `ADMIN`, `TEACHER` or `STUDENT` | ✅ |
| 2 | REST API with CRUD for the Admin, Teacher and Student resources | `/api/admins`, `/api/teachers` and `/api/students`, each with Create, Read all, Read one, Update and Delete (15 operations in total) | ✅ |
| 3 | Proper REST conventions | Plural resource nouns, standard HTTP methods (GET, POST, PUT, DELETE), IDs in the path, `Location` header on create, no verbs in URLs | ✅ |
| 4 | JSON responses | Every response is JSON, including errors | ✅ |
| 5 | Proper HTTP status codes | 200, 201, 204, 400, 401, 403, 404, 405, 409 used according to their meaning | ✅ |
| 6 | Login | `POST /api/auth/login` with email and password returns a signed JWT | ✅ |
| 7 | Logout | `POST /api/auth/logout` revokes the token on the server, so it stops working immediately | ✅ |
| 8 | Password hashing | Passwords stored only as BCrypt hashes; never returned by the API | ✅ |
| 9 | Role-based access control | URL rules in `SecurityConfig` plus `@PreAuthorize` on every controller | ✅ |
| 10 | Admin manages all three resources | Admin has full CRUD on admins, teachers and students | ✅ |
| 11 | Teachers manage only teachers | Teacher has full CRUD on teachers; **403 Forbidden** on admins and students | ✅ |
| 12 | Students manage only students | Student has full CRUD on students; **403 Forbidden** on admins and teachers | ✅ |
| 13 | Dockerfile | Multi-stage build: a JDK image compiles, a smaller JRE image runs as a non-root user | ✅ |
| 14 | `compose.yaml` running the services together | `app` (Spring Boot) and `db` (PostgreSQL 17) with a health check, startup order and a persistent volume | ✅ |

---

## 3. Technology stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 21 | Application code |
| Framework | Spring Boot 4.1.1 | Web server, dependency injection, auto-configuration |
| Web | Spring Web MVC (embedded Tomcat 11) | REST controllers and JSON |
| Security | Spring Security 7 + OAuth2 Resource Server (JWT) | Login, token validation, RBAC |
| Password hashing | BCrypt (`BCryptPasswordEncoder`, strength 10) | One-way password hashes |
| Persistence | Spring Data JPA + Hibernate 7.4 | Database access and table mapping |
| Database | PostgreSQL 17 (Docker), H2 (local and tests) | Data storage |
| Validation | Jakarta Bean Validation | Checks request bodies |
| JSON | Jackson 3 | Converts Java objects to and from JSON |
| Health check | Spring Boot Actuator | `GET /actuator/health` |
| Build | Maven (through the `mvnw` wrapper) | Compile, test, package |
| Containers | Docker, Docker Compose | Packaging and running the app with its database |
| Testing | JUnit 5, Spring Boot Test, MockMvc | Automated integration tests |
| API testing | Postman collection (45 requests) | Manual and automated API checks |
| Front end | Plain HTML, CSS and JavaScript | Simple browser interface that calls the REST API |

---

## 4. System architecture

The application uses a classic **layered architecture**. Each layer has one job and only talks to the layer below it.

```
 Browser UI / Postman / curl
            │  HTTP + JSON  (Authorization: Bearer <JWT>)
            ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Spring Security filter chain                                 │
 │  • validates the JWT (signature, expiry, revoked?, user ok?) │
 │  • checks the role against the URL rules  → 401 / 403        │
 └──────────────────────────────────────────────────────────────┘
            ▼
 Controller layer   (REST API: URLs, HTTP methods, status codes, @PreAuthorize)
            ▼
 Service layer      (business rules: duplicates, self-delete, password hashing)
            ▼
 Repository layer   (Spring Data JPA)
            ▼
 Database           (PostgreSQL: user_accounts, teachers, students, revoked_tokens)
```

A **GlobalExceptionHandler** sits beside the controllers. It turns every error (not found, duplicate, validation failure and so on) into a JSON response with the correct status code.

### Request flow example: a teacher creates a teacher
1. The client sends `POST /api/teachers` with `Authorization: Bearer <token>` and a JSON body.
2. Spring Security decodes the JWT and checks its signature, expiry, issuer, whether it was logged out, whether the user still exists, and whether the password changed since it was issued.
3. The role claim (`TEACHER`) is checked against the URL rule for `/api/teachers/**` (ADMIN or TEACHER). The request is allowed.
4. `TeacherController` validates the JSON body (400 if invalid) and `@PreAuthorize` checks the role again.
5. `TeacherService` checks for a duplicate username, email or employee ID (409 if found), hashes the password with BCrypt and saves the record.
6. The response is **201 Created** with a `Location` header and the new teacher as JSON.

---

## 5. Data model

### Tables

**`user_accounts`**: one row per login account, for every role.

| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | Auto-generated |
| `username` | varchar(50) | Unique |
| `password` | varchar(100) | **BCrypt hash**, never plain text |
| `full_name` | varchar(100) | Required |
| `email` | varchar(120) | Unique, stored in lower case, used to sign in |
| `role` | varchar(20) | `ADMIN`, `TEACHER` or `STUDENT` |
| `token_version` | int | Increases on every password change; older tokens are rejected |
| `created_at`, `updated_at` | timestamp | Set automatically |

**`teachers`**: profile data for teacher accounts.

| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | This is the ID used in `/api/teachers/{id}` |
| `user_id` | bigint, FK → `user_accounts.id` | One-to-one, unique |
| `employee_id` | varchar(30) | Unique |
| `department` | varchar(100) | Required |
| `designation` | varchar(60) | Required |
| `phone` | varchar(20) | Optional |
| `created_at`, `updated_at` | timestamp | Set automatically |

**`students`**: profile data for student accounts.

| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | This is the ID used in `/api/students/{id}` |
| `user_id` | bigint, FK → `user_accounts.id` | One-to-one, unique |
| `student_id` | varchar(30) | Unique |
| `department` | varchar(100) | Required |
| `semester` | int | 1 to 12 |
| `phone` | varchar(20) | Optional |
| `created_at`, `updated_at` | timestamp | Set automatically |

**`revoked_tokens`**: tokens that were logged out before they expired.

| Column | Type | Notes |
|---|---|---|
| `jti` | varchar(64), PK | The token's unique ID |
| `expires_at` | timestamp | Rows are purged automatically every hour once expired |

### Relationships
```
user_accounts (role = ADMIN)                      ← an admin is just an account
user_accounts (role = TEACHER) 1 ──── 1 teachers   ← account + teacher profile
user_accounts (role = STUDENT) 1 ──── 1 students   ← account + student profile
```
Deleting a teacher or student also deletes its login account (cascade), so that person can no longer sign in, and any token they hold stops working.

---

## 6. Roles and permissions (RBAC)

### Permission matrix

| Endpoint | ADMIN | TEACHER | STUDENT | No token |
|---|:-:|:-:|:-:|:-:|
| `POST /api/auth/login` | ✅ | ✅ | ✅ | ✅ (public) |
| `GET /api/auth/me` (own profile) | ✅ | ✅ | ✅ | 401 |
| `PUT /api/auth/password` (own password) | ✅ | ✅ | ✅ | 401 |
| `POST /api/auth/logout` | ✅ | ✅ | ✅ | 401 |
| `/api/admins/**` (CRUD) | ✅ | ❌ 403 | ❌ 403 | 401 |
| `/api/teachers/**` (CRUD) | ✅ | ✅ | ❌ 403 | 401 |
| `/api/students/**` (CRUD) | ✅ | ❌ 403 | ✅ | 401 |
| `GET /actuator/health` | ✅ | ✅ | ✅ | ✅ (public) |
| Web UI files (`/`, `/app.js`, `/app.css`) | ✅ | ✅ | ✅ | ✅ (public, contain no data) |

### In words
- **Admin**: full create, read, update and delete on **admins, teachers and students**.
- **Teacher**: full create, read, update and delete on **teachers only**. Any request to `/api/students` or `/api/admins` returns **403 Forbidden**.
- **Student**: full create, read, update and delete on **students only**. Any request to `/api/teachers` or `/api/admins` returns **403 Forbidden**.
- **Everyone** who is logged in can view their own profile, change their own password and log out.
- **Nobody** can delete their own account (400). Because of this rule there is always at least one admin left, so the system can never be locked out.

### Where the rules are enforced (defense in depth)
The rules are checked **twice**, so one mistake cannot open a hole:

1. **URL rules** in [`SecurityConfig.java`](src/main/java/com/university/ums/config/SecurityConfig.java):
   ```java
   .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
   .requestMatchers("/api/admins/**").hasRole("ADMIN")
   .requestMatchers("/api/teachers/**").hasAnyRole("ADMIN", "TEACHER")
   .requestMatchers("/api/students/**").hasAnyRole("ADMIN", "STUDENT")
   .anyRequest().authenticated()
   ```
2. **Method security** on each controller class:
   - `AdminController`: `@PreAuthorize("hasRole('ADMIN')")`
   - `TeacherController`: `@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")`
   - `StudentController`: `@PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")`

The web interface also hides tabs a role cannot use, but that is only for convenience. **The server is what enforces security**, and it rejects forbidden requests even when they are sent directly (for example from Postman or the browser console).

---

## 7. CRUD on every resource, by role

This section lists every combination of **role × resource × operation** and the result. All 45 combinations were tested against the running Docker application, and all behaved as shown.

### 7.1 Admin

| Resource | Create `POST` | Read all `GET` | Read one `GET /{id}` | Update `PUT /{id}` | Delete `DELETE /{id}` |
|---|:-:|:-:|:-:|:-:|:-:|
| Admins `/api/admins` | ✅ 201 | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 204 |
| Teachers `/api/teachers` | ✅ 201 | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 204 |
| Students `/api/students` | ✅ 201 | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 204 |

Special case for admins: an admin cannot delete **their own** admin account → **400 Bad Request** ("You cannot delete your own admin account"). They can delete other admins. This guarantees at least one admin always remains. (The service also has a "last remaining admin" check returning 409 as an extra safeguard, but it cannot normally be reached because of the self-delete rule.)

### 7.2 Teacher

| Resource | Create | Read all | Read one | Update | Delete |
|---|:-:|:-:|:-:|:-:|:-:|
| Admins `/api/admins` | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 |
| Teachers `/api/teachers` | ✅ 201 | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 204 |
| Students `/api/students` | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 |

Special case: a teacher cannot delete **their own** teacher record → **400 Bad Request**. They can delete other teachers.

### 7.3 Student

| Resource | Create | Read all | Read one | Update | Delete |
|---|:-:|:-:|:-:|:-:|:-:|
| Admins `/api/admins` | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 |
| Teachers `/api/teachers` | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 |
| Students `/api/students` | ✅ 201 | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 204 |

Special case: a student cannot delete **their own** student record → **400 Bad Request**. They can delete other students.

### 7.4 No token (not logged in)

Every request to `/api/admins`, `/api/teachers`, `/api/students`, `/api/auth/me`, `/api/auth/password` and `/api/auth/logout` returns **401 Unauthorized**. Only login, the health check and the web UI files are public.

### 7.5 Example walkthroughs

**Admin creates a student**
```http
POST /api/students
Authorization: Bearer <admin token>
Content-Type: application/json

{"username":"mitu","password":"Mitu@12345","fullName":"Mitu Akter","email":"mitu@university.edu",
 "studentId":"STU-310","department":"Mathematics","semester":3}
```
Response **201 Created**, header `Location: http://localhost:9090/api/students/36`:
```json
{
  "id": 36,
  "username": "mitu",
  "fullName": "Mitu Akter",
  "email": "mitu@university.edu",
  "role": "STUDENT",
  "studentId": "STU-310",
  "department": "Mathematics",
  "semester": 3,
  "phone": null,
  "createdAt": "2026-10-24T09:51:43.241857Z",
  "updatedAt": "2026-10-24T09:51:43.241870Z"
}
```

**Teacher tries to list students**
```http
GET /api/students
Authorization: Bearer <teacher token>
```
Response **403 Forbidden**:
```json
{
  "timestamp": "2026-10-24T09:51:43.268742939Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource",
  "path": "/api/students"
}
```

**Teacher tries to delete their own record**

Response **400 Bad Request**:
```json
{
  "timestamp": "2026-10-24T09:51:43.560655007Z",
  "status": 400,
  "error": "Bad Request",
  "message": "You cannot delete your own account",
  "path": "/api/teachers/1"
}
```

---

## 8. Authentication and authorization design

### 8.1 Login (authentication)
1. The client sends `POST /api/auth/login` with `{"email": "...", "password": "..."}`.
2. Spring Security's `AuthenticationManager` loads the account by **email** (case-insensitive) through `DatabaseUserDetailsService`.
3. The submitted password is checked against the stored **BCrypt hash**.
4. On success, `JwtService` issues a **JWT** signed with **HMAC-SHA256** and valid for **1 hour** (configurable).
5. On failure, the response is **401** with the message "Invalid email or password". It deliberately does not say whether the email exists.

Login response (**200 OK**):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-10-24T10:51:42Z",
  "username": "admin",
  "role": "ADMIN"
}
```

### 8.2 The JWT
The token is sent on every request as `Authorization: Bearer <token>`. It contains these claims:

| Claim | Meaning |
|---|---|
| `sub` | Username of the account |
| `role` | `ADMIN`, `TEACHER` or `STUDENT`, mapped to the Spring authority `ROLE_<role>` |
| `jti` | Unique token ID, used for logout |
| `ver` | The account's token version at login time |
| `iss` | `university-management-system` |
| `iat`, `exp` | Issued-at and expiry times |

### 8.3 Token validation on every request
Spring Security's resource-server support decodes the token, then the custom `ActiveTokenValidator` adds checks. A token is rejected with **401** if any of these fails:

| Check | Protects against |
|---|---|
| Signature is valid (HMAC-SHA256 with the server secret) | Forged or tampered tokens |
| Not expired, issuer matches | Old or foreign tokens |
| `jti` is not in `revoked_tokens` | Using a token after logout |
| The user still exists | A deleted user continuing to act |
| The role in the token matches the database | Stale role information |
| `ver` equals the user's current `token_version` | Old tokens after a password change |

### 8.4 Logout
`POST /api/auth/logout` stores the token's `jti` in the `revoked_tokens` table. That exact token is rejected from then on, even though it has not expired yet. Other devices of the same user stay signed in. Expired entries are purged automatically every hour.

### 8.5 Password hashing
- Passwords are hashed with **BCrypt** (`BCryptPasswordEncoder`, strength 10, random salt per password) before being stored.
- Stored values look like `$2a$10$...`. Plain passwords are never stored or logged.
- No API response ever includes a password field.
- Changing a password, whether by the user through `PUT /api/auth/password` or by an authorized user through `PUT /api/{resource}/{id}`, increases `token_version`. **All tokens issued earlier stop working.**

### 8.6 Authorization (RBAC)
After authentication, the `role` claim becomes the authority `ROLE_ADMIN`, `ROLE_TEACHER` or `ROLE_STUDENT`. URL rules and `@PreAuthorize` then decide access (see [section 6](#6-roles-and-permissions-rbac)). If the role is not allowed, the response is **403 Forbidden** in JSON.

### 8.7 Other security decisions
- **Stateless**: no server session and no cookies. Each request carries its own token.
- **CSRF protection is disabled** because there are no cookies to abuse; this is standard for bearer-token APIs.
- **Form login and HTTP Basic are disabled**; only the JSON login endpoint exists.
- **JSON 401/403 responses**: `JsonSecurityErrorHandler` returns JSON errors instead of HTML pages, and adds a `WWW-Authenticate: Bearer` header on 401.
- **Secrets from the environment**: the JWT secret, database password and first admin password come from environment variables (`.env`).
- **Non-root container**: the Docker image runs the app as an unprivileged `spring` user.
- **First admin**: there is no public sign-up. The first admin is created on startup from environment variables.

---

## 9. REST API reference

Base URL: `http://localhost:9090`

All requests and responses use `Content-Type: application/json`. Every endpoint except login needs `Authorization: Bearer <token>`.

### 9.1 Authentication endpoints (`AuthController`)

| Method | URL | Who | Body | Success |
|---|---|---|---|---|
| `POST` | `/api/auth/login` | Anyone | `{"email","password"}` | 200 + token |
| `POST` | `/api/auth/logout` | Logged in | none | 200 `{"message":"Logged out successfully"}` |
| `GET` | `/api/auth/me` | Logged in | none | 200 + own profile |
| `PUT` | `/api/auth/password` | Logged in | `{"currentPassword","newPassword"}` | 200 `{"message":"Password changed. Please log in again with the new password."}` |

`GET /api/auth/me` returns a different shape depending on the role. A student, for example, gets:
```json
{
  "id": 1,
  "username": "student1",
  "fullName": "Demo Student",
  "email": "student1@university.edu",
  "role": "STUDENT",
  "studentId": "STU-001",
  "department": "Computer Science",
  "semester": 1,
  "phone": null,
  "createdAt": "2026-10-16T12:03:25.841490Z",
  "updatedAt": "2026-10-16T12:03:25.841511Z"
}
```

### 9.2 Admin endpoints (`AdminController`, ADMIN only)

| Method | URL | Action | Success |
|---|---|---|---|
| `POST` | `/api/admins` | Create an admin | 201 + `Location` |
| `GET` | `/api/admins` | List all admins | 200 |
| `GET` | `/api/admins/{id}` | Get one admin | 200 |
| `PUT` | `/api/admins/{id}` | Update an admin | 200 |
| `DELETE` | `/api/admins/{id}` | Delete an admin | 204 |

Request body:
```json
{
  "username": "admin2",
  "password": "Admin2@1234",
  "fullName": "Second Admin",
  "email": "admin2@university.edu"
}
```
Response fields: `id, username, fullName, email, role, createdAt, updatedAt`.

### 9.3 Teacher endpoints (`TeacherController`, ADMIN or TEACHER)

| Method | URL | Action | Success |
|---|---|---|---|
| `POST` | `/api/teachers` | Create a teacher | 201 + `Location` |
| `GET` | `/api/teachers` | List all teachers | 200 |
| `GET` | `/api/teachers/{id}` | Get one teacher | 200 |
| `PUT` | `/api/teachers/{id}` | Update a teacher | 200 |
| `DELETE` | `/api/teachers/{id}` | Delete a teacher | 204 |

Request body:
```json
{
  "username": "rafiq",
  "password": "Rafiq@1234",
  "fullName": "Rafiq Ahmed",
  "email": "rafiq@university.edu",
  "employeeId": "EMP-310",
  "department": "Mathematics",
  "designation": "Lecturer",
  "phone": "+8801712345678"
}
```
Response (**201 Created**, `Location: http://localhost:9090/api/teachers/35`):
```json
{
  "id": 35,
  "username": "rafiq",
  "fullName": "Rafiq Ahmed",
  "email": "rafiq@university.edu",
  "role": "TEACHER",
  "employeeId": "EMP-310",
  "department": "Mathematics",
  "designation": "Lecturer",
  "phone": "+8801712345678",
  "createdAt": "2026-10-24T09:51:43.143556Z",
  "updatedAt": "2026-10-24T09:51:43.143567Z"
}
```

### 9.4 Student endpoints (`StudentController`, ADMIN or STUDENT)

| Method | URL | Action | Success |
|---|---|---|---|
| `POST` | `/api/students` | Create a student | 201 + `Location` |
| `GET` | `/api/students` | List all students | 200 |
| `GET` | `/api/students/{id}` | Get one student | 200 |
| `PUT` | `/api/students/{id}` | Update a student | 200 |
| `DELETE` | `/api/students/{id}` | Delete a student | 204 |

Request body:
```json
{
  "username": "mitu",
  "password": "Mitu@12345",
  "fullName": "Mitu Akter",
  "email": "mitu@university.edu",
  "studentId": "STU-310",
  "department": "Mathematics",
  "semester": 3,
  "phone": "+8801811000000"
}
```
Response fields: `id, username, fullName, email, role, studentId, department, semester, phone, createdAt, updatedAt`.

### 9.5 Other endpoints

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/actuator/health` | Health check, public. Returns `"status":"UP"` when the app is running |
| `GET` | `/` | The web interface (HTML), public |

### 9.6 REST conventions followed
- **Resources are nouns in the plural**: `/api/admins`, `/api/teachers`, `/api/students`.
- **The HTTP method is the action**: GET reads, POST creates, PUT updates, DELETE deletes. There are no verbs in URLs.
- **The ID is in the path**: `/api/teachers/{id}`.
- **Create returns 201** with a `Location` header pointing to the new resource.
- **Delete returns 204** with no body.
- **The API is stateless**: every request carries its own token.
- **JSON everywhere**, including errors.

---

## 10. Validation and business rules

### Field validation
Invalid input returns **400 Bad Request** with a `fieldErrors` object that names each problem.

| Field | Rule |
|---|---|
| `username` | Required, 3 to 50 characters: letters, digits, `.`, `_` or `-` |
| `password` | **Required on create**, 8 to 72 characters. **Optional on update**: leave it out to keep the current password |
| `fullName` | Required, up to 100 characters |
| `email` | Required, valid email format, up to 120 characters |
| `employeeId` (teacher) | Required, up to 30 characters |
| `department` | Required, up to 100 characters |
| `designation` (teacher) | Required, up to 60 characters |
| `studentId` (student) | Required, up to 30 characters |
| `semester` (student) | Required, a number from 1 to 12 |
| `phone` | Optional; 6 to 20 digits, may start with `+` and contain spaces or dashes |

Example: **400 Bad Request**
```json
{
  "timestamp": "2026-10-24T09:51:43.307416913Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/students",
  "fieldErrors": {
    "studentId": "must not be blank",
    "email": "must be a well-formed email address",
    "semester": "must be less than or equal to 12",
    "fullName": "must not be blank",
    "username": "must be 3-50 characters: letters, digits, '.', '_' or '-'",
    "password": "size must be between 8 and 72",
    "department": "must not be blank"
  }
}
```

### Business rules
| Rule | Result if broken |
|---|---|
| Username must be unique across all accounts (case-insensitive) | 409 Conflict |
| Email must be unique across all accounts (case-insensitive; stored in lower case) | 409 Conflict |
| Employee ID must be unique among teachers | 409 Conflict |
| Student ID must be unique among students | 409 Conflict |
| You cannot delete your own account (any role) | 400 Bad Request |
| The last remaining admin cannot be deleted (extra safeguard; normally prevented by the self-delete rule) | 409 Conflict |
| Changing your password needs the correct current password | 400 Bad Request |
| The new password must differ from the current one | 400 Bad Request |
| Text values are trimmed; emails are converted to lower case | (automatic) |

Example: **409 Conflict**
```json
{
  "timestamp": "2026-10-24T09:51:43.332558016Z",
  "status": 409,
  "error": "Conflict",
  "message": "Username 'teacher1' is already taken",
  "path": "/api/teachers"
}
```

---

## 11. HTTP status codes and error format

| Code | Meaning in this API |
|---|---|
| **200 OK** | Successful GET, PUT, login, logout or password change |
| **201 Created** | Successful POST; the `Location` header has the new resource URL |
| **204 No Content** | Successful DELETE |
| **400 Bad Request** | Validation failed, malformed JSON, invalid ID format, wrong current password, or deleting your own account |
| **401 Unauthorized** | Missing, invalid, expired, tampered or logged-out token, or wrong email or password at login |
| **403 Forbidden** | Logged in, but your role may not use this resource |
| **404 Not Found** | The ID does not exist, or the URL does not exist |
| **405 Method Not Allowed** | HTTP method not supported on that URL (for example PATCH) |
| **409 Conflict** | Duplicate username, email, employee ID or student ID |
| **415 Unsupported Media Type** | Body not sent as `application/json` |
| **500 Internal Server Error** | Unexpected error. Details are logged on the server, not shown to the client |

### Uniform error body
Every error has the same JSON shape (built by `GlobalExceptionHandler` and `JsonSecurityErrorHandler`):
```json
{
  "timestamp": "2026-10-24T09:51:43.351399834Z",
  "status": 404,
  "error": "Not Found",
  "message": "Teacher with id 9999 not found",
  "path": "/api/teachers/9999"
}
```
Validation errors add a `fieldErrors` object (see [section 10](#10-validation-and-business-rules)).

More real examples:

No token (**401**):
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required: send 'Authorization: Bearer <token>' (get one from POST /api/auth/login)",
  "path": "/api/teachers"
}
```

Token used after logout (**401**):
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired token: An error occurred while attempting to decode the Jwt: Token has been revoked (logged out)",
  "path": "/api/auth/me"
}
```

Wrong password at login (**401**):
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/api/auth/login"
}
```

---

## 12. Web interface

Open **http://localhost:9090** and sign in with an **email and password**.

| Role | What they see |
|---|---|
| Admin | **Teachers**, **Students** and **Admins** tabs, each with a table, search box, **+ Add**, **Edit** and **Delete**; plus **My account** |
| Teacher | **Teachers** tab (full add, edit, delete) and **My account** |
| Student | **Students** tab (full add, edit, delete) and **My account** |

- **My account** shows your own profile and has a form to change your password.
- Your own row is marked **You** and has no Delete button.
- After **Log out**, or when a token expires, you return to the sign-in page.
- The interface is a **single-page app**: the address bar always shows `http://localhost:9090/`. Every click calls the REST API in the background (for example, the Teachers tab calls `GET /api/teachers`). You can watch these calls in the browser's DevTools under **Network → Fetch/XHR** (press F12).
- It is plain HTML, CSS and JavaScript in [`src/main/resources/static`](src/main/resources/static). The token is kept in `sessionStorage`, which is cleared when the tab closes.
- All user data is inserted with `textContent`, never `innerHTML`, so stored names cannot inject script into the page (XSS protection).
- The interface hides tabs a role cannot use, but **security is enforced by the server**.

---

## 13. Dockerization

### 13.1 Dockerfile (multi-stage build)
```dockerfile
# Stage 1: build the jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Stage 2: minimal runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
COPY --from=build /workspace/target/ums.jar app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

| Design choice | Why |
|---|---|
| **Two stages** | The large JDK and Maven are only used to build. The final image contains just the JRE and the jar, so it is smaller and has less attack surface |
| **Dependencies downloaded before the source is copied** | Docker caches that layer, so rebuilding after a code change is fast |
| **`sed` on `mvnw`** | Removes Windows line endings so the wrapper runs inside Linux |
| **Non-root `spring` user** | If the app were compromised, the attacker would not be root in the container |
| **`MaxRAMPercentage=75`** | The JVM respects the container's memory limit |

### 13.2 compose.yaml
```yaml
services:
  db:
    image: postgres:17-alpine
    container_name: ums-db
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-ums}
      POSTGRES_USER: ${POSTGRES_USER:-ums}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-ums_password}
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]

  app:
    build: .
    container_name: ums-app
    depends_on:
      db:
        condition: service_healthy
    ports:
      - "${APP_PORT:-9090}:8080"
    environment:
      SERVER_PORT: 8080
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB:-ums}
      JWT_SECRET: ${JWT_SECRET:-...}
      ADMIN_EMAIL: ${ADMIN_EMAIL:-admin@university.edu}
      # ...more settings, see compose.yaml

volumes:
  db-data:
```
(Shortened; see [`compose.yaml`](compose.yaml) for the full file.)

| Feature | Purpose |
|---|---|
| **Two services** (`db`, `app`) | The database and the application run in separate containers on a shared network |
| **`depends_on` + `service_healthy`** | The app starts only after PostgreSQL passes its health check (`pg_isready`) |
| **Service name as host** | The app reaches the database at `db:5432` |
| **Named volume `db-data`** | Data survives `docker compose down` and restarts |
| **Port mapping `9090:8080`** | The app listens on 8080 inside the container and is reached at `localhost:9090` on the computer |
| **Environment variables / `.env`** | Secrets and settings are not hard-coded; copy `.env.example` to `.env` to change them |
| **The database port is not published** | PostgreSQL is reachable only from the app container, not from outside |
| **`restart: unless-stopped`** | Containers restart automatically when Docker Desktop starts |

### 13.3 What happens on first start
1. Compose builds the app image and pulls `postgres:17-alpine`.
2. PostgreSQL starts and becomes healthy.
3. The app starts, Hibernate creates the tables, and `DataInitializer` creates the first admin (plus a demo teacher and demo student if `SEED_DEMO_DATA=true`).
4. The app is ready at http://localhost:9090.

---

## 14. How to run the project

### Option A: Docker Compose (recommended; uses PostgreSQL)
Requirements: Docker Desktop installed and running ("Engine running").

```bash
docker compose up --build -d
```
Open http://localhost:9090.

| Task | Command |
|---|---|
| Stop (keeps data) | `docker compose down` |
| Stop and delete all data | `docker compose down -v` |
| See app logs | `docker compose logs -f app` (Ctrl + C to stop watching) |
| See container status | `docker compose ps` |
| Rebuild after code changes | `docker compose up --build -d` |

Optional: copy `.env.example` to `.env` and change the passwords and `JWT_SECRET` first.

### Option B: Run without Docker (H2 in-memory database)
Requirements: JDK 21 or newer. Maven is not needed; the `mvnw` wrapper downloads it.

Windows:
```bash
.\mvnw.cmd spring-boot:run
```
macOS / Linux:
```bash
./mvnw spring-boot:run
```
Open http://localhost:9090. Data is lost when the app stops. Do not run this while the Docker version is running, because both use port 9090.

### Run the automated tests
```bash
.\mvnw.cmd test
```
Expected result: `Tests run: 31, Failures: 0, Errors: 0` and `BUILD SUCCESS` (6 unit tests + 25 integration tests).

---

## 15. Default accounts

On first start an admin account is created. With `SEED_DEMO_DATA=true` (the default), a demo teacher and a demo student are created too.

| Role | Email (used to sign in) | Password | Username |
|---|---|---|---|
| Admin | `admin@university.edu` | `Admin@123` | `admin` |
| Teacher | `teacher1@university.edu` | `Teacher@123` | `teacher1` |
| Student | `student1@university.edu` | `Student@123` | `student1` |

- Users sign in with their **email**, not their username. Email is not case-sensitive.
- These are demo credentials. Change them in `.env` for any real use.

---

## 16. Testing and verification

**31 automated tests, all passing:** 6 unit tests and 25 integration tests. They run on every push through **GitHub Actions** (see [16.4](#164-continuous-integration-github-actions)).

| Kind | What it means | Count | Location |
|---|---|:-:|---|
| **Unit tests** | One class tested alone. Its dependencies are mocked with **Mockito**; no Spring context, no database | 6 | [`src/test/java/com/university/ums/unit/`](src/test/java/com/university/ums/unit) |
| **Integration tests** | The full application starts (security, controllers, services, JPA, H2 database) and real HTTP requests are sent with **MockMvc** | 3 + 22 | [`src/test/java/com/university/ums/integration/`](src/test/java/com/university/ums/integration) and `UniversityManagementSystemApplicationTests.java` |

### Unit tests (6)
| Test class | Test | What it proves |
|---|---|---|
| `AccountServiceTest` | `newAccountStoresBcryptHashNotPlainPassword` | Passwords are stored as BCrypt hashes; emails are stored in lower case |
| `AccountServiceTest` | `duplicateEmailIsRejected` | A duplicate email throws `ConflictException` (409) |
| `TeacherServiceTest` | `teacherCannotDeleteOwnAccount` | Deleting your own account throws `BadRequestException` (400) and nothing is deleted |
| `TeacherServiceTest` | `unknownTeacherIdThrowsNotFound` | An unknown ID throws `ResourceNotFoundException` (404) |
| `JwtServiceTest` | `issuedTokenContainsUsernameRoleAndExpiry` | The JWT carries the username, role, issuer, a unique ID and a one-hour expiry |
| `ActiveTokenValidatorTest` | `loggedOutTokenIsRejected` | A revoked (logged-out) token fails validation |

### Integration tests: `SecurityFlowIntegrationTest` (3)
| Test | What it proves |
|---|---|
| `adminCanCreateTeacher` | Admin login + create teacher → 201 with a `Location` header; no password in the response |
| `teacherCannotAccessStudents` | Teacher calling `/api/students` → 403 Forbidden (RBAC) |
| `tokenIsRejectedAfterLogout` | After logout the same token → 401 |

### 16.1 More integration tests (22 tests)
File: [`UniversityManagementSystemApplicationTests.java`](src/test/java/com/university/ums/UniversityManagementSystemApplicationTests.java)

The tests start the full application with an H2 database and send real HTTP requests through the real security filter chain (MockMvc), with real logins and real JWTs.

| Test | What it proves |
|---|---|
| `healthEndpointIsPublic` | Health check works without a token |
| `webUiIsPublicButContainsNoData` | The UI files load without a token |
| `protectedEndpointsRequireToken` | Every protected endpoint returns 401 without a token |
| `loginIsByEmailNotUsername` | Login works by email (case-insensitive); a username is rejected |
| `loginWithWrongPasswordReturns401` | Wrong password or unknown email → 401 |
| `loginWithMissingFieldsReturns400` | Empty login body → 400 with field errors |
| `tamperedOrGarbageTokenIsRejected` | Forged or modified tokens → 401 |
| `passwordsAreStoredHashedNotInPlainText` | Passwords never appear in responses; login still works |
| `logoutRevokesToken` | After logout the token → 401; other sessions keep working |
| `changePasswordInvalidatesOldTokens` | Old tokens fail after a password change; the new password works |
| `deletedUsersTokenStopsWorking` | A deleted user's token → 401 |
| `teacherCanManageTeachersButNotStudentsOrAdmins` | Teacher: full CRUD on teachers; 403 on students and admins; cannot delete self |
| `studentCanManageStudentsButNotTeachersOrAdmins` | Student: full CRUD on students; 403 on teachers and admins; cannot delete self |
| `everyRoleCanViewOwnProfile` | `/api/auth/me` returns the correct profile for each role |
| `adminCanCrudTeachers` | Admin: create, read, list, update, delete teachers; 404 after delete |
| `adminCanCrudStudents` | Admin: full CRUD on students |
| `adminCanCrudAdmins` | Admin: full CRUD on admins; cannot delete self |
| `adminEndpointDoesNotExposeTeachersOrStudents` | `/api/admins` lists only admins |
| `invalidBodyReturns400WithFieldErrors` | Invalid data → 400 with a message per field |
| `passwordIsRequiredOnCreate` | Creating without a password → 400 |
| `duplicatesReturn409` | Duplicate username, email or ID → 409 |
| `malformedJsonAndBadIdsReturn400` | Broken JSON and non-numeric IDs → 400; unknown ID → 404 |

### 16.2 Live verification against Docker
Every **role × resource × operation** combination (3 × 3 × 5 = 45) was tested against the running Docker app with PostgreSQL:

| Role | Admins | Teachers | Students |
|---|:-:|:-:|:-:|
| Admin | 201 / 200 / 200 / 200 / 204 | 201 / 200 / 200 / 200 / 204 | 201 / 200 / 200 / 200 / 204 |
| Teacher | 403 × 5 | 201 / 200 / 200 / 200 / 204 | 403 × 5 |
| Student | 403 × 5 | 403 × 5 | 201 / 200 / 200 / 200 / 204 |

(Order: Create / Read all / Read one / Update / Delete.)

Also verified live: no token → 401, wrong password → 401, unknown ID → 404, invalid body → 400, duplicate → 409, wrong HTTP method → 405, `Location` header on create, logout → 200 and the reused token → 401, and passwords never returned. In the PostgreSQL database, every stored password is a BCrypt hash (`$2a$10$...`).

### 16.3 Postman collection (45 requests, all passing)
File: [`postman/University-Management-System.postman_collection.json`](postman/University-Management-System.postman_collection.json)

| Folder | Contents |
|---|---|
| 1. Auth | Log in as admin, teacher and student (tokens saved automatically); wrong password (401); own profile per role; no token (401) |
| 2. Admin - Teachers CRUD | Create, list, get, update, duplicate (409), invalid (400), delete (204), get deleted (404) |
| 3. Admin - Students CRUD | Create, list, get, update, delete |
| 4. Admin - Admins CRUD | Create, list, get, update, delete |
| 5. RBAC - Teacher manages teachers only | Teacher CRUD on teachers (200/201/204); 403 on students and admins |
| 6. RBAC - Student manages students only | Student CRUD on students (200/201/204); 403 on teachers and admins |
| 7. Logout | Log out (200), then the same token is rejected (401) |

Each request has a test script that checks its expected status code. Created usernames and IDs get a unique suffix on each run, so the collection can be run repeatedly.

How to use it: Postman → **Import** → choose the file → open **1. Auth → Login as admin** → **Send**. To run everything: **⋯** on the collection → **Run**.

### 16.4 Continuous integration (GitHub Actions)
File: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

On every **push** and **pull request** to `main`, GitHub Actions:
1. Checks out the code on a fresh Ubuntu machine
2. Installs **Java 21** (Temurin), with Maven dependencies cached between runs
3. Runs `./mvnw -B test`: all 6 unit tests and all 25 integration tests
4. Uploads the test reports (`target/surefire-reports/`) as a downloadable artifact

The integration tests use the built-in H2 database, so the workflow needs no PostgreSQL or Docker.

The result appears in the repository's **Actions** tab and in the **CI** badge at the top of this README: green means every test passed.

---

## 17. Project structure

```
University management system/
├── src/main/java/com/university/ums/
│   ├── UniversityManagementSystemApplication.java   Entry point (@SpringBootApplication, @EnableScheduling)
│   ├── config/
│   │   ├── SecurityConfig.java        Security filter chain, URL role rules, BCrypt, JWT encoder/decoder
│   │   └── DataInitializer.java       Creates the first admin and demo users on startup
│   ├── controller/                    ← THE REST API
│   │   ├── AuthController.java        /api/auth: login, logout, me, password
│   │   ├── AdminController.java       /api/admins: CRUD (ADMIN)
│   │   ├── TeacherController.java     /api/teachers: CRUD (ADMIN, TEACHER)
│   │   └── StudentController.java     /api/students: CRUD (ADMIN, STUDENT)
│   ├── dto/                           JSON request/response shapes and validation rules
│   │   ├── AdminRequest / AdminResponse
│   │   ├── TeacherRequest / TeacherResponse
│   │   ├── StudentRequest / StudentResponse
│   │   ├── LoginRequest / LoginResponse / ChangePasswordRequest / MessageResponse
│   │   ├── ApiError.java              Uniform error body
│   │   ├── OnCreate.java              Validation group (password required only on create)
│   │   └── ValidationRules.java       Username and phone patterns
│   ├── entity/                        Database tables (JPA)
│   │   ├── UserAccount.java, Teacher.java, Student.java, RevokedToken.java
│   │   └── Role.java                  ADMIN, TEACHER, STUDENT
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java   Maps exceptions to JSON + status codes
│   │   └── ResourceNotFoundException (404), ConflictException (409), BadRequestException (400)
│   ├── repository/                    Spring Data JPA repositories
│   ├── security/
│   │   ├── JwtService.java            Issues signed JWTs
│   │   ├── JwtProperties.java         JWT settings (secret, expiry, issuer)
│   │   ├── ActiveTokenValidator.java  Logout / deleted-user / password-change checks
│   │   ├── DatabaseUserDetailsService.java   Loads users by email for login
│   │   └── JsonSecurityErrorHandler.java     JSON 401 / 403 responses
│   └── service/
│       ├── AuthService.java           Login, logout, profile, password change, token purge
│       ├── AccountService.java        Shared account logic: uniqueness checks, BCrypt hashing
│       ├── AdminService.java, TeacherService.java, StudentService.java
├── src/main/resources/
│   ├── application.properties         Configuration (port, database, JWT, first admin)
│   └── static/                        Web interface: index.html, app.js, app.css
├── src/test/java/com/university/ums/
│   ├── unit/                          6 unit tests (Mockito, no Spring context)
│   ├── integration/                   3 integration tests (SecurityFlowIntegrationTest)
│   └── UniversityManagementSystemApplicationTests.java   22 more integration tests
├── .github/workflows/ci.yml           GitHub Actions: runs all tests on every push
├── postman/University-Management-System.postman_collection.json       45 API requests
├── Dockerfile                         Multi-stage image build
├── compose.yaml                       App + PostgreSQL services
├── .env.example                       Template for secrets and settings
├── .dockerignore                      Files excluded from the Docker build
├── pom.xml                            Maven dependencies and build
└── mvnw, mvnw.cmd, .mvn/              Maven wrapper (no Maven install needed)
```

---

## 18. Configuration

All settings can be changed with environment variables (or a `.env` file when using Docker Compose).

| Variable | Default | Purpose |
|---|---|---|
| `APP_PORT` | `9090` | Port on your computer (Docker) |
| `SERVER_PORT` | `9090` locally, `8080` in Docker | Port the app listens on |
| `JWT_SECRET` | development value | Key that signs tokens; at least 32 characters. **Change it** |
| `JWT_EXPIRATION` | `PT1H` | Token lifetime (ISO-8601 duration, e.g. `PT30M`, `PT2H`) |
| `ADMIN_USERNAME` | `admin` | First admin's username |
| `ADMIN_PASSWORD` | `Admin@123` | First admin's password |
| `ADMIN_EMAIL` | `admin@university.edu` | First admin's email (used to sign in) |
| `SEED_DEMO_DATA` | `true` | Create the demo teacher and student |
| `SPRING_DATASOURCE_URL` | H2 in-memory | Database connection (Compose sets PostgreSQL) |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `sa` / empty | Database login |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `ums` / `ums` / `ums_password` | PostgreSQL container settings |

The first admin is only created when the database has **no** admin yet. Changing these values later does not change an existing admin.

---

## 19. Limitations and future work

These are outside the assignment's scope but would be the next steps for a production system:

| Limitation | Possible improvement |
|---|---|
| A teacher can edit any teacher (including resetting their password), and the same for students, because the brief gives each role full management of its own resource | Let teachers and students change only their own password, or add finer ownership rules |
| No limit on failed login attempts | Rate limiting or temporary lockout after repeated failures |
| No refresh token; users sign in again after 1 hour | Add refresh tokens |
| No pagination or filtering on list endpoints | Add `?page=&size=&sort=` and search parameters |
| Tables are created automatically (`ddl-auto=update`) | Use a migration tool such as Flyway |
| Demo passwords and secret have defaults | Require them to be set in production |
| Plain HTTP | Run behind HTTPS (for example a reverse proxy with TLS) |
| Basic web interface | A full front-end framework (React, Angular) with more features |
| No courses, enrolment or grades | Add more university resources (courses, departments, results) |

---

## 20. Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `failed to connect to the docker API ... dockerDesktopLinuxEngine` | Docker Desktop is not running | Open Docker Desktop and wait for "Engine running", then run the command again |
| Docker Desktop shows "An unexpected error occurred" about `docker-secrets-engine/engine.sock` | A stale file left after Windows shut down while Docker was running | Quit Docker Desktop, rename the folder `%LOCALAPPDATA%\docker-secrets-engine` (for example to `docker-secrets-engine.old`), start Docker Desktop again. Do **not** choose "Reset to factory defaults", which deletes your data |
| `ports are not available ... 9090` | Another program (often a local `mvnw` run) is using port 9090 | Stop the other program (Ctrl + C in its terminal), or set a different `APP_PORT` in `.env` |
| `Port 8080 was already in use` | Another server (for example an Oracle Database listener) uses 8080 | The project already uses 9090 to avoid this |
| `docker` is not recognized in VS Code | VS Code was opened before Docker was installed | Close and reopen VS Code |
| Browser shows a 401 JSON message at `/api/...` | Opening an API URL directly without a token | Expected. Use the web interface, Postman or curl with a token |
| Login returns 400 | The body uses `username` instead of `email`, or the email is not valid | Send `{"email": "...", "password": "..."}` |
| Postman "Create teacher" returns 409 | A record with the same username, email or ID already exists | Re-import the latest collection, which uses unique values per run |
| VS Code "Go Live" only shows a file list | Live Server serves static files; it does not run Java | Run the app with Docker or `mvnw.cmd spring-boot:run` and open http://localhost:9090 |
