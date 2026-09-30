# Lesson 3 — Tokens and JWT

> **Show your password once, get a pass, show the pass from then on.**
> Clients log in once at `POST /api/v1/auth/login` and receive a signed JSON
> Web Token. Every later request carries the token instead of the password.
> Spring's resource-server support checks the token's signature and expiry,
> and reads the roles from inside it.

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJwaXMiLCJzdWIiOiJvZmZpY2VyIi….Xk2p…
                      └──── header ────┘ └──────────── payload ───────────────┘ └ signature ┘
```

| Duration | Builds on | You should know |
| --- | --- | --- |
| About 2½ hours | [Lesson 2 — Users in the Database](02-users-in-database.md) | `SecurityFilterChain`, `UserDetailsService`, `AuditorAware` |

**Contents:** [Goals](#goals) · [Concepts](#concepts) · [Request flow](#request-flow) ·
[Upgrade checklist](#upgrade-checklist) · [Build it](#build-it) · [Try it](#try-it) ·
[Test it](#test-it) · [Common mistakes](#common-mistakes) · [Exercises](#exercises) ·
[Quiz](#quiz) · [Next lesson](#next-lesson)

---

## Goals

By the end of this lesson you can:

- Explain why a front end wants tokens instead of sending a password on every request.
- Take a JWT apart: header, payload, signature. Say what each part proves, and what it doesn't hide.
- Issue a signed token at a login endpoint with `JwtEncoder`.
- Accept tokens with `oauth2ResourceServer().jwt()` and map a `roles` claim to `hasRole(...)`.
- Explain why a token keeps working after its account is disabled, and what short expiry buys you.
- Test token security with a real login, a forged token, an expired token, and `jwt()`.

### Lesson plan

| Time (min) | Activity |
| --- | --- |
| 0–20 | Concepts: sessions vs tokens; anatomy of a JWT; signing vs encrypting |
| 20–30 | Request flow: login once, then Bearer on every request |
| 30–80 | Build it: dependency, key and properties, encoder/decoder, login endpoint, wiring |
| 80–100 | Try it live: log in, decode the token, forge one, watch it fail |
| 100–120 | The trade-off: a token outlives a disabled account |
| 120–150 | Tests, exercises, quiz |

---

## Concepts

### Why not just keep using Basic?

| HTTP Basic (Lessons 1–2) | Bearer token (Lesson 3) |
| --- | --- |
| The password travels on **every** request | The password travels **once**, at login |
| A browser front end must keep the password in memory, or worse, in storage | The front end keeps a token that expires by itself |
| The server runs BCrypt on every request (slow by design: tens of milliseconds) | The server checks an HMAC signature (microseconds) |
| No logout: the browser caches credentials until it closes | Throw the token away. It also dies on its own at `exp` |
| Server looks the user up in `app_user` every time | Server trusts what is signed inside the token: **no database lookup** |

The last row is both the big advantage and the big catch. We come back to it in
[the trade-off](#the-trade-off-a-token-outlives-a-disabled-account).

### Anatomy of a JWT

A JWT is three base64url strings joined by dots. Here is a real one from PIS, taken apart:

```
eyJhbGciOiJIUzI1NiJ9                    →  {"alg":"HS256"}
.
eyJpc3MiOiJwaXMiLCJzdWIiOiJvZmZpY2VyIiwiZXhwIjoxNzkwMjUxNzIxLCJpYXQiOjE3OTAyNDk5MjEsInJvbGVzIjpbIk9GRklDRVIiXX0
                                        →  {"iss":"pis","sub":"officer","exp":1790251721,
                                            "iat":1790249921,"roles":["OFFICER"]}
.
<43 characters>                         →  HMAC-SHA256(header + "." + payload, JWT_SECRET)
```

| Claim | Meaning | Set by |
| --- | --- | --- |
| `iss` | Issuer: who made the token | `TokenService`: `"pis"` |
| `sub` | Subject: who the token is about | the username |
| `iat` | Issued at (seconds since 1970) | now |
| `exp` | Expires at | now + 30 minutes |
| `roles` | Our own claim | the user's roles at login time |

> ⚠️ **Signed, not encrypted.** Anyone holding a token can read the payload.
> It is base64url, exactly as readable as a Basic header. **Never put a
> password, national ID or salary in a claim.**
>
> What the signature *does* guarantee: nobody without `JWT_SECRET` can change
> a single character without the server noticing. Change `OFFICER` to `ADMIN`
> and the signature no longer matches. You get 401, not admin.

### Who holds the secret?

HS256 is **symmetric**: the same `JWT_SECRET` signs tokens at login and checks
them on every request. That's fine while one application does both. When a
separate service (or a cloud identity provider like Keycloak or Entra ID)
issues tokens, you switch to **RS256**. The issuer signs with a private key,
and every API checks with the matching public key. The resource-server code
you write today stays almost the same.

### Stateless: the server remembers nothing

With `SessionCreationPolicy.STATELESS` (set since Lesson 1), the server keeps
no session. Each request is judged only by the token it carries. That means:

- any server behind a load balancer can check any token; nothing is shared between them;
- there is nothing to "log out" on the server; the client discards the token;
- **what the token says is what the server believes, until `exp`.**

---

## Request flow

```mermaid
sequenceDiagram
    participant C as Client
    participant L as AuthController / TokenService
    participant M as AuthenticationManager
    participant F as BearerTokenAuthenticationFilter
    participant D as JwtDecoder
    participant API as SupplierController

    Note over C,M: Once: log in with a password
    C->>L: POST /api/v1/auth/login {username, password}
    L->>M: authenticate(username, password)
    M-->>L: OK: DatabaseUserDetailsService + BCrypt, as in Lesson 2
    L-->>C: 200 {accessToken, tokenType: "Bearer", expiresIn: 1800}

    Note over C,API: Every later request: no password, no database lookup
    C->>F: GET /api/v1/suppliers + Authorization: Bearer eyJ...
    F->>D: decode(token)
    D->>D: check HS256 signature with JWT_SECRET, check exp
    alt signature wrong, expired, or garbage
        D-->>C: 401 + WWW-Authenticate: Bearer error="invalid_token"
    else valid
        D-->>F: Jwt: sub=officer, roles=[OFFICER] → ROLE_OFFICER
        F->>API: same hasRole(...) rules as Lessons 1 and 2
        API-->>C: 200
    end
```

---

## Upgrade checklist

Starting from the end of Lesson 2.

| | File | Change |
| --- | --- | --- |
| ✏️ | `pom.xml` | Add `spring-boot-starter-oauth2-resource-server` |
| ✏️ | `application.yml`, `application-test.yml`, `.env`, `.env.example` | Add `pis.security.jwt.secret` and `expiry`; `JWT_SECRET` |
| ➕ | `config/JwtProperties.java` | Binds `pis.security.jwt.*` |
| ➕ | `config/JwtConfig.java` | Signing key, `JwtEncoder`, `JwtDecoder`, roles converter |
| ➕ | `dto/LoginRequest.java`, `dto/TokenResponse.java` | New |
| ➕ | `service/TokenService.java` | Checks the password, signs the token |
| ➕ | `controller/AuthController.java` | `POST /api/v1/auth/login` |
| ✏️ | `config/SecurityConfig.java` | Permit login; add `oauth2ResourceServer`; expose `AuthenticationManager` |
| ✏️ | `config/ProblemDetailSecurityHandler.java` | Bearer-aware 401 |
| ✏️ | `exception/GlobalExceptionHandler.java` | Failed login → 401, not 500 |
| ✏️ | `controller/UserController.java` | `me()` takes `Authentication`, not `UserDetails` |
| ✏️ | `config/OpenApiConfig.java` | Bearer scheme in Swagger UI |
| ➕ | `JwtTest.java` | New, 9 tests |

HTTP Basic keeps working alongside tokens, so every Lesson 1 and 2 test and
curl script still passes. Switching Basic off is [Exercise 3](#3--switch-basic-off--core).

---

## Build it

### 1 · The dependency

```xml
<!-- pom.xml, next to spring-boot-starter-security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

This brings Spring Security's JWT support and the Nimbus library that does the
cryptography. **No other JWT library is needed.** You'll see tutorials using
`jjwt` with a hand-written `JwtFilter`. Spring has had this built in for years,
and the built-in filter handles the edge cases for you.

### 2 · The secret and the expiry

```yaml
# application.yml
pis:
  security:
    admin-password: ${ADMIN_PASSWORD}
    jwt:
      # HS256 needs at least 32 bytes. Generate one with:  openssl rand -base64 32
      secret: ${JWT_SECRET}
      expiry: 30m
```

```bash
# .env
JWT_SECRET=paste-the-output-of-openssl-rand-base64-32-here
```

```yaml
# src/test/resources/application-test.yml
pis:
  security:
    admin-password: admin-pass
    jwt:
      secret: test-only-secret-that-is-at-least-32-bytes-long
      expiry: 30m
```

> **Whoever has `JWT_SECRET` can mint a token for any user with any role.**
> It's more powerful than the admin password. Treat it the same way: `.env`
> only, never git, and a different value on every server.

```java
// config/JwtProperties.java
/**
 * pis.security.jwt.* from application.yml, bound and type-checked at startup.
 *
 * expiry accepts "30m", "8h" or "PT30M". A wrong value stops the app starting,
 * rather than failing on the first login.
 */
@ConfigurationProperties(prefix = "pis.security.jwt")
public record JwtProperties(String secret, Duration expiry) { }
```

### 3 · Encoder, decoder, and the roles claim

```java
// config/JwtConfig.java
/**
 * One secret key, used twice: the encoder signs tokens at login, the decoder
 * checks the signature on every request. HS256 is symmetric, so whoever holds
 * the secret can both issue and verify. It never leaves the server.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public SecretKey jwtSigningKey(JwtProperties properties) {
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 is only as strong as its key: 256 bits minimum.
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes; got " + bytes.length);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /** Checks signature and expiry on every request. A token that fails either is a 401. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Turns the token's "roles": ["OFFICER"] claim into the authority ROLE_OFFICER,
     * so every hasRole(...) rule from Lesson 1 works unchanged.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
```

Imports worth knowing: `ImmutableSecret` comes from `com.nimbusds.jose.jwk.source`;
the rest are from `org.springframework.security.oauth2.jwt`,
`...oauth2.jose.jws` and `...oauth2.server.resource.authentication`.

> **Why the converter?** By default Spring reads a claim called `scope` and
> prefixes it `SCOPE_`. That's the OAuth 2 convention, and it would turn our
> roles into `SCOPE_OFFICER`, which no `hasRole` rule matches. Configure it once
> here, and the rules from Lesson 1 don't change at all.

### 4 · The login endpoint

```java
// dto/LoginRequest.java
public record LoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password
) { }
```

```java
// dto/TokenResponse.java
/** Field names follow OAuth 2 (RFC 6749 §5.1), so any client library understands them. */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) { }
```

```java
// service/TokenService.java
/**
 * Checks a username and password once, and hands back a signed token that
 * stands in for them until it expires.
 */
@RequiredArgsConstructor
@Service
public class TokenService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenResponse login(String username, String password) {
        // The same check as HTTP Basic: DatabaseUserDetailsService + BCrypt.
        // Wrong password, unknown user or disabled account throws AuthenticationException.
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .toList();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pis")
                .subject(auth.getName())      // becomes Authentication.getName() on later requests
                .issuedAt(now)
                .expiresAt(now.plus(properties.expiry()))
                .claim("roles", roles)
                .build();

        // Without an explicit header Spring would pick RS256, which needs a private key we don't have.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new TokenResponse(token, "Bearer", properties.expiry().toSeconds());
    }
}
```

```java
// controller/AuthController.java
@Tag(name = "Authentication", description = "Exchange a username and password for a token")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final TokenService tokenService;

    @Operation(summary = "Log in",
               description = "Returns a JWT. Send it on later requests as  Authorization: Bearer <token>")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return tokenService.login(request.username(), request.password());
    }
}
```

### 5 · Wire it into `SecurityConfig`

Three additions. Everything else stays as it was.

```java
// config/SecurityConfig.java
private final ProblemDetailSecurityHandler problemHandler;
private final JwtAuthenticationConverter jwtAuthenticationConverter;   // ← new field

// ... inside authorizeHttpRequests, right after the permitAll() line:
// You can't need a token to get a token.
.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()

// ... after .httpBasic(...):
// Reads "Authorization: Bearer ...", checks signature and expiry with the
// JwtDecoder bean, and turns the roles claim into ROLE_ authorities.
.oauth2ResourceServer(jwt -> jwt
        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter))
        .authenticationEntryPoint(problemHandler)   // bad or expired token
        .accessDeniedHandler(problemHandler))
```

```java
// config/SecurityConfig.java — new bean
/**
 * The same AuthenticationManager that HTTP Basic uses (DatabaseUserDetailsService
 * + BCrypt), exposed so TokenService can check a password at login.
 */
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

### 6 · Three small fixes that tokens force on us

**a) A failed login must be 401, not 500.** In Lesson 1 a wrong password was
rejected in a *filter*. Now `TokenService` calls `authenticate(...)` inside a
*controller*, so the exception lands in `GlobalExceptionHandler`. It would fall
through to the catch-all `Exception` handler and become a 500.

```java
// exception/GlobalExceptionHandler.java — add above the Exception handler
/**
 * A failed login at POST /api/v1/auth/login. Unlike a 401 from the security
 * filters, this one is thrown inside a controller, so it arrives here.
 * Without this handler it would fall through to Exception and become a 500.
 *
 * One message for wrong password, unknown user and disabled account alike.
 */
@ExceptionHandler(AuthenticationException.class)
public ProblemDetail onLoginFailed(AuthenticationException ex) {
    return problem(HttpStatus.UNAUTHORIZED, "Login failed",
            "Invalid username or password", "login-failed");
}
```

**b) A bad token should say so.** RFC 6750 says a 401 for a bad bearer token
carries `WWW-Authenticate: Bearer error="invalid_token"`, which tells the
client to log in again.

```java
// config/ProblemDetailSecurityHandler.java — start of commence(...)
String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
if (authorization != null && authorization.startsWith("Bearer ")) {
    // RFC 6750: tell the client its token was the problem, so it knows to log in again.
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
    write(response, HttpStatus.UNAUTHORIZED, "Invalid token",
            "The token is invalid or has expired. Log in again at POST /api/v1/auth/login",
            "invalid-token");
    return;
}
// ... the Basic branch from Lesson 1 continues unchanged
```

**c) With a token, the principal is a `Jwt`, not a `UserDetails`.**
`@AuthenticationPrincipal UserDetails principal` would be `null`, and `/me`
would throw a `NullPointerException`. `Authentication.getName()` works for both:

```java
// controller/UserController.java
@GetMapping("/me")
public UserResponse me(Authentication authentication) {
    // Not @AuthenticationPrincipal UserDetails: with a token the principal is a Jwt,
    // and that parameter would be null. getName() works for Basic and Bearer alike.
    return service.findByUsername(authentication.getName());
}
```

`AuditorAware` from Lesson 2 already uses `getName()`, which returns the token's
`sub` claim. So `created_by` works with tokens without any change.

### 7 · Swagger UI

```java
// config/OpenApiConfig.java
// The Authorize button offers both: paste a token from /auth/login, or use Basic.
.components(new Components()
        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
        .addSecuritySchemes("basicAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("basic")))
.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
.addSecurityItem(new SecurityRequirement().addList("basicAuth"));
```

In Swagger UI: call **Authentication → Log in**, copy `accessToken`, click
**Authorize**, and paste it into *bearerAuth*. Don't type the word `Bearer`;
Swagger adds it.

---

## Try it

Add `JWT_SECRET` to `.env` (`openssl rand -base64 32`), start the app, and
make sure `officer` and `approver` exist (the Lesson 2 smoke test creates them).

**Log in → `200`**

```bash
curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"officer","password":"officer123"}' | jq
```
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJwaXMiLCJzdWIiOiJvZmZpY2VyIiwiZXhwIjoxNzkwMjUxNzIxLCJpYXQiOjE3OTAyNDk5MjEsInJvbGVzIjpbIk9GRklDRVIiXX0.…",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

**Keep the token in a shell variable**

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"officer","password":"officer123"}' | jq -r .accessToken)
```

**Read the payload: anyone can**

```bash
echo "$TOKEN" | jq -R 'split(".") | .[1] | gsub("-";"+") | gsub("_";"/") | @base64d | fromjson | .exp |= todate'
```
```json
{
  "iss": "pis",
  "sub": "officer",
  "exp": "2026-09-24T12:09:03Z",
  "iat": 1790249943,
  "roles": ["OFFICER"]
}
```

Or paste the token into <https://jwt.io>. Point out to the class that the page
can read everything, but shows *"invalid signature"* until you give it the secret.

**Use it → `200`, `403`**

```bash
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/suppliers
# 200

curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/users/me | jq .username
# "officer"

curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" -X POST \
  localhost:8080/api/v1/requisitions/99999999-9999-9999-9999-999999999999/approve
# 403 — the role comes from inside the token
```

**A bad token → `401` with a Bearer challenge**

```bash
curl -s -i -H "Authorization: Bearer not.a.token" localhost:8080/api/v1/suppliers
```
```
HTTP/1.1 401
WWW-Authenticate: Bearer error="invalid_token"
{"type":"https://hmy.co.tz/problems/invalid-token","title":"Invalid token","status":401,
 "detail":"The token is invalid or has expired. Log in again at POST /api/v1/auth/login",...}
```

**Forge a token: officer → ADMIN → `401`**

This is the moment the lesson is built around. The payload is readable and
editable, so let the class try to promote themselves:

```bash
b64url_decode() { local s; s=$(printf '%s' "$1" | tr '_-' '/+'); while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done; printf '%s' "$s" | base64 -d; }
b64url_encode() { base64 | tr '/+' '_-' | tr -d '=\n'; }

HEADER=$(echo "$TOKEN" | cut -d. -f1)
PAYLOAD=$(b64url_decode "$(echo "$TOKEN" | cut -d. -f2)")
SIGNATURE=$(echo "$TOKEN" | cut -d. -f3)
echo "$PAYLOAD"                                   # ..."roles":["OFFICER"]}
FORGED="$HEADER.$(printf '%s' "${PAYLOAD/OFFICER/ADMIN}" | b64url_encode).$SIGNATURE"

curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" -X DELETE \
  localhost:8080/api/v1/suppliers/99999999-9999-9999-9999-999999999999
# 403 — the real token: officer can't delete

curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $FORGED" -X DELETE \
  localhost:8080/api/v1/suppliers/99999999-9999-9999-9999-999999999999
# 401 — the forged token: signature doesn't match the new payload
```

Not 404 (which would mean "got past security as ADMIN"), and not even 403. The
server doesn't trust a single claim in a token whose signature is wrong.

### The trade-off: a token outlives a disabled account

```bash
ADMIN_TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)
ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" localhost:8080/api/v1/users | jq -r '.[] | select(.username=="officer") | .id')

curl -s -H "Authorization: Bearer $ADMIN_TOKEN" -X PATCH localhost:8080/api/v1/users/$ID/enabled \
  -H 'Content-Type: application/json' -d '{"enabled":false}' | jq .enabled          # false

curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"officer","password":"officer123"}'   # 401
curl -s -o /dev/null -w '%{http_code}\n' -u officer:officer123 localhost:8080/api/v1/suppliers   # 401
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/suppliers   # 200 (!)

curl -s -H "Authorization: Bearer $ADMIN_TOKEN" -X PATCH localhost:8080/api/v1/users/$ID/enabled \
  -H 'Content-Type: application/json' -d '{"enabled":true}' | jq .enabled           # true
```

The disabled officer can't log in and can't use Basic, but **the token they
already hold still works until it expires.** Nothing on the server looks at
`app_user` when a token arrives. That's the "no database lookup" advantage,
seen from the other side.

| Mitigation | Cost |
| --- | --- |
| **Short expiry** (we use 30 min; high-security systems often use 5) | Users log in more often, unless you add refresh tokens |
| Check the account on every request ([Exercise 5](#5--close-the-gap--stretch)) | One database read per request: you've given up the main benefit |
| A deny-list of revoked token IDs (`jti` claim) | A shared store (Redis) every server must check |

For PIS, 30 minutes means a dismissed employee has at most half an hour. Discuss
with the class whether that's acceptable for a procurement system.

---

## Test it

`JwtTest`, 9 tests. Most go through the real login endpoint, so the whole chain
is exercised: password check, signing, and signature and expiry checks on the
way back in.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtTest {

    private static final String UNKNOWN_ID = "99999999-9999-9999-9999-999999999999";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtEncoder jwtEncoder;

    @BeforeEach
    void createUsers() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of(Role.OFFICER)));
        users.save(new AppUser("approver", encoder.encode("approver-pass"), "Test Approver", Set.of(Role.APPROVER)));
    }

    /** POST /api/v1/auth/login and return just the token. */
    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"%s\", \"password\": \"%s\" }".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void login_returns_a_bearer_token() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"officer\", \"password\": \"officer-pass\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
            // header.payload.signature
            .andExpect(jsonPath("$.accessToken").value(org.hamcrest.Matchers.matchesPattern("[\\w-]+\\.[\\w-]+\\.[\\w-]+")));
    }

    @Test
    void a_wrong_password_returns_401_not_500() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"officer\", \"password\": \"wrong\" }"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Login failed"))
            .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void the_token_opens_the_api() throws Exception {
        String token = login("officer", "officer-pass");

        mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk());
    }

    @Test
    void roles_inside_the_token_are_enforced() throws Exception {
        String officer = login("officer", "officer-pass");
        String approver = login("approver", "approver-pass");

        mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer(officer)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/requisitions/{id}/approve", UNKNOWN_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer(approver)))
            .andExpect(status().isNotFound());
    }

    @Test
    void me_and_created_by_work_with_a_token() throws Exception {
        String token = login("officer", "officer-pass");

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("officer"));

        mvc.perform(post("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Mafia Island Traders", "tin": "777-111-222",
                      "registrationNumber": "BRELA-2025-7777",
                      "category": "GOODS", "email": "hello@mafiatraders.co.tz" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value("officer"));
    }

    @Test
    void an_officer_cannot_promote_themselves_by_editing_the_token() throws Exception {
        String token = login("officer", "officer-pass");
        String[] parts = token.split("\\.");                       // header . payload . signature

        // Anyone can read the payload...
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        // ...and anyone can change it. But the signature was made for the original payload.
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("OFFICER", "ADMIN").getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forged + "." + parts[2];

        mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID).header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
            .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    @Test
    void an_expired_token_is_rejected() throws Exception {
        // Signed with the real key, but it expired five minutes ago.
        // (The decoder allows 60 seconds of clock skew, so one second ago would still pass.)
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pis").subject("officer")
                .issuedAt(past).expiresAt(past.plus(55, ChronoUnit.MINUTES))
                .claim("roles", List.of("OFFICER"))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    /**
     * The price of statelessness. The server does not look the user up on each
     * request, so a token issued before the account was disabled still works
     * until it expires. Short expiry times are what keep this window small.
     */
    @Test
    void a_token_outlives_a_disabled_account() throws Exception {
        String token = login("officer", "officer-pass");

        AppUser officer = users.findByUsername("officer").orElseThrow();
        officer.setEnabled(false);
        users.flush();

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"officer\", \"password\": \"officer-pass\" }"))
            .andExpect(status().isUnauthorized());          // no new token

        mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk());                    // but the old one still works
    }

    /** jwt() skips login and signing entirely, like @WithMockUser did for Basic. */
    @Test
    void jwt_post_processor_tests_rules_without_logging_in() throws Exception {
        mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER"))))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/suppliers/{id}", UNKNOWN_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }
}
```

Imports: `com.jayway.jsonpath.JsonPath` (already on the test classpath through
`spring-boot-starter-test`), `java.util.Base64`, `java.nio.charset.StandardCharsets`,
`java.time.Instant`, `java.time.temporal.ChronoUnit`, and static
`SecurityMockMvcRequestPostProcessors.jwt`.

| Tool | Use it for |
| --- | --- |
| Real login (`login(...)` helper) | The whole chain: password, signing, decoding |
| `jwtEncoder` in the test | Tokens you can't get from login: expired, wrong issuer |
| `.with(jwt().authorities(...))` | Authorization rules only, like `@WithMockUser` for Basic |

```bash
mvn test
# SecurityTest 12 · UserApiTest 9 · JwtTest 9 · SupplierApiTest 6 · InvoiceRepositoryTest 3
# Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
```

---

## Common mistakes

<details>
<summary><b>Login returns 500</b></summary>

Two usual causes:

1. **No `AuthenticationException` handler** in `GlobalExceptionHandler`: a wrong
   password becomes a 500 instead of a 401. See step 6a.
2. **No `JwsHeader`**: `JwtEncoderParameters.from(claims)` defaults to RS256, finds
   no RSA key, and throws `JwtEncodingException`. Pass
   `JwsHeader.with(MacAlgorithm.HS256).build()` (Exercise 2).
</details>

<details>
<summary><b>App won't start: "JWT_SECRET must be at least 32 bytes"</b></summary>

HS256 needs a 256-bit key. `openssl rand -base64 32` gives 44 characters, which is plenty.
</details>

<details>
<summary><b>GETs work with a token, but every write or approve is 403</b></summary>

The roles converter is missing or not wired in. By default Spring looks for a
`scope` claim, finds none, and gives the token no roles at all. Rules that only
need `authenticated()` (the GETs) still pass, but every `hasRole(...)` fails. Check
`.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter))`.
</details>

<details>
<summary><b><code>/users/me</code> throws NullPointerException with a token</b></summary>

`@AuthenticationPrincipal UserDetails` is `null` when the principal is a `Jwt`.
Take `Authentication authentication` and use `authentication.getName()`.
</details>

<details>
<summary><b>All tokens stop working after a restart</b></summary>

`JWT_SECRET` changed. Maybe it isn't in `.env` and something generated a random
one, or two servers have different values. Every token signed with the old
secret is now invalid. That's also how you **revoke every token at once** in an
emergency.
</details>

<details>
<summary><b>Swagger UI sends <code>Bearer Bearer eyJ…</code></b></summary>

Paste only the token into *bearerAuth*. Swagger adds the word `Bearer` itself.
</details>

<details>
<summary><b>Storing the token in the browser</b></summary>

Not a Spring bug, but the first question from the front-end team. `localStorage`
can be read by any script on the page, so one XSS bug leaks it. Keep tokens
short-lived. For a browser-only front end, many teams prefer an `HttpOnly`
cookie, which brings CSRF protection back into play. That's a Lesson 4 decision.
</details>

---

## Exercises

Every exercise ends with `mvn test` passing.

### 1 · Read a token — *warm-up*

Log in as `approver` and decode the token with the `jq` command from *Try it*.

1. At what time does it expire, and how is that computed?
2. What would a front end need to show "Signed in as Juma Ali (APPROVER)"? Which parts are in the token, and which aren't?
3. Paste it into jwt.io. Why does jwt.io say *invalid signature*?

<details>
<summary>Solution</summary>

1. `exp` = `iat` + 1800 seconds (`expiry: 30m`). `.exp |= todate` prints it as a date.
2. The username (`sub`) and roles are in the token. The full name isn't. The
   front end can call `GET /api/v1/users/me` once after login, or you could
   add a `name` claim. Only add what isn't sensitive: everyone can read claims.
3. jwt.io doesn't know `JWT_SECRET`, so it can't verify the signature. It can
   still read the payload, which is the point.
</details>

### 2 · Break it on purpose — *understanding*

In `TokenService`, replace `JwtEncoderParameters.from(header, claims)` with
`JwtEncoderParameters.from(claims)`. Run `JwtTest`. What happens to login, and
which tests still pass? Put it back afterwards.

<details>
<summary>Solution</summary>

Login returns **500**: without a header, Spring asks for an RS256 key, finds only
our HMAC secret, and throws `JwtEncodingException`. Six tests fail: every test
that logs in through `/auth/login`.

Three still pass, because they never ask the server to sign anything:
`a_wrong_password_returns_401_not_500` (fails before signing),
`an_expired_token_is_rejected` (the test signs its own token with a header),
and `jwt_post_processor_tests_rules_without_logging_in` (`jwt()` skips signing).

A side lesson: the log shows no stack trace, because our catch-all
`onUnexpected` handler doesn't log. Adding `log.error("Unexpected error", ex)`
there is worth doing.
</details>

### 3 · Switch Basic off — *core*

Delete `.httpBasic(...)` from `SecurityConfig`, so the only way in is a token.
Run `mvn test`. Which test classes fail, and why? Migrate them to tokens.

<details>
<summary>Solution</summary>

12 tests fail with 401: the 3 in `SecurityTest` that log in with `httpBasic(...)`
(`correct_credentials_return_200`, `a_hand_built_authorization_header_works_too`,
`admin_can_delete_using_real_credentials`), and all 9 in `UserApiTest`.
`JwtTest` and `SupplierApiTest` (which uses `@WithMockUser`) still pass.

Migrate each `httpBasic("u", "p")` to a real login:

```java
// was:
mvc.perform(get("/api/v1/users/me").with(httpBasic("officer", "officer-pass")))
// now:
mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(login("officer", "officer-pass"))))
```

Copy the `login(...)` and `bearer(...)` helpers from `JwtTest`, or move them into
a small shared test base class. Delete the Basic-specific tests
(`a_hand_built_authorization_header_works_too`), and update the Lesson 1 and 2
smoke scripts to log in first.
</details>

### 4 · Shorter tokens — *core*

Set `expiry: 1m` in `application.yml`, restart, log in, and call
`/api/v1/suppliers` every 30 seconds. When do you get 401? Why is it later than one minute?

<details>
<summary>Solution</summary>

About **2 minutes** after login, not 1. `JwtTimestampValidator` allows **60 seconds
of clock skew** by default, because servers' clocks are never perfectly in step.
`JwtTest.an_expired_token_is_rejected` makes its token expire five minutes ago
for the same reason. Put `expiry: 30m` back afterwards.
</details>

### 5 · Close the gap — *stretch*

Make a disabled account's token stop working **immediately**. Also reject
tokens whose `iss` isn't `"pis"`. Which existing test now fails, and what
should it say instead? What did you give up?

<details>
<summary>Solution</summary>

Add validators to the decoder in `JwtConfig`:

```java
@Bean
public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AppUserRepository users) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

    // One database lookup per request: the token is no longer enough on its own.
    OAuth2TokenValidator<Jwt> accountEnabled = jwt -> users.findByUsername(jwt.getSubject())
            .filter(AppUser::isEnabled)
            .map(user -> OAuth2TokenValidatorResult.success())
            .orElseGet(() -> OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Account is disabled", null)));

    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("pis"),   // expiry + iss must be "pis"
            accountEnabled));
    return decoder;
}
```

Exactly one test fails: `a_token_outlives_a_disabled_account` now gets **401**
instead of 200. Rename it to `a_disabled_account_token_stops_working` and expect
`isUnauthorized()`.

What you gave up: every request now reads `app_user` again, just as Basic did,
although it's much cheaper than a BCrypt check. You've traded some of
statelessness for instant revocation. Many real systems make exactly this trade.
</details>

---

## Quiz

**1. What stops an officer from editing their token's `roles` to `["ADMIN"]`?**\
a) The payload is encrypted  b) The signature no longer matches, so the server rejects it  c) Nothing; that's why we check the database

**2. Which of these is safe to put in a JWT claim?**\
a) The user's password  b) The user's roles  c) The user's national ID number

**3. A dismissed employee is disabled at 10:00. Their token was issued at 09:50 with a 30-minute expiry. Until when can they use the API?**\
a) 10:00  b) About 10:20 (plus up to 60 s clock skew)  c) Forever, until they log out

**4. Why does a wrong password at `/auth/login` need its own `@ExceptionHandler`, when in Lesson 1 it didn't?**\
a) JWT passwords are different  b) Now the check runs inside a controller, so the exception reaches `@RestControllerAdvice`  c) Spring requires it for tokens

**5. What happens to every issued token if you change `JWT_SECRET` and restart?**\
a) Nothing  b) They all become invalid  c) They're re-signed automatically

**6. With a token, why does `@AuthenticationPrincipal UserDetails` give `null`?**\
a) The token has no username  b) The principal is a `Jwt` object, not a `UserDetails`  c) `/me` must be `permitAll()`

<details>
<summary>Answers</summary>

1. **b.** The payload is only encoded. The HMAC signature is what makes it tamper-proof.
2. **b.** Anyone holding the token can read every claim.
3. **b.** Nothing checks `app_user` when a token arrives, so it works until `exp`, plus the clock-skew allowance.
4. **b.** In Lesson 1 the filter rejected the password before any controller ran.
5. **b.** The decoder can't verify their signatures any more: a global logout.
6. **b.** `Authentication.getName()` works for both Basic and Bearer.
</details>

---

## Next lesson

**→ [Lesson 3B — Refresh Tokens](03b-refresh-tokens.md)** answers this lesson's
trade-off: 5-minute access tokens, plus a revocable refresh token with rotation,
reuse detection and logout.

After that, three OAuth2 lectures, each a separate project: Spring Authorization
Server, Keycloak, and log in with Google or GitHub.
