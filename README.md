# PIS Security Course — Spring Boot

Securing a real Spring Boot API step by step: the **Procurement Information
System (PIS)** goes from wide open to one account per person, signed tokens,
refresh tokens, permissions, and finally a separate OAuth2 authorization server.
Every lesson ends with tests you can run.

## Start here

1. **[The lessons](procurement-information-system_v1.0/docs/lessons/README.md)**: what to read, in what order.
2. **[The testing guide](procurement-information-system_v1.0/docs/lessons/TESTING.md)**: check each lesson step by step.
3. **[The course web page](procurement-information-system_v1.0/docs/lessons/index.html)**: everything on one page. Download it and open it in a browser.

## What's in this repository

| Folder | What it is | Port |
| --- | --- | --- |
| [`procurement-information-system_v1.0/`](procurement-information-system_v1.0/) | **The project students work on.** Lesson 1 is applied; the lessons show how to add the rest. | 8080 |
| [`pis-security-demo/`](pis-security-demo/) | **The reference solution:** the same project with Lessons 1–3C finished, plus a smoke-test script per lesson. | 8099 |
| [`pis-lecture4-authorization-server/`](pis-lecture4-authorization-server/) | **Lecture 4:** a Spring Authorization Server and PIS as a pure OAuth2 resource server. | 9100, 8181 |

## The lessons

| # | Lesson | Where |
| --- | --- | --- |
| 1 | [HTTP Basic and roles](procurement-information-system_v1.0/docs/lessons/01-basic-auth.md) | PIS project |
| 2 | [Users in the database](procurement-information-system_v1.0/docs/lessons/02-users-in-database.md) | PIS project |
| 3 | [Tokens and JWT](procurement-information-system_v1.0/docs/lessons/03-jwt.md) | PIS project |
| 3B | [Refresh tokens](procurement-information-system_v1.0/docs/lessons/03b-refresh-tokens.md) | PIS project |
| 3C | [Permissions and method security](procurement-information-system_v1.0/docs/lessons/03c-permissions.md) | PIS project |
| 4 | [OAuth2 with Spring Authorization Server](pis-lecture4-authorization-server/LECTURE.md) | Lecture 4 project |
| 5 | OAuth2 with Keycloak | planned |
| 6 | Log in with Google / GitHub | planned |

## What you need

JDK 17, Maven, PostgreSQL 17, and for testing `curl` and `jq`. Each project has
its own `README.md` with its setup, and its own `.env.example`: copy it to `.env`
and fill in your values. `.env` files are never committed.
