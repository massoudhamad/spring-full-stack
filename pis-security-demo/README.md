# pis-security-demo — the reference solution

The PIS project with **Lessons 1–3C finished**: HTTP Basic, users in the
database, JWT, refresh tokens, and permissions with method security. Use it to
compare with your own code, or to demo a lesson without building it live.

```bash
createdb pmis_demo && createdb pmis_demo_test
cp .env.example .env        # set DB_URL=…/pmis_demo, TEST_DB_URL=…/pmis_demo_test,
                            # SERVER_PORT=8099, ADMIN_PASSWORD, JWT_SECRET (32+ characters)
mvn test                    # 56 tests
mvn spring-boot:run         # port 8099
```

Once it's running, each lesson has a smoke test:

```bash
bash smoke/smoke-lesson2.sh     # run Lesson 2 first: it creates the officer and approver
bash smoke/smoke-lesson1.sh
bash smoke/smoke-lesson3.sh
bash smoke/smoke-lesson3b.sh
bash smoke/smoke-lesson3c.sh
```

The scripts use `http://localhost:8099` and the passwords `admin123`,
`officer123` and `approver123`. Edit the top of a script if yours differ.

The lessons themselves are in
[`../procurement-information-system_v1.0/docs/lessons/`](../procurement-information-system_v1.0/docs/lessons/).
