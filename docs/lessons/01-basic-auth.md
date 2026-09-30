# Lesson 1 — HTTP Basic Authentication

> **Who are you, and what may you do?**
> We lock the PIS API with HTTP Basic authentication and role-based rules, then
> prove it works with curl, Swagger UI and automated tests.

```
Authorization: Basic b2ZmaWNlcjpvZmZpY2VyMTIz
```

| Duration | Stack | You should know |
| --- | --- | --- |
| About 2 hours | Spring Boot 3.5 · Java 17 · PostgreSQL | Controllers, services, MockMvc tests |

**Contents:** [Goals](#goals) · [Concepts](#concepts) · [Request flow](#request-flow) ·
[Build it](#build-it) · [Try it](#try-it) · [Test it](#test-it) ·
[Common mistakes](#common-mistakes) · [Exercises](#exercises) · [Quiz](#quiz) ·
[Next lesson](#next-lesson)

---

## Goals

By the end of this lesson you can:

- Explain the difference between **authentication** and **authorization**, and between **401** and **403**.
- Read and write an HTTP Basic `Authorization` header by hand, and explain why Basic auth needs HTTPS.
- Configure a `SecurityFilterChain` with role rules and explain why rule order matters.
- Store passwords as BCrypt hashes, not plain text.
- Test security with `httpBasic()` and `@WithMockUser`.

### Lesson plan

| Time (min) | Activity |
| --- | --- |
| 0–15 | Concepts: authentication vs authorization, how Basic works |
| 15–25 | How a request passes through the security filters |
| 25–60 | Build it: dependency, users, rules, error handler, Swagger |
| 60–75 | Try it live with curl and Swagger UI |
| 75–95 | Test it: `SecurityTest` walkthrough |
| 95–120 | Exercises and quiz |

---

## Concepts

### Two questions, asked in order

Every protected request must answer two questions. Spring Security keeps them
in two separate places, and so should your thinking.

| | Question | Proved by | When it fails |
| --- | --- | --- | --- |
| **Authentication** | *Who are you?* | username and password | **401 Unauthorized** |
| **Authorization** | *What may you do?* | your role vs the rule for this URL | **403 Forbidden** |

> **Remember it like this:** 401 means *"I don't know who you are."* 403 means
> *"I know exactly who you are, and the answer is no."* (The name "Unauthorized"
> for 401 is a historical mistake in HTTP. It really means *unauthenticated*.)

### How HTTP Basic works

1. The client calls `GET /api/v1/suppliers` with no credentials.
2. The server replies **401** with `WWW-Authenticate: Basic realm="pis"`. That header says: "use Basic auth".
3. The client joins username and password with a colon, `officer:officer123`,
   Base64-encodes it, and sends it again:
   `Authorization: Basic b2ZmaWNlcjpvZmZpY2VyMTIz`
4. The server decodes it, looks up the user, checks the password against the
   stored hash, then checks the role rules.
5. There is no session and no login endpoint. **The client sends the header on every single request.**

> ⚠️ **Base64 is not encryption.** Anyone who sees the header can decode it in one command:
>
> ```bash
> echo b2ZmaWNlcjpvZmZpY2VyMTIz | base64 -d     # officer:officer123
> ```
>
> Basic auth is safe **only over HTTPS**, where the whole request is encrypted.

### Where passwords are stored

The server never stores the password itself. It stores a **BCrypt hash**: a
one-way value. At login, Spring hashes what you typed and compares the two
hashes. If the database leaks, the attacker gets hashes, not passwords.

```
{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
```

The `{bcrypt}` prefix names the algorithm. That lets you switch to a stronger
algorithm later while old passwords keep working.

### Roles in PIS

Procurement has a natural separation of duties: the person who *requests* a
purchase should not be the one who *approves* it. Our roles mirror that.

| User | Roles | Job |
| --- | --- | --- |
| `officer` | OFFICER | Registers suppliers, raises requisitions and purchase orders |
| `approver` | APPROVER | Approves or rejects requisitions, changes supplier status |
| `admin` | ADMIN, APPROVER, OFFICER | Everything, including deletes |

---

## Request flow

Spring Security is a chain of **servlet filters**. They run before the request
reaches `DispatcherServlet`, so a rejected request never touches a controller
or a service.

```mermaid
sequenceDiagram
    participant C as Client (curl)
    participant B as BasicAuthenticationFilter
    participant A as AuthorizationFilter
    participant D as Controller
    C->>B: POST /requisitions/{id}/approve + Authorization header
    alt header missing or password wrong
        B-->>C: 401 via ProblemDetailSecurityHandler.commence()
    else credentials valid
        B->>A: authenticated as officer (ROLE_OFFICER)
        alt role not allowed by the rules
            A-->>C: 403 via ProblemDetailSecurityHandler.handle()
        else role allowed
            A->>D: request reaches RequisitionController
            D-->>C: 200 / 404 / 422 via GlobalExceptionHandler
        end
    end
```

> **Why we need a second error handler.** `GlobalExceptionHandler` is a
> `@RestControllerAdvice`. It only sees exceptions thrown *inside* controllers.
> A 401 or 403 happens in a filter, before any controller runs, so we need
> `ProblemDetailSecurityHandler` to give those errors the same JSON shape.

---

## Build it

Six changes, one at a time.

### 1 · Add the dependencies

The moment this starter is on the classpath, **every endpoint is locked**.
Spring generates a user called `user` with a random password printed in the
log. Show this to the class before writing any config.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- test scope: httpBasic(), @WithMockUser -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 2 · Keep passwords out of the code

Same rule as the database credentials: they live in `.env`, which is
gitignored. `application.yml` only points at them.

```yaml
# src/main/resources/application.yml
pis:
  security:
    officer-password: ${OFFICER_PASSWORD}
    approver-password: ${APPROVER_PASSWORD}
    admin-password: ${ADMIN_PASSWORD}
```

```bash
# .env (never committed)
OFFICER_PASSWORD=officer123
APPROVER_PASSWORD=approver123
ADMIN_PASSWORD=admin123
```

If a variable is missing, the app refuses to start. That is deliberate: failing
loudly beats running with no password.

### 3 · The users: authentication

A `UserDetailsService` answers one question: *"given a username, who is this
and what is their password hash?"* For now the users live in memory.

```java
// config/SecurityConfig.java
@Bean
public UserDetailsService userDetailsService(
        PasswordEncoder encoder,
        @Value("${pis.security.officer-password}") String officerPassword,
        @Value("${pis.security.approver-password}") String approverPassword,
        @Value("${pis.security.admin-password}") String adminPassword) {

    // roles("OFFICER") stores the authority "ROLE_OFFICER"; hasRole("OFFICER") checks for it.
    return new InMemoryUserDetailsManager(
            User.withUsername("officer")
                    .password(encoder.encode(officerPassword))
                    .roles("OFFICER")
                    .build(),
            User.withUsername("approver")
                    .password(encoder.encode(approverPassword))
                    .roles("APPROVER")
                    .build(),
            User.withUsername("admin")
                    .password(encoder.encode(adminPassword))
                    .roles("ADMIN", "APPROVER", "OFFICER")
                    .build());
}

@Bean
public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // BCrypt by default
}
```

### 4 · The rules: authorization

This is the heart of the lesson. Read the rules out loud, top to bottom.

```java
// config/SecurityConfig.java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
            // No session cookie, so there is nothing for CSRF to forge.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Checked top to bottom. The FIRST match wins.
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                            "/v3/api-docs/**").permitAll()

                    .requestMatchers(HttpMethod.POST, "/api/v1/requisitions/*/approve",
                            "/api/v1/requisitions/*/reject").hasRole("APPROVER")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/suppliers/*/status").hasRole("APPROVER")

                    .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                    .requestMatchers("/api/v1/**").hasRole("OFFICER")

                    .anyRequest().denyAll())

            .httpBasic(basic -> basic.authenticationEntryPoint(problemHandler))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(problemHandler)   // 401
                    .accessDeniedHandler(problemHandler))       // 403
            .build();
}
```

| Request | Allowed |
| --- | --- |
| Health check, Swagger UI, API docs | Anyone |
| `POST …/requisitions/{id}/approve` and `/reject` | APPROVER |
| `PATCH …/suppliers/{id}/status` | APPROVER |
| Any `DELETE` | ADMIN |
| Any `GET` | Any logged-in user |
| Any other write (`POST`, `PUT`) | OFFICER |
| Anything not listed | Nobody |

> ⚠️ **Order matters.** Move `.requestMatchers("/api/v1/**").hasRole("OFFICER")`
> to the top and it matches *every* API call first. Approvers would then be
> refused on approve, and deletes would only need OFFICER. Put specific paths
> above general ones.

### 5 · 401 and 403 as problem details

One class implements both hooks: `commence()` handles *"who are you?"* failures
and `handle()` handles *"you may not"* failures.

```java
// config/ProblemDetailSecurityHandler.java (core)
@Override   // 401: no credentials, or wrong ones
public void commence(HttpServletRequest request, HttpServletResponse response,
                     AuthenticationException ex) throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"pis\"");
    write(response, HttpStatus.UNAUTHORIZED, "Authentication required",
            "Send a valid username and password using HTTP Basic", "unauthorized");
}

@Override   // 403: valid user, wrong role
public void handle(HttpServletRequest request, HttpServletResponse response,
                   AccessDeniedException ex) throws IOException {
    write(response, HttpStatus.FORBIDDEN, "Access denied",
            "Your role does not permit this operation", "forbidden");
}
```

### 6 · The Authorize button in Swagger UI

```java
// config/OpenApiConfig.java
.components(new Components().addSecuritySchemes("basicAuth",
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
.addSecurityItem(new SecurityRequirement().addList("basicAuth"));
```

---

## Try it

Start the app with `mvn spring-boot:run`, then run these in order.
**Before each one, ask the class to predict the status code.**

**No credentials → `401`**

```bash
curl -i localhost:8080/api/v1/suppliers
```
```
HTTP/1.1 401
WWW-Authenticate: Basic realm="pis"
{"type":"https://hmy.co.tz/problems/unauthorized","title":"Authentication required","status":401,...}
```

**Wrong password → `401`**

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:wrong localhost:8080/api/v1/suppliers
```

**Correct credentials → `200`** (`-u` builds the header for you)

```bash
curl -s -u officer:officer123 localhost:8080/api/v1/suppliers | jq
```

**The same request, header built by hand → `200`**

```bash
printf 'officer:officer123' | base64          # b2ZmaWNlcjpvZmZpY2VyMTIz
curl -s -H "Authorization: Basic b2ZmaWNlcjpvZmZpY2VyMTIz" localhost:8080/api/v1/suppliers | jq

echo b2ZmaWNlcjpvZmZpY2VyMTIz | base64 -d     # anyone can do this
```

**Right person, wrong role → `403`**

```bash
curl -s -u officer:officer123 -X POST \
  localhost:8080/api/v1/requisitions/99999999-9999-9999-9999-999999999999/approve | jq
```
```json
{ "title": "Access denied", "status": 403, "detail": "Your role does not permit this operation" }
```

**Right role → `404`**

```bash
curl -s -u approver:approver123 -X POST \
  localhost:8080/api/v1/requisitions/99999999-9999-9999-9999-999999999999/approve | jq
```
```json
{ "title": "Resource not found", "status": 404, "detail": "Requisition not found: 9999..." }
```

> **Why is 404 a success here?** The id does not exist, so the service says 404.
> But the service only runs if security let the request through. A 404 proves
> the approver passed both checks, without changing any real data.

### Swagger UI

1. Open `http://localhost:8080/swagger-ui.html`. The page loads without a password, because it is on the `permitAll()` list.
2. Try `GET /api/v1/suppliers`. You get 401.
3. Click **Authorize**, enter `officer` / `officer123`, and try again. You get 200.
4. Try `DELETE /api/v1/suppliers/{id}`. You get 403. Log out, authorize as `admin`, and try again.

---

## Test it

Two tools, two purposes:

| Tool | What it does | Use it to test |
| --- | --- | --- |
| `httpBasic("user", "pass")` | Sends a real `Authorization` header; the password check runs | **Authentication** |
| `@WithMockUser(roles = "…")` | Puts a user straight into the security context; skips the password | **Authorization** rules |

Tests use fixed, fake passwords from `application-test.yml` (`officer-pass`,
`approver-pass`, `admin-pass`), so they pass on every machine no matter what is
in each student's `.env`.

```java
// src/test/java/tz/co/hmy/pis/SecurityTest.java (extract)
@Test
void no_credentials_returns_401_with_a_basic_challenge() throws Exception {
    mvc.perform(get("/api/v1/suppliers"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"pis\""))
        .andExpect(jsonPath("$.title").value("Authentication required"));
}

@Test
void correct_credentials_return_200() throws Exception {
    mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")))
        .andExpect(status().isOk());
}

@Test
@WithMockUser(roles = "OFFICER")
void officer_cannot_approve_a_requisition() throws Exception {
    mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID))
        .andExpect(status().isForbidden());
}

@Test
@WithMockUser(roles = "APPROVER")
void approver_can_approve_a_requisition() throws Exception {
    mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID))
        .andExpect(status().isNotFound());   // got past security
}
```

### What the suite covers

| Test | Who | Expect |
| --- | --- | --- |
| No credentials | nobody | 401 |
| Wrong password | officer / wrong | 401 |
| Unknown user | nobody | 401 |
| Correct credentials | officer | 200 |
| Hand-built Base64 header | officer | 200 |
| Health and API docs stay public | nobody | 200 |
| Officer approves requisition | OFFICER | 403 |
| Approver approves requisition | APPROVER | 404 |
| Approver creates supplier | APPROVER | 403 |
| Officer deletes | OFFICER | 403 |
| Admin deletes (real login) | admin | 404 |
| Any logged-in user reads | APPROVER | 200 |

### Existing tests need a user now

After adding security, every test in `SupplierApiTest` fails with 401. One
class-level annotation fixes it, because those tests are about the API, not
about logging in:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "OFFICER")
class SupplierApiTest { ... }
```

```bash
mvn test
# Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
```

---

## Common mistakes

<details>
<summary><b>Every POST returns 403, even for admin</b></summary>

CSRF protection is still on. It is enabled by default and rejects
state-changing requests without a CSRF token. For a stateless API that uses
Basic auth, disable it: `.csrf(AbstractHttpConfigurer::disable)`. Keep it on
for browser apps that log in with a session cookie.
</details>

<details>
<summary><b><code>hasRole("ROLE_ADMIN")</code> fails at startup</b></summary>

`hasRole` adds the `ROLE_` prefix itself. Write `hasRole("ADMIN")`, or use
`hasAuthority("ROLE_ADMIN")` if you want to spell it out.
</details>

<details>
<summary><b>A rule seems to be ignored</b></summary>

A broader matcher above it matched first. Rules are checked top to bottom and
the first match wins. Move specific paths above general ones.
</details>

<details>
<summary><b>App will not start: "Could not resolve placeholder 'OFFICER_PASSWORD'"</b></summary>

Your `.env` is missing the password variables. Copy them from `.env.example`
and set your own values.
</details>

<details>
<summary><b>The browser keeps showing a login pop-up</b></summary>

That is the browser reacting to `WWW-Authenticate: Basic` on a 401. It is
expected when you open an API URL directly. The browser then caches the
credentials until you close it: Basic auth has no real logout.
</details>

<details>
<summary><b>"There is no PasswordEncoder mapped for the id null"</b></summary>

A password was stored without an `{id}` prefix, for example a plain
`"admin123"`. Always pass it through `encoder.encode(...)`, which adds
`{bcrypt}`.
</details>

---

## Exercises

Do each one on your own branch. Every exercise ends with `mvn test` passing.

### 1 · Decode a captured header — *warm-up*

You capture this header from an unencrypted request:
`Authorization: Basic YXBwcm92ZXI6YXBwcm92ZXIxMjM=`.
Who is the user and what is the password? Which role do they have?

<details>
<summary>Solution</summary>

`echo YXBwcm92ZXI6YXBwcm92ZXIxMjM= | base64 -d` gives `approver:approver123`.
The role is APPROVER. This is why Basic auth needs HTTPS.
</details>

### 2 · Add a read-only auditor — *core*

Add a user `auditor` with role `AUDITOR` and password from `AUDITOR_PASSWORD`.
The auditor can read everything and change nothing. Write two tests: auditor
can list suppliers (200), auditor cannot create one (403).

<details>
<summary>Solution</summary>

No new rule is needed: `GET` is allowed for any authenticated user, and every
write needs another role. Add the user, the yml property, the `.env` variable,
and a fake password in `application-test.yml`.

```java
User.withUsername("auditor")
        .password(encoder.encode(auditorPassword))
        .roles("AUDITOR")
        .build()

@Test
void auditor_can_read_but_not_write() throws Exception {
    mvc.perform(get("/api/v1/suppliers").with(httpBasic("auditor", "auditor-pass")))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/suppliers").with(httpBasic("auditor", "auditor-pass"))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
}
```
</details>

### 3 · Only approvers may issue purchase orders — *core*

Issuing a purchase order commits money. Change the rules so
`POST /api/v1/purchase-orders/{id}/issue` needs APPROVER, and prove it with a
403 test for OFFICER and a 404 test for APPROVER.

<details>
<summary>Solution</summary>

Add the rule next to the other approver rules, *above* the general OFFICER rule:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/requisitions/*/approve",
        "/api/v1/requisitions/*/reject",
        "/api/v1/purchase-orders/*/issue").hasRole("APPROVER")
```
</details>

### 4 · Break it on purpose — *understanding*

Move `.requestMatchers("/api/v1/**").hasRole("OFFICER")` to the top of the
rules and run `mvn test`. Which tests fail, and why? Put it back afterwards.

<details>
<summary>Solution</summary>

The broad rule now matches every API request first. Four tests fail:

- `approver_can_approve_a_requisition` and `any_authenticated_user_can_read`
  get 403, because an approver is not an officer.
- `officer_cannot_approve_a_requisition` and `officer_cannot_delete` fail the
  other way: the officer now reaches the service and gets 404 instead of 403.

The rule gives too little to approvers and too much to officers, all because
the first match wins.
</details>

### 5 · Prove the password is hashed — *stretch*

Write a unit test that encodes `"secret"` twice with the `PasswordEncoder`.
Assert that the two hashes are different, that both start with `{bcrypt}`, and
that `matches("secret", hash)` is true for both. Explain why the hashes differ.

<details>
<summary>Solution</summary>

```java
PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
String a = encoder.encode("secret");
String b = encoder.encode("secret");

assertThat(a).isNotEqualTo(b).startsWith("{bcrypt}");
assertThat(encoder.matches("secret", a)).isTrue();
assertThat(encoder.matches("secret", b)).isTrue();
```

BCrypt adds a random **salt** to every hash and stores it inside the hash. So
two users with the same password get different hashes, and precomputed tables
of hashes are useless to an attacker.
</details>

---

## Quiz

**1. An officer calls `DELETE /api/v1/suppliers/{id}` with the correct password. What comes back?**\
a) 401 Unauthorized  b) 403 Forbidden  c) 204 No Content

**2. What does Base64 do to the credentials in a Basic header?**\
a) Encrypts them with the server's key  b) Hashes them with BCrypt  c) Only encodes them; anyone can decode them

**3. Why can't `GlobalExceptionHandler` produce our 401 and 403 responses?**\
a) Security runs in filters, before any controller  b) It only handles exceptions from services  c) ProblemDetail cannot represent 401

**4. Which test tool checks that the password itself is verified?**\
a) `@WithMockUser`  b) `httpBasic("officer", "officer-pass")`  c) `@Transactional`

**5. Why is it safe to disable CSRF in this API?**\
a) Basic auth encrypts every request  b) The API only accepts JSON  c) There is no session cookie for the browser to send automatically

<details>
<summary>Answers</summary>

1. **b** — The officer is authenticated, so it is not 401. DELETE needs ADMIN, so the answer is 403.
2. **c** — Base64 is a reversible encoding, not security. HTTPS is what protects the header.
3. **a** — `@RestControllerAdvice` only sees exceptions thrown from controllers. The security filters reject the request earlier.
4. **b** — `@WithMockUser` skips the password check completely. `httpBasic()` sends a real header.
5. **c** — CSRF tricks a browser into sending a cookie it holds. A stateless API has no session cookie to abuse.
</details>

---

## Next lesson

**→ [Lesson 2 — Users in the Database, and Who Did What](02-users-in-database.md)** covers the first two points below.

- **Users in the database.** Replace `InMemoryUserDetailsManager` with a
  `UserDetailsService` that reads a `users` table through a repository. The
  rules do not change.
- **Who did it?** Add `@CreatedBy` and `@LastModifiedBy` to `Auditable` with an
  `AuditorAware` that reads the logged-in username.
- **Method security.** Use `@PreAuthorize("hasRole('APPROVER')")` on service
  methods, and add an `AccessDeniedException` handler so it returns 403, not 500.
- **Tokens.** Basic auth sends the password on every request and has no logout.
  A front end will want JWT or OAuth2.

All code shown is in the project under
[`src/main/java/tz/co/hmy/pis/config/`](../../src/main/java/tz/co/hmy/pis/config/)
and [`src/test/java/tz/co/hmy/pis/`](../../src/test/java/tz/co/hmy/pis/).
