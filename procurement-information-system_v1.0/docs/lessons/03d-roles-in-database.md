# Lesson 3D — Roles and Permissions in the Database

> **Let the organisation change who may do what, without a release.**
> In Lesson 3C the mapping from roles to permissions was Java code: the `Role`
> enum. Adding an auditor, or letting approvers edit invoices, meant a code
> change and a deployment. This lesson moves **roles** into two tables, gives
> admins an API to manage them, and keeps **permissions** in code, where they belong.

```
Lesson 3C:  enum Role { OFFICER(EnumSet.of(SUPPLIER_READ, …)) }         change = release
Lesson 3D:  role + role_permission tables, POST /api/v1/roles             change = one API call
```

| Duration | Builds on | You should know |
| --- | --- | --- |
| About 2½ hours | [Lesson 3C — Permissions and Method Security](03c-permissions.md) | `Permission`, `Authorities`, `@WithRole`, Flyway migrations |

**Contents:** [Goals](#goals) · [Concepts](#concepts) · [Upgrade checklist](#upgrade-checklist) ·
[Build it](#build-it) · [Try it](#try-it) · [Test it](#test-it) · [Common mistakes](#common-mistakes) ·
[Exercises](#exercises) · [Quiz](#quiz) · [Smoke test](#smoke-test)

> **Complete files.** Every code block is a complete file with its `package` and
> `import` lines, taken from the tested project, unless it's labelled "changed part".

---

## Goals

By the end of this lesson you can:

- Decide what belongs in code (permissions) and what belongs in data (roles and their permissions), and explain why.
- Model roles as a JPA entity with an `@ElementCollection` of enum permissions, seeded by a Flyway migration.
- Load a user's authorities from the database on every request, in one query.
- Build an admin API for roles, with guard rails: ADMIN can't be changed, roles in use can't be deleted.
- Explain when a change reaches a **Basic** user (next request) and a **JWT** user (next refresh).
- Return **400**, not 500, for malformed JSON and unknown enum values.

### Lesson plan

| Time (min) | Activity |
| --- | --- |
| 0–20 | Concepts: code vs data; the two tables; when a change takes effect |
| 20–80 | Build it: migration, entity, `Authorities` as a bean, users, roles API, guard rails |
| 80–100 | Try it: create a role, change it while someone is logged in |
| 100–150 | Tests, the lock-out exercise, quiz |

---

## Concepts

### What goes in code, and what goes in the database

| | Permission | Role |
| --- | --- | --- |
| Example | `requisition:approve` | `APPROVER` |
| Who decides it exists | The **developers**: a rule in the code checks it | The **organisation**: it's a job description |
| Changes when | New code is written | Someone is hired, reorganised, audited |
| Lives in | `Permission` enum (code) | `role` and `role_permission` tables (data) |

A permission nobody checks is meaningless, and a check for a permission that doesn't
exist can never pass. So permissions arrive **with** code. Roles are just bundles
of those permissions, and bundling is a business decision. So roles are **data**.

### The tables

```
role                                    role_permission                   app_user_role
┌──────────┬──────────┬──────────┐      ┌──────────┬─────────────────┐    ┌─────────┬──────────┐
│ name  PK │ built_in │ descr.   │◄─────┤ role     │ permission      │    │ user_id │ role  FK │──► role.name
├──────────┼──────────┼──────────┤      ├──────────┼─────────────────┤    └─────────┴──────────┘
│ ADMIN    │ true     │ …        │      │ APPROVER │ SUPPLIER_READ   │
│ APPROVER │ true     │ …        │      │ APPROVER │ SUPPLIER_APPROVE│
│ AUDITOR  │ false    │ …        │      │ …        │ …               │
└──────────┴──────────┴──────────┘      └──────────┴─────────────────┘
```

`app_user_role` from Lesson 2 doesn't change shape. It gains a **foreign key** to
`role.name`, so a user can only hold a role that exists.

### When does a change take effect?

| How the user logged in | Sees a role change | Why |
| --- | --- | --- |
| HTTP Basic | On their **next request** | Every request loads the user, their roles and permissions again |
| JWT (Lesson 3) | At their **next refresh**, at most 5 minutes | The permissions are inside the token until it expires |

It's the same trade-off as Lesson 3: tokens are fast because the server doesn't
look anything up, and that's exactly why they don't notice changes. Lesson 3B's
short access tokens are what keep the delay small.

### Guard rails

Roles are now editable by a person with an API call. So the API must stop the
edits that break everything:

| Refused | Why | Status |
| --- | --- | --- |
| Changing the ADMIN role | Remove `role:manage` from ADMIN, and nobody can ever put it back (Exercise 2) | 422 |
| Deleting a built-in role | The code, the docs and the other lessons refer to it | 422 |
| Deleting a role someone holds | Their account would point at nothing | 422 |
| An unknown permission | The code wouldn't know what it means | 400 |
| An unknown role on a user | Caught before the database's foreign key turns it into a 500 | 422 |

---

## Upgrade checklist

Starting from the end of Lesson 3C.

| | File | Change |
| --- | --- | --- |
| ➕ | `db/migration/V7__create_role_tables.sql` | `role`, `role_permission`, seeds, foreign key |
| ✏️ | `model/Permission.java` | Adds `ROLE_MANAGE`; JSON uses the authority (`"supplier:read"`) |
| ❌ | `model/Role.java` | **Deleted**: roles are data now |
| ➕ | `model/AppRole.java` | The `role` table |
| ➕ | `repository/AppRoleRepository.java` | Roles with their permissions, in one query |
| ✏️ | `security/Authorities.java` | Becomes a Spring bean that reads the tables |
| ✏️ | `model/AppUser.java`, `dto/UserRequest.java` | Roles are names: `Set<String>` |
| ✏️ | `service/DatabaseUserDetailsService.java`, `service/TokenService.java` | Use the `Authorities` bean |
| ✏️ | `service/UserService.java`, `controller/UserController.java`, `dto/UserResponse.java` | Checks roles exist; `PUT /users/{id}/roles` |
| ➕ | `dto/UserRolesRequest.java` | New |
| ✏️ | `repository/AppUserRepository.java` | `countWithRole(...)` |
| ➕ | `service/RoleService.java`, `controller/RoleController.java` | The roles API and its guard rails |
| ➕ | `dto/RoleRequest.java`, `dto/RolePermissionsRequest.java`, `dto/RoleResponse.java` | New |
| ✏️ | `config/SecurityConfig.java` | `/roles` and `/permissions` need `role:manage` |
| ✏️ | `exception/GlobalExceptionHandler.java` | Malformed JSON → 400 (it was a 500 in every earlier lesson) |
| ✏️ | `config/AdminAccountInitializer.java` | Role names as strings |
| ✏️ | `WithRole`, `WithRoleSecurityContextFactory` (tests) | Read the role from the database |
| ✏️ | Every test using `Role.X` | Use `"X"` |
| ➕ | `RoleApiTest.java` | New, 10 tests |

---

## Build it

### 1 · The migration

```sql
-- db/migration/V7__create_role_tables.sql
-- Roles move from code (the Role enum, Lesson 3C) into the database, so an
-- admin can create a role or change its permissions without a release.
--
-- Permissions stay in code (the Permission enum): a permission is what the code
-- CHECKS, so a new one always arrives with new code. role_permission.permission
-- holds the enum constant's name, e.g. SUPPLIER_READ.

CREATE TABLE role (
    name        VARCHAR(20)   PRIMARY KEY,           -- what app_user_role.role refers to
    description VARCHAR(200)  NOT NULL,
    built_in    BOOLEAN       NOT NULL DEFAULT FALSE, -- shipped with PIS; cannot be deleted
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(50),
    updated_by  VARCHAR(50)
);

CREATE TABLE role_permission (
    role       VARCHAR(20) NOT NULL REFERENCES role (name) ON DELETE CASCADE,
    permission VARCHAR(40) NOT NULL,
    PRIMARY KEY (role, permission)
);

-- The three roles from Lesson 3C, with exactly the same permissions.
INSERT INTO role (name, description, built_in, created_at, created_by) VALUES
    ('OFFICER',  'Raises requisitions, registers suppliers, prepares orders and invoices', TRUE, now(), 'system'),
    ('APPROVER', 'Approves what officers raise. Reads everything, writes nothing',        TRUE, now(), 'system'),
    ('ADMIN',    'Everything, including users and roles',                                 TRUE, now(), 'system');

INSERT INTO role_permission (role, permission) VALUES
    ('OFFICER', 'SUPPLIER_READ'), ('OFFICER', 'SUPPLIER_WRITE'),
    ('OFFICER', 'REQUISITION_READ'), ('OFFICER', 'REQUISITION_WRITE'),
    ('OFFICER', 'PURCHASE_ORDER_READ'), ('OFFICER', 'PURCHASE_ORDER_WRITE'),
    ('OFFICER', 'INVOICE_READ'), ('OFFICER', 'INVOICE_WRITE'),

    ('APPROVER', 'SUPPLIER_READ'), ('APPROVER', 'SUPPLIER_APPROVE'),
    ('APPROVER', 'REQUISITION_READ'), ('APPROVER', 'REQUISITION_APPROVE'),
    ('APPROVER', 'PURCHASE_ORDER_READ'), ('APPROVER', 'INVOICE_READ'),

    ('ADMIN', 'SUPPLIER_READ'), ('ADMIN', 'SUPPLIER_WRITE'), ('ADMIN', 'SUPPLIER_APPROVE'),
    ('ADMIN', 'REQUISITION_READ'), ('ADMIN', 'REQUISITION_WRITE'), ('ADMIN', 'REQUISITION_APPROVE'),
    ('ADMIN', 'PURCHASE_ORDER_READ'), ('ADMIN', 'PURCHASE_ORDER_WRITE'),
    ('ADMIN', 'INVOICE_READ'), ('ADMIN', 'INVOICE_WRITE'),
    ('ADMIN', 'RECORD_DELETE'), ('ADMIN', 'USER_MANAGE'), ('ADMIN', 'ROLE_MANAGE');

-- A user can only hold a role that exists. The existing rows already match.
ALTER TABLE app_user_role
    ADD CONSTRAINT fk_app_user_role_role FOREIGN KEY (role) REFERENCES role (name);
```

The seeds reproduce Lesson 3C's mapping **exactly**, so nothing changes for
anyone on the day you deploy. The five earlier smoke tests pass unchanged on
this lesson's code.

### 2 · Permissions stay in code, with one new one

```java
// model/Permission.java
package tz.co.hmy.pis.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * One permission per action. The code checks these, never role names:
 *   hasAuthority("requisition:approve")   not   hasRole("APPROVER")
 *
 * Permissions stay in code even though roles now live in the database: a
 * permission is what a rule checks, so a new one always comes with new code
 * (and a migration that grants it to ADMIN).
 *
 * In JSON a permission is written as its authority, "requisition:approve".
 * In the role_permission table it is stored by name, REQUISITION_APPROVE.
 */
public enum Permission {

    SUPPLIER_READ("supplier:read"),
    SUPPLIER_WRITE("supplier:write"),
    SUPPLIER_APPROVE("supplier:approve"),

    REQUISITION_READ("requisition:read"),
    REQUISITION_WRITE("requisition:write"),
    REQUISITION_APPROVE("requisition:approve"),

    PURCHASE_ORDER_READ("purchase-order:read"),
    PURCHASE_ORDER_WRITE("purchase-order:write"),

    INVOICE_READ("invoice:read"),
    INVOICE_WRITE("invoice:write"),

    RECORD_DELETE("record:delete"),
    USER_MANAGE("user:manage"),
    ROLE_MANAGE("role:manage");

    private final String authority;

    Permission(String authority) { this.authority = authority; }

    @JsonValue
    public String authority() { return authority; }

    /** "requisition:approve" → REQUISITION_APPROVE. An unknown value becomes a 400, not a 500. */
    @JsonCreator
    public static Permission fromAuthority(String authority) {
        return Arrays.stream(values())
                .filter(p -> p.authority.equals(authority))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown permission: " + authority));
    }
}
```

`@JsonValue` and `@JsonCreator` make the API speak `"supplier:read"` in both
directions, while the database stores the enum name `SUPPLIER_READ`. An unknown
value makes `fromAuthority` throw, which Spring reports as a malformed request (step 10).

### 3 · A role is an entity

Delete `model/Role.java`. In its place:

```java
// model/AppRole.java
package tz.co.hmy.pis.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A role is a named bundle of permissions, stored in the role and
 * role_permission tables. Replaces the Role enum from Lesson 3C.
 *
 * The name is the primary key because it is what app_user_role stores and what
 * the "roles" claim in a JWT carries, e.g. "OFFICER".
 */
@Entity
@Table(name = "role")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = "name")
public class AppRole extends Auditable {

    /** The role everyone else depends on: it always holds every permission. */
    public static final String ADMIN = "ADMIN";

    @Id
    @Column(length = 20)
    private String name;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @ElementCollection
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40)
    private Set<Permission> permissions = new HashSet<>();

    public AppRole(String name, String description, Set<Permission> permissions) {
        this.name = name;
        this.description = description;
        this.permissions = permissions.isEmpty() ? new HashSet<>() : EnumSet.copyOf(permissions);
    }

    /** Replaces every permission. Hibernate turns this into DELETE + INSERT on role_permission. */
    public void replacePermissions(Set<Permission> permissions) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }
}
```

```java
// repository/AppRoleRepository.java
package tz.co.hmy.pis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.hmy.pis.model.AppRole;

import java.util.Collection;
import java.util.List;

public interface AppRoleRepository extends JpaRepository<AppRole, String> {

    /**
     * The roles AND their permissions in one query. Login needs both on every
     * request, and a separate lazy load per role would be one query each.
     */
    @Query("SELECT DISTINCT r FROM AppRole r LEFT JOIN FETCH r.permissions WHERE r.name IN :names")
    List<AppRole> findWithPermissions(@Param("names") Collection<String> names);

    @Query("SELECT DISTINCT r FROM AppRole r LEFT JOIN FETCH r.permissions ORDER BY r.name")
    List<AppRole> findAllWithPermissions();
}
```

> **Why a `JOIN FETCH` query?** A login needs every role *and* its permissions.
> Loading the roles and then touching each `getPermissions()` would be one extra
> query per role, on every single Basic request.

### 4 · `Authorities` reads the tables

In Lesson 3C this was a class of static methods reading the enum. Now it needs a
repository, so it becomes a Spring bean. Its callers inject it instead of calling
`Authorities.of(...)`.

```java
// security/Authorities.java
package tz.co.hmy.pis.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppRole;
import tz.co.hmy.pis.model.Permission;
import tz.co.hmy.pis.repository.AppRoleRepository;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Role names → authorities, in one place, for every way of logging in.
 *
 * Lesson 3C read the mapping from the Role enum. Now it comes from the role and
 * role_permission tables, so this is a Spring bean with a repository. Each role
 * becomes ROLE_<name> (so hasRole still works) and each of its permissions
 * becomes its own authority (for hasAuthority).
 */
@Component
@RequiredArgsConstructor
public class Authorities {

    private final AppRoleRepository roles;

    @Transactional(readOnly = true)
    public List<GrantedAuthority> of(Collection<String> roleNames) {
        Stream<String> names = roleNames.stream().map(name -> "ROLE_" + name);
        return Stream.concat(names, permissions(roleNames).stream())
                .distinct()
                .sorted()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    /** The union of every role's permissions, e.g. ["requisition:approve", "supplier:read", ...]. */
    @Transactional(readOnly = true)
    public List<String> permissions(Collection<String> roleNames) {
        if (roleNames.isEmpty()) {
            return List.of();
        }
        return roles.findWithPermissions(roleNames).stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::authority)
                .distinct()
                .sorted()
                .toList();
    }
}
```

```java
// service/DatabaseUserDetailsService.java
package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.security.Authorities;

/**
 * The bridge between our app_user table and Spring Security.
 *
 * Spring calls loadUserByUsername on every request that carries a Basic header,
 * then compares the password it was sent with the hash we return here.
 * We never compare passwords ourselves.
 */
@RequiredArgsConstructor
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;
    private final Authorities authorities;

    @Override
    @Transactional(readOnly = true) // roles are lazy; read them while the session is open
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username.toLowerCase())
                // Same 401 as a wrong password: never tell a caller which usernames exist.
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                // Not .roles(...): in this builder, roles() and authorities() replace
                // each other, and whichever is called last wins.
                .authorities(authorities.of(user.getRoles()))   // read from role_permission on every request
                .disabled(!user.isEnabled())
                .build();
    }
}
```

`TokenService` changes in three places: an `Authorities` field, the roles
(already strings), and the permissions claim:

```java
// service/TokenService.java (changed part)
private TokenResponse issue(AppUser user, UUID familyId) {
    Instant now = Instant.now();

    // Roles are read from the database on every login AND every refresh,
    // so a role change reaches the user within one access-token lifetime.
    List<String> roles = user.getRoles().stream().sorted().toList();

    JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("pis")
            .subject(user.getUsername())      // becomes Authentication.getName() on later requests
            .issuedAt(now)
            .expiresAt(now.plus(properties.expiry()))
            .claim("roles", roles)
            .claim("permissions", authorities.permissions(user.getRoles()))   // what the user may DO
            .build();
```

### 5 · Users hold role names

`AppUser.roles` becomes a set of names, with no `@Enumerated`:

```java
// model/AppUser.java (changed part)
/** Role names, e.g. "OFFICER". Each must exist in the role table (a foreign key checks it). */
@ElementCollection
@CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
@Column(name = "role", nullable = false, length = 20)
private Set<String> roles = new HashSet<>();
```

`UserRequest` validates each name's shape; whether it *exists* is checked in the service:

```java
// dto/UserRequest.java
package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

/** What an admin sends to create an account. The password arrives in plain text, over HTTPS, and is hashed at once. */
public record UserRequest(

        @NotBlank(message = "username is required")
        @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}",
                 message = "username must be 3-50 letters, digits, dots, dashes or underscores")
        String username,

        // BCrypt ignores everything after 72 bytes, so a longer password gives a false sense of security.
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password,

        @NotBlank(message = "full name is required")
        @Size(max = 150)
        String fullName,

        @NotEmpty(message = "at least one role is required")
        Set<@Pattern(regexp = "[A-Z_]{2,20}", message = "role names are 2-20 capital letters or underscores") String> roles
) { }
```

```java
// dto/UserResponse.java
package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.AppUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** No password and no hash. There is no reason for either to ever leave the server. */
public record UserResponse(
        UUID id,
        String username,
        String fullName,
        boolean enabled,
        List<String> roles,
        List<String> permissions,
        Instant createdAt,
        String createdBy
) {
    /** The permissions come from the role tables, so the caller looks them up. */
    public static UserResponse from(AppUser u, List<String> permissions) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.isEnabled(),
                u.getRoles().stream().sorted().toList(), permissions,
                u.getCreatedAt(), u.getCreatedBy());
    }
}
```

```java
// dto/UserRolesRequest.java
package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record UserRolesRequest(
        @NotEmpty(message = "at least one role is required")
        Set<@Pattern(regexp = "[A-Z_]{2,20}", message = "role names are 2-20 capital letters or underscores") String> roles
) { }
```

```java
// service/UserService.java
package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.UserRequest;
import tz.co.hmy.pis.dto.UserResponse;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppRoleRepository;
import tz.co.hmy.pis.repository.AppUserRepository;
import tz.co.hmy.pis.security.Authorities;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Authorities authorities;

    @Transactional
    public UserResponse create(UserRequest request) {
        String username = request.username().toLowerCase();
        if (users.existsByUsername(username)) {
            throw new DuplicateResourceException("Username " + username + " is already taken");
        }
        requireExistingRoles(request.roles());
        // The only place a plain password exists: it is hashed before it reaches the entity.
        AppUser user = new AppUser(username, passwordEncoder.encode(request.password()),
                request.fullName(), request.roles());
        return toResponse(users.save(user));
    }

    public List<UserResponse> findAll() {
        return users.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse findByUsername(String username) {
        return users.findByUsername(username)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    /** Disable, don't delete: the user's name stays on every record they created. */
    @Transactional
    public UserResponse setEnabled(UUID id, boolean enabled) {
        AppUser user = getOrThrow(id);
        user.setEnabled(enabled);
        return toResponse(user);
    }

    /** Replaces the user's roles. A Basic login sees the change on its next request; a JWT at its next refresh. */
    @Transactional
    public UserResponse setRoles(UUID id, Set<String> roleNames) {
        AppUser user = getOrThrow(id);
        requireExistingRoles(roleNames);
        user.getRoles().clear();
        user.getRoles().addAll(roleNames);
        return toResponse(user);
    }

    /** Checked here so an unknown role is a clear 422, not a foreign-key error from the database. */
    private void requireExistingRoles(Set<String> roleNames) {
        Set<String> unknown = new TreeSet<>(roleNames);
        roles.findAllById(roleNames).forEach(role -> unknown.remove(role.getName()));
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException("Unknown role: " + String.join(", ", unknown));
        }
    }

    private AppUser getOrThrow(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private UserResponse toResponse(AppUser user) {
        return UserResponse.from(user, authorities.permissions(user.getRoles()));
    }
}
```

```java
// repository/AppUserRepository.java
package tz.co.hmy.pis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.hmy.pis.model.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** How many accounts hold a role: a role in use can't be deleted. */
    @Query("SELECT COUNT(u) FROM AppUser u JOIN u.roles r WHERE r = :role")
    long countWithRole(@Param("role") String role);
}
```

`UserController` gets one new endpoint:

```java
// controller/UserController.java (changed part)
@Operation(summary = "Replace an account's roles (ADMIN)")
@PutMapping("/{id}/roles")
public UserResponse setRoles(@PathVariable UUID id, @Valid @RequestBody UserRolesRequest request) {
    return service.setRoles(id, request.roles());
}
```

And `AdminAccountInitializer` names the roles as strings:
`Set.of("ADMIN", "APPROVER", "OFFICER")`.

### 6 · The roles API

```java
// dto/RoleRequest.java
package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tz.co.hmy.pis.model.Permission;

import java.util.Set;

/** A new role. Permissions are written as authorities: "supplier:read". */
public record RoleRequest(

        @NotBlank(message = "name is required")
        @Pattern(regexp = "[A-Z_]{2,20}", message = "role names are 2-20 capital letters or underscores")
        String name,

        @NotBlank(message = "description is required")
        @Size(max = 200)
        String description,

        @NotNull(message = "permissions is required (it may be empty)")
        Set<Permission> permissions
) { }
```

```java
// dto/RolePermissionsRequest.java
package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotNull;
import tz.co.hmy.pis.model.Permission;

import java.util.Set;

public record RolePermissionsRequest(
        @NotNull(message = "permissions is required (it may be empty)")
        Set<Permission> permissions
) { }
```

```java
// dto/RoleResponse.java
package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.AppRole;
import tz.co.hmy.pis.model.Permission;

import java.time.Instant;
import java.util.List;

public record RoleResponse(
        String name,
        String description,
        boolean builtIn,
        List<String> permissions,
        long users,
        Instant updatedAt,
        String updatedBy
) {
    public static RoleResponse from(AppRole role, long users) {
        return new RoleResponse(role.getName(), role.getDescription(), role.isBuiltIn(),
                role.getPermissions().stream().map(Permission::authority).sorted().toList(),
                users, role.getUpdatedAt(), role.getUpdatedBy());
    }
}
```

```java
// service/RoleService.java
package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.RoleRequest;
import tz.co.hmy.pis.dto.RoleResponse;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.AppRole;
import tz.co.hmy.pis.model.Permission;
import tz.co.hmy.pis.repository.AppRoleRepository;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Roles are data now: an admin creates them and changes their permissions
 * while PIS is running. Three rules keep that safe:
 *   1. ADMIN can't be edited or deleted, so nobody can lock everyone out.
 *   2. A built-in role can't be deleted, because the code and the docs refer to it.
 *   3. A role that users still hold can't be deleted.
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class RoleService {

    private final AppRoleRepository roles;
    private final AppUserRepository users;

    public List<RoleResponse> findAll() {
        return roles.findAllWithPermissions().stream().map(this::toResponse).toList();
    }

    public List<String> allPermissions() {
        return Arrays.stream(Permission.values()).map(Permission::authority).toList();
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roles.existsById(request.name())) {
            throw new DuplicateResourceException("Role " + request.name() + " already exists");
        }
        AppRole role = new AppRole(request.name(), request.description(), request.permissions());
        return toResponse(roles.save(role));
    }

    @Transactional
    public RoleResponse replacePermissions(String name, Set<Permission> permissions) {
        AppRole role = getOrThrow(name);
        if (AppRole.ADMIN.equals(name)) {
            throw new BusinessRuleException("The ADMIN role always has every permission and can't be changed");
        }
        role.replacePermissions(permissions);
        return toResponse(roles.saveAndFlush(role));
    }

    @Transactional
    public void delete(String name) {
        AppRole role = getOrThrow(name);
        if (role.isBuiltIn()) {
            throw new BusinessRuleException("Role " + name + " is built in and can't be deleted");
        }
        long holders = users.countWithRole(name);
        if (holders > 0) {
            throw new BusinessRuleException("Role " + name + " is held by " + holders
                    + " user(s). Take it away from them first.");
        }
        roles.delete(role);
    }

    private AppRole getOrThrow(String name) {
        return roles.findById(name).orElseThrow(() -> new ResourceNotFoundException("Role", name));
    }

    private RoleResponse toResponse(AppRole role) {
        return RoleResponse.from(role, users.countWithRole(role.getName()));
    }
}
```

```java
// controller/RoleController.java
package tz.co.hmy.pis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tz.co.hmy.pis.dto.RolePermissionsRequest;
import tz.co.hmy.pis.dto.RoleRequest;
import tz.co.hmy.pis.dto.RoleResponse;
import tz.co.hmy.pis.service.RoleService;

import java.util.List;

/** Needs the role:manage permission (SecurityConfig). */
@Tag(name = "Roles", description = "Roles and the permissions they grant")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService service;

    @Operation(summary = "Every permission the code knows, for building a role")
    @GetMapping("/permissions")
    public List<String> permissions() {
        return service.allPermissions();
    }

    @Operation(summary = "List roles with their permissions")
    @GetMapping("/roles")
    public List<RoleResponse> findAll() {
        return service.findAll();
    }

    @Operation(summary = "Create a role")
    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request, UriComponentsBuilder uri) {
        RoleResponse created = service.create(request);
        return ResponseEntity.created(uri.path("/api/v1/roles/{name}").buildAndExpand(created.name()).toUri())
                .body(created);
    }

    @Operation(summary = "Replace a role's permissions")
    @PutMapping("/roles/{name}/permissions")
    public RoleResponse replacePermissions(@PathVariable String name,
                                           @Valid @RequestBody RolePermissionsRequest request) {
        return service.replacePermissions(name, request.permissions());
    }

    @Operation(summary = "Delete a role nobody holds")
    @DeleteMapping("/roles/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        service.delete(name);
    }
}
```

### 7 · Who may manage roles

One new rule in `SecurityConfig`, next to the users rule:

```java
// config/SecurityConfig.java (changed part)
.requestMatchers("/api/v1/users/**").hasAuthority("user:manage")
.requestMatchers("/api/v1/roles/**", "/api/v1/permissions").hasAuthority("role:manage")
```

### 8 · Malformed JSON is a 400

Until now, a request body that wasn't valid JSON fell through to the catch-all
handler and came back as a **500**, in every lesson. This lesson needs a proper
400 for unknown permissions, so it fixes it for everything. Add the import
`org.springframework.http.converter.HttpMessageNotReadableException`:

```java
// exception/GlobalExceptionHandler.java (changed part)
/**
 * The body isn't valid JSON, or holds a value that can't be converted, such
 * as an unknown permission name. Without this it fell through to the
 * catch-all and became a 500.
 */
@ExceptionHandler(HttpMessageNotReadableException.class)
public ProblemDetail onUnreadableBody(HttpMessageNotReadableException ex) {
    return problem(HttpStatus.BAD_REQUEST, "Malformed request",
            "The request body is not valid JSON, or contains a value that is not allowed", "malformed-request");
}
```

### 9 · Tests read roles from the database too

`@WithRole` now takes a role **name**, and its factory uses the same
`Authorities` bean as a real login. Spring creates the factory, so it can be `@Autowired`:

```java
// (test) WithRole.java
package tz.co.hmy.pis;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Like @WithMockUser(roles = "OFFICER"), but the fake user also gets every
 * permission the OFFICER role has, read from the role tables exactly as a real
 * login reads them.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@WithSecurityContext(factory = WithRoleSecurityContextFactory.class)
public @interface WithRole {

    /** A role name from the role table, e.g. "OFFICER". */
    String value();

    /** Defaults to the role name in lower case: "officer", "approver", "admin". */
    String username() default "";
}
```

```java
// (test) WithRoleSecurityContextFactory.java
package tz.co.hmy.pis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import tz.co.hmy.pis.security.Authorities;

import java.util.List;
import java.util.Set;

/** Spring creates this factory, so it can use the same Authorities bean as a real login. */
public class WithRoleSecurityContextFactory implements WithSecurityContextFactory<WithRole> {

    @Autowired
    private Authorities authorities;

    @Override
    public SecurityContext createSecurityContext(WithRole withRole) {
        String username = withRole.username().isEmpty()
                ? withRole.value().toLowerCase()
                : withRole.username();
        List<GrantedAuthority> granted = authorities.of(Set.of(withRole.value()));

        User principal = new User(username, "not-used", granted);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, granted));
        return context;
    }
}
```

Then, across the existing tests:

| Was | Now |
| --- | --- |
| `Set.of(Role.OFFICER)` | `Set.of("OFFICER")` |
| `@WithRole(Role.OFFICER)` | `@WithRole("OFFICER")` |
| `getRoles().add(Role.APPROVER)` | `getRoles().add("APPROVER")` |
| `Authorities.of(Set.of(Role.OFFICER))` (in `JwtTest`) | `authorities.of(Set.of("OFFICER"))`, with `@Autowired Authorities authorities;` |
| `Role.APPROVER.permissions()` (in `PermissionTest`) | `authorities.permissions(Set.of("APPROVER"))` |

---

## Try it

These steps run against the running app. The output shown is from a real run;
`$BASE`, `$ADMIN_PW` and `$JSON` are set as in the [testing guide](TESTING.md#before-you-start-set-your-variables).

**1 · Create a role while PIS is running** No code change, no restart.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/roles \
  -d '{"name":"AUDITOR","description":"Internal audit: reads everything",
       "permissions":["supplier:read","requisition:read","purchase-order:read","invoice:read"]}' \
  | jq -c '{name, builtIn, permissions, users}'
```
```
{"name":"AUDITOR","builtIn":false,"permissions":["invoice:read","purchase-order:read","requisition:read","supplier:read"],"users":0}
```

**2 · Give someone the role** The new account gets exactly the role's permissions.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/users \
  -d '{"username":"auditor","password":"auditor123","fullName":"Zuhura Audit","roles":["AUDITOR"]}' \
  | jq -c '{username, roles, permissions}'
```
```
{"username":"auditor","roles":["AUDITOR"],"permissions":["invoice:read","purchase-order:read","requisition:read","supplier:read"]}
```

**3 · The auditor can read, and cannot write**

```bash
TIN=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
SUPPLIER="{\"name\":\"Audit Test $TIN\",\"tin\":\"$TIN\",\"registrationNumber\":\"AUDIT-$TIN\",\"category\":\"GOODS\",\"email\":\"audit@example.co.tz\"}"
curl -s -o /dev/null -w 'read:  %{http_code}\n' -u auditor:auditor123 $BASE/api/v1/suppliers
curl -s -o /dev/null -w 'write: %{http_code}\n' -u auditor:auditor123 -X POST -H "$JSON" -d "$SUPPLIER" $BASE/api/v1/suppliers
```
```
read:  200
write: 403
```

**4 · Change the role while the auditor is logged in** Grant `supplier:write`. A Basic login sees it on its next request. A token issued before the change doesn't, until it's refreshed.

```bash
LOGIN=$(curl -s -X POST -H "$JSON" -d '{"username":"auditor","password":"auditor123"}' $BASE/api/v1/auth/login)
OLD_TOKEN=$(echo "$LOGIN" | jq -r .accessToken); REFRESH=$(echo "$LOGIN" | jq -r .refreshToken)

curl -s -u admin:$ADMIN_PW -X PUT -H "$JSON" $BASE/api/v1/roles/AUDITOR/permissions \
  -d '{"permissions":["supplier:read","supplier:write","requisition:read","purchase-order:read","invoice:read"]}' \
  | jq -c '{name, permissions, users}'

TIN=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
SUPPLIER="{\"name\":\"Audit Test $TIN\",\"tin\":\"$TIN\",\"registrationNumber\":\"AUDIT-$TIN\",\"category\":\"GOODS\",\"email\":\"audit@example.co.tz\"}"
curl -s -o /dev/null -w 'Basic login:     %{http_code}\n' -u auditor:auditor123 -X POST -H "$JSON" -d "$SUPPLIER" $BASE/api/v1/suppliers
TIN=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
SUPPLIER="{\"name\":\"Audit Test $TIN\",\"tin\":\"$TIN\",\"registrationNumber\":\"AUDIT-$TIN\",\"category\":\"GOODS\",\"email\":\"audit@example.co.tz\"}"
curl -s -o /dev/null -w 'old token:       %{http_code}\n' -H "Authorization: Bearer $OLD_TOKEN" -X POST -H "$JSON" -d "$SUPPLIER" $BASE/api/v1/suppliers
NEW_TOKEN=$(curl -s -X POST -H "$JSON" -d "{\"refreshToken\":\"$REFRESH\"}" $BASE/api/v1/auth/refresh | jq -r .accessToken)
TIN=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
SUPPLIER="{\"name\":\"Audit Test $TIN\",\"tin\":\"$TIN\",\"registrationNumber\":\"AUDIT-$TIN\",\"category\":\"GOODS\",\"email\":\"audit@example.co.tz\"}"
curl -s -o /dev/null -w 'after refresh:   %{http_code}\n' -H "Authorization: Bearer $NEW_TOKEN" -X POST -H "$JSON" -d "$SUPPLIER" $BASE/api/v1/suppliers
```
```
{"name":"AUDITOR","permissions":["invoice:read","purchase-order:read","requisition:read","supplier:read","supplier:write"],"users":1}
Basic login:     201
old token:       403
after refresh:   201
```

**5 · The guard rails** Three changes PIS refuses, each with a clear error.

```bash
curl -s -u admin:$ADMIN_PW -X PUT -H "$JSON" -d '{"permissions":["supplier:read"]}' $BASE/api/v1/roles/ADMIN/permissions | jq -c '{status, detail}'
curl -s -u admin:$ADMIN_PW -X DELETE $BASE/api/v1/roles/AUDITOR | jq -c '{status, detail}'
curl -s -u admin:$ADMIN_PW -X PUT -H "$JSON" -d '{"permissions":["supplier:fly"]}' $BASE/api/v1/roles/AUDITOR/permissions | jq -c '{status, title}'
```
```
{"status":422,"detail":"The ADMIN role always has every permission and can't be changed"}
{"status":422,"detail":"Role AUDITOR is held by 1 user(s). Take it away from them first."}
{"status":400,"title":"Malformed request"}
```

---

## Test it

`RoleApiTest`, 10 tests, every one through a real login:

```java
// (test) RoleApiTest.java
package tz.co.hmy.pis;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Roles and their permissions live in the database, and an admin changes them
 * at runtime. Every request here uses a real login, so the whole path is
 * covered: role_permission → Authorities → the security rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleApiTest {

    private static final String AUDITOR = """
            { "name": "AUDITOR", "description": "Internal audit: reads everything",
              "permissions": ["supplier:read", "requisition:read", "purchase-order:read", "invoice:read"] }
            """;
    private static final String SUPPLIER = """
            { "name": "Tanga Paper Mills", "tin": "444-555-666", "registrationNumber": "BRELA-2026-4444",
              "category": "GOODS", "email": "sales@tangapaper.co.tz" }
            """;

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createOfficer() {
        users.save(new AppUser("officer", encoder.encode("officer-pass"), "Test Officer", Set.of("OFFICER")));
    }

    private ResultActions asAdmin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(httpBasic("admin", "admin-pass")));
    }

    /** Creates the AUDITOR role and an "auditor" account holding it. */
    private void createAuditor() throws Exception {
        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content(AUDITOR))
            .andExpect(status().isCreated());
        asAdmin(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                { "username": "auditor", "password": "auditor-pass", "fullName": "Zuhura Audit",
                  "roles": ["AUDITOR"] }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.permissions", hasSize(4)));
    }

    private ResultActions grantSupplierWrite() throws Exception {
        return asAdmin(put("/api/v1/roles/AUDITOR/permissions").contentType(MediaType.APPLICATION_JSON).content("""
                { "permissions": ["supplier:read", "supplier:write", "requisition:read",
                                  "purchase-order:read", "invoice:read"] }
                """));
    }

    // ---------------------------------------------------------------- reading

    @Test
    void the_built_in_roles_come_from_the_migration() throws Exception {
        asAdmin(get("/api/v1/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'APPROVER')].permissions[*]", hasItem("requisition:approve")))
            .andExpect(jsonPath("$[?(@.name == 'ADMIN')].builtIn", hasItem(true)))
            .andExpect(jsonPath("$[?(@.name == 'ADMIN')].permissions[*]", hasItem("role:manage")));
        asAdmin(get("/api/v1/permissions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(13)))
            .andExpect(jsonPath("$", hasItem("role:manage")));
    }

    @Test
    void only_role_managers_may_see_or_change_roles() throws Exception {
        mvc.perform(get("/api/v1/roles").with(httpBasic("officer", "officer-pass")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/roles").with(httpBasic("officer", "officer-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(AUDITOR))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- a new role at runtime

    @Test
    void a_new_role_works_without_a_release() throws Exception {
        createAuditor();

        mvc.perform(get("/api/v1/suppliers").with(httpBasic("auditor", "auditor-pass")))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/suppliers").with(httpBasic("auditor", "auditor-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isForbidden());
    }

    @Test
    void a_basic_login_sees_a_permission_change_on_its_next_request() throws Exception {
        createAuditor();
        grantSupplierWrite().andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions", hasItem("supplier:write")))
            .andExpect(jsonPath("$.users").value(1));

        mvc.perform(post("/api/v1/suppliers").with(httpBasic("auditor", "auditor-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isCreated());
    }

    /** The same trade-off as Lesson 3: a token carries what was true when it was issued. */
    @Test
    void a_token_sees_a_permission_change_only_after_refresh() throws Exception {
        createAuditor();
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"auditor\", \"password\": \"auditor-pass\" }"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oldToken = JsonPath.read(login, "$.accessToken");
        String refreshToken = JsonPath.read(login, "$.refreshToken");

        grantSupplierWrite().andExpect(status().isOk());

        mvc.perform(post("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isForbidden());                       // old token: old permissions

        String refreshed = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"refreshToken\": \"%s\" }".formatted(refreshToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(refreshed, "$.accessToken"))
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isCreated());                         // new token: new permissions
    }

    @Test
    void roles_can_be_reassigned_at_runtime() throws Exception {
        createAuditor();
        AppUser officer = users.findByUsername("officer").orElseThrow();

        asAdmin(put("/api/v1/users/{id}/roles", officer.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"roles\": [\"AUDITOR\"] }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("AUDITOR"));

        mvc.perform(post("/api/v1/suppliers").with(httpBasic("officer", "officer-pass"))
                .contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
            .andExpect(status().isForbidden());                       // no longer an officer
    }

    // ---------------------------------------------------------------- guard rails

    @Test
    void nobody_can_change_the_admin_role() throws Exception {
        asAdmin(put("/api/v1/roles/ADMIN/permissions").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"permissions\": [\"supplier:read\"] }"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void a_role_in_use_or_built_in_cannot_be_deleted() throws Exception {
        createAuditor();
        asAdmin(delete("/api/v1/roles/AUDITOR")).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Role AUDITOR is held by 1 user(s). Take it away from them first."));
        asAdmin(delete("/api/v1/roles/APPROVER")).andExpect(status().isUnprocessableEntity());

        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"TEMP\", \"description\": \"Unused\", \"permissions\": [] }"))
            .andExpect(status().isCreated());
        asAdmin(delete("/api/v1/roles/TEMP")).andExpect(status().isNoContent());
    }

    @Test
    void bad_input_is_refused_clearly() throws Exception {
        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content(AUDITOR))
            .andExpect(status().isCreated());
        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content(AUDITOR))
            .andExpect(status().isConflict());                        // duplicate name
        asAdmin(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"X\", \"description\": \"d\", \"permissions\": [\"supplier:fly\"] }"))
            .andExpect(status().isBadRequest())                       // unknown permission
            .andExpect(jsonPath("$.title").value("Malformed request"));
        asAdmin(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                { "username": "ghost", "password": "ghost-pass", "fullName": "Ghost", "roles": ["NOPE"] }
                """))
            .andExpect(status().isUnprocessableEntity())              // unknown role
            .andExpect(jsonPath("$.detail").value("Unknown role: NOPE"));
    }

    @Test
    void broken_json_is_a_400_not_a_500() throws Exception {
        asAdmin(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{ \"username\": "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Malformed request"));
    }
}
```

| Test | What it proves |
| --- | --- |
| `the_built_in_roles_come_from_the_migration` | V7's seeds, and the 13 permissions in code |
| `only_role_managers_may_see_or_change_roles` | `role:manage` is required |
| `a_new_role_works_without_a_release` | Create AUDITOR, assign it, it works |
| `a_basic_login_sees_a_permission_change_on_its_next_request` | Basic: immediate |
| `a_token_sees_a_permission_change_only_after_refresh` | JWT: old token 403, refreshed token 201 |
| `roles_can_be_reassigned_at_runtime` | `PUT /users/{id}/roles` takes effect |
| `nobody_can_change_the_admin_role` | Guard rail: 422 |
| `a_role_in_use_or_built_in_cannot_be_deleted` | Guard rails: 422, 422, then 204 for an unused role |
| `bad_input_is_refused_clearly` | Duplicate 409, unknown permission 400, unknown role 422 |
| `broken_json_is_a_400_not_a_500` | The fix from step 8 |

Two of these were checked the other way round too: removing the ADMIN guard makes
`nobody_can_change_the_admin_role` fail, and removing the malformed-JSON handler
makes two tests fail with 500.

```bash
mvn clean test
# … PermissionTest 8 · RoleApiTest 10 · … → Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
```

---

## Common mistakes

<details>
<summary><b>Every login with a role fails with 401 after a permission is removed from the code</b></summary>

`role_permission` still holds the permission's name, and Hibernate can't turn it
back into an enum constant: `No enum constant tz.co.hmy.pis.model.Permission.REPORT_READ`.
Loading the role fails, so every login holding it fails, admin included.
**Remove a permission from the database first** (a migration deleting its rows),
then from the code. This really happened while building the lesson (next item).
</details>

<details>
<summary><b>A migration you deleted still runs</b></summary>

Maven copies `src/main/resources` into `target/classes` but never deletes from
it. A migration file you removed keeps running on a fresh database until you run
`mvn clean`. That's how the previous mistake happened here: a deleted `V8` put
`REPORT_READ` back in the table.
</details>

<details>
<summary><b>An unknown role gives a 500</b></summary>

The foreign key on `app_user_role` refused it, and the database error reached the
catch-all. Check roles in the service first, as `UserService.requireExistingRoles` does.
</details>

<details>
<summary><b><code>LazyInitializationException</code> when reading a role's permissions</b></summary>

`permissions` is loaded lazily. Use `findWithPermissions(...)` (`JOIN FETCH`),
or read it inside a `@Transactional` method, as `Authorities` does.
</details>

<details>
<summary><b>A permission change doesn't reach a user</b></summary>

With a token, that's expected until their next refresh (at most 5 minutes). With
Basic, check that `DatabaseUserDetailsService` calls `authorities.of(...)` on every
request and nothing caches the result.
</details>

<details>
<summary><b>The new permission works for everyone except the admin</b></summary>

The enum got a new constant, but no migration granted it to ADMIN. ADMIN's
permissions are rows now, not `EnumSet.allOf(...)`. See Exercise 3.
</details>

---

## Exercises

### 1 · Read the tables — *warm-up*

Create the AUDITOR role through the API (Try it, step 3). Then answer in `psql`:
how many rows does AUDITOR have in `role_permission`? What's in its
`created_by` column, and why? Try `DELETE FROM role WHERE name = 'OFFICER';`:
what stops it?

<details>
<summary>Solution</summary>

Four rows, one per permission. `created_by` is `admin`: `AppRole` extends
`Auditable`, so Lesson 2's `AuditorAware` fills it in. The `DELETE` fails on
the foreign key from `app_user_role`, because officers still hold the role. The
API refuses even earlier, because OFFICER is built in.
</details>

### 2 · Break it: remove the ADMIN guard — *understanding*

In `RoleService.replacePermissions`, delete the `if (AppRole.ADMIN.equals(name))`
check and restart. As admin, set ADMIN's permissions to just `["supplier:read"]`.
Now try to put them back. What happens, and how do you recover?

<details>
<summary>Solution</summary>

```
{"name":"ADMIN","permissions":["supplier:read"]}
admin puts the permissions back: 403
admin lists users:              403
```

The admin removed `role:manage` from their own role, and that is exactly the
permission needed to fix roles. Nobody can undo it through the API. The only way
back is straight into the database:

```sql
INSERT INTO role_permission (role, permission)
SELECT 'ADMIN', p FROM unnest(ARRAY['SUPPLIER_WRITE','SUPPLIER_APPROVE','REQUISITION_READ','REQUISITION_WRITE',
  'REQUISITION_APPROVE','PURCHASE_ORDER_READ','PURCHASE_ORDER_WRITE','INVOICE_READ','INVOICE_WRITE',
  'RECORD_DELETE','USER_MANAGE','ROLE_MANAGE']) p ON CONFLICT DO NOTHING;
```

After that, `admin lists users` is `200` again. The test `nobody_can_change_the_admin_role`
fails without the guard (`expected 422 but was 200`), so this can't slip back in unnoticed.
</details>

### 3 · A new permission — *core*

PIS gets a reports module, protected by `report:read`. Add the permission to the
code, run the tests, then make them pass. What else has to change, and why
didn't Lesson 3C need it?

<details>
<summary>Solution</summary>

Add the constant:

```java
ROLE_MANAGE("role:manage"),

REPORT_READ("report:read");
```

Run `mvn test`: `roles_are_bundles_of_permissions` fails. ADMIN is supposed to
hold every permission, but its permissions are **rows** now, and no row grants
`REPORT_READ`. A migration fixes that:

```sql
-- V8__grant_report_read.sql
-- A new permission always needs a migration too: ADMIN must hold every permission.
INSERT INTO role_permission (role, permission) VALUES ('ADMIN', 'REPORT_READ');
```

Then only `the_built_in_roles_come_from_the_migration` fails, because it counts
13 permissions. Change it to 14. In Lesson 3C, ADMIN was `EnumSet.allOf(...)`,
so every new permission reached it automatically. Data doesn't update itself.
</details>

### 4 · A procurement lead — *core*

Create a `PROCUREMENT_LEAD` role through the API that may raise **and** approve
requisitions. Give it to a new user, raise a requisition as that user, and try to
approve it. What happens, and which lesson's rule is responsible?

<details>
<summary>Solution</summary>

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/roles -d '{"name":"PROCUREMENT_LEAD",
  "description":"Raises and approves requisitions","permissions":["requisition:read","requisition:write","requisition:approve"]}'
```

The lead can approve other people's requisitions, but **not their own**: 403.
That's Lesson 3C's `@PreAuthorize` rule, `!@requisitionGuard.raisedBy(...)`. A
role grants permissions; it can never switch off separation of duties.
</details>

### 5 · Cache the permissions — *stretch, design*

Every Basic request now runs a query for the user's roles and permissions. Design
a cache for `Authorities.of(...)`:

1. What is the cache key, and what invalidates an entry?
2. What happens to the "next request" promise from the concepts section?
3. Why is this less urgent for JWT users?

<details>
<summary>Discussion points</summary>

1. Key: the set of role names. Invalidate on every `RoleService` change
   (`@CacheEvict(allEntries = true)` is simplest, because role changes are rare).
2. It holds only if every change evicts the cache. With several app servers,
   each has its own cache, so you need a shared cache (Redis) or a short
   time-to-live. That's the same trade-off again.
3. A JWT request never calls `Authorities`: the permissions are in the token.
   Only login and refresh hit the database.

This is a design discussion, with no reference solution in the project.
</details>

---

## Quiz

**1. Why do permissions stay in code while roles move to the database?**\
a) Enums are faster  b) A permission is what the code checks, so a new one always comes with new code  c) Databases can't store permissions

**2. An admin gives AUDITOR `supplier:write`. When can an auditor who logged in with a token use it?**\
a) Immediately  b) After their next refresh  c) Only after the server restarts

**3. Why can't the ADMIN role be changed through the API?**\
a) It's slow  b) Removing `role:manage` from it would lock everyone out of fixing roles  c) Hibernate forbids it

**4. You add `REPORT_READ` to the enum. Why doesn't the admin have it?**\
a) Enums are cached  b) ADMIN's permissions are rows; a migration must grant the new one  c) It needs a restart

**5. Why does `Authorities` use a `JOIN FETCH` query?**\
a) To load roles and permissions in one query instead of one per role  b) JOIN FETCH is required for enums  c) To sort the results

**6. A permission is removed from the enum but not from `role_permission`. What happens?**\
a) Nothing  b) Loading any role with it fails, so those users can't log in  c) The row is ignored

<details>
<summary>Answers</summary>

1. **b.** A permission nobody checks is meaningless; a check for one that doesn't exist never passes.
2. **b.** The token carries the permissions it was issued with.
3. **b.** It's the one change the API can't undo (Exercise 2).
4. **b.** Lesson 3C's `EnumSet.allOf` did that automatically; data doesn't.
5. **a.** Every Basic request loads them, so the query count matters.
6. **b.** `No enum constant …`: remove from the database first, then from the code.
</details>

---

## Smoke test

Save as `smoke.sh` and run while the app is up. It needs the Lesson 3D code and
the officer from Lesson 2. It resets AUDITOR to read-only at the start and the
end, so it can be run again.

```bash
#!/usr/bin/env bash
# Lesson 3D smoke test: roles and permissions in the database.
# Creates an AUDITOR role and an "auditor" account (password auditor123) if missing,
# and leaves AUDITOR read-only at the end, so it can be run again.
BASE=http://localhost:8080
ADMIN='admin:admin123'
JSON='Content-Type: application/json'
READ_ONLY='{"permissions":["supplier:read","requisition:read","purchase-order:read","invoice:read"]}'
WITH_WRITE='{"permissions":["supplier:read","supplier:write","requisition:read","purchase-order:read","invoice:read"]}'

check() {   # check <expected status, e.g. 200 or "201|409"> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if echo "$got" | grep -qE "^($want)$"; then echo "PASS  $got  $desc"; else echo "FAIL  $got  $desc (expected $want)"; fi
}
field()    { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
supplier() { local t; t=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
             echo "{\"name\":\"Smoke Supplier $t\",\"tin\":\"$t\",\"registrationNumber\":\"SMOKE-$t\",\"category\":\"GOODS\",\"email\":\"smoke@example.co.tz\"}"; }

echo "--- a role created at runtime"
check "201|409" "admin creates the AUDITOR role"          -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/roles" \
      -d '{"name":"AUDITOR","description":"Internal audit: reads everything","permissions":[]}'
check 200       "AUDITOR gets read-only permissions"       -u "$ADMIN" -X PUT -H "$JSON" -d "$READ_ONLY" "$BASE/api/v1/roles/AUDITOR/permissions"
check "201|409" "admin creates the auditor account"        -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/users" \
      -d '{"username":"auditor","password":"auditor123","fullName":"Zuhura Audit","roles":["AUDITOR"]}'
check 200       "auditor reads suppliers"                  -u auditor:auditor123 "$BASE/api/v1/suppliers"
check 403       "auditor cannot create a supplier"         -u auditor:auditor123 -X POST -H "$JSON" -d "$(supplier)" "$BASE/api/v1/suppliers"

echo "--- change the role while people are logged in"
LOGIN=$(curl -s -X POST -H "$JSON" -d '{"username":"auditor","password":"auditor123"}' "$BASE/api/v1/auth/login")
OLD_TOKEN=$(echo "$LOGIN" | field accessToken); REFRESH=$(echo "$LOGIN" | field refreshToken)
check 200       "admin grants supplier:write to AUDITOR"   -u "$ADMIN" -X PUT -H "$JSON" -d "$WITH_WRITE" "$BASE/api/v1/roles/AUDITOR/permissions"
check 201       "Basic login: allowed on the next request" -u auditor:auditor123 -X POST -H "$JSON" -d "$(supplier)" "$BASE/api/v1/suppliers"
check 403       "old token: still the old permissions"     -H "Authorization: Bearer $OLD_TOKEN" -X POST -H "$JSON" -d "$(supplier)" "$BASE/api/v1/suppliers"
NEW_TOKEN=$(curl -s -X POST -H "$JSON" -d "{\"refreshToken\":\"$REFRESH\"}" "$BASE/api/v1/auth/refresh" | field accessToken)
check 201       "after refresh: the new permissions"       -H "Authorization: Bearer $NEW_TOKEN" -X POST -H "$JSON" -d "$(supplier)" "$BASE/api/v1/suppliers"
check 200       "admin takes supplier:write away again"    -u "$ADMIN" -X PUT -H "$JSON" -d "$READ_ONLY" "$BASE/api/v1/roles/AUDITOR/permissions"
check 403       "Basic login: refused on the next request" -u auditor:auditor123 -X POST -H "$JSON" -d "$(supplier)" "$BASE/api/v1/suppliers"

echo "--- guard rails"
check 403 "an officer cannot manage roles"                 -u 'officer:officer123' "$BASE/api/v1/roles"
check 422 "nobody can change the ADMIN role"               -u "$ADMIN" -X PUT -H "$JSON" -d "$READ_ONLY" "$BASE/api/v1/roles/ADMIN/permissions"
check 422 "a role in use cannot be deleted"                -u "$ADMIN" -X DELETE "$BASE/api/v1/roles/AUDITOR"
check 422 "a built-in role cannot be deleted"              -u "$ADMIN" -X DELETE "$BASE/api/v1/roles/APPROVER"
check 400 "an unknown permission is a 400"                 -u "$ADMIN" -X PUT -H "$JSON" -d '{"permissions":["supplier:fly"]}' "$BASE/api/v1/roles/AUDITOR/permissions"
check 422 "an unknown role is a 422"                       -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/users" \
      -d '{"username":"ghost","password":"ghost-pass","fullName":"Ghost","roles":["NOPE"]}'
```

Expected output:

```
--- a role created at runtime
PASS  201  admin creates the AUDITOR role
PASS  200  AUDITOR gets read-only permissions
PASS  201  admin creates the auditor account
PASS  200  auditor reads suppliers
PASS  403  auditor cannot create a supplier
--- change the role while people are logged in
PASS  200  admin grants supplier:write to AUDITOR
PASS  201  Basic login: allowed on the next request
PASS  403  old token: still the old permissions
PASS  201  after refresh: the new permissions
PASS  200  admin takes supplier:write away again
PASS  403  Basic login: refused on the next request
--- guard rails
PASS  403  an officer cannot manage roles
PASS  422  nobody can change the ADMIN role
PASS  422  a role in use cannot be deleted
PASS  422  a built-in role cannot be deleted
PASS  400  an unknown permission is a 400
PASS  422  an unknown role is a 422
```

(On a second run, the first and third lines say `409`: the role and account already exist.)

---

## Next

**→ [Lecture 4 — OAuth2 with Spring Authorization Server](../../../pis-lecture4-authorization-server/LECTURE.md).**
Lecture 4 starts from Lesson 3B. Exercise idea: move its hard-coded users and
their roles into tables the same way, on the authorization server's side.
