# Lecture 4 — Spring Authorization Server

Two applications:

```
pis-lecture4-authorization-server/
├── auth-server/   Spring Authorization Server, port 9100: logins, clients, tokens
├── pis-api/       PIS as a pure resource server, port 8181: trusts auth-server's tokens
├── smoke/         smoke-lecture4.sh: every flow, end to end, with curl
└── LECTURE.md     the lecture
```

## Run

```bash
# once
createdb pmis_oauth && createdb pmis_oauth_test
cp auth-server/.env.example auth-server/.env     # set passwords and client secrets
cp pis-api/.env.example pis-api/.env             # set DB_USER / DB_PASSWORD

# two terminals, auth-server first
cd auth-server && mvn spring-boot:run
cd pis-api     && mvn spring-boot:run

# a third terminal
bash smoke/smoke-lecture4.sh
```

The smoke script reads the passwords and secrets from environment variables,
with defaults matching the lecture (`officer123`, `bff-secret-123`, …).

Ports 9100 and 8181 were chosen because 9000 and 8080–8081 are often taken.
