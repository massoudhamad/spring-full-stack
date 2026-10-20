# Lesson 3E — The Security Filter Chain

> **See what Spring Security does to every request, and add your own steps.**
> Every lesson so far changed `SecurityConfig` and trusted it to work. This one
> opens it up: a request passes through a list of **filters**, one after the
> other, and each line of `SecurityConfig` adds, configures or removes one of them.
> You watch a request go through the list, then add two filters of your own: an
> access log that names the user, and a lock against password guessing.

```
request ─► … ─► 6 LoginRateLimitFilter ─► 7 Bearer ─► 8 Basic ─► 9 RequestLogFilter ─► … ─► 16 AuthorizationFilter ─► controller
                  ours: before login                              ours: after login
```

| Duration | Builds on | You should know |
| --- | --- | --- |
| About 2 hours | [Lesson 3D — Roles and Permissions in the Database](03d-roles-in-database.md) | `SecurityConfig`, `ProblemDetailSecurityHandler`, HTTP Basic and JWT logins |

**Contents:** [Goals](#goals) · [Concepts](#concepts) · [Upgrade checklist](#upgrade-checklist) ·
[Build it](#build-it) · [Try it](#try-it) · [Test it](#test-it) · [Common mistakes](#common-mistakes) ·
[Exercises](#exercises) · [Quiz](#quiz) · [Smoke test](#smoke-test)

> **Complete files.** Every code block is a complete file with its `package` and
> `import` lines, taken from the tested project, unless it's labelled "changed part".

---

## Goals

By the end of this lesson you can:

- Explain where the security filter chain sits, and list the filters a request goes through.
- Match each line of `SecurityConfig` to the filter it adds, configures or removes.
- Watch a real request go through the chain with the `TRACE` log.
- Write a filter with `OncePerRequestFilter`, and put it in the right place with `addFilterBefore` / `addFilterAfter`.
- Use the three things a filter can do: pass the request on, answer and stop, act on the way back.
- Stop password guessing with a 429 and a `Retry-After` header.
- Test a filter with and without Spring, and test the order of the chain itself.

### Lesson plan

| Time (min) | Activity |
| --- | --- |
| 0–20 | Concepts: where the chain sits; config lines are filters; what a filter can do |
| 20–35 | Try it, step 1: watch one request go through all 16 filters |
| 35–80 | Build it: the request log, the 429, the login limit, the two `addFilter` lines |
| 80–100 | Try it: the log names the user; guess passwords until you're blocked |
| 100–120 | Tests, the CSRF exercise, quiz |

---

## Concepts

### Where the chain sits

```
Tomcat ─► servlet filters ─► "springSecurityFilterChain"  (a DelegatingFilterProxy, registered by Spring Boot)
                                   │
                                   ▼
                            FilterChainProxy ─► the first SecurityFilterChain whose matcher fits the request
                                                    │   (PIS has one: SecurityConfig.filterChain, for every request)
                                                    ▼
                                      filter 1 ─► filter 2 ─► … ─► filter 16 ─► DispatcherServlet ─► your controller
```

Security runs **before** your controller, in plain servlet filters. That's why
`@RestControllerAdvice` never sees a 401 or 403 from security, and why Lesson 1
needed `ProblemDetailSecurityHandler`.

### Your config is a list of filters

PIS's chain, from the `TRACE` log of a real request (Try it, step 1):

| # | Filter | Comes from | Does |
| --- | --- | --- | --- |
| 1–5 | `DisableEncodeUrlFilter` … `LogoutFilter` | defaults | Housekeeping, the security headers on every response, `/logout` |
| 6 | `LoginRateLimitFilter` | **ours**, `addFilterBefore(…)` | Refuses password guessing (this lesson) |
| 7 | `BearerTokenAuthenticationFilter` | `.oauth2ResourceServer(…)` | `Authorization: Bearer <JWT>` (Lesson 3) |
| 8 | `BasicAuthenticationFilter` | `.httpBasic(…)` | `Authorization: Basic …` (Lesson 1) |
| 9 | `RequestLogFilter` | **ours**, `addFilterAfter(…)` | The access log (this lesson) |
| 10 | `AuthenticationFilter` | `.oauth2ResourceServer(…)` | DPoP-bound tokens; unused in PIS |
| 11–12 | `RequestCacheAwareFilter`, `SecurityContextHolderAwareRequestFilter` | defaults | Servlet API glue |
| 13 | `AnonymousAuthenticationFilter` | defaults | Nobody logged in? Then the caller is `anonymousUser` |
| 14 | `SessionManagementFilter` | `.sessionManagement(…)` | `STATELESS`: no session is ever created |
| 15 | `ExceptionTranslationFilter` | `.exceptionHandling(…)` | Turns "denied" into a 401 or a 403 |
| 16 | `AuthorizationFilter` | `.authorizeHttpRequests(…)` | Your `requestMatchers` rules: the decision |
| — | `CsrfFilter` | `.csrf(AbstractHttpConfigurer::disable)` | **Not in the list:** this line removes it (Exercise 1 puts it back) |

Filters 7 and 8 answer *who are you?*. Filter 16 answers *what may you do?*. The
two questions from Lesson 1 are two places in this list.

### Three things a filter can do

```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
    // 1. before: look at the request; maybe answer it yourself and return (the chain STOPS)
    chain.doFilter(request, response);   // 2. pass it on: every later filter, then the controller
    // 3. after: the response is ready; look at its status
}
```

| | Who does it | Example in PIS |
| --- | --- | --- |
| **Pass it on** | almost every filter, most of the time | `RequestLogFilter` |
| **Answer and stop** | `BasicAuthenticationFilter`, on a wrong password: it writes the 401 and never calls `chain.doFilter` | `LoginRateLimitFilter`, when an address is blocked: 429 |
| **Act on the way back** | code after `chain.doFilter` runs after the controller has answered | `RequestLogFilter` logs the status; `LoginRateLimitFilter` counts 401s |

Put together, for one wrong password:

```
6 LoginRateLimitFilter ──► 8 BasicAuthenticationFilter: wrong password → writes 401, STOPS
        ▲                        (9 RequestLogFilter and the controller never run)
        └── on the way back: status is 401 → "Failed login 1 of 5"
```

### Where to put your own filter

| Your filter must … | Put it | In this lesson |
| --- | --- | --- |
| Refuse a request before any password is checked | **before** `BearerTokenAuthenticationFilter` | `LoginRateLimitFilter` |
| Know who the caller is | **after** `BasicAuthenticationFilter`: by then both login filters (7 and 8) have run | `RequestLogFilter` |
| Run before everything, even the headers | before `DisableEncodeUrlFilter` | a request id (Exercise 3) |

> **Not a bean.** Create your filters with `new`, inside `SecurityConfig`. Spring
> Boot also registers every `Filter` **bean** as an ordinary servlet filter, outside
> the security chain. A plain `Filter` then runs **twice** per request. (Checked: two
> identical log lines for one request.) `OncePerRequestFilter` notices the second
> run and skips it, but there's no reason to depend on that.

---

## Upgrade checklist

Starting from the end of Lesson 3D.

| | File | Change |
| --- | --- | --- |
| ➕ | `config/LoginLimitProperties.java` | `pis.security.login-limit.*`: how many failures, for how long |
| ➕ | `security/RequestLogFilter.java` | The access log: after the login filters |
| ➕ | `security/LoginRateLimitFilter.java` | The password-guessing lock: before the login filters |
| ✏️ | `config/ProblemDetailSecurityHandler.java` | `tooManyLogins(…)`: the 429, in the same problem-detail shape |
| ✏️ | `config/SecurityConfig.java` | `addFilterBefore` / `addFilterAfter`; `@EnableConfigurationProperties` |
| ✏️ | `application.yml` | `login-limit`: 5 failures, 1 minute |
| ✏️ | `application-test.yml` (test) | A high limit, so tests can't lock each other out |
| ➕ | `FilterChainTest.java` | New, 6 tests: the order of the chain, and what the log sees |
| ➕ | `LoginLimitTest.java` | New, 5 tests: the lock, through the whole app |
| ➕ | `security/LoginRateLimitFilterTest.java` | New, 2 tests: the filter alone, with a fake clock |

No migration and no new dependency: everything here is plain Spring Security.

---

## Build it

### 1 · Look before you change anything

Start the app with the filter chain's trace switched on, send any request, and
read the app's terminal. Try it, step 1, shows what to expect: before this
lesson it lists **14** filters, and after it **16**.

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--logging.level.org.springframework.security.web.FilterChainProxy=TRACE
```

### 2 · The settings

```java
// config/LoginLimitProperties.java
package tz.co.hmy.pis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * pis.security.login-limit.* from application.yml.
 *
 * @param maxFailures failed logins allowed from one address within the window
 * @param window      how long the count lasts, from the first failure ("1m", "15m")
 */
@ConfigurationProperties(prefix = "pis.security.login-limit")
public record LoginLimitProperties(int maxFailures, Duration window) { }
```

```yaml
# application.yml (changed part): under pis.security, after jwt
    # Password guessing: after 5 failed logins from one address, that address
    # must wait until a minute has passed since its first failure (429).
    # The smoke tests of Lessons 1-3 fail logins on purpose: raise the limit while
    # running them back to back, e.g.  LOGIN_MAX_FAILURES=50 mvn spring-boot:run
    login-limit:
      max-failures: ${LOGIN_MAX_FAILURES:5}
      window: 1m
```

The test profile gets a limit nobody reaches. Several test classes send wrong
passwords on purpose, all from the same address, and they share one Spring
context: with a limit of 5 they would lock each other out.

```yaml
# application-test.yml (changed part): under pis.security, after jwt
    # High, so the many wrong-password tests can't lock each other out.
    # LoginLimitTest sets its own, low limit.
    login-limit:
      max-failures: 1000
      window: 1m
```

### 3 · The request log: a filter AFTER the login filters

It reads the user and the status **after** `chain.doFilter`, so it sees the final
answer, including a 403 from `AuthorizationFilter`.

```java
// security/RequestLogFilter.java
package tz.co.hmy.pis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * An access log that names the user:  GET /api/v1/suppliers -> 200 by officer in 12 ms
 *
 * Placed AFTER the login filters (SecurityConfig), so the SecurityContext already
 * says who is calling. It reads the status after the rest of the chain has run,
 * so a request refused by AuthorizationFilter is logged with its 401 or 403.
 *
 * A login that FAILS never gets here: BasicAuthenticationFilter answers 401 itself
 * and stops the chain. LoginRateLimitFilter, placed before it, does see those.
 *
 * Not a @Component on purpose: Spring Boot also registers every Filter BEAN as a
 * plain servlet filter, outside the security chain. A plain Filter then runs twice
 * per request. OncePerRequestFilter notices and skips the second run, but there's
 * no reason to depend on that: create it with new, in SecurityConfig.
 */
@Slf4j
public class RequestLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);          // the rest of the chain, then the controller
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} by {} in {} ms", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), who(), ms);
        }
    }

    private static String who() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) return "anonymous";
        return auth.getName();
    }
}
```

### 4 · The 429, in the same shape as every other error

`ProblemDetailSecurityHandler` already writes the 401 and the 403. It gets one
public method for the 429, with a `Retry-After` header (RFC 9110) saying how many
seconds to wait.

```java
// config/ProblemDetailSecurityHandler.java
package tz.co.hmy.pis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * 401 and 403 as RFC 9457 problem details, the same shape as every other error.
 *
 * GlobalExceptionHandler cannot do this: security runs in a servlet filter,
 * before the request reaches any controller, so @RestControllerAdvice never sees it.
 * The same goes for the 429 from LoginRateLimitFilter.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** No credentials, wrong ones, or a bad or expired token. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            // RFC 6750: tell the client its token was the problem, so it knows to log in again.
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            write(response, HttpStatus.UNAUTHORIZED, "Invalid token",
                    "The token is invalid or has expired. Log in again at POST /api/v1/auth/login",
                    "invalid-token");
            return;
        }
        // A 401 must say which scheme to use (RFC 9110). This header is also
        // what makes a browser show its own username/password dialog.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"pis\"");
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required",
                "Send a valid username and password using HTTP Basic", "unauthorized");
    }

    /** Valid credentials, but the role does not allow this operation. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Access denied",
                "Your role does not permit this operation", "forbidden");
    }

    /** Too many failed logins from one address (LoginRateLimitFilter). */
    public void tooManyLogins(HttpServletResponse response, Duration wait) throws IOException {
        long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);       // round up, never 0
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many failed logins",
                "Too many failed logins from your address. Try again in " + seconds + " seconds",
                "too-many-logins");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title,
                       String detail, String type) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://hmy.co.tz/problems/" + type));
        pd.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
```

### 5 · The login limit: a filter BEFORE the login filters

```java
// security/LoginRateLimitFilter.java
package tz.co.hmy.pis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import tz.co.hmy.pis.config.LoginLimitProperties;
import tz.co.hmy.pis.config.ProblemDetailSecurityHandler;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops password guessing: after too many FAILED logins from one address, every
 * login attempt from that address gets 429 until the window ends.
 *
 * Placed BEFORE the login filters (SecurityConfig), for two reasons:
 *   - it must be able to refuse a request before any password is checked;
 *   - it wraps them, so it sees their answer: a 401 means a failed login.
 *
 * Only login attempts are filtered: POST /api/v1/auth/login, or any request with
 * "Authorization: Basic". A request with no credentials is not a guess.
 *
 * Not a @Component, for the same reason as RequestLogFilter.
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN = "/api/v1/auth/login";

    /** Failed logins from one address, counted from the first one in the window. */
    private record Failures(Instant since, int count) { }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final LoginLimitProperties limit;
    private final Clock clock;
    private final ProblemDetailSecurityHandler problems;

    public LoginRateLimitFilter(LoginLimitProperties limit, Clock clock, ProblemDetailSecurityHandler problems) {
        this.limit = limit;
        this.clock = clock;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean basic = authorization != null && authorization.startsWith("Basic ");
        boolean login = "POST".equals(request.getMethod()) && LOGIN.equals(request.getRequestURI());
        return !(basic || login);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // The address the connection came from. NOT the X-Forwarded-For header:
        // anyone can send that, and an attacker would just send a new one each time.
        String address = request.getRemoteAddr();

        Duration wait = blockedFor(address);
        if (wait != null) {
            problems.tooManyLogins(response, wait);    // answer here: the chain stops
            return;
        }

        chain.doFilter(request, response);             // the login filters, and the rest

        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            Failures f = failures.compute(address, (a, old) ->
                    old == null || expired(old) ? new Failures(clock.instant(), 1)
                                                : new Failures(old.since(), old.count() + 1));
            log.warn("Failed login {} of {} from {}", f.count(), limit.maxFailures(), address);
        }
    }

    /** How long this address must wait, or null if it may try. */
    private Duration blockedFor(String address) {
        Failures f = failures.get(address);
        if (f == null || expired(f) || f.count() < limit.maxFailures()) return null;
        return Duration.between(clock.instant(), f.since().plus(limit.window()));
    }

    private boolean expired(Failures f) {
        return !clock.instant().isBefore(f.since().plus(limit.window()));
    }
}
```

Four decisions worth discussing:

- **What counts** is a *failed* login, a 401 on the way back. Successful logins
  don't count, and neither do requests with no credentials at all.
- **What is blocked** is every login attempt from that address, including the
  right password. Otherwise an attacker just keeps guessing until one works.
- **The address** is `getRemoteAddr()`, the connection itself. The
  `X-Forwarded-For` header is written by the client, so an attacker would send a
  new one with every guess.
- **The `Clock`** is a constructor parameter, so a test can move time forward
  without waiting a minute.

### 6 · Put both filters in the chain

The two `addFilter…` lines at the end are the whole change to the chain. Each
names an existing filter to stand next to.

```java
// config/SecurityConfig.java
package tz.co.hmy.pis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import tz.co.hmy.pis.security.LoginRateLimitFilter;
import tz.co.hmy.pis.security.RequestLogFilter;

import java.time.Clock;

/**
 * Two ways to prove who you are, both checked by the same rules:
 *
 *   Authorization: Basic base64(username:password)   password on every request (Lessons 1-2)
 *   Authorization: Bearer <JWT>                      password once, at POST /api/v1/auth/login
 *
 * Base64 and JWT payloads are both readable by anyone, so either is only safe over HTTPS.
 *
 * Two separate questions, answered in two separate places:
 *   Authentication — who are you?       DatabaseUserDetailsService + passwordEncoder()
 *   Authorization  — what may you do?   the rules in filterChain()
 *
 * Users live in the app_user table. DatabaseUserDetailsService is the only
 * UserDetailsService bean, so Spring Security picks it up without being told.
 *
 * Every line of filterChain() adds, configures or removes a FILTER. A request goes
 * through them in order; run with --logging.level.org.springframework.security=TRACE
 * to watch it. Two of the filters are ours: see addFilterBefore / addFilterAfter.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(LoginLimitProperties.class)
public class SecurityConfig {

    private final ProblemDetailSecurityHandler problemHandler;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final LoginLimitProperties loginLimit;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF protects browser sessions that send cookies automatically.
                // This API has no session and no cookie, so there is nothing to forge.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Rules are checked top to bottom and the FIRST match wins,
                // so specific paths go above general ones.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()

                        // You can't need a token to get a token. For refresh and logout,
                        // the refresh token in the request body is the credential.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()

                        // Anyone logged in may ask who they are.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()

                        // From here on, every rule names a PERMISSION, never a role.
                        // Which roles hold which permission is data, in the role_permission table.
                        .requestMatchers("/api/v1/users/**").hasAuthority("user:manage")
                        .requestMatchers("/api/v1/roles/**", "/api/v1/permissions").hasAuthority("role:manage")

                        // Specific actions first: the first match wins.
                        .requestMatchers(HttpMethod.POST, "/api/v1/requisitions/*/approve",
                                "/api/v1/requisitions/*/reject").hasAuthority("requisition:approve")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/suppliers/*/status").hasAuthority("supplier:approve")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasAuthority("record:delete")

                        // Then read and write, one pair per resource.
                        .requestMatchers(HttpMethod.GET, "/api/v1/suppliers/**").hasAuthority("supplier:read")
                        .requestMatchers("/api/v1/suppliers/**").hasAuthority("supplier:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/requisitions/**").hasAuthority("requisition:read")
                        .requestMatchers("/api/v1/requisitions/**").hasAuthority("requisition:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/purchase-orders/**").hasAuthority("purchase-order:read")
                        .requestMatchers("/api/v1/purchase-orders/**").hasAuthority("purchase-order:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/invoices/**").hasAuthority("invoice:read")
                        .requestMatchers("/api/v1/invoices/**").hasAuthority("invoice:write")

                        // Anything not listed above is refused rather than left open.
                        .anyRequest().denyAll())

                .httpBasic(basic -> basic.authenticationEntryPoint(problemHandler))

                // Reads "Authorization: Bearer ...", checks signature and expiry with the
                // JwtDecoder bean, and turns the roles claim into ROLE_ authorities.
                .oauth2ResourceServer(jwt -> jwt
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)   // bad or expired token
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler)   // 401: who are you?
                        .accessDeniedHandler(problemHandler))       // 403: I know you, but no

                // Our own filters. Each is placed next to a filter Spring Security already has.
                // BEFORE the login filters: can refuse a password guess before it's checked.
                .addFilterBefore(new LoginRateLimitFilter(loginLimit, Clock.systemUTC(), problemHandler),
                        BearerTokenAuthenticationFilter.class)
                // AFTER the login filters: knows who the caller is.
                .addFilterAfter(new RequestLogFilter(), BasicAuthenticationFilter.class)
                .build();
    }

    /**
     * The same AuthenticationManager that HTTP Basic uses (DatabaseUserDetailsService
     * + BCrypt), exposed so TokenService can check a password at login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt by default. Hashes are stored with a prefix, e.g. {bcrypt}$2a$10$...,
     * so the algorithm can be changed later without invalidating existing passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

---

## Try it

Set the variables from the [testing guide](TESTING.md#before-you-start-set-your-variables)
(`$BASE`, `$OFFICER_PW`, `$APPROVER_PW`, `$ADMIN_PW`, `$ID`, `$JSON`). Keep the
app's terminal in view: half of what this lesson shows is in its log. Every
output below is from a real run.

**1 · Watch a request go through the chain** Stop the app (Ctrl+C) and start it with the filter chain's trace switched on:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--logging.level.org.springframework.security.web.FilterChainProxy=TRACE
```

Then send one request:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/suppliers
```
```
200
```

In the app's terminal:

```
Invoking DisableEncodeUrlFilter (1/16)
Invoking WebAsyncManagerIntegrationFilter (2/16)
Invoking SecurityContextHolderFilter (3/16)
Invoking HeaderWriterFilter (4/16)
Invoking LogoutFilter (5/16)
Invoking LoginRateLimitFilter (6/16)
Invoking BearerTokenAuthenticationFilter (7/16)
Invoking BasicAuthenticationFilter (8/16)
Invoking RequestLogFilter (9/16)
Invoking AuthenticationFilter (10/16)
Invoking RequestCacheAwareFilter (11/16)
Invoking SecurityContextHolderAwareRequestFilter (12/16)
Invoking AnonymousAuthenticationFilter (13/16)
Invoking SessionManagementFilter (14/16)
Invoking ExceptionTranslationFilter (15/16)
Invoking AuthorizationFilter (16/16)
```

When you've seen it, stop the app (Ctrl+C) and start it normally again (`mvn spring-boot:run`): the trace is very noisy.

**2 · The request log names the user** Four requests: allowed, refused by a rule, no credentials, and a JWT.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/suppliers
curl -s -o /dev/null -w '%{http_code}\n' -u officer:$OFFICER_PW -X PATCH -H "$JSON" -d '{"status":"ACTIVE"}' $BASE/api/v1/suppliers/$ID/status
curl -s -o /dev/null -w '%{http_code}\n' $BASE/api/v1/requisitions
TOKEN=$(curl -s -X POST -H "$JSON" -d "{\"username\":\"approver\",\"password\":\"$APPROVER_PW\"}" $BASE/api/v1/auth/login | jq -r .accessToken)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" $BASE/api/v1/invoices
```
```
200
403
401
200
```

In the app's terminal:

```
RequestLogFilter: GET /api/v1/suppliers -> 200 by officer in 22 ms
RequestLogFilter: PATCH /api/v1/suppliers/99999999-9999-9999-9999-999999999999/status -> 403 by officer in 5 ms
RequestLogFilter: GET /api/v1/requisitions -> 401 by anonymous in 0 ms
RequestLogFilter: POST /api/v1/auth/login -> 200 by anonymous in 123 ms
RequestLogFilter: GET /api/v1/invoices -> 200 by approver in 5 ms
```

**3 · A wrong password never reaches the request log** `BasicAuthenticationFilter` (8) answers 401 itself and stops the chain. Our rate-limit filter (6) is outside it, so it sees the 401 on the way back.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:wrong $BASE/api/v1/suppliers
```
```
401
```

In the app's terminal:

```
LoginRateLimitFilter: Failed login 1 of 5 from 0:0:0:0:0:0:0:1
```

**4 · Guessing passwords gets you blocked** Four more wrong guesses make five failures. After that, every login attempt from this address is refused, even with the right password, and even at the login endpoint.

```bash
for i in 1 2 3 4; do curl -s -o /dev/null -w "guess $i: %{http_code}\n" -u officer:guess$i $BASE/api/v1/suppliers; done
curl -s -D - -u officer:$OFFICER_PW $BASE/api/v1/suppliers | grep -iE '^(HTTP|retry-after)|title'
curl -s -X POST -H "$JSON" -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PW\"}" $BASE/api/v1/auth/login | jq -c '{status, title}'
```
```
guess 1: 401
guess 2: 401
guess 3: 401
guess 4: 401
HTTP/1.1 429 
Retry-After: 59
{"type":"https://hmy.co.tz/problems/too-many-logins","title":"Too many failed logins","status":429,"detail":"Too many failed logins from your address. Try again in 59 seconds","timestamp":"2026-10-20T08:49:16.909643Z"}
{"status":429,"title":"Too many failed logins"}
```

In the app's terminal:

```
LoginRateLimitFilter: Failed login 2 of 5 from 0:0:0:0:0:0:0:1
LoginRateLimitFilter: Failed login 3 of 5 from 0:0:0:0:0:0:0:1
LoginRateLimitFilter: Failed login 4 of 5 from 0:0:0:0:0:0:0:1
LoginRateLimitFilter: Failed login 5 of 5 from 0:0:0:0:0:0:0:1
```

**5 · Requests that aren't logins still work** Only login attempts are blocked. A request with no credentials isn't a guess.

```bash
curl -s -o /dev/null -w 'no credentials: %{http_code}\n' $BASE/api/v1/suppliers
curl -s -o /dev/null -w 'health:         %{http_code}\n' $BASE/actuator/health
```
```
no credentials: 401
health:         200
```

**6 · A minute later, the block lifts** The window is one minute from the **first** failure: the single wrong password before the guessing.

```bash
sleep 60
curl -s -o /dev/null -w 'right password: %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/suppliers
```
```
right password: 200
```

The same steps, with what to check when one doesn't match, are in the
[testing guide](TESTING.md#lesson-3e--the-security-filter-chain).

---

## Test it

Three test classes. `FilterChainTest` asks the chain itself what's in it:
`FilterChainProxy` is a bean, and `getFilterChains()` returns the same list the
`TRACE` log shows. `OutputCaptureExtension` hands each test the log it wrote.

```java
// (test) FilterChainTest.java
package tz.co.hmy.pis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.security.LoginRateLimitFilter;
import tz.co.hmy.pis.security.RequestLogFilter;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security filter chain itself: what's in it, in which order, and what our
 * two filters see. OutputCaptureExtension hands each test the log it produced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class FilterChainTest {

    private static final String SUPPLIER_ID = "33333333-3333-3333-3333-333333333333";

    @Autowired MockMvc mvc;
    @Autowired FilterChainProxy filterChainProxy;      // the bean that runs every SecurityFilterChain
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createUser() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
    }

    // ---------------------------------------------------------------- the chain

    @Test
    void the_chain_is_a_list_of_filters_and_ours_are_where_we_put_them() {
        List<String> filters = filterChainProxy.getFilterChains().get(0).getFilters().stream()
                .map(f -> f.getClass().getSimpleName()).toList();

        // addFilterBefore(..., BearerTokenAuthenticationFilter.class)
        assertThat(filters.indexOf("LoginRateLimitFilter"))
                .isEqualTo(filters.indexOf("BearerTokenAuthenticationFilter") - 1);
        // addFilterAfter(..., BasicAuthenticationFilter.class)
        assertThat(filters.indexOf("RequestLogFilter"))
                .isEqualTo(filters.indexOf("BasicAuthenticationFilter") + 1);
        // the decision is always last
        assertThat(filters).endsWith("ExceptionTranslationFilter", "AuthorizationFilter");
        // .csrf(AbstractHttpConfigurer::disable) REMOVES a filter
        assertThat(filters).doesNotContain("CsrfFilter");
    }

    /** A Filter bean is ALSO registered as a plain servlet filter, outside the chain. */
    @Test
    void our_filters_are_not_beans() {
        assertThat(context.getBeansOfType(RequestLogFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(LoginRateLimitFilter.class)).isEmpty();
    }

    // ---------------------------------------------------------------- RequestLogFilter

    @Test
    void the_request_log_names_the_user_and_the_status(CapturedOutput log) throws Exception {
        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")))
            .andExpect(status().isOk());

        assertThat(log.getOut()).contains("GET /api/v1/suppliers -> 200 by officer in ");
    }

    /** AuthorizationFilter runs after ours, so we see its refusal on the way back. */
    @Test
    void a_refused_request_is_logged_with_its_403(CapturedOutput log) throws Exception {
        mvc.perform(patch("/api/v1/suppliers/{id}/status", SUPPLIER_ID).with(httpBasic("officer", "officer-pass"))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"status\": \"ACTIVE\" }"))
            .andExpect(status().isForbidden());

        assertThat(log.getOut()).contains("PATCH /api/v1/suppliers/" + SUPPLIER_ID + "/status -> 403 by officer");
    }

    @Test
    void no_credentials_is_logged_as_anonymous(CapturedOutput log) throws Exception {
        mvc.perform(get("/api/v1/requisitions")).andExpect(status().isUnauthorized());

        assertThat(log.getOut()).contains("GET /api/v1/requisitions -> 401 by anonymous");
    }

    /**
     * A wrong password: BasicAuthenticationFilter answers 401 and STOPS the chain,
     * so the request never reaches RequestLogFilter. LoginRateLimitFilter, placed
     * before it, sees the 401 on the way back.
     */
    @Test
    void a_failed_login_stops_the_chain_before_the_request_log(CapturedOutput log) throws Exception {
        mvc.perform(get("/api/v1/invoices").with(httpBasic("officer", "wrong"))
                .with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
            .andExpect(status().isUnauthorized());

        assertThat(log.getOut()).doesNotContain("GET /api/v1/invoices ->");
        assertThat(log.getOut()).contains("Failed login 1 of 1000 from 10.9.9.9");
    }
}
```

`LoginLimitTest` runs the lock through the whole app with a limit of 3. Each test
sends from its own address with `setRemoteAddr`, because the counts live as long
as the Spring context does.

```java
// (test) LoginLimitTest.java
package tz.co.hmy.pis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LoginRateLimitFilter through the whole chain, with a limit of 3.
 *
 * Its own limit makes this a separate Spring context. Each test sends from its own
 * address, because the filter's counts live as long as the context does.
 */
@SpringBootTest(properties = "pis.security.login-limit.max-failures=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginLimitTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createUser() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
    }

    private static RequestPostProcessor from(String address) {
        return request -> { request.setRemoteAddr(address); return request; };
    }

    private void wrongBasicLogin(String address) throws Exception {
        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "guess")).with(from(address)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void after_three_failures_even_the_right_password_is_refused() throws Exception {
        for (int i = 0; i < 3; i++) wrongBasicLogin("10.0.0.1");

        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")).with(from("10.0.0.1")))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Too many failed logins"));
    }

    @Test
    void failures_at_the_login_endpoint_count_too() throws Exception {
        String wrong = "{ \"username\": \"officer\", \"password\": \"guess\" }";
        String right = "{ \"username\": \"officer\", \"password\": \"officer-pass\" }";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").with(from("10.0.0.2"))
                    .contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").with(from("10.0.0.2"))
                .contentType(MediaType.APPLICATION_JSON).content(right))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void another_address_is_not_affected() throws Exception {
        for (int i = 0; i < 3; i++) wrongBasicLogin("10.0.0.3");

        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")).with(from("10.0.0.4")))
            .andExpect(status().isOk());
    }

    @Test
    void successful_logins_do_not_count() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")).with(from("10.0.0.5")))
                .andExpect(status().isOk());
        }
    }

    /** No credentials is a 401 too, but it isn't a guess: the filter skips it. */
    @Test
    void requests_without_credentials_do_not_count() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/suppliers").with(from("10.0.0.6"))).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/suppliers").with(httpBasic("officer", "officer-pass")).with(from("10.0.0.6")))
            .andExpect(status().isOk());
    }
}
```

A filter is a plain class, so it can be tested without Spring at all: a mock
request, a mock chain whose "servlet" always answers 401, and a clock the test
moves by hand.

```java
// (test) security/LoginRateLimitFilterTest.java
package tz.co.hmy.pis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tz.co.hmy.pis.config.LoginLimitProperties;
import tz.co.hmy.pis.config.ProblemDetailSecurityHandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A filter is a plain class: it can be tested without Spring, with mock
 * requests and a fake clock, so "wait a minute" takes no time at all.
 */
class LoginRateLimitFilterTest {

    /** A clock the test moves by hand. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2030-01-01T08:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    final TestClock clock = new TestClock();
    final LoginRateLimitFilter filter = new LoginRateLimitFilter(
            new LoginLimitProperties(2, Duration.ofMinutes(1)), clock,
            new ProblemDetailSecurityHandler(new ObjectMapper().findAndRegisterModules()));

    /** Sends one Basic login through the filter; behind it, every password is wrong. */
    private MockHttpServletResponse attempt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/suppliers");
        request.addHeader("Authorization", "Basic b2ZmaWNlcjpndWVzcw==");   // officer:guess
        request.setRemoteAddr("10.1.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                res.setStatus(401);                                            // "wrong password"
            }
        }));
        return response;
    }

    @Test
    void the_block_lifts_when_the_window_ends() throws Exception {
        assertThat(attempt().getStatus()).isEqualTo(401);
        assertThat(attempt().getStatus()).isEqualTo(401);
        assertThat(attempt().getStatus()).isEqualTo(429);                    // blocked

        clock.advance(Duration.ofSeconds(59));
        assertThat(attempt().getStatus()).isEqualTo(429);                    // still blocked

        clock.advance(Duration.ofSeconds(1));                                 // a minute after the first failure
        assertThat(attempt().getStatus()).isEqualTo(401);                    // may try again
    }

    @Test
    void retry_after_says_how_long_to_wait() throws Exception {
        attempt();
        clock.advance(Duration.ofSeconds(20));
        attempt();

        MockHttpServletResponse blocked = attempt();
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("40");          // 60 s from the FIRST failure
        assertThat(blocked.getContentAsString()).contains("Try again in 40 seconds");
    }
}
```

| Test | Proves |
| --- | --- |
| `the_chain_is_a_list_of_filters_and_ours_are_where_we_put_them` | `addFilterBefore` / `addFilterAfter` put ours exactly next to their neighbours; CSRF is gone |
| `our_filters_are_not_beans` | Nobody made them a `@Component` |
| `the_request_log_names_the_user_and_the_status` | The log runs after login |
| `a_refused_request_is_logged_with_its_403` | It sees `AuthorizationFilter`'s answer on the way back |
| `no_credentials_is_logged_as_anonymous` | `AnonymousAuthenticationFilter` |
| `a_failed_login_stops_the_chain_before_the_request_log` | Basic answers 401 and stops; the rate limit still sees it |
| `after_three_failures_even_the_right_password_is_refused` | 429, `Retry-After`, problem detail |
| `failures_at_the_login_endpoint_count_too` | Both doors: Basic and `POST /auth/login` |
| `another_address_is_not_affected` | The count is per address |
| `successful_logins_do_not_count`, `requests_without_credentials_do_not_count` | Only failed logins count |
| `the_block_lifts_when_the_window_ends`, `retry_after_says_how_long_to_wait` | The window, with a fake clock |

These were checked the other way round too. Making `RequestLogFilter` a
`@Component` fails `our_filters_are_not_beans`. Moving it **before**
`BasicAuthenticationFilter` fails two tests: the order test, and
`a_failed_login_stops_the_chain_before_the_request_log`, because a failed login
is then logged.

```bash
mvn clean test
# … FilterChainTest 6 · LoginLimitTest 5 · LoginRateLimitFilterTest 2 · … → Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
```

---

## Common mistakes

<details>
<summary><b>The filter is a <code>@Component</code></b></summary>

Spring Boot registers every `Filter` bean as an ordinary servlet filter as well.
A plain `Filter` (say, a `GenericFilterBean`) then runs twice for every request:
checked, two identical log lines for one `curl`. `OncePerRequestFilter` detects
the second run and skips it. Create filters with `new` in `SecurityConfig`;
`our_filters_are_not_beans` guards it.
</details>

<details>
<summary><b>Every request is an empty <code>200</code>, even the health check</b></summary>

A filter forgot `chain.doFilter(request, response)`. The request stops inside it:
no later filter runs, no controller runs, and the client gets whatever the filter
wrote, which is nothing. (Checked: `Content-Length: 0` for `/api/v1/suppliers`
and for `/actuator/health`.) Only skip `chain.doFilter` when the filter has
written the answer itself, like the 429.
</details>

<details>
<summary><b>The other lessons' smoke tests suddenly get <code>429</code></b></summary>

They send wrong passwords on purpose: Lessons 1, 2 and 3 together make exactly
five failed logins, so the scripts after them are locked out for a minute. Start
the app with a higher limit while running them back to back:
`LOGIN_MAX_FAILURES=50 mvn spring-boot:run`.
</details>

<details>
<summary><b>The limit trusts <code>X-Forwarded-For</code></b></summary>

The client writes that header, so the attacker picks a new "address" for every
guess and is never blocked. Use `request.getRemoteAddr()`. Behind a proxy or a
gateway (Lecture 5) every request comes from the proxy's address, and one attacker
would lock out everyone: then let Spring read the forwarded address
(`server.forward-headers-strategy`), trusting only your own proxy.
</details>

<details>
<summary><b>The trace shows <code>Securing GET …</code> but no <code>Invoking …</code> lines</b></summary>

The level is `DEBUG`. The per-filter lines are `TRACE`:
`--logging.level.org.springframework.security.web.FilterChainProxy=TRACE`.
</details>

<details>
<summary><b><code>addFilterAfter(…, BasicAuthenticationFilter.class)</code>, but JWT requests aren't logged with the user</b></summary>

They are: `BearerTokenAuthenticationFilter` (7) runs before Basic (8), so after
8 both logins have run. If you see `anonymous` for a token, the token wasn't sent:
check the `Authorization: Bearer` header.
</details>

---

## Exercises

### 1 · Put CSRF back — *warm-up*

Comment out `.csrf(AbstractHttpConfigurer::disable)` and start the app with the
trace. Which filter appears, and where? Then try `POST /api/v1/auth/login`, and a
`POST /api/v1/suppliers` with Basic. What do you get, and is the error message
honest? Put the line back afterwards.

<details>
<summary>Solution</summary>

`CsrfFilter` appears at position 5 of 17, between `HeaderWriterFilter` and
`LogoutFilter`; every filter after it moves down one. Every `POST` is refused with **403**,
even the login, because none of them carries a CSRF token. The message says
"Your role does not permit this operation", which is wrong: a missing CSRF token
reaches `ProblemDetailSecurityHandler` as an `AccessDeniedException`, the same as
a missing permission. One config line, one filter, and a different API.
</details>

### 2 · Break it: run a filter twice — *understanding*

Change `RequestLogFilter` to extend `GenericFilterBean` (rename `doFilterInternal`
to `doFilter(ServletRequest, ServletResponse, FilterChain)`), annotate it with
`@Component`, and inject it into `SecurityConfig` instead of `new`. How many log
lines does one request write? Now go back to `OncePerRequestFilter`, keeping
`@Component`. How many now?

<details>
<summary>Solution</summary>

Two lines, then one. As a bean it's registered twice: inside the security chain
(where you put it) and as an ordinary servlet filter. `OncePerRequestFilter` marks
the request the first time and skips the second run. `our_filters_are_not_beans`
fails in both cases, which is the point of that test.
</details>

### 3 · A request id — *core*

Write a `RequestIdFilter`: take the caller's `X-Request-Id` header if it looks
sane, or make a new UUID; put it in the response header, and in every log line
for that request. Where in the chain does it go?

<details>
<summary>Solution</summary>

First of all, before `DisableEncodeUrlFilter`, so even a 429 or a 401 carries it:

```java
// security/RequestIdFilter.java
package tz.co.hmy.pis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request an id: the caller's X-Request-Id if it looks sane, or a new one.
 * The id goes back in the response, and into every log line written for the request.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || !id.matches("[A-Za-z0-9-]{1,64}")) id = UUID.randomUUID().toString();
        response.setHeader(HEADER, id);
        MDC.put("requestId", id);                  // %X{requestId} in the log pattern
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");               // the thread serves other requests next
        }
    }
}
```

In `SecurityConfig`, next to the other two:
`.addFilterBefore(new RequestIdFilter(), DisableEncodeUrlFilter.class)`
(import `org.springframework.security.web.session.DisableEncodeUrlFilter`). In
`application.yml`, show it in the log:

```yaml
logging:
  pattern:
    level: "%5p [%X{requestId}]"
```

Checked: `curl -H 'X-Request-Id: class-demo-42' …` returns
`X-Request-Id: class-demo-42`, and the request log line starts with
`INFO [class-demo-42]`. Without the header, each request gets its own UUID. The
check on the header stops a caller from writing anything they like into your logs.
</details>

### 4 · One address, many users — *core, design*

The lock counts per **address**. An office behind one router shares one address:
a single user mistyping five times locks out the whole office for a minute. And an
attacker with many addresses is never locked. What would you count instead, or as
well? What does each choice let an attacker do?

<details>
<summary>Discussion</summary>

Per **username**: an attacker can lock any account by guessing wrong on purpose,
from anywhere (a denial of service against that user). Per **address**: the
office problem. Real systems combine them: a short per-address limit, a longer
per-username one, and slower answers rather than a hard block. Counting per
username needs the name, which for `POST /auth/login` is in the JSON body: a
filter that reads the body must wrap the request so the controller can read it again.
</details>

### 5 · A second chain — *stretch, design*

PIS has one `SecurityFilterChain` for every request. Sketch a second one, just
for `/actuator/**`, that allows `health` to everyone and everything else only to
`ADMIN`, with HTTP Basic and nothing else. Which two calls make Spring pick the
right chain? What happens to a request that matches both?

<details>
<summary>Discussion</summary>

`http.securityMatcher("/actuator/**")` limits which requests a chain claims, and
`@Order(1)` on its bean puts it before the general one. `FilterChainProxy` uses
the **first** chain whose matcher fits, and only that one, so the order decides.
The general chain stays last, matching everything. Check it the same way as this
lesson: `getFilterChains()` returns two lists, and the trace says which chain
handled each request.
</details>

---

## Quiz

**1. Why doesn't `@RestControllerAdvice` see a 401 from Spring Security?**\
a) It's disabled for security  b) Security runs in servlet filters, before the request reaches any controller  c) 401s aren't exceptions

**2. You add `.addFilterAfter(myFilter, BasicAuthenticationFilter.class)`. Inside `myFilter`, who is the caller for a request with a valid JWT?**\
a) Nobody yet  b) The token's user: the Bearer filter ran before Basic  c) `anonymousUser`

**3. A wrong Basic password. Does `RequestLogFilter` (position 9) log the request?**\
a) Yes, with 401  b) No: `BasicAuthenticationFilter` writes the 401 and stops the chain  c) Only with `TRACE` on

**4. How does `LoginRateLimitFilter` know a login failed?**\
a) It checks the password itself  b) It calls `chain.doFilter` and looks at the status on the way back: 401  c) It reads the log

**5. Why not take the address from `X-Forwarded-For`?**\
a) It's slower  b) The client writes it, so an attacker sends a new one with every guess  c) Tomcat removes it

**6. Why are the filters created with `new` instead of being `@Component`s?**\
a) Filters can't be beans  b) A filter bean is also registered as a plain servlet filter, outside the chain  c) `new` is faster

<details>
<summary>Answers</summary>

1. **b.** That's why `ProblemDetailSecurityHandler` writes those answers itself.
2. **b.** After filter 8, both login filters have run.
3. **b.** Only filters before 8 see it, on the way back.
4. **b.** A filter can act after the rest of the chain has answered.
5. **b.** Use `getRemoteAddr()`, and configure trusted proxies when there is one.
6. **b.** A plain filter then runs twice; `OncePerRequestFilter` skips the second run.
</details>

---

## Smoke test

Save as `smoke.sh` and run while the app is up. It guesses passwords until your
machine is blocked, then waits for the block to lift, so it takes a little over a
minute. Run it on its own: their wrong passwords make the other smoke tests count
against the limit too.

```bash
#!/usr/bin/env bash
# Lesson 3E smoke test: our own filters in the security filter chain.
# Guesses passwords until this machine is blocked, then waits for the block to
# lift, so it takes a little over a minute. Run it on its own: at least a minute
# after any other smoke test, because their wrong passwords count too.
BASE=http://localhost:8080
OFFICER='officer:officer123'
MAX=${MAX:-5}                       # pis.security.login-limit.max-failures (LOGIN_MAX_FAILURES)
JSON='Content-Type: application/json'

check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  [ "$got" = "$want" ] && echo "PASS  $got  $desc" || echo "FAIL  $got  $desc (expected $want)"
}

echo "--- before: the right password works"
check 200 "officer reads suppliers"                          -u "$OFFICER" "$BASE/api/v1/suppliers"

echo "--- guessing: $MAX wrong passwords, each one a 401"
for i in $(seq 1 "$MAX"); do
  check 401 "wrong password $i of $MAX"                      -u "officer:guess$i" "$BASE/api/v1/suppliers"
done

echo "--- blocked: every login attempt from this address is refused"
check 429 "even the right password"                          -u "$OFFICER" "$BASE/api/v1/suppliers"
check 429 "and the login endpoint"                           -X POST -H "$JSON" \
      -d '{"username":"officer","password":"officer123"}' "$BASE/api/v1/auth/login"
wait=$(curl -s -D - -o /dev/null -u "$OFFICER" "$BASE/api/v1/suppliers" | tr -d '\r' | awk -F': ' 'tolower($1)=="retry-after"{print $2}')
[ -n "$wait" ] && echo "PASS       Retry-After: $wait seconds" || echo "FAIL       no Retry-After header"
type=$(curl -s -D - -o /dev/null -u "$OFFICER" "$BASE/api/v1/suppliers" | tr -d '\r' | awk -F': ' 'tolower($1)=="content-type"{print $2}')
[ "$type" = "application/problem+json" ] && echo "PASS       the 429 is a problem detail ($type)" || echo "FAIL       content type: $type"

echo "--- not logins: still answered as usual"
check 401 "no credentials is a 401, not a 429"               "$BASE/api/v1/suppliers"
check 200 "health"                                           "$BASE/actuator/health"

echo "--- waiting ${wait:-60} seconds for the block to lift"
sleep "${wait:-60}"
check 200 "the right password works again"                   -u "$OFFICER" "$BASE/api/v1/suppliers"
```

Expected output:

```
--- before: the right password works
PASS  200  officer reads suppliers
--- guessing: 5 wrong passwords, each one a 401
PASS  401  wrong password 1 of 5
PASS  401  wrong password 2 of 5
PASS  401  wrong password 3 of 5
PASS  401  wrong password 4 of 5
PASS  401  wrong password 5 of 5
--- blocked: every login attempt from this address is refused
PASS  429  even the right password
PASS  429  and the login endpoint
PASS       Retry-After: 60 seconds
PASS       the 429 is a problem detail (application/problem+json)
--- not logins: still answered as usual
PASS  401  no credentials is a 401, not a 429
PASS  200  health
--- waiting 60 seconds for the block to lift
PASS  200  the right password works again
```

---

## Next

**→ [Lesson 3F — URL Rules in the Database](03f-url-rules-in-database.md).** The last
filter, `AuthorizationFilter`, asks the `requestMatchers` rules. Lesson 3F moves
those rules into a table.

After that, [Lecture 4 — OAuth2 with Spring Authorization Server](../../../pis-lecture4-authorization-server/LECTURE.md):
its resource server has a filter chain too. Run it with the same `TRACE` switch
and compare its list with this one.
