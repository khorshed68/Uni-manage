# Postman Testing Guide: University Management System

This guide shows how to check **every API request** of the project with Postman, step by step. Follow it from top to bottom the first time.

- **Part 1**: Get ready (start the app, import the collection)
- **Part 2**: Understand how the collection works
- **Part 3**: Run all 45 requests automatically (the fastest check)
- **Part 4**: Check every request one by one (the detailed check, good for a demo)
- **Part 5**: Extra checks you can build yourself
- **Part 6**: How to read a Postman response
- **Part 7**: Troubleshooting
- **Part 8**: A 5-minute demo script for your teacher

---

## Part 1: Get ready

### Step 1.1: Start the application
1. Open **Docker Desktop** and wait until the bottom-left corner says **Engine running**.
2. In VS Code, open the terminal (**Ctrl + `**) in the project folder and run:
   ```
   docker compose up -d
   ```
3. Open http://localhost:9090/actuator/health in a browser. You must see `"status":"UP"`.
   If the page doesn't load, the app isn't running yet; wait 20 seconds and refresh.

### Step 1.2: Import the collection (do this once, and again whenever the file changes)
1. Open **Postman**.
2. If you already have an old **University Management System** collection in the left sidebar, right-click it, choose **Delete**, and confirm. Postman keeps its own copy and does not update it automatically, so an old copy will give wrong results.
3. Press **Ctrl + O** (or click **Import**).
4. Click **files**, go to `E:\University management system\postman\` and choose
   **`University-Management-System.postman_collection.json`**.
5. Click **Import**.
6. In the left sidebar you now see **University Management System** with 7 folders:

| Folder | Requests | What it checks |
|---|:-:|---|
| 1. Auth | 8 | Login for all 3 roles, wrong password, own profile, no token |
| 2. Admin - Teachers CRUD | 8 | Admin: create, list, get, update, duplicate, invalid, delete, get deleted |
| 3. Admin - Students CRUD | 5 | Admin: create, list, get, update, delete |
| 4. Admin - Admins CRUD | 5 | Admin: create, list, get, update, delete |
| 5. RBAC - Teacher manages teachers only | 9 | Teacher: CRUD on teachers allowed; students and admins forbidden |
| 6. RBAC - Student manages students only | 8 | Student: CRUD on students allowed; teachers and admins forbidden |
| 7. Logout | 2 | Logout, then the old token is rejected |
| **Total** | **45** | |

### Step 1.3: Check the login body is the new version
Open **1. Auth → Login as admin → Body**. It must look like this:
```json
{
  "email": "admin@university.edu",
  "password": "Admin@123"
}
```
If it says `"username"` instead of `"email"`, you have the old collection. Go back to Step 1.2.

You **don't** need to choose an environment. Leave **No Environment** selected at the top right.

---

## Part 2: How the collection works

### Collection variables
The collection stores values in **variables** so you never copy and paste tokens or IDs. To see them, click the collection name and open the **Variables** tab.

| Variable | Set by | Used for |
|---|---|---|
| `baseUrl` | Already set to `http://localhost:9090` | The start of every URL: `{{baseUrl}}/api/...` |
| `adminToken` | **Login as admin** | Every admin request |
| `teacherToken` | **Login as teacher1** | Every teacher request |
| `studentToken` | **Login as student1** | Every student request |
| `runId` | **Login as admin** | A random 6-digit number added to new usernames, emails and IDs so each run creates unique records (no 409 on the second run) |
| `teacherId`, `studentId`, `adminId` | The admin "Create ..." requests | The record to get, update and delete next |
| `teacherOwnId`, `studentOwnId` | The teacher/student "creates ..." requests | The record that teacher or student updates and deletes |

### Tokens are added automatically
Each request's **Authorization** tab is set to **Bearer Token** with a variable such as `{{adminToken}}`. Postman then sends this header for you:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....
```

### Every request checks itself
Each request has a small test in its **Scripts → Post-response** tab, for example:
```javascript
pm.test('status 201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('teacherId', pm.response.json().id);
```
After you click **Send**, open the **Test Results** tab at the bottom to see **PASS** or **FAIL**.

### Important order rules
- **Always run "Login as admin" first.** It sets the tokens and `runId` that the other requests need.
- The **Logout** folder logs out **student1's token**. After it, any student request fails with 401 until you send **Login as student1** again.
- Tokens last **1 hour**. If requests suddenly return 401 after a break, run the folder **1. Auth** again.

---

## Part 3: Run all 45 requests automatically (fastest check)

1. In the left sidebar, hover over **University Management System** and click the **⋯** (three dots).
2. Choose **Run** (or "Run collection").
3. In the Runner window, keep **all requests selected** and the order as it is. Iterations: **1**. Delay: **0**.
4. Click **Run University Management System**.
5. Wait a few seconds. You should see:
   - **45 requests**, all with a green **PASS**
   - **Passed: 45, Failed: 0**

If you see any **FAIL**, click it to see what came back, and check [Part 7](#part-7-troubleshooting).

The Runner can be run as many times as you like; each run creates new records with a new `runId` and deletes most of them again.

---

## Part 4: Check every request one by one (detailed check)

For each request: click it in the sidebar, click **Send**, then check the **status code** (top right of the response) and the **response body**. The tables below list what you should see.

### Folder 1: Auth

| # | Request | Method + URL | Token | Expected | What to look for in the response |
|:-:|---|---|---|:-:|---|
| 1 | Login as admin | `POST /api/auth/login` | none | **200** | `accessToken`, `"tokenType": "Bearer"`, `"expiresIn": 3600`, `"role": "ADMIN"` |
| 2 | Login as teacher1 | `POST /api/auth/login` | none | **200** | `"role": "TEACHER"` |
| 3 | Login as student1 | `POST /api/auth/login` | none | **200** | `"role": "STUDENT"` |
| 4 | Login with wrong password (401) | `POST /api/auth/login` | none | **401** | `"message": "Invalid email or password"` |
| 5 | My profile (admin) | `GET /api/auth/me` | admin | **200** | Admin's id, username, fullName, email, `"role": "ADMIN"` |
| 6 | My profile (teacher) | `GET /api/auth/me` | teacher | **200** | Also `employeeId`, `department`, `designation` |
| 7 | My profile (student) | `GET /api/auth/me` | student | **200** | Also `studentId`, `department`, `semester` |
| 8 | No token (401) | `GET /api/teachers` | none | **401** | `"message": "Authentication required: ..."` |

Request bodies used for login:
```json
{ "email": "admin@university.edu",    "password": "Admin@123" }
{ "email": "teacher1@university.edu", "password": "Teacher@123" }
{ "email": "student1@university.edu", "password": "Student@123" }
```

**What this proves:** login works for all three roles, wrong credentials are rejected, and the API cannot be used without a token.

### Folder 2: Admin - Teachers CRUD

| # | Request | Method + URL | Expected | What to look for |
|:-:|---|---|:-:|---|
| 9 | Create teacher | `POST /api/teachers` | **201 Created** | New teacher JSON with an `id`; **Headers** tab has `Location: http://localhost:9090/api/teachers/{id}`; no `password` field in the response |
| 10 | List teachers | `GET /api/teachers` | **200** | A JSON **array** that includes the new teacher |
| 11 | Get teacher | `GET /api/teachers/{{teacherId}}` | **200** | Just that one teacher |
| 12 | Update teacher | `PUT /api/teachers/{{teacherId}}` | **200** | `"designation": "Associate Professor"` (it was "Assistant Professor"); `updatedAt` changed |
| 13 | Create duplicate teacher (409) | `POST /api/teachers` | **409 Conflict** | `"message": "Username '...' is already taken"` |
| 14 | Create invalid teacher (400) | `POST /api/teachers` | **400 Bad Request** | `"message": "Validation failed"` and a `fieldErrors` object listing each bad field |
| 15 | Delete teacher | `DELETE /api/teachers/{{teacherId}}` | **204 No Content** | Empty body (this is correct for delete) |
| 16 | Get deleted teacher (404) | `GET /api/teachers/{{teacherId}}` | **404 Not Found** | `"message": "Teacher with id ... not found"` |

Body of **Create teacher**:
```json
{
  "username": "karim{{runId}}",
  "password": "Karim@1234",
  "fullName": "Karim Hossain",
  "email": "karim{{runId}}@university.edu",
  "employeeId": "EMP-200{{runId}}",
  "department": "Computer Science",
  "designation": "Assistant Professor",
  "phone": "+8801711000000"
}
```
The **Update** body has no `password`. That is on purpose: leaving the password out keeps the current one.

**What this proves:** full CRUD on teachers by an admin, correct status codes (201, 200, 204, 404), duplicate detection (409) and validation (400).

### Folder 3: Admin - Students CRUD

| # | Request | Method + URL | Expected | What to look for |
|:-:|---|---|:-:|---|
| 17 | Create student | `POST /api/students` | **201** | New student with `id`, `"semester": 2`; `Location` header |
| 18 | List students | `GET /api/students` | **200** | Array including the new student |
| 19 | Get student | `GET /api/students/{{studentId}}` | **200** | That one student |
| 20 | Update student | `PUT /api/students/{{studentId}}` | **200** | `"semester": 3` |
| 21 | Delete student | `DELETE /api/students/{{studentId}}` | **204** | Empty body |

Body of **Create student**:
```json
{
  "username": "nusrat{{runId}}",
  "password": "Nusrat@1234",
  "fullName": "Nusrat Jahan",
  "email": "nusrat{{runId}}@university.edu",
  "studentId": "STU-200{{runId}}",
  "department": "Computer Science",
  "semester": 2,
  "phone": "+8801811000000"
}
```

**What this proves:** full CRUD on students by an admin.

### Folder 4: Admin - Admins CRUD

| # | Request | Method + URL | Expected | What to look for |
|:-:|---|---|:-:|---|
| 22 | Create admin | `POST /api/admins` | **201** | New admin with `"role": "ADMIN"` |
| 23 | List admins | `GET /api/admins` | **200** | Only admins appear (no teachers or students) |
| 24 | Get admin | `GET /api/admins/{{adminId}}` | **200** | That one admin |
| 25 | Update admin | `PUT /api/admins/{{adminId}}` | **200** | `"fullName": "Second Admin (renamed)"` |
| 26 | Delete admin | `DELETE /api/admins/{{adminId}}` | **204** | Empty body |

Body of **Create admin**:
```json
{
  "username": "admin2{{runId}}",
  "password": "Admin2@1234",
  "fullName": "Second Admin",
  "email": "admin2{{runId}}@university.edu"
}
```

**What this proves:** full CRUD on admins. Together with folders 2 and 3: **an admin manages all three resources.**

### Folder 5: RBAC - Teacher manages teachers only

All requests here use **`{{teacherToken}}`** (logged in as teacher1).

| # | Request | Method + URL | Expected | Meaning |
|:-:|---|---|:-:|---|
| 27 | Teacher lists teachers (200) | `GET /api/teachers` | **200** | ✅ Teacher can read teachers |
| 28 | Teacher creates teacher (201) | `POST /api/teachers` | **201** | ✅ Teacher can create teachers |
| 29 | Teacher updates teacher (200) | `PUT /api/teachers/{{teacherOwnId}}` | **200** | ✅ Teacher can update teachers (`"designation": "Senior Lecturer"`) |
| 30 | Teacher deletes teacher (204) | `DELETE /api/teachers/{{teacherOwnId}}` | **204** | ✅ Teacher can delete teachers |
| 31 | Teacher lists students (403) | `GET /api/students` | **403** | ❌ Blocked: students are not the teacher's resource |
| 32 | Teacher creates student (403) | `POST /api/students` | **403** | ❌ Blocked |
| 33 | Teacher deletes student (403) | `DELETE /api/students/1` | **403** | ❌ Blocked |
| 34 | Teacher lists admins (403) | `GET /api/admins` | **403** | ❌ Blocked |
| 35 | Teacher creates admin (403) | `POST /api/admins` | **403** | ❌ Blocked (a teacher cannot make themself an admin) |

Every 403 response looks like this:
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource",
  "path": "/api/students"
}
```

**What this proves:** **teachers can manage only teachers.**

### Folder 6: RBAC - Student manages students only

All requests here use **`{{studentToken}}`** (logged in as student1).

| # | Request | Method + URL | Expected | Meaning |
|:-:|---|---|:-:|---|
| 36 | Student lists students (200) | `GET /api/students` | **200** | ✅ Student can read students |
| 37 | Student creates student (201) | `POST /api/students` | **201** | ✅ Student can create students |
| 38 | Student updates student (200) | `PUT /api/students/{{studentOwnId}}` | **200** | ✅ Student can update students (`"semester": 2`) |
| 39 | Student deletes student (204) | `DELETE /api/students/{{studentOwnId}}` | **204** | ✅ Student can delete students |
| 40 | Student lists teachers (403) | `GET /api/teachers` | **403** | ❌ Blocked |
| 41 | Student creates teacher (403) | `POST /api/teachers` | **403** | ❌ Blocked |
| 42 | Student updates teacher (403) | `PUT /api/teachers/1` | **403** | ❌ Blocked |
| 43 | Student lists admins (403) | `GET /api/admins` | **403** | ❌ Blocked |

**What this proves:** **students can manage only students.**

### Folder 7: Logout

| # | Request | Method + URL | Token | Expected | Meaning |
|:-:|---|---|---|:-:|---|
| 44 | Logout student | `POST /api/auth/logout` | student | **200** | `{"message": "Logged out successfully"}` |
| 45 | Use token after logout (401) | `GET /api/auth/me` | same student token | **401** | `"Token has been revoked (logged out)"` |

**What this proves:** logout really invalidates the token on the server. It's not just deleted on the client.

After this folder, send **1. Auth → Login as student1** again before using any student request.

---

## Part 5: Extra checks you can build yourself

These are not in the collection, but they show more of the security and validation. Create each one with **New → HTTP Request** (or the **+** tab), set the method, URL, **Authorization → Bearer Token** and, where needed, **Body → raw → JSON**. Send **Login as admin** first so `{{adminToken}}` is set.

| # | What to test | Method + URL | Token | Body | Expected |
|:-:|---|---|---|---|:-:|
| A | Login with a username instead of email | `POST {{baseUrl}}/api/auth/login` | none | `{"username":"admin","password":"Admin@123"}` | **400** (`email` must not be blank) |
| B | Email is not case-sensitive | `POST {{baseUrl}}/api/auth/login` | none | `{"email":"ADMIN@University.edu","password":"Admin@123"}` | **200** |
| C | Admin cannot delete themself | `DELETE {{baseUrl}}/api/admins/1` | `{{adminToken}}` | none | **400** "You cannot delete your own admin account" |
| D | Teacher cannot delete themself | `GET {{baseUrl}}/api/auth/me` with `{{teacherToken}}` to see the id, then `DELETE {{baseUrl}}/api/teachers/{that id}` | `{{teacherToken}}` | none | **400** "You cannot delete your own account" |
| E | Unknown ID | `GET {{baseUrl}}/api/students/99999` | `{{adminToken}}` | none | **404** |
| F | Invalid ID | `GET {{baseUrl}}/api/teachers/abc` | `{{adminToken}}` | none | **400** |
| G | Wrong HTTP method | `PATCH {{baseUrl}}/api/teachers/1` | `{{adminToken}}` | `{}` | **405** |
| H | Fake token | `GET {{baseUrl}}/api/auth/me` | type `abc.def.ghi` as the token | none | **401** |
| I | Semester out of range | `POST {{baseUrl}}/api/students` | `{{adminToken}}` | a valid student body with `"semester": 20` | **400** (`semester` must be ≤ 12) |
| J | Password too short | `POST {{baseUrl}}/api/admins` | `{{adminToken}}` | `{"username":"shorty","password":"123","fullName":"S","email":"shorty@university.edu"}` | **400** |
| K | Change own password | `PUT {{baseUrl}}/api/auth/password` | `{{teacherToken}}` | `{"currentPassword":"Teacher@123","newPassword":"Teacher@456"}` | **200**, then the old teacher token gets **401** |

⚠️ **After test K**, teacher1's password is `Teacher@456`, so the collection's "Login as teacher1" will fail. Change it back right away with the **new** teacher token: log in with `Teacher@456`, then send `PUT /api/auth/password` with `{"currentPassword":"Teacher@456","newPassword":"Teacher@123"}`.

### See the password is hashed
Postman never shows a password in any response. To see how it is stored, look in the database (VS Code terminal):
```
docker exec -it ums-db psql -U ums -d ums -c "SELECT email, password FROM user_accounts;"
```
Every password looks like `$2a$10$...`. That's a **BCrypt hash**, not the real password.

---

## Part 6: How to read a Postman response

After **Send**, the bottom half of Postman shows:

| Place | What it shows |
|---|---|
| **Status** (top right, e.g. `201 Created`) | The HTTP status code. Green means 2xx; red/orange means 4xx/5xx (still correct when you expect an error) |
| **Time / Size** | How long the request took and the response size |
| **Body → Pretty / JSON** | The JSON response |
| **Headers** | Response headers, e.g. `Location` after a create, `Content-Type: application/json`, `WWW-Authenticate: Bearer` on 401 |
| **Test Results** | **PASS** / **FAIL** for the request's built-in check |

What the codes mean in this project:

| Code | Meaning |
|---|---|
| 200 OK | Read, update, login or logout worked |
| 201 Created | Create worked (look for the `Location` header) |
| 204 No Content | Delete worked (empty body is normal) |
| 400 Bad Request | Invalid data; `fieldErrors` says which field |
| 401 Unauthorized | Not logged in, or the token is wrong, expired or logged out |
| 403 Forbidden | Logged in, but your role is not allowed to do this |
| 404 Not Found | That ID or URL does not exist |
| 405 Method Not Allowed | Wrong HTTP method for that URL |
| 409 Conflict | Duplicate username, email, employee ID or student ID |

A red 401, 403, 404 or 409 is **not a failure** when the request name says that code is expected. That's the security and validation working.

---

## Part 7: Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| **Error: connect ECONNREFUSED 127.0.0.1:9090** / "Could not send request" | The app is not running | Start Docker Desktop, run `docker compose up -d`, check http://localhost:9090/actuator/health |
| **Login as admin → 400 "Validation failed"** | Old collection that sends `"username"` | Delete the collection in Postman and import the file again (Step 1.2) |
| **Login as admin → 401 "Invalid email or password"** | The admin password was changed (for example in the web page) | Change it back to `Admin@123` in the web page (**My account → Change password**), or put the new password in the request body and save |
| **Every request → 401 "Invalid or expired token"** | Token older than 1 hour, password changed, or you didn't log in first | Run folder **1. Auth** again |
| **Student requests → 401 after the Logout folder** | Logout revoked student1's token | Send **Login as student1** again |
| **Create → 409 Conflict** when you didn't expect it | `runId` is empty because you didn't run **Login as admin** first, so the name already exists | Send **Login as admin**, then try again |
| **Get / Update / Delete → 404** with `{{teacherId}}` in the URL | The "Create" request wasn't sent first, so the ID variable is empty or old | Send the folder's **Create** request first |
| **Teacher requests → 401** after Part 5 test K | teacher1's password changed | Change it back to `Teacher@123` (see the warning under Part 5) |
| Postman AI suggests changing a test to accept 400 or 401 | It's guessing | Click **Decline**. Fix the real cause instead |
| `{{baseUrl}}` shown in red | Variable missing | Click the collection → **Variables** and check `baseUrl` = `http://localhost:9090` |

---

## Part 8: 5-minute demo script for your teacher

1. **Show the app is running:** open http://localhost:9090/actuator/health → `"status":"UP"`.
2. **Login (authentication):** send **Login as admin**. Point out `accessToken`, `tokenType: Bearer` and `role: ADMIN`.
3. **No token is rejected:** send **No token (401)**.
4. **Admin CRUD on teachers:** send **Create teacher** (201 and the `Location` header), **List teachers** (200), **Update teacher** (200), **Delete teacher** (204) and **Get deleted teacher** (404).
5. **Validation and duplicates:** send **Create invalid teacher** (400 + `fieldErrors`) and **Create duplicate teacher** (409).
6. **RBAC, teacher:** send **Teacher creates teacher** (201) and then **Teacher lists students** (403). Say: *"Teachers can manage only teachers."*
7. **RBAC, student:** send **Student creates student** (201) and then **Student lists teachers** (403). Say: *"Students can manage only students."*
8. **Logout:** send **Logout student** (200) and then **Use token after logout** (401). Say: *"Logout revokes the token on the server."*
9. **Password hashing:** run the `docker exec ... SELECT email, password ...` command from Part 5 to show the `$2a$10$...` BCrypt hashes.
10. **Everything at once:** run the whole collection in the **Runner** → **45 / 45 passed**.

---

### Quick reference: accounts

| Role | Email | Password |
|---|---|---|
| Admin | `admin@university.edu` | `Admin@123` |
| Teacher | `teacher1@university.edu` | `Teacher@123` |
| Student | `student1@university.edu` | `Student@123` |
