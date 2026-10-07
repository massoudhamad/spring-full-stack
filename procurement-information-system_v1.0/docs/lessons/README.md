# PIS Security Course

| # | Lesson | Project | Status |
| --- | --- | --- | --- |
| 1 | [HTTP Basic and roles](01-basic-auth.md) | this project | Ready |
| 2 | [Users in the database, and who did what](02-users-in-database.md) | this project | Ready |
| 3 | [Tokens and JWT](03-jwt.md) | this project | Ready |
| 3B | [Refresh tokens](03b-refresh-tokens.md) | this project | Ready |
| 3C | [Permissions and method security](03c-permissions.md) | this project | Ready |
| 3D | [Roles and permissions in the database](03d-roles-in-database.md) | this project | Ready |
| 4 | [OAuth2 with Spring Authorization Server](../../../pis-lecture4-authorization-server/LECTURE.md) | `../pis-lecture4-authorization-server/` | Ready |
| 5 | [API gateway and service discovery (Eureka)](../../../pis-lecture5-gateway-eureka/LECTURE.md) | `../pis-lecture5-gateway-eureka/` | Ready |
| 6 | OAuth2 with Keycloak | `../pis-lecture6-keycloak/` | Planned |
| 7 | Log in with Google / GitHub | `../pis-lecture7-social-login/` | Planned |

**[TESTING.md](TESTING.md)** tests each lesson step by step: the command, what
you should see (real output), and what to check if it doesn't match.

Lessons 1–3D build on each other inside this project, one upgrade at a time.
Lectures 4–7 are separate projects. Lecture 4 starts from the finished Lesson 3B code; Lecture 5 is self-contained.

A reference copy with Lessons 1–3D already applied is in
[`pis-security-demo/`](../../../pis-security-demo/) (port 8099), with a smoke-test script per lesson in `smoke/`.

[`index.html`](index.html) is the whole course as one web page: a sidebar with every
lesson, a course overview, and a setup box that fills your URL and passwords into
every command. Open it in a browser, or use the published copy.
