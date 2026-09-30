# Procurement Information System

A Spring Boot API for suppliers, requisitions, purchase orders and invoices, and
the codebase for the **PIS Security Course**.

## PIS Security Course

Step by step, from a wide-open API to signed tokens and permissions. Each lesson
upgrades this project, and each one ends with tests you can run.

| # | Lesson | What you add |
| --- | --- | --- |
| 1 | [HTTP Basic and roles](docs/lessons/01-basic-auth.md) | Log in, role rules, 401 vs 403 |
| 2 | [Users in the database](docs/lessons/02-users-in-database.md) | `app_user` table, first admin, who created what |
| 3 | [Tokens and JWT](docs/lessons/03-jwt.md) | Log in once, then `Bearer` tokens |
| 3B | [Refresh tokens](docs/lessons/03b-refresh-tokens.md) | 5-minute tokens, rotation, logout |
| 3C | [Permissions and method security](docs/lessons/03c-permissions.md) | `hasAuthority`, `@PreAuthorize`, nobody approves their own requisition |

- **[Testing guide](docs/lessons/TESTING.md):** test each lesson step by step: the command, what you should see, and what to check if it doesn't match.
- **[Course index](docs/lessons/README.md):** the full course map, including the OAuth2 lectures.
- **[Course web page](https://massoudhamad.github.io/spring-full-stack/procurement-information-system_v1.0/docs/lessons/):** the whole course as one page, with a sidebar and a setup box.

This project contains **Lesson 1** in the code; the lessons show how to add the rest. The finished solution is in [`../pis-security-demo/`](../pis-security-demo/), and Lecture 4 in [`../pis-lecture4-authorization-server/`](../pis-lecture4-authorization-server/).

---

## The project

Spring Boot CRUD skeleton — model, repository, service, DTO, controller.

**Spring Boot 3.5.6 · Java 17 · PostgreSQL 17 · Flyway**

## Setup

PostgreSQL 17 and JDK 17. One-time database setup:

```bash
createdb pmis         # development
createdb pmis_test    # tests
```

Then copy the environment template and fill in your own credentials:

```bash
cp .env.example .env
```

`.env` is gitignored and read automatically at startup (via `spring-dotenv`).
Credentials live only there — never in `application.yml`.

## Run

```bash
mvn spring-boot:run
```

Flyway creates the schema and loads the seed data on first start.

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Connection settings come from `.env`. Real environment variables of the same
name take precedence, which is how you configure a deployed instance:

```bash
DB_URL=jdbc:postgresql://db.internal:5432/pmis \
DB_USER=pmis_app DB_PASSWORD=... \
mvn spring-boot:run
```

Three suppliers and one approved requisition are seeded by `V2`.

## Layers

```
controller/   HTTP only. No rules, no repository calls.
service/      Transaction boundary. Business rules live here.
repository/   Spring Data JPA. Queries only.
model/        JPA entities and enums.
dto/          Request and response records. Never expose an entity.
exception/    Domain exceptions plus one @RestControllerAdvice.
config/       Auditing, OpenAPI, security.
```

**There is no mapper package.** Each response record carries a static
`from(entity)` factory, and each service builds its own entities from requests.
At three aggregates a mapper class only forwards calls; it earns its place
later, when one entity feeds several different response shapes.

The rule that matters: **a request flows controller → service → repository and
never skips a layer or goes backwards.** If a controller injects a repository,
the design has already broken.

## Endpoints

### Suppliers

| Method | Path | |
| --- | --- | --- |
| POST | `/api/v1/suppliers` | 201 + `Location` |
| GET | `/api/v1/suppliers` | `?status=&category=&search=&page=&size=&sort=` |
| GET | `/api/v1/suppliers/{id}` | |
| PUT | `/api/v1/suppliers/{id}` | TIN and registration number are immutable |
| PATCH | `/api/v1/suppliers/{id}/status` | |
| DELETE | `/api/v1/suppliers/{id}` | 422 if purchase orders exist |

### Requisitions

| Method | Path | |
| --- | --- | --- |
| POST | `/api/v1/requisitions` | reference auto-generated |
| GET | `/api/v1/requisitions` | `?status=&department=` |
| GET | `/api/v1/requisitions/{id}` | |
| PUT | `/api/v1/requisitions/{id}` | DRAFT only; replaces all items |
| POST | `/api/v1/requisitions/{id}/submit` | DRAFT → SUBMITTED |
| POST | `/api/v1/requisitions/{id}/approve` | SUBMITTED → APPROVED |
| POST | `/api/v1/requisitions/{id}/reject` | SUBMITTED → REJECTED |
| DELETE | `/api/v1/requisitions/{id}` | DRAFT only |

### Purchase orders

| Method | Path | |
| --- | --- | --- |
| POST | `/api/v1/purchase-orders` | supplier must be ACTIVE; requisition must be APPROVED |
| GET | `/api/v1/purchase-orders` | `?status=&supplierId=` |
| GET | `/api/v1/purchase-orders/{id}` | |
| PUT | `/api/v1/purchase-orders/{id}` | DRAFT only |
| POST | `/api/v1/purchase-orders/{id}/issue` | DRAFT → ISSUED |
| POST | `/api/v1/purchase-orders/{id}/cancel` | |
| DELETE | `/api/v1/purchase-orders/{id}` | DRAFT only |

State changes are `POST` to a named sub-resource rather than a `PATCH` that lets
the client set any status it likes. `submit` and `approve` are different
operations with different rules; one endpoint taking a status field cannot
express that.

## Authentication (HTTP Basic)

Every `/api/v1/**` call needs a username and password. The client sends them
on **every request** as a header:

```
Authorization: Basic b2ZmaWNlcjpvZmZpY2VyMTIz      ← base64("officer:officer123")
```

Base64 is an encoding, not encryption: `echo b2ZmaWNlcjpvZmZpY2VyMTIz | base64 -d`
gives the password straight back. **Basic auth is only safe over HTTPS.**

Three demo users are held in memory (`SecurityConfig`). Their passwords come
from `.env` and are BCrypt-hashed at startup:

| User | Roles | `.env` variable |
| --- | --- | --- |
| `officer` | OFFICER | `OFFICER_PASSWORD` |
| `approver` | APPROVER | `APPROVER_PASSWORD` |
| `admin` | ADMIN, APPROVER, OFFICER | `ADMIN_PASSWORD` |

| Request | Who |
| --- | --- |
| `/actuator/health`, Swagger UI, `/v3/api-docs` | anyone |
| `GET /api/v1/**` | any logged-in user |
| `POST /api/v1/requisitions/{id}/approve` and `/reject` | APPROVER |
| `PATCH /api/v1/suppliers/{id}/status` | APPROVER |
| `DELETE /api/v1/**` | ADMIN |
| any other `/api/v1/**` write | OFFICER |
| anything else | nobody |

**401 vs 403.** 401 means *I don't know who you are*: no credentials, or wrong
ones. 403 means *I know who you are, and your role cannot do this*. Both come
back as problem details, like every other error.

In Swagger UI, click **Authorize** and enter a username and password.

Full lesson with a demo script, exercises and a quiz:
[docs/lessons/01-basic-auth.md](docs/lessons/01-basic-auth.md).

## Try it

```bash
# no credentials → 401 with  WWW-Authenticate: Basic realm="pis"
curl -i localhost:8080/api/v1/suppliers

# -u builds the Authorization header for you
curl -s -u officer:officer123 localhost:8080/api/v1/suppliers | jq

# the same thing, by hand
curl -s -H "Authorization: Basic $(printf 'officer:officer123' | base64)" \
  localhost:8080/api/v1/suppliers | jq

# right user, wrong role → 403
curl -s -u officer:officer123 -X POST \
  localhost:8080/api/v1/requisitions/99999999-9999-9999-9999-999999999999/approve | jq

curl -s -u officer:officer123 -X POST localhost:8080/api/v1/suppliers \
  -H 'Content-Type: application/json' \
  -d '{"name":"Unguja Stationers Ltd","tin":"555-666-777",
       "registrationNumber":"BRELA-2022-5555","category":"GOODS",
       "email":"sales@unguja.co.tz"}' | jq

# validation failure — returns field-level errors
curl -s -u officer:officer123 -X POST localhost:8080/api/v1/suppliers \
  -H 'Content-Type: application/json' \
  -d '{"name":"","tin":"bad","registrationNumber":"X","category":"GOODS","email":"nope"}' | jq
```

## Errors

Every error is an RFC 9457 problem detail:

```json
{
  "type": "https://hmy.co.tz/problems/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "timestamp": "2026-09-09T08:14:22Z",
  "errors": { "tin": "TIN must look like 123-456-789" }
}
```

| Status | When |
| --- | --- |
| 400 | Bean validation failed |
| 401 | No credentials, or wrong ones |
| 403 | Logged in, but the role does not allow it |
| 404 | `ResourceNotFoundException` |
| 409 | Duplicate TIN or registration number |
| 422 | Business rule violated |

## Decisions worth knowing

**Flyway owns the schema.** `ddl-auto: validate` — Hibernate refuses to start if
the entities and tables disagree. Never `update`.

**Totals are derived, not stored.** A stored total is a total that can disagree
with its lines.

**`open-in-view: false`.** A lazy load in the view layer is a query nobody
planned. Fetch what you need in the service.

**Package-by-layer**, because you asked for those packages. It reads well at
this size. Past roughly fifteen entities, switch to package-by-feature —
`supplier/`, `requisition/` each holding their own layers — or `service/` grows
into a folder nobody can navigate.

**Lombok, but not `@Data`.** Entities use `@Getter`, `@NoArgsConstructor(PROTECTED)`,
`@Builder` and a narrow `@ToString`. Services and controllers use
`@RequiredArgsConstructor` over `final` fields — that is where most of the
boilerplate was.

`@Data`, `@Value` and `@EqualsAndHashCode` are set to `ERROR` in `lombok.config`
and will fail the build. On an entity, `@Data` generates `toString` across every
field including lazy associations, so logging one entity pulls the whole object
graph out of the database; and it generates `hashCode` from mutable fields, so an
entity added to a `HashSet` before saving becomes unreachable after the id is
assigned. Both are silent until they are not.

**No `@Setter` on entities.** State changes go through named methods — `submit()`,
`approve()`, `changeStatus()` — so the rules stay next to the state they govern.

**DTOs stay as records.** A record already gives you the constructor, accessors,
`equals`, `hashCode` and `toString`, natively and immutably. Lombok adds nothing
there.

## Commands

| | |
| --- | --- |
| Run | `mvn spring-boot:run` |
| Test | `mvn test` |
| Package | `mvn package` |
| Skip tests | `mvn package -DskipTests` |
| Dependency tree | `mvn dependency:tree` |
| Clean | `mvn clean` |

`mvn test` runs against the `pis_test` database, not an embedded one. Each test
rolls back, so the suite is repeatable and the seed data survives.

**Why not H2 for tests.** H2 accepts SQL that PostgreSQL rejects, so a green H2
suite tells you very little about the database you deploy to. The schema here
uses `TIMESTAMPTZ` and functional indexes on `LOWER(name)` — both PostgreSQL
features, and the `LOWER()` indexes are what make the supplier search use an
index rather than scan.

If you want the Maven version pinned for everyone, run `mvn -N wrapper:wrapper`
once and commit `mvnw` and `.mvn/`.

## Known limits

**`ReferenceGenerator` counts rows.** Two simultaneous creates can produce the
same reference. Replace with a database sequence per year before this goes
anywhere real.

**Users live in memory.** Adding a user means a code change and a restart.
Move them to a `users` table behind a `UserDetailsService`; nothing else in
`SecurityConfig` has to change. Basic auth also has no logout and sends the
password on every request, so a browser front end will want sessions or tokens.

**No optimistic locking.** Add `@Version` before two users can edit the same
requisition.

**Delete seed data (`V2`) before deploying.**

## Not yet built

Not built, and worth pricing separately: tender and contract management,
goods-received notes and three-way matching, budget line checks against
available funds, approval workflows with delegation, and document attachments.
