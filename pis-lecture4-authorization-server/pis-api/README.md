# pis-api — PIS as an OAuth2 resource server

The Procurement Information System from Lessons 1–3B, with its own login,
users table, signing key and refresh tokens **removed**. It accepts only
`Authorization: Bearer <token>` issued by `../auth-server`, and checks each
token with that server's public keys.

```bash
createdb pmis_oauth          # the app
createdb pmis_oauth_test     # the tests
cp .env.example .env         # then fill in DB_USER / DB_PASSWORD
mvn spring-boot:run          # port 8181; start auth-server first
mvn test                     # needs no running auth-server: tests use jwt()
```

Compared with the end of Lesson 3B:

| Removed | Why |
| --- | --- |
| `AppUser`, `Role`, `app_user` tables, `DatabaseUserDetailsService`, `UserController`, `AdminAccountInitializer` | Users live in the authorization server |
| `AuthController`, `TokenService`, `JwtConfig`, `JwtProperties`, `RefreshToken`, `JWT_SECRET` | The authorization server issues and refreshes tokens |
| HTTP Basic | The only credential PIS accepts is a token |

| Added or changed | |
| --- | --- |
| `application.yml` | `spring.security.oauth2.resourceserver.jwt.issuer-uri` |
| `SecurityConfig` | Roles **and** scopes; `SCOPE_suppliers.read` for the reporting job |
| `MeController` | `GET /api/v1/me`: what the token says about the caller |
| `ResourceServerTest` | Rules tested with `jwt()` |

The lecture is in [`../LECTURE.md`](../LECTURE.md).
