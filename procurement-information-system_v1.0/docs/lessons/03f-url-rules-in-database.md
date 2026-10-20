# Lesson 3F — URL Rules in the Database

> **Change what an endpoint requires, without a release.**
> Lesson 3D moved *who holds which permission* into tables. The other half is
> still code: the `requestMatchers(...)` lines that say *which permission each
> endpoint needs*. This lesson moves those into a `url_rule` table too, checked by
> your own `AuthorizationManager`, and shows what that costs: a wrong row can
> close an endpoint, or open one.

```
Lesson 3E:  .requestMatchers(HttpMethod.GET, "/api/v1/suppliers/**").hasAuthority("supplier:read")   change = release
Lesson 3F:  url_rule: 50 | GET | /api/v1/suppliers/** | SUPPLIER_READ                                   change = one API call
```

| Duration | Builds on | You should know |
| --- | --- | --- |
| About 2 hours | [Lesson 3E — The Security Filter Chain](03e-filter-chain.md) | `Permission`, the roles API (3D), `AuthorizationFilter` (3E), Flyway |

**Contents:** [Goals](#goals) · [Concepts](#concepts) · [Upgrade checklist](#upgrade-checklist) ·
[Build it](#build-it) · [Try it](#try-it) · [Test it](#test-it) · [Common mistakes](#common-mistakes) ·
[Exercises](#exercises) · [Quiz](#quiz) · [Smoke test](#smoke-test)

> **Complete files.** Every code block is a complete file with its `package` and
> `import` lines, taken from the tested project, unless it's labelled "changed part".

---

## Goals

By the end of this lesson you can:

- Tell the two mappings apart: role → permission (3D) and URL → permission (this lesson).
- Decide which rules must stay in code, and explain why.
- Write an `AuthorizationManager` that reads rules from a table, and plug it in with `.access(...)`.
- Explain "the first match decides" and "no match: denied", and show how deleting a rule can still **open** an endpoint.
- Cache the rules safely: reload after the transaction commits, and not between tests.
- Build guard rails for rules an admin edits at runtime.
- Argue whether a real system should do this at all.

### Lesson plan

| Time (min) | Activity |
| --- | --- |
| 0–20 | Concepts: two mappings; what stays in code; first match; the cache |
| 20–75 | Build it: migration, entity, `DatabaseUrlRules`, service, controller, the one-line `SecurityConfig` change |
| 75–100 | Try it: change an endpoint's rule while PIS runs; delete one and watch it open |
| 100–120 | Tests, the lock-out exercise, quiz, the design discussion |

---

## Concepts

### Two mappings

| Mapping | Example | Lesson 3E | Lesson 3F | Changed by |
| --- | --- | --- | --- | --- |
| **Role → permission** | APPROVER has `supplier:read` | `role_permission` table | `role_permission` table | an admin, at runtime (3D) |
| **URL → permission** | `GET /api/v1/suppliers/**` needs `supplier:read` | `SecurityConfig` | **`url_rule` table** | an admin, at runtime (this lesson) |

Lesson 3D already covers the usual request, "let approvers edit suppliers": give
APPROVER `supplier:write`. This lesson covers a different one: "from now on,
reading requisitions needs `requisition:approve`". That changes what the
**endpoint** needs, for everyone.

### What stays in code

| Rule | Why it stays in `SecurityConfig` |
| --- | --- |
| Health, Swagger, login, refresh, logout: `permitAll()` | A rule that opens an endpoint to everyone is too dangerous to be one row away |
| `GET /api/v1/users/me`: any logged-in user | "Logged in" isn't a permission |
| `/users`, `/roles`, `/permissions`, `/url-rules`: `user:manage` / `role:manage` | **The lock-out.** If a row could protect `/url-rules`, one wrong row would stop every admin from fixing the rows. Checked: with the table asked first, even the admin gets 403 on `/url-rules` |
| `.anyRequest().denyAll()` | Anything outside `/api/v1` stays shut |

Everything else under `/api/v1` goes to the table, in **one** line:

```java
.requestMatchers("/api/v1/**").access(urlRules)
```

`access(...)` takes any `AuthorizationManager`. `AuthorizationFilter`, the last
filter of the chain (Lesson 3E), calls its `check(...)` for every such request.

### How the table decides

```
GET /api/v1/requisitions, by an officer
  rule 10  POST   /api/v1/requisitions/*/approve   no (method)
  rule 20  POST   /api/v1/requisitions/*/reject    no
  …
  rule 70  GET    /api/v1/requisitions/**          YES → needs requisition:read → the officer has it → allowed
  (rules after 70 are never looked at)
```

- **The first match decides.** That's why specific rules have lower priorities
  than general ones, exactly like the order of `requestMatchers` lines.
- **No match: denied**, even for the admin, who has every permission. So a rule
  that's *missing* closes an endpoint.
- **But deleting a rule can open one.** Delete rule 30 (a status change needs
  `supplier:approve`) and the next match is rule 60 (any method on suppliers
  needs `supplier:write`), which officers have. Try it, step 6, shows it happen.

### The cache

The rules are read from the table once and kept in memory: a query on every
request would cost more than the request. So every change must be followed by a
reload, and the reload must happen **after** the change has committed. The
controller does it after the service method returns, when the service's
transaction is over.

With several copies of PIS behind a load balancer (Lecture 5), only the copy that
handled the change reloads. The others keep the old rules until they reload too
(Exercise 4).

### Should a real system do this?

| | Rules in code (3E) | Rules in a table (3F) |
| --- | --- | --- |
| A change needs | a release | one API call |
| Reviewed | in a pull request, with the code | by nobody, unless you add it |
| Proven by | the tests | the tests prove the **seeded** rows; later rows are untested |
| A typo | fails to compile, or fails a test | silently matches nothing, or the wrong thing |
| Cost per request | none | a list lookup, plus a cache to keep fresh |

Most systems keep URL rules in code and put **role → permission** in the
database, as PIS did after 3D. Rules in a table make sense when admins create new
kinds of resources without a release. This lesson builds it so you can judge
that trade-off from working code, and so you know what guard rails it needs.

---

## Upgrade checklist

Starting from the end of Lesson 3E.

| | File | Change |
| --- | --- | --- |
| ➕ | `db/migration/V8__create_url_rule.sql` | The table, seeded with the 12 rules `SecurityConfig` had |
| ➕ | `model/UrlRule.java` | The `url_rule` table |
| ➕ | `repository/UrlRuleRepository.java` | The rules in priority order |
| ➕ | `security/DatabaseUrlRules.java` | The `AuthorizationManager`: first match decides, no match denied, cached |
| ➕ | `dto/UrlRuleRequest.java`, `dto/UrlRuleResponse.java`, `dto/UrlRuleMatch.java` | New |
| ➕ | `service/UrlRuleService.java` | The guard rails |
| ➕ | `controller/UrlRuleController.java` | `/api/v1/url-rules`, plus `/match`; reloads after each change |
| ✏️ | `config/SecurityConfig.java` | 12 rules become `.requestMatchers("/api/v1/**").access(urlRules)`; `/url-rules` needs `role:manage` |
| ➕ | `UrlRuleTest.java` | New, 11 tests |

Every other test class stays as it is, and must still pass: that's the proof that
the seeded table behaves exactly like the old code.

---

## Build it

### 1 · The migration

The 12 rules from `SecurityConfig`, in the same order. Priorities go up in tens,
so a new rule fits between two old ones.

```sql
-- db/migration/V8__create_url_rule.sql
-- The URL rules move from code (SecurityConfig) into a table, so an admin can
-- change what an endpoint requires without a release.
--
-- Each request under /api/v1 is checked against the rules in priority order, and
-- the FIRST match decides: the caller needs that rule's permission. No match: denied.
--
-- The admin endpoints (/users, /roles, /permissions, /url-rules) stay in code,
-- so no row in this table can lock the admins out of fixing it.

CREATE TABLE url_rule (
    id          BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    priority    INT           NOT NULL,              -- lower first; gaps leave room to insert
    http_method VARCHAR(10),                         -- NULL = any method
    pattern     VARCHAR(200)  NOT NULL,              -- a Spring path pattern: * one segment, ** the rest
    permission  VARCHAR(40)   NOT NULL,              -- the Permission enum's name, e.g. SUPPLIER_READ
    description VARCHAR(200)  NOT NULL,              -- why the rule exists
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(50),
    updated_by  VARCHAR(50),
    CONSTRAINT uk_url_rule_priority UNIQUE (priority),
    CONSTRAINT ck_url_rule_method CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE'))
);

-- Exactly the rules SecurityConfig had at the end of Lesson 3E, in the same order.
INSERT INTO url_rule (priority, http_method, pattern, permission, description, created_at, created_by) VALUES
    ( 10, 'POST',   '/api/v1/requisitions/*/approve', 'REQUISITION_APPROVE',  'Approving is a different job from requesting', now(), 'system'),
    ( 20, 'POST',   '/api/v1/requisitions/*/reject',  'REQUISITION_APPROVE',  'Rejecting is part of approving',               now(), 'system'),
    ( 30, 'PATCH',  '/api/v1/suppliers/*/status',     'SUPPLIER_APPROVE',     'Activating or suspending a supplier',          now(), 'system'),
    ( 40, 'DELETE', '/api/v1/**',                     'RECORD_DELETE',        'Deleting anything',                            now(), 'system'),
    ( 50, 'GET',    '/api/v1/suppliers/**',           'SUPPLIER_READ',        'Reading suppliers',                            now(), 'system'),
    ( 60, NULL,     '/api/v1/suppliers/**',           'SUPPLIER_WRITE',       'Creating and changing suppliers',              now(), 'system'),
    ( 70, 'GET',    '/api/v1/requisitions/**',        'REQUISITION_READ',     'Reading requisitions',                         now(), 'system'),
    ( 80, NULL,     '/api/v1/requisitions/**',        'REQUISITION_WRITE',    'Raising and changing requisitions',            now(), 'system'),
    ( 90, 'GET',    '/api/v1/purchase-orders/**',     'PURCHASE_ORDER_READ',  'Reading purchase orders',                      now(), 'system'),
    (100, NULL,     '/api/v1/purchase-orders/**',     'PURCHASE_ORDER_WRITE', 'Preparing purchase orders',                    now(), 'system'),
    (110, 'GET',    '/api/v1/invoices/**',            'INVOICE_READ',         'Reading invoices',                             now(), 'system'),
    (120, NULL,     '/api/v1/invoices/**',            'INVOICE_WRITE',        'Recording invoices',                           now(), 'system');
```

### 2 · The entity and its repository

```java
// model/UrlRule.java
package tz.co.hmy.pis.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One URL rule: requests matching httpMethod + pattern need this permission.
 * The url_rule table replaces the requestMatchers(...) lines of SecurityConfig.
 */
@Entity
@Table(name = "url_rule")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"priority", "httpMethod", "pattern", "permission"})
public class UrlRule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lower is checked first. The first matching rule decides. */
    @Column(nullable = false, unique = true)
    private int priority;

    /** GET, POST, PUT, PATCH or DELETE; null means any method. */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(nullable = false, length = 200)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Permission permission;

    @Column(nullable = false, length = 200)
    private String description;

    public UrlRule(int priority, String httpMethod, String pattern, Permission permission, String description) {
        change(priority, httpMethod, pattern, permission, description);
    }

    public void change(int priority, String httpMethod, String pattern, Permission permission, String description) {
        this.priority = priority;
        this.httpMethod = httpMethod;
        this.pattern = pattern;
        this.permission = permission;
        this.description = description;
    }
}
```

```java
// repository/UrlRuleRepository.java
package tz.co.hmy.pis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.hmy.pis.model.UrlRule;

import java.util.List;
import java.util.Optional;

public interface UrlRuleRepository extends JpaRepository<UrlRule, Long> {

    /** In the order they are checked. */
    List<UrlRule> findAllByOrderByPriorityAsc();

    Optional<UrlRule> findByPriority(int priority);
}
```

### 3 · `DatabaseUrlRules`: the decision

```java
// security/DatabaseUrlRules.java
package tz.co.hmy.pis.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tz.co.hmy.pis.model.Permission;
import tz.co.hmy.pis.repository.UrlRuleRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The URL rules, read from the url_rule table instead of written in SecurityConfig.
 *
 * SecurityConfig hands every /api/v1 request (except the admin endpoints, which stay
 * in code) to check(). The rules are tried in priority order and the FIRST match
 * decides: the caller needs that rule's permission. No rule matches: denied. So a
 * missing or broken rule can only close an endpoint, never open one.
 *
 * The rules are kept in memory, because a query on every request would be slow.
 * UrlRuleController calls reload() after each change. With more than one copy of
 * PIS running, the OTHER copies keep their old rules until they reload too.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseUrlRules implements AuthorizationManager<RequestAuthorizationContext> {

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    /** One url_rule row, with its pattern parsed once. method null = any method. */
    public record Rule(Long id, int priority, String method, PathPattern pattern, Permission permission) {
        boolean matches(String requestMethod, String path) {
            return (method == null || method.equals(requestMethod))
                    && pattern.matches(PathContainer.parsePath(path));
        }
    }

    private final UrlRuleRepository repository;
    private volatile List<Rule> rules;          // null until first needed

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        Optional<Rule> rule = find(request.getMethod(), PATHS.getPathWithinApplication(request));
        if (rule.isEmpty()) {
            return new AuthorizationDecision(false);                     // no rule: denied, never allowed
        }
        String needed = rule.get().permission().authority();
        boolean granted = authentication.get().getAuthorities().stream()
                .anyMatch(a -> needed.equals(a.getAuthority()));
        return new AuthorizationDecision(granted);
    }

    /** The rule that decides this method and path, if any. */
    public Optional<Rule> find(String method, String path) {
        List<Rule> current = rules;
        if (current == null) current = reload();
        return current.stream().filter(r -> r.matches(method, path)).findFirst();
    }

    /** Reads the table again. Call it after every change to url_rule. */
    public List<Rule> reload() {
        List<Rule> loaded = repository.findAllByOrderByPriorityAsc().stream()
                .map(r -> new Rule(r.getId(), r.getPriority(), r.getHttpMethod(), parse(r.getPattern()), r.getPermission()))
                .toList();
        rules = loaded;
        log.info("Loaded {} URL rules", loaded.size());
        return loaded;
    }

    /** Throws PatternParseException for a pattern Spring can't read: ** followed by more segments, say. */
    public static PathPattern parse(String pattern) {
        return PathPatternParser.defaultInstance.parse(pattern);
    }
}
```

Two details:

- `PathPatternParser` is the same parser Spring MVC uses for `@GetMapping`
  paths, so `*` is one path segment and `**` is the rest, as in `requestMatchers`.
- The pattern is parsed **once**, when the rules are loaded, not on every request.

### 4 · The request and the responses

```java
// dto/UrlRuleRequest.java
package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tz.co.hmy.pis.model.Permission;

/**
 * A new or changed URL rule. Leave method out for "any method".
 * The permission is written as an authority: "supplier:read".
 */
public record UrlRuleRequest(

        @NotNull(message = "priority is required")
        @Min(value = 1, message = "priority is 1 or more")
        Integer priority,

        @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE", message = "method is GET, POST, PUT, PATCH or DELETE (or leave it out for any method)")
        String method,

        @NotBlank(message = "pattern is required")
        @Size(max = 200)
        String pattern,

        @NotNull(message = "permission is required")
        Permission permission,

        @NotBlank(message = "description is required: say why the rule exists")
        @Size(max = 200)
        String description
) { }
```

```java
// dto/UrlRuleResponse.java
package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.UrlRule;

import java.time.Instant;

/** method is "ANY" when the rule matches every method. */
public record UrlRuleResponse(
        Long id,
        int priority,
        String method,
        String pattern,
        String permission,
        String description,
        Instant updatedAt,
        String updatedBy
) {
    public static UrlRuleResponse from(UrlRule rule) {
        return new UrlRuleResponse(rule.getId(), rule.getPriority(),
                rule.getHttpMethod() == null ? "ANY" : rule.getHttpMethod(),
                rule.getPattern(), rule.getPermission().authority(), rule.getDescription(),
                rule.getUpdatedAt() != null ? rule.getUpdatedAt() : rule.getCreatedAt(),
                rule.getUpdatedBy() != null ? rule.getUpdatedBy() : rule.getCreatedBy());
    }
}
```

```java
// dto/UrlRuleMatch.java
package tz.co.hmy.pis.dto;

/** Which rule decides a request, and what it needs. rule is null when none matches. */
public record UrlRuleMatch(String method, String path, UrlRuleResponse rule, String decision) { }
```

### 5 · The service: guard rails

```java
// service/UrlRuleService.java
package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.PatternParseException;
import tz.co.hmy.pis.dto.UrlRuleMatch;
import tz.co.hmy.pis.dto.UrlRuleRequest;
import tz.co.hmy.pis.dto.UrlRuleResponse;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.UrlRule;
import tz.co.hmy.pis.repository.UrlRuleRepository;
import tz.co.hmy.pis.security.DatabaseUrlRules;

import java.util.List;
import java.util.Locale;

/**
 * URL rules are data now: an admin changes what an endpoint requires while PIS is
 * running. Four checks keep that safe:
 *   1. A pattern must be under /api/v1/: only those requests are decided by the table.
 *   2. It can't be under an admin endpoint: those are protected in code (SecurityConfig),
 *      so no rule can lock the admins out of fixing the rules.
 *   3. Spring must be able to read the pattern; otherwise it would never match anything.
 *   4. Two rules can't share a priority, so the order is never a coin toss.
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UrlRuleService {

    /** Protected in SecurityConfig, in code. */
    static final List<String> IN_CODE = List.of("/api/v1/users", "/api/v1/roles", "/api/v1/permissions", "/api/v1/url-rules");

    private final UrlRuleRepository rules;
    private final DatabaseUrlRules urlRules;

    public List<UrlRuleResponse> findAll() {
        return rules.findAllByOrderByPriorityAsc().stream().map(UrlRuleResponse::from).toList();
    }

    /** Which rule decides this request: the same lookup the security check does. */
    public UrlRuleMatch match(String method, String path) {
        String m = method.toUpperCase(Locale.ROOT);
        return urlRules.find(m, path)
                .map(r -> new UrlRuleMatch(m, path, UrlRuleResponse.from(getOrThrow(r.id())),
                        "needs " + r.permission().authority() + " (rule " + r.priority() + ")"))
                .orElseGet(() -> new UrlRuleMatch(m, path, null, "no rule matches: denied"));
    }

    @Transactional
    public UrlRuleResponse create(UrlRuleRequest request) {
        check(request, null);
        UrlRule rule = new UrlRule(request.priority(), request.method(), request.pattern(),
                request.permission(), request.description());
        return UrlRuleResponse.from(rules.saveAndFlush(rule));
    }

    @Transactional
    public UrlRuleResponse update(Long id, UrlRuleRequest request) {
        UrlRule rule = getOrThrow(id);
        check(request, id);
        rule.change(request.priority(), request.method(), request.pattern(), request.permission(), request.description());
        return UrlRuleResponse.from(rules.saveAndFlush(rule));
    }

    @Transactional
    public void delete(Long id) {
        rules.delete(getOrThrow(id));
    }

    private void check(UrlRuleRequest request, Long id) {
        String pattern = request.pattern();
        if (!pattern.startsWith("/api/v1/")) {
            throw new BusinessRuleException("A rule's pattern must start with /api/v1/: only those requests are decided by the rules");
        }
        if (IN_CODE.stream().anyMatch(p -> pattern.equals(p) || pattern.startsWith(p + "/"))) {
            throw new BusinessRuleException(pattern + " is an admin endpoint. Those are protected in code, "
                    + "so a rule can never lock the admins out");
        }
        try {
            DatabaseUrlRules.parse(pattern);
        } catch (PatternParseException e) {
            throw new BusinessRuleException("Pattern " + pattern + " is not valid: " + e.getMessage());
        }
        rules.findByPriority(request.priority())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Priority " + request.priority() + " is already used by rule "
                            + other.getId() + " (" + other.getPattern() + ")");
                });
    }

    private UrlRule getOrThrow(Long id) {
        return rules.findById(id).orElseThrow(() -> new ResourceNotFoundException("URL rule", id));
    }
}
```

### 6 · The controller: reload after the commit

```java
// controller/UrlRuleController.java
package tz.co.hmy.pis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tz.co.hmy.pis.dto.UrlRuleMatch;
import tz.co.hmy.pis.dto.UrlRuleRequest;
import tz.co.hmy.pis.dto.UrlRuleResponse;
import tz.co.hmy.pis.security.DatabaseUrlRules;
import tz.co.hmy.pis.service.UrlRuleService;

import java.util.List;

/**
 * Needs the role:manage permission (SecurityConfig, in code).
 *
 * After every change it reloads the rules. That happens HERE, after the service
 * method has returned, because by then its transaction has committed: a reload
 * inside the transaction could keep a rule that is then rolled back.
 */
@Tag(name = "URL rules", description = "Which permission each endpoint needs")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/url-rules")
public class UrlRuleController {

    private final UrlRuleService service;
    private final DatabaseUrlRules urlRules;

    @Operation(summary = "List the rules, in the order they are checked")
    @GetMapping
    public List<UrlRuleResponse> findAll() {
        return service.findAll();
    }

    @Operation(summary = "Which rule decides a request, e.g. ?method=GET&path=/api/v1/invoices")
    @GetMapping("/match")
    public UrlRuleMatch match(@RequestParam String method, @RequestParam String path) {
        return service.match(method, path);
    }

    @Operation(summary = "Add a rule")
    @PostMapping
    public ResponseEntity<UrlRuleResponse> create(@Valid @RequestBody UrlRuleRequest request, UriComponentsBuilder uri) {
        UrlRuleResponse created = service.create(request);
        urlRules.reload();
        return ResponseEntity.created(uri.path("/api/v1/url-rules/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @Operation(summary = "Change a rule")
    @PutMapping("/{id}")
    public UrlRuleResponse update(@PathVariable Long id, @Valid @RequestBody UrlRuleRequest request) {
        UrlRuleResponse updated = service.update(id, request);
        urlRules.reload();
        return updated;
    }

    @Operation(summary = "Delete a rule")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
        urlRules.reload();
    }
}
```

### 7 · `SecurityConfig`: 12 lines become one

The order matters: the admin endpoints are matched **before** the table is asked.

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
import tz.co.hmy.pis.security.DatabaseUrlRules;
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
    private final DatabaseUrlRules urlRules;

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

                        // The admin endpoints stay in CODE: no row in url_rule can lock the
                        // admins out of fixing the rules.
                        .requestMatchers("/api/v1/users/**").hasAuthority("user:manage")
                        .requestMatchers("/api/v1/roles/**", "/api/v1/permissions",
                                "/api/v1/url-rules/**").hasAuthority("role:manage")

                        // Everything else under /api/v1: the url_rule table decides (Lesson 3F).
                        .requestMatchers("/api/v1/**").access(urlRules)

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
(`$BASE`, `$ADMIN_PW`, `$OFFICER_PW`, `$APPROVER_PW`, `$ID`, `$JSON`, `$DB`).
Every output below is from a real run.

**1 · The rules are rows** Migration V8 moved the rules out of `SecurityConfig`, in the same order.

```bash
psql -d $DB -c "SELECT priority, coalesce(http_method, 'ANY') AS method, pattern, permission
  FROM url_rule ORDER BY priority;"
```
```
 priority | method |            pattern             |      permission      
----------+--------+--------------------------------+----------------------
       10 | POST   | /api/v1/requisitions/*/approve | REQUISITION_APPROVE
       20 | POST   | /api/v1/requisitions/*/reject  | REQUISITION_APPROVE
       30 | PATCH  | /api/v1/suppliers/*/status     | SUPPLIER_APPROVE
       40 | DELETE | /api/v1/**                     | RECORD_DELETE
       50 | GET    | /api/v1/suppliers/**           | SUPPLIER_READ
       60 | ANY    | /api/v1/suppliers/**           | SUPPLIER_WRITE
       70 | GET    | /api/v1/requisitions/**        | REQUISITION_READ
       80 | ANY    | /api/v1/requisitions/**        | REQUISITION_WRITE
       90 | GET    | /api/v1/purchase-orders/**     | PURCHASE_ORDER_READ
      100 | ANY    | /api/v1/purchase-orders/**     | PURCHASE_ORDER_WRITE
      110 | GET    | /api/v1/invoices/**            | INVOICE_READ
      120 | ANY    | /api/v1/invoices/**            | INVOICE_WRITE
(12 rows)
```

**2 · The same, through the API** Permissions are shown as authorities, like everywhere else in the API.

```bash
curl -s -u admin:$ADMIN_PW $BASE/api/v1/url-rules | jq -r '.[] | "\(.priority) \(.method) \(.pattern) -> \(.permission)"'
```
```
10 POST /api/v1/requisitions/*/approve -> requisition:approve
20 POST /api/v1/requisitions/*/reject -> requisition:approve
30 PATCH /api/v1/suppliers/*/status -> supplier:approve
40 DELETE /api/v1/** -> record:delete
50 GET /api/v1/suppliers/** -> supplier:read
60 ANY /api/v1/suppliers/** -> supplier:write
70 GET /api/v1/requisitions/** -> requisition:read
80 ANY /api/v1/requisitions/** -> requisition:write
90 GET /api/v1/purchase-orders/** -> purchase-order:read
100 ANY /api/v1/purchase-orders/** -> purchase-order:write
110 GET /api/v1/invoices/** -> invoice:read
120 ANY /api/v1/invoices/** -> invoice:write
```

**3 · Which rule decides?** Ask for a request without sending it. The first matching rule decides; a request that matches none is refused, even for the admin, who has every permission.

```bash
curl -s -u admin:$ADMIN_PW "$BASE/api/v1/url-rules/match?method=PATCH&path=/api/v1/suppliers/$ID/status" | jq -c '{decision}'
curl -s -u admin:$ADMIN_PW "$BASE/api/v1/url-rules/match?method=GET&path=/api/v1/reports" | jq -c '{decision}'
curl -s -o /dev/null -w 'admin, GET /api/v1/reports: %{http_code}\n' -u admin:$ADMIN_PW $BASE/api/v1/reports
```
```
{"decision":"needs supplier:approve (rule 30)"}
{"decision":"no rule matches: denied"}
admin, GET /api/v1/reports: 403
```

**4 · Change what an endpoint needs, while PIS is running** A new rule at priority 65 sits before rule 70 (any reader may read requisitions), so it wins: only approvers may read them now.

```bash
curl -s -o /dev/null -w 'officer, before: %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/requisitions
RULE=$(curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":65,"method":"GET","pattern":"/api/v1/requisitions/**","permission":"requisition:approve","description":"Only approvers may read requisitions"}')
echo "$RULE" | jq -c '{id, priority, method, pattern, permission, updatedBy}'; RULE_ID=$(echo "$RULE" | jq .id)
curl -s -o /dev/null -w 'officer, after:  %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/requisitions
curl -s -o /dev/null -w 'approver, after: %{http_code}\n' -u approver:$APPROVER_PW $BASE/api/v1/requisitions
```
```
officer, before: 200
{"id":13,"priority":65,"method":"GET","pattern":"/api/v1/requisitions/**","permission":"requisition:approve","updatedBy":"admin"}
officer, after:  403
approver, after: 200
```

**5 · Take it away again** The officer can read requisitions on the very next request.

```bash
curl -s -o /dev/null -w 'delete the rule: %{http_code}\n' -u admin:$ADMIN_PW -X DELETE $BASE/api/v1/url-rules/$RULE_ID
curl -s -o /dev/null -w 'officer:         %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/requisitions
```
```
delete the rule: 204
officer:         200
```

**6 · Order matters: deleting a rule can OPEN an endpoint** Rule 30 says a status change needs `supplier:approve`. Delete it, and rule 60 (any method on suppliers: `supplier:write`) decides instead. Officers have `supplier:write`. `$ID` is a supplier that doesn't exist, so a `404` proves the request got **past** security without changing anything.

```bash
curl -s -o /dev/null -w 'officer changes a status, before: %{http_code}\n' -u officer:$OFFICER_PW -X PATCH -H "$JSON" -d '{"status":"ACTIVE"}' $BASE/api/v1/suppliers/$ID/status
R30=$(curl -s -u admin:$ADMIN_PW $BASE/api/v1/url-rules | jq '.[] | select(.priority == 30) | .id')
curl -s -o /dev/null -w 'delete rule 30: %{http_code}\n' -u admin:$ADMIN_PW -X DELETE $BASE/api/v1/url-rules/$R30
curl -s -u admin:$ADMIN_PW "$BASE/api/v1/url-rules/match?method=PATCH&path=/api/v1/suppliers/$ID/status" | jq -c '{decision}'
curl -s -o /dev/null -w 'officer changes a status, after:  %{http_code}\n' -u officer:$OFFICER_PW -X PATCH -H "$JSON" -d '{"status":"ACTIVE"}' $BASE/api/v1/suppliers/$ID/status
```
```
officer changes a status, before: 403
delete rule 30: 204
{"decision":"needs supplier:write (rule 60)"}
officer changes a status, after:  404
```

**7 · Put rule 30 back** Same priority, same pattern, same permission.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":30,"method":"PATCH","pattern":"/api/v1/suppliers/*/status","permission":"supplier:approve","description":"Activating or suspending a supplier"}' | jq -c '{priority, pattern, permission}'
curl -s -o /dev/null -w 'officer changes a status: %{http_code}\n' -u officer:$OFFICER_PW -X PATCH -H "$JSON" -d '{"status":"ACTIVE"}' $BASE/api/v1/suppliers/$ID/status
```
```
{"priority":30,"pattern":"/api/v1/suppliers/*/status","permission":"supplier:approve"}
officer changes a status: 403
```

**8 · The guard rails** Four changes PIS refuses.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":5,"pattern":"/api/v1/roles/**","permission":"supplier:read","description":"x"}' | jq -c '{status, detail}'
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":5,"pattern":"/api/v1/**/approve","permission":"supplier:read","description":"x"}' | jq -c '{status, detail}'
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":50,"pattern":"/api/v1/reports/**","permission":"supplier:read","description":"x"}' | jq -c '{status, detail}'
curl -s -o /dev/null -w 'officer reads the rules: %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/url-rules
```
```
{"status":422,"detail":"/api/v1/roles/** is an admin endpoint. Those are protected in code, so a rule can never lock the admins out"}
{"status":422,"detail":"Pattern /api/v1/**/approve is not valid: No more pattern data allowed after {*...} or ** pattern element"}
{"status":409,"detail":"Priority 50 is already used by rule 5 (/api/v1/suppliers/**)"}
officer reads the rules: 403
```

The same steps, with what to check when one doesn't match, are in the
[testing guide](TESTING.md#lesson-3f--url-rules-in-the-database).

---

## Test it

`UrlRuleTest`, 11 tests, every one through a real login:

```java
// (test) UrlRuleTest.java
package tz.co.hmy.pis;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.repository.UrlRuleRepository;
import tz.co.hmy.pis.security.DatabaseUrlRules;

import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * URL rules in the database: the url_rule table decides what each /api/v1 endpoint
 * needs. Every other test class still passes against the seeded table, which is
 * the proof that V8 holds exactly the rules SecurityConfig had.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UrlRuleTest {

    private static final String SUPPLIER_ID = "33333333-3333-3333-3333-333333333333";
    private static final String APPROVERS_ONLY = """
            { "priority": 65, "method": "GET", "pattern": "/api/v1/requisitions/**",
              "permission": "requisition:approve", "description": "Only approvers may read requisitions" }
            """;

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired UrlRuleRepository rules;
    @Autowired DatabaseUrlRules urlRules;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createUsers() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
        users.save(new AppUser("approver", encoder.encode("approver-pass"), "Test Approver", Set.of("APPROVER")));
    }

    /**
     * The test's transaction rolls back every rule it added, but the in-memory copy
     * would keep them and leak into the next test. Reload after the rollback.
     */
    @AfterTransaction
    void reloadRules() {
        urlRules.reload();
    }

    private ResultActions as(String user, String password, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(httpBasic(user, password)));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return as("admin", "admin-pass", request);
    }

    private ResultActions addRule(String json) throws Exception {
        return asAdmin(post("/api/v1/url-rules").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long idOfRule(int priority) {
        return rules.findByPriority(priority).orElseThrow().getId();
    }

    // ---------------------------------------------------------------- the table

    @Test
    void the_table_holds_the_rules_in_the_order_they_are_checked() throws Exception {
        asAdmin(get("/api/v1/url-rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(12)))
            .andExpect(jsonPath("$[0].pattern").value("/api/v1/requisitions/*/approve"))
            .andExpect(jsonPath("$[3].method").value("DELETE"))
            .andExpect(jsonPath("$[3].permission").value("record:delete"))
            .andExpect(jsonPath("$[5].method").value("ANY"));
    }

    @Test
    void only_role_managers_may_see_or_change_the_rules() throws Exception {
        as("officer", "officer-pass", get("/api/v1/url-rules")).andExpect(status().isForbidden());
        as("officer", "officer-pass", post("/api/v1/url-rules").contentType(MediaType.APPLICATION_JSON)
                .content(APPROVERS_ONLY)).andExpect(status().isForbidden());
    }

    @Test
    void match_says_which_rule_decides() throws Exception {
        asAdmin(get("/api/v1/url-rules/match").param("method", "patch").param("path", "/api/v1/suppliers/" + SUPPLIER_ID + "/status"))
            .andExpect(jsonPath("$.rule.priority").value(30))
            .andExpect(jsonPath("$.decision").value("needs supplier:approve (rule 30)"));
        asAdmin(get("/api/v1/url-rules/match").param("method", "GET").param("path", "/api/v1/reports"))
            .andExpect(jsonPath("$.rule").doesNotExist())                // null fields are left out
            .andExpect(jsonPath("$.decision").value("no rule matches: denied"));
    }

    // ---------------------------------------------------------------- changes take effect

    @Test
    void a_new_rule_works_on_the_next_request() throws Exception {
        as("officer", "officer-pass", get("/api/v1/requisitions")).andExpect(status().isOk());

        String body = addRule(APPROVERS_ONLY)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.updatedBy").value("admin"))
            .andReturn().getResponse().getContentAsString();

        as("officer", "officer-pass", get("/api/v1/requisitions")).andExpect(status().isForbidden());
        as("approver", "approver-pass", get("/api/v1/requisitions")).andExpect(status().isOk());

        Integer id = JsonPath.read(body, "$.id");
        asAdmin(delete("/api/v1/url-rules/{id}", id)).andExpect(status().isNoContent());
        as("officer", "officer-pass", get("/api/v1/requisitions")).andExpect(status().isOk());
    }

    /**
     * The first match decides, so deleting a SPECIFIC rule lets a broader one below
     * it decide instead. Here that OPENS something: rule 60 (any method on suppliers,
     * supplier:write) now covers the status change, and officers have supplier:write.
     */
    @Test
    void deleting_a_specific_rule_lets_a_broader_one_decide() throws Exception {
        MockHttpServletRequestBuilder activate = patch("/api/v1/suppliers/{id}/status", SUPPLIER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"status\": \"ACTIVE\" }");
        as("officer", "officer-pass", activate).andExpect(status().isForbidden());

        asAdmin(delete("/api/v1/url-rules/{id}", idOfRule(30))).andExpect(status().isNoContent());

        as("officer", "officer-pass", activate).andExpect(status().isOk());
    }

    /** No rule: denied. Even the admin, who holds every permission. */
    @Test
    void when_no_rule_matches_even_the_admin_is_refused() throws Exception {
        asAdmin(get("/api/v1/invoices")).andExpect(status().isOk());

        asAdmin(delete("/api/v1/url-rules/{id}", idOfRule(110))).andExpect(status().isNoContent());
        asAdmin(delete("/api/v1/url-rules/{id}", idOfRule(120))).andExpect(status().isNoContent());

        asAdmin(get("/api/v1/invoices")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- guard rails

    /** The admin endpoints are matched in code BEFORE the table is asked. */
    @Test
    void a_rule_cannot_open_the_admin_endpoints() throws Exception {
        asAdmin(get("/api/v1/url-rules")).andExpect(status().isOk());
        addRule("""
                { "priority": 1, "pattern": "/api/v1/**", "permission": "supplier:read",
                  "description": "Anyone who can read suppliers can do anything" }
                """).andExpect(status().isCreated());

        as("officer", "officer-pass", get("/api/v1/roles")).andExpect(status().isForbidden());
        as("officer", "officer-pass", get("/api/v1/users")).andExpect(status().isForbidden());
        as("officer", "officer-pass", get("/api/v1/url-rules")).andExpect(status().isForbidden());
    }

    @Test
    void rules_for_admin_endpoints_or_outside_the_api_are_refused() throws Exception {
        addRule("""
                { "priority": 5, "pattern": "/api/v1/roles/**", "permission": "supplier:read", "description": "x" }
                """).andExpect(status().isUnprocessableEntity());
        addRule("""
                { "priority": 5, "pattern": "/actuator/**", "permission": "supplier:read", "description": "x" }
                """).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void a_pattern_spring_cannot_read_is_refused() throws Exception {
        addRule("""
                { "priority": 5, "pattern": "/api/v1/**/approve", "permission": "supplier:read", "description": "x" }
                """)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Pattern /api/v1/**/approve is not valid")));
    }

    @Test
    void two_rules_cannot_share_a_priority() throws Exception {
        addRule("""
                { "priority": 50, "pattern": "/api/v1/reports/**", "permission": "supplier:read", "description": "x" }
                """).andExpect(status().isConflict());
    }

    @Test
    void unknown_permissions_and_methods_are_a_400() throws Exception {
        addRule("""
                { "priority": 5, "pattern": "/api/v1/reports/**", "permission": "supplier:fly", "description": "x" }
                """).andExpect(status().isBadRequest());
        addRule("""
                { "priority": 5, "method": "FETCH", "pattern": "/api/v1/reports/**", "permission": "supplier:read", "description": "x" }
                """).andExpect(status().isBadRequest());
    }
}
```

| Test | Proves |
| --- | --- |
| `the_table_holds_the_rules_in_the_order_they_are_checked` | V8 seeded 12 rules, in order |
| `only_role_managers_may_see_or_change_the_rules` | `/url-rules` needs `role:manage` |
| `match_says_which_rule_decides` | The same lookup the security check does |
| `a_new_rule_works_on_the_next_request` | No restart: the reload works |
| `deleting_a_specific_rule_lets_a_broader_one_decide` | First match decides, even when that opens something |
| `when_no_rule_matches_even_the_admin_is_refused` | No match: denied |
| `a_rule_cannot_open_the_admin_endpoints` | They're matched in code, before the table |
| `rules_for_admin_endpoints_or_outside_the_api_are_refused` | 422 |
| `a_pattern_spring_cannot_read_is_refused` | 422, with Spring's reason |
| `two_rules_cannot_share_a_priority` | 409 |
| `unknown_permissions_and_methods_are_a_400` | 400 |

These were checked the other way round too:

| Break this | And this fails |
| --- | --- |
| Remove `@AfterTransaction` | `match_says_which_rule_decides` sees rule **60** where rule 30 should be: another test deleted rule 30, the rollback put it back in the table, but not in the cache |
| Return `true` when no rule matches | `when_no_rule_matches_even_the_admin_is_refused`: 200 instead of 403 |
| Put `.access(urlRules)` above the admin lines | 8 of the 11 tests, all with 403: the admin can't even reach `/url-rules` |

```bash
mvn clean test
# … FilterChainTest 6 · UrlRuleTest 11 · … → Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
```

---

## Common mistakes

<details>
<summary><b>Every request under <code>/api/v1/url-rules</code> is a 403, even for the admin</b></summary>

`.requestMatchers("/api/v1/**").access(urlRules)` is above the admin lines, so
the table decides `/url-rules` too, and no rule matches it. That's the lock-out
this design must prevent, and a new row can't fix it: you need a code change.
(Checked: 8 tests fail with 403.) Keep the admin lines first.
</details>

<details>
<summary><b>A new rule only works after a restart</b></summary>

Nothing called `reload()`: the rules are read once and kept in memory. The
controller reloads after every create, update and delete.
</details>

<details>
<summary><b>Tests pass alone but fail together</b></summary>

A test changed the rules and its transaction rolled back, but the in-memory copy
kept the change and the next test saw it. Reload after the rollback with
`@AfterTransaction`, not `@AfterEach`, which runs **before** the rollback.
(Checked: without it, a deleted rule 30 leaked into `match_says_which_rule_decides`.)
</details>

<details>
<summary><b>Deleting a rule gave officers MORE rights</b></summary>

The first match decides, and a broader rule further down took over (Try it,
step 6). Before deleting a rule, ask `/api/v1/url-rules/match` which rule will
decide instead.
</details>

<details>
<summary><b>"unclosed string literal" in a file you only added a comment to</b></summary>

A Javadoc comment with a path pattern like `/api/**/x` in it: the `*/` inside the
pattern ends the comment, and the rest of the line is read as code. This happened
while building the lesson. Describe the pattern in words in comments.
</details>

---

## Exercises

### 1 · Read the table — *warm-up*

Without running anything: which rule decides `DELETE /api/v1/suppliers/{id}`,
and what does it need? Rule 60 also matches; why doesn't it decide? Then check
your answer with `/api/v1/url-rules/match`.

<details>
<summary>Solution</summary>

Rule 40, `DELETE /api/v1/**`: it needs `record:delete`. Rule 60 (any method on
suppliers) matches too, but 40 comes first. Checked: `/match` says
`needs record:delete (rule 40)`.
</details>

### 2 · Break it: lock yourself out — *understanding*

Move `.requestMatchers("/api/v1/**").access(urlRules)` above the line for
`/api/v1/users/**`, restart, and try `GET /api/v1/url-rules` as admin. Can you fix
it with an API call? Put the line back.

<details>
<summary>Solution</summary>

403. No rule in the table matches `/api/v1/url-rules`, so it's denied, for
everyone. There's no API call that can help, because the API that would fix the
rules is the thing that's locked. The fix is a code change and a release, which
is exactly what the table was meant to avoid. That's why the admin endpoints stay
in code.
</details>

### 3 · An audit freeze — *core*

The auditors want no invoices created or changed while they work, except by an
admin, and everyone may still read them. Do it with **one** new rule, then end the
freeze. Which priority, which method, which permission?

<details>
<summary>Solution</summary>

Priority 115 (after 110, the reading rule; before 120, the writing rule), any
method, `/api/v1/invoices/**`, `record:delete` (only ADMIN holds it):

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":115,"pattern":"/api/v1/invoices/**","permission":"record:delete","description":"Audit freeze: only admins may change invoices"}'
```

Checked: an officer still reads invoices (200) but can't create one (403); the
admin gets past security (a 400, for the empty body this check sends). Deleting
the rule ends the freeze. Reads aren't affected because rule 110 matches `GET`
first.
</details>

### 4 · Two copies of PIS — *core, design*

Run two copies behind the Lecture 5 gateway. An admin adds a rule through copy A.
What does a request that lands on copy B see? Give three ways to keep the copies
in step, and the cost of each.

<details>
<summary>Discussion</summary>

Copy B keeps its old rules until it reloads. Options: **reload on a timer** (simple;
up to one interval stale); **PostgreSQL `LISTEN`/`NOTIFY`** (copy A sends a
notification after the commit, every copy reloads; needs a listening connection);
**a message broker** (the same, through Kafka or RabbitMQ; one more system to run).
Whichever you pick, a removed restriction or an added one is late on some copies
for a while, the same trade-off as JWTs and role changes in Lesson 3D.
</details>

### 5 · Its own permission — *stretch, design*

Managing URL rules uses `role:manage`. Should it have its own `rule:manage`?
What would you have to change, given what Lesson 3D taught about new permissions?

<details>
<summary>Discussion</summary>

Possibly: changing what an endpoint needs is a different power from changing who
holds what. A new permission is a new `Permission` constant (code), a migration that
grants it to ADMIN (the ADMIN role's permissions are rows), and the `SecurityConfig`
line for `/url-rules`.
</details>

---

## Quiz

**1. Lesson 3D already stores roles in the database. What does this lesson add?**\
a) Users in the database  b) Which permission each endpoint needs, as data  c) Encrypted permissions

**2. No rule matches a request. What happens?**\
a) Allowed, if logged in  b) Denied, even for the admin  c) The default rule applies

**3. Rule 30 is deleted. Why can officers now change a supplier's status?**\
a) A bug  b) The first match decides, and rule 60, which needs `supplier:write`, matches next  c) Deleting a rule allows everything

**4. Why do `/users`, `/roles` and `/url-rules` stay protected in code?**\
a) They're faster  b) So no row can lock the admins out of fixing the rows  c) Spring requires it

**5. Why does the controller reload the rules, rather than the service?**\
a) Controllers run first  b) After the service returns, its transaction has committed  c) Services can't call components

**6. Why `@AfterTransaction` and not `@AfterEach` in the test?**\
a) It's newer  b) `@AfterEach` runs before the rollback, so it would reload the test's own changes  c) No difference

<details>
<summary>Answers</summary>

1. **b.** Role → permission was data already; URL → permission was code.
2. **b.** A missing rule closes an endpoint.
3. **b.** Ask `/match` before deleting a rule.
4. **b.** That lock-out can't be fixed with an API call.
5. **b.** Otherwise a rule could stay cached after its transaction rolled back.
6. **b.** Reload after the rollback, so the cache matches the table.
</details>

---

## Smoke test

Save as `smoke.sh` and run while the app is up. It adds a rule at priority 65 and
deletes it again, and never touches the seeded rules, so it can be run again.

```bash
#!/usr/bin/env bash
# Lesson 3F smoke test: URL rules in the database.
# Adds a rule at priority 65 and deletes it again, so it can be run again. It
# never touches the seeded rules.
BASE=http://localhost:8080
ADMIN='admin:admin123'
OFFICER='officer:officer123'
APPROVER='approver:approver123'
JSON='Content-Type: application/json'

check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  [ "$got" = "$want" ] && echo "PASS  $got  $desc" || echo "FAIL  $got  $desc (expected $want)"
}
rule_at() { curl -s -u "$ADMIN" "$BASE/api/v1/url-rules" | grep -o "{\"id\":[0-9]*,\"priority\":$1," | grep -o '[0-9]*' | head -1; }

# Left over from an interrupted run?
old=$(rule_at 65); [ -n "$old" ] && curl -s -o /dev/null -u "$ADMIN" -X DELETE "$BASE/api/v1/url-rules/$old"

echo "--- the rules are data"
n=$(curl -s -u "$ADMIN" "$BASE/api/v1/url-rules" | grep -o '"priority"' | wc -l | tr -d ' ')
[ "$n" -ge 12 ] && echo "PASS       $n rules in url_rule" || echo "FAIL       $n rules in url_rule (expected 12)"
d=$(curl -s -u "$ADMIN" "$BASE/api/v1/url-rules/match?method=PATCH&path=/api/v1/suppliers/x/status" | grep -o '"decision":"[^"]*"')
[ "$d" = '"decision":"needs supplier:approve (rule 30)"' ] && echo "PASS       a status change: rule 30 decides" || echo "FAIL       a status change: $d"
check 403 "no rule matches: refused, even for the admin"         -u "$ADMIN" "$BASE/api/v1/reports"
check 403 "an officer can't see the rules"                       -u "$OFFICER" "$BASE/api/v1/url-rules"

echo "--- change what an endpoint needs, no restart"
check 200 "officer reads requisitions"                           -u "$OFFICER" "$BASE/api/v1/requisitions"
check 201 "admin: only approvers may read requisitions"          -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":65,"method":"GET","pattern":"/api/v1/requisitions/**","permission":"requisition:approve","description":"Smoke test: only approvers may read requisitions"}'
check 403 "officer, on the next request"                         -u "$OFFICER" "$BASE/api/v1/requisitions"
check 200 "approver still can"                                   -u "$APPROVER" "$BASE/api/v1/requisitions"
check 204 "admin deletes the rule"                               -u "$ADMIN" -X DELETE "$BASE/api/v1/url-rules/$(rule_at 65)"
check 200 "officer can read again"                               -u "$OFFICER" "$BASE/api/v1/requisitions"

echo "--- guard rails"
check 422 "no rules for admin endpoints"                         -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":5,"pattern":"/api/v1/roles/**","permission":"supplier:read","description":"x"}'
check 422 "a pattern Spring can't read"                          -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":5,"pattern":"/api/v1/**/approve","permission":"supplier:read","description":"x"}'
check 409 "a priority that's taken"                              -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":50,"pattern":"/api/v1/reports/**","permission":"supplier:read","description":"x"}'
check 400 "an unknown permission"                                -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":5,"pattern":"/api/v1/reports/**","permission":"supplier:fly","description":"x"}'
```

Expected output:

```
--- the rules are data
PASS       12 rules in url_rule
PASS       a status change: rule 30 decides
PASS  403  no rule matches: refused, even for the admin
PASS  403  an officer can't see the rules
--- change what an endpoint needs, no restart
PASS  200  officer reads requisitions
PASS  201  admin: only approvers may read requisitions
PASS  403  officer, on the next request
PASS  200  approver still can
PASS  204  admin deletes the rule
PASS  200  officer can read again
--- guard rails
PASS  422  no rules for admin endpoints
PASS  422  a pattern Spring can't read
PASS  409  a priority that's taken
PASS  400  an unknown permission
```

---

## Next

**→ [Lecture 4 — OAuth2 with Spring Authorization Server](../../../pis-lecture4-authorization-server/LECTURE.md).**
