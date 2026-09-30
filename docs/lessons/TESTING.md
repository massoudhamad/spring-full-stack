# Testing guide — PIS security course

A step-by-step check for each lesson. Do the steps in order, in **one terminal**:
later steps use variables (`$TOKEN`, `$R1`, …) that earlier steps set.

Every "you should see" block below is the **real output** of that command, run
on the finished demo project. IDs, tokens, times and the random TIN will differ
on your machine; the status codes and the fields should not.

**Tools:** `curl`, `jq`, `openssl` and `psql`. On macOS: `brew install jq`
(the others are already there, or come with PostgreSQL).

| Lesson | Unit tests | Needs the app running | Steps |
| --- | --- | --- | --- |
| [1 · HTTP Basic and roles](#lesson-1--http-basic-and-roles) | `SecurityTest` (12 tests) | yes | 10 |
| [2 · Users in the database](#lesson-2--users-in-the-database) | `UserApiTest` (9 tests) | yes | 10 |
| [3 · Tokens and JWT](#lesson-3--tokens-and-jwt) | `JwtTest` (9 tests) | yes | 8 |
| [3B · Refresh tokens](#lesson-3b--refresh-tokens) | `RefreshTokenTest` (9 tests) | yes | 6 |
| [3C · Permissions and method security](#lesson-3c--permissions-and-method-security) | `PermissionTest` (8 tests) | yes | 9 |
| [4 · OAuth2 with Spring Authorization Server](#lecture-4--oauth2-with-spring-authorization-server) | both apps (8 + 15 tests) | both apps | 9 |

## Before you start: set your variables

Paste this once into your terminal, and change anything that differs on your machine:

```bash
BASE=http://localhost:8080          # your PIS app (the finished demo runs on 8099)
ADMIN_PW=admin123                   # ADMIN_PASSWORD from .env
OFFICER_PW=officer123               # Lesson 1: OFFICER_PASSWORD from .env; later: what you create the officer with
APPROVER_PW=approver123
ID=99999999-9999-9999-9999-999999999999   # a requisition/supplier id that doesn't exist
JSON='Content-Type: application/json'
export PGUSER=postgres DB=pmis      # your database user and database, for the psql steps
```

Start the app in a **second** terminal and leave it running:

```bash
mvn spring-boot:run
```

Run the unit-test steps from the project folder (where `pom.xml` is).

---

## Lesson 1 — HTTP Basic and roles

**You need:** The Lesson 1 code, and the app running (`mvn spring-boot:run`).

### Step 1 · Run this lesson's unit tests

They use the test database, not the running app.

```bash
mvn test -Dtest=SecurityTest
```

**You should see** `Tests run: 12, Failures: 0` and `BUILD SUCCESS`:

```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** `There are test failures`: open `target/surefire-reports/` for the failing test. `No tests were executed`: this lesson's test class doesn't exist yet.

### Step 2 · The server is up, and health is public

Health must answer without a password, or monitoring breaks.

```bash
curl -s -o /dev/null -w '%{http_code}\n' $BASE/actuator/health
```

**You should see** `200`:

```
200
```

> **If not:** Connection refused: the app isn't running, or `BASE` has the wrong port.

### Step 3 · No credentials → 401, with a Basic challenge

Proves the API is locked, and tells clients which scheme to use.

```bash
curl -s -i $BASE/api/v1/suppliers | grep -E '^HTTP|^WWW-Authenticate'
```

**You should see** `HTTP/1.1 401` and `WWW-Authenticate: Basic realm="pis"`:

```
HTTP/1.1 401 
WWW-Authenticate: Basic realm="pis"
```

> **If not:** `200`: `SecurityConfig` isn't loaded, or the rule is `permitAll()`.

### Step 4 · Wrong password → 401

Authentication really checks the password.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:wrong $BASE/api/v1/suppliers
```

**You should see** `401`:

```
401
```

### Step 5 · The officer can read

A correct login passes, and any role may read.

```bash
curl -s -u officer:$OFFICER_PW "$BASE/api/v1/suppliers?size=1" | jq '{totalElements, first: .content[0].name}'
```

**You should see** A `totalElements` count and a supplier name:

```
{
  "totalElements": 10,
  "first": "Kisiwa ICT Consultants"
}
```

> **If not:** `401`: `OFFICER_PW` doesn't match `OFFICER_PASSWORD` in `.env`.

### Step 6 · The officer cannot approve → 403

Authorization: logged in, but the wrong role.

```bash
curl -s -u officer:$OFFICER_PW -X POST $BASE/api/v1/requisitions/$ID/approve | jq '{status, title}'
```

**You should see** `403`, "Access denied":

```
{
  "status": 403,
  "title": "Access denied"
}
```

> **If not:** `404`: the approve rule is below a broader rule. The first match wins.

### Step 7 · The approver can approve → 404

404 means security let the request through, and the service didn't find the (fake) id. No data changes.

```bash
curl -s -u approver:$APPROVER_PW -X POST $BASE/api/v1/requisitions/$ID/approve | jq '{status, title}'
```

**You should see** `404`, "Resource not found":

```
{
  "status": 404,
  "title": "Resource not found"
}
```

> **If not:** `403`: the rule uses the wrong role name.

### Step 8 · The approver cannot create a supplier → 403

Writes need OFFICER.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u approver:$APPROVER_PW -X POST -H "$JSON" -d '{}' $BASE/api/v1/suppliers
```

**You should see** `403`:

```
403
```

> **If not:** `400`: the write rule is missing, so validation ran instead.

### Step 9 · Only the admin may delete

The officer gets 403; the admin gets through to a 404.

```bash
curl -s -o /dev/null -w 'officer: %{http_code}\n' -u officer:$OFFICER_PW -X DELETE $BASE/api/v1/suppliers/$ID
curl -s -o /dev/null -w 'admin:   %{http_code}\n' -u admin:$ADMIN_PW -X DELETE $BASE/api/v1/suppliers/$ID
```

**You should see** `officer: 403` and `admin: 404`:

```
officer: 403
admin:   404
```

### Step 10 · The Basic header, built by hand

`-u` is just Base64 of `user:password`. Anyone can decode it, so Basic auth needs HTTPS.

```bash
HEADER=$(printf 'officer:%s' "$OFFICER_PW" | base64)
echo "$HEADER" | base64 -d; echo
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Basic $HEADER" $BASE/api/v1/suppliers
```

**You should see** The decoded `officer:…` line, then `200`:

```
officer:officer123
200
```

✅ **Lesson 1 passes** when every step above matches.

---

## Lesson 2 — Users in the database

**You need:** The Lesson 2 code, and the app running. The first start creates `admin` from `ADMIN_PASSWORD`.

### Step 1 · Run this lesson's unit tests

They use the test database, not the running app.

```bash
mvn test -Dtest=UserApiTest
```

**You should see** `Tests run: 9, Failures: 0` and `BUILD SUCCESS`:

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** `There are test failures`: open `target/surefire-reports/` for the failing test. `No tests were executed`: this lesson's test class doesn't exist yet.

### Step 2 · The admin logs in from the database

The password is now checked against the `app_user` table.

```bash
curl -s -u admin:$ADMIN_PW $BASE/api/v1/users/me | jq -c '{username, roles, createdBy}'
```

**You should see** `admin`, all three roles, `createdBy: "system"`:

```
{"username":"admin","roles":["OFFICER","APPROVER","ADMIN"],"createdBy":"system"}
```

> **If not:** `401`: the admin was created with an older `ADMIN_PASSWORD`. It's only read while `app_user` is empty.

### Step 3 · The admin creates the officer

Accounts are created through the API, and the response never contains a password.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/users \
  -d "{\"username\":\"officer\",\"password\":\"$OFFICER_PW\",\"fullName\":\"Neema Said\",\"roles\":[\"OFFICER\"]}" | jq -c '{username, roles, createdBy, password}'
```

**You should see** `createdBy: "admin"`, and `password: null`: no password comes back:

```
{"username":"officer","roles":["OFFICER"],"createdBy":"admin","password":null}
```

> **If not:** `409`: the officer already exists. Carry on, but use the password it was created with.

### Step 4 · …and the approver

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/users \
  -d "{\"username\":\"approver\",\"password\":\"$APPROVER_PW\",\"fullName\":\"Juma Ali\",\"roles\":[\"APPROVER\"]}" | jq -c '{username, roles}'
```

**You should see** The approver account:

```
{"username":"approver","roles":["APPROVER"]}
```

### Step 5 · The officer asks who they are

`/me` works for any logged-in user.

```bash
curl -s -u officer:$OFFICER_PW $BASE/api/v1/users/me | jq -c '{username, fullName, roles}'
```

**You should see** The officer's account:

```
{"username":"officer","fullName":"Neema Said","roles":["OFFICER"]}
```

### Step 6 · The officer cannot manage users → 403

Only the admin may.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/users
```

**You should see** `403`:

```
403
```

> **If not:** `200`: the `/api/v1/users/**` rule is below the general GET rule.

### Step 7 · A new supplier records who created it

The client never sends `createdBy`; Spring fills it in from the login.

```bash
TIN=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
curl -s -u officer:$OFFICER_PW -X POST -H "$JSON" $BASE/api/v1/suppliers \
  -d "{\"name\":\"Test Supplier $TIN\",\"tin\":\"$TIN\",\"registrationNumber\":\"TEST-$TIN\",\"category\":\"GOODS\",\"email\":\"test@example.co.tz\"}" \
  | jq -c '{name, createdBy, updatedBy}'
```

**You should see** `createdBy: "officer"`:

```
{"name":"Test Supplier 080-510-239","createdBy":"officer","updatedBy":"officer"}
```

> **If not:** `createdBy` missing: `SupplierResponse` doesn't return it, or auditing has no `auditorAwareRef`.

### Step 8 · Passwords are stored as BCrypt hashes

Look in the table: no password anywhere.

```bash
psql -d $DB -c "SELECT u.username, left(u.password_hash, 22) || '…' AS password_hash, r.role
  FROM app_user u JOIN app_user_role r ON r.user_id = u.id ORDER BY 1, 3;"
```

**You should see** Every hash starts with `{bcrypt}$2a$10$`:

```
 username |      password_hash      |   role   
----------+-------------------------+----------
 admin    | {bcrypt}$2a$10$Z2tefM/… | ADMIN
 admin    | {bcrypt}$2a$10$Z2tefM/… | APPROVER
 admin    | {bcrypt}$2a$10$Z2tefM/… | OFFICER
 approver | {bcrypt}$2a$10$uaIoV5X… | APPROVER
 officer  | {bcrypt}$2a$10$lS1j9/5… | OFFICER
(5 rows)
```

### Step 9 · Disable the officer → 401

A disabled account is refused on its next request, and its records keep its name.

```bash
OFFICER_ID=$(curl -s -u admin:$ADMIN_PW $BASE/api/v1/users | jq -r '.[] | select(.username=="officer") | .id')
curl -s -u admin:$ADMIN_PW -X PATCH -H "$JSON" -d '{"enabled":false}' $BASE/api/v1/users/$OFFICER_ID/enabled | jq -c '{username, enabled}'
curl -s -o /dev/null -w 'officer now: %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/suppliers
```

**You should see** `enabled: false`, then `officer now: 401`:

```
{"username":"officer","enabled":false}
officer now: 401
```

### Step 10 · Enable the officer again

Leave the account working for the next lessons.

```bash
curl -s -u admin:$ADMIN_PW -X PATCH -H "$JSON" -d '{"enabled":true}' $BASE/api/v1/users/$OFFICER_ID/enabled | jq -c '{username, enabled}'
curl -s -o /dev/null -w 'officer now: %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/suppliers
```

**You should see** `enabled: true`, then `officer now: 200`:

```
{"username":"officer","enabled":true}
officer now: 200
```

✅ **Lesson 2 passes** when every step above matches.

---

## Lesson 3 — Tokens and JWT

**You need:** The Lesson 3 code, `JWT_SECRET` in `.env`, and the officer and approver from Lesson 2.

### Step 1 · Run this lesson's unit tests

They use the test database, not the running app.

```bash
mvn test -Dtest=JwtTest
```

**You should see** `Tests run: 9, Failures: 0` and `BUILD SUCCESS`:

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** `There are test failures`: open `target/surefire-reports/` for the failing test. `No tests were executed`: this lesson's test class doesn't exist yet.

### Step 2 · Log in and keep the token

The password is sent once; the token is used from then on.

```bash
login() { curl -s -X POST -H "$JSON" -d "{\"username\":\"$1\",\"password\":\"$2\"}" $BASE/api/v1/auth/login; }
LOGIN=$(login officer $OFFICER_PW)
echo "$LOGIN" | jq -c '{tokenType, expiresIn}'
TOKEN=$(echo "$LOGIN" | jq -r .accessToken)
```

**You should see** `tokenType: "Bearer"` and `expiresIn`: 1800 in Lesson 3, 300 from Lesson 3B:

```
{"tokenType":"Bearer","expiresIn":300}
```

> **If not:** `401 Login failed`: wrong password. `500`: see Lesson 3, common mistakes.

### Step 3 · Read the token: anyone can

A JWT is signed, not encrypted. Never put secrets in it.

```bash
echo "$TOKEN" | jq -R 'split(".") | .[1] | gsub("-";"+") | gsub("_";"/") | @base64d | fromjson | .exp |= todate | {sub, roles, iss, exp}' -c
```

**You should see** `sub: "officer"`, `roles: ["OFFICER"]`, and an expiry time:

```
{"sub":"officer","roles":["OFFICER"],"iss":"pis","exp":"2026-09-30T11:58:32Z"}
```

### Step 4 · The token opens the API

No password on this request.

```bash
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/v1/users/me | jq -c '{username, roles}'
```

**You should see** The officer:

```
{"username":"officer","roles":["OFFICER"]}
```

> **If not:** `401`: the token expired. Log in again.

### Step 5 · Roles inside the token are enforced → 403

The role comes from the token's `roles` claim.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" -X POST $BASE/api/v1/requisitions/$ID/approve
```

**You should see** `403`:

```
403
```

### Step 6 · A bad token → 401, with a Bearer challenge

The client learns its token is the problem.

```bash
curl -s -i -H "Authorization: Bearer not.a.token" $BASE/api/v1/suppliers | grep -E '^HTTP|^WWW-Authenticate'
```

**You should see** `401` and `WWW-Authenticate: Bearer error="invalid_token"`:

```
HTTP/1.1 401 
WWW-Authenticate: Bearer error="invalid_token"
```

### Step 7 · Forge a token: officer → ADMIN → 401

Change one word in the payload, and the signature no longer matches.

```bash
b64url_decode() { local s; s=$(printf '%s' "$1" | tr '_-' '/+'); while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done; printf '%s' "$s" | base64 -d; }
b64url_encode() { base64 | tr '/+' '_-' | tr -d '=\n'; }
PAYLOAD=$(b64url_decode "$(echo "$TOKEN" | cut -d. -f2)")
FORGED="$(echo "$TOKEN" | cut -d. -f1).$(printf '%s' "${PAYLOAD/OFFICER/ADMIN}" | b64url_encode).$(echo "$TOKEN" | cut -d. -f3)"
curl -s -o /dev/null -w 'real token:   %{http_code}\n' -H "Authorization: Bearer $TOKEN"  -X DELETE $BASE/api/v1/suppliers/$ID
curl -s -o /dev/null -w 'forged token: %{http_code}\n' -H "Authorization: Bearer $FORGED" -X DELETE $BASE/api/v1/suppliers/$ID
```

**You should see** `real token: 403` and `forged token: 401`:

```
real token:   403
forged token: 401
```

> **If not:** `404` for the forged token would mean the signature isn't checked. It never should be.

### Step 8 · The trade-off: a token outlives a disabled account

Nothing looks up the user when a token arrives.

```bash
ADMIN_TOKEN=$(login admin $ADMIN_PW | jq -r .accessToken)
OFFICER_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" $BASE/api/v1/users | jq -r '.[] | select(.username=="officer") | .id')
curl -s -o /dev/null -H "Authorization: Bearer $ADMIN_TOKEN" -X PATCH -H "$JSON" -d '{"enabled":false}' $BASE/api/v1/users/$OFFICER_ID/enabled
curl -s -o /dev/null -w 'new login: %{http_code}\n' -X POST -H "$JSON" -d "{\"username\":\"officer\",\"password\":\"$OFFICER_PW\"}" $BASE/api/v1/auth/login
curl -s -o /dev/null -w 'old token: %{http_code}\n' -H "Authorization: Bearer $TOKEN" $BASE/api/v1/suppliers
curl -s -o /dev/null -H "Authorization: Bearer $ADMIN_TOKEN" -X PATCH -H "$JSON" -d '{"enabled":true}' $BASE/api/v1/users/$OFFICER_ID/enabled
```

**You should see** `new login: 401` but `old token: 200`. The last line enables the officer again.:

```
new login: 401
old token: 200
```

✅ **Lesson 3 passes** when every step above matches.

---

## Lesson 3B — Refresh tokens

**You need:** The Lesson 3B code and the officer from Lesson 2.

### Step 1 · Run this lesson's unit tests

They use the test database, not the running app.

```bash
mvn test -Dtest=RefreshTokenTest
```

**You should see** `Tests run: 9, Failures: 0` and `BUILD SUCCESS`:

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** `There are test failures`: open `target/surefire-reports/` for the failing test. `No tests were executed`: this lesson's test class doesn't exist yet.

### Step 2 · Log in: two tokens

A short access token, and a long refresh token.

```bash
login() { curl -s -X POST -H "$JSON" -d "{\"username\":\"$1\",\"password\":\"$2\"}" $BASE/api/v1/auth/login; }
LOGIN=$(login officer $OFFICER_PW)
echo "$LOGIN" | jq -c '{expiresIn, refreshToken}'
R1=$(echo "$LOGIN" | jq -r .refreshToken)
```

**You should see** `expiresIn: 300` and a 43-character refresh token:

```
{"expiresIn":300,"refreshToken":"V0yA_zu-_o8xkRoDiyP5FWhhrImLR1Wq8RSPYapYpiI"}
```

> **If not:** `expiresIn: 1800`: `application.yml` still says `expiry: 30m`.

### Step 3 · Refresh: a new pair, and a different refresh token

Rotation: every refresh token works once.

```bash
PAIR=$(curl -s -X POST -H "$JSON" -d "{\"refreshToken\":\"$R1\"}" $BASE/api/v1/auth/refresh)
R2=$(echo "$PAIR" | jq -r .refreshToken)
echo "R1=$R1"; echo "R2=$R2"
curl -s -o /dev/null -w 'new access token: %{http_code}\n' -H "Authorization: Bearer $(echo "$PAIR" | jq -r .accessToken)" $BASE/api/v1/users/me
```

**You should see** Two different values, and `new access token: 200`:

```
R1=V0yA_zu-_o8xkRoDiyP5FWhhrImLR1Wq8RSPYapYpiI
R2=1SyQF-_FLT0SwqM7U39dGbbidZmWL9rsP8zTMaxHt5c
new access token: 200
```

> **If not:** `401`: the client also sent an old `Authorization: Bearer` header. Send none to `/auth/refresh`.

### Step 4 · Replay the old one: the whole family is revoked

Two holders of R1 means one is a thief, so both are logged out.

```bash
curl -s -X POST -H "$JSON" -d "{\"refreshToken\":\"$R1\"}" $BASE/api/v1/auth/refresh | jq -c '{status, title}'
curl -s -o /dev/null -w 'R2 now: %{http_code}\n' -X POST -H "$JSON" -d "{\"refreshToken\":\"$R2\"}" $BASE/api/v1/auth/refresh
```

**You should see** `401 Invalid refresh token`, then `R2 now: 401`:

```
{"status":401,"title":"Invalid refresh token"}
R2 now: 401
```

> **If not:** `R2 now: 200`: `noRollbackFor` is missing on `refresh`. The unit tests can't catch this one.

### Step 5 · Log out

Logout revokes the refresh token and its family.

```bash
R3=$(login officer $OFFICER_PW | jq -r .refreshToken)
curl -s -o /dev/null -w 'logout:  %{http_code}\n' -X POST -H "$JSON" -d "{\"refreshToken\":\"$R3\"}" $BASE/api/v1/auth/logout
curl -s -o /dev/null -w 'refresh: %{http_code}\n' -X POST -H "$JSON" -d "{\"refreshToken\":\"$R3\"}" $BASE/api/v1/auth/refresh
```

**You should see** `logout: 204` and `refresh: 401`:

```
logout:  204
refresh: 401
```

### Step 6 · Look at the table: hashes only

A leaked backup gives out no working tokens.

```bash
psql -d $DB -c "SELECT left(token_hash, 12) || '…' AS token_hash, used_at IS NOT NULL AS used,
  revoked_at IS NOT NULL AS revoked FROM refresh_token ORDER BY created_at DESC LIMIT 4;"
```

**You should see** 64-character hex hashes, with the `used` and `revoked` flags from the steps above:

```
  token_hash   | used | revoked 
---------------+------+---------
 b7741ff14ff0… | f    | t
 ee2de70e222e… | f    | t
 778304c6d078… | t    | t
 25ae91800dee… | f    | f
(4 rows)
```

✅ **Lesson 3B passes** when every step above matches.

---

## Lesson 3C — Permissions and method security

**You need:** The Lesson 3C code, and the officer and approver from Lesson 2.

### Step 1 · Run this lesson's unit tests

They use the test database, not the running app.

```bash
mvn test -Dtest=PermissionTest
```

**You should see** `Tests run: 8, Failures: 0` and `BUILD SUCCESS`:

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** `There are test failures`: open `target/surefire-reports/` for the failing test. `No tests were executed`: this lesson's test class doesn't exist yet.

### Step 2 · Three tokens

```bash
login() { curl -s -X POST -H "$JSON" -d "{\"username\":\"$1\",\"password\":\"$2\"}" $BASE/api/v1/auth/login; }
ADMIN=$(login admin $ADMIN_PW | jq -r .accessToken)
OFFICER=$(login officer $OFFICER_PW | jq -r .accessToken)
APPROVER=$(login approver $APPROVER_PW | jq -r .accessToken)
REQ='{"department":"ICT","requestedBy":"Neema Said","justification":"Laptops","requiredByDate":"2030-12-31","items":[{"description":"Laptop","quantity":1,"unit":"PIECE","estimatedUnitPrice":2500000.00}]}'
echo "${#ADMIN} ${#OFFICER} ${#APPROVER}"
```

**You should see** Three lengths, none of them 4 (4 means `null`: a login failed):

```
512 395 351
```

### Step 3 · What may the approver do?

Roles are now bundles of permissions.

```bash
curl -s -H "Authorization: Bearer $APPROVER" $BASE/api/v1/users/me | jq -c '{roles, permissions}'
```

**You should see** `requisition:approve`, `supplier:approve` and the `…:read` permissions; nothing ending `:write`:

```
{"roles":["APPROVER"],"permissions":["invoice:read","purchase-order:read","requisition:approve","requisition:read","supplier:approve","supplier:read"]}
```

> **If not:** No `permissions` field: `UserResponse` wasn't updated.

### Step 4 · The token carries the permissions too

So token requests are judged the same way as Basic ones.

```bash
echo "$APPROVER" | jq -R 'split(".") | .[1] | gsub("-";"+") | gsub("_";"/") | @base64d | fromjson | .permissions' -c
```

**You should see** The same list as `/me`:

```
["invoice:read","purchase-order:read","requisition:approve","requisition:read","supplier:approve","supplier:read"]
```

### Step 5 · The approver cannot raise a requisition → 403

No `requisition:write`.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H "$JSON" -H "Authorization: Bearer $APPROVER" -d "$REQ" $BASE/api/v1/requisitions
```

**You should see** `403`:

```
403
```

### Step 6 · The officer raises and submits one

The response now shows who raised it.

```bash
R_OFF=$(curl -s -X POST -H "$JSON" -H "Authorization: Bearer $OFFICER" -d "$REQ" $BASE/api/v1/requisitions | jq -r .id)
curl -s -X POST -H "Authorization: Bearer $OFFICER" $BASE/api/v1/requisitions/$R_OFF/submit | jq -c '{status, createdBy}'
```

**You should see** `SUBMITTED`, `createdBy: "officer"`:

```
{"status":"SUBMITTED","createdBy":"officer"}
```

### Step 7 · The officer cannot approve it; the approver can

The URL rule checks `requisition:approve`.

```bash
curl -s -o /dev/null -w 'officer:  %{http_code}\n' -X POST -H "Authorization: Bearer $OFFICER" $BASE/api/v1/requisitions/$R_OFF/approve
curl -s -X POST -H "Authorization: Bearer $APPROVER" $BASE/api/v1/requisitions/$R_OFF/approve | jq -c '{status}'
```

**You should see** `officer: 403`, then `APPROVED`:

```
officer:  403
{"status":"APPROVED"}
```

### Step 8 · Nobody approves their own, not even the admin

The method rule reads `created_by`. The admin holds every permission, and is still refused.

```bash
R_ADM=$(curl -s -X POST -H "$JSON" -H "Authorization: Bearer $ADMIN" -d "$REQ" $BASE/api/v1/requisitions | jq -r .id)
curl -s -o /dev/null -X POST -H "Authorization: Bearer $ADMIN" $BASE/api/v1/requisitions/$R_ADM/submit
curl -s -X POST -H "Authorization: Bearer $ADMIN" $BASE/api/v1/requisitions/$R_ADM/approve | jq -c '{status, title, detail}'
```

**You should see** `403`, "Access denied":

```
{"status":403,"title":"Access denied","detail":"You may not perform this operation on this record"}
```

> **If not:** `500`: no `AccessDeniedException` handler. `200`: `@EnableMethodSecurity` is missing.

### Step 9 · Someone else may approve the admin's

Separation of duties, not a ban.

```bash
curl -s -X POST -H "Authorization: Bearer $APPROVER" $BASE/api/v1/requisitions/$R_ADM/approve | jq -c '{status, createdBy}'
```

**You should see** `APPROVED`, `createdBy: "admin"`:

```
{"status":"APPROVED","createdBy":"admin"}
```

✅ **Lesson 3C passes** when every step above matches.

---

## Lecture 4 — OAuth2 with Spring Authorization Server

**You need:** Both apps of `pis-lecture4-authorization-server/` running: `auth-server` first, then `pis-api`. Its logins are `officer` / `officer123` and friends, and the client
secrets are in `auth-server/.env`.

Set these variables too:

```bash
AUTH=http://localhost:9100          # auth-server
API=http://localhost:8181           # pis-api
ID=99999999-9999-9999-9999-999999999999
```

### Step 1 · Run the unit tests of both apps

They need no running apps: each server is tested on its own.

```bash
cd pis-lecture4-authorization-server/auth-server && mvn test
cd ../pis-api && mvn test
```

**You should see** 8 tests in `auth-server` and 15 in `pis-api`, `BUILD SUCCESS` for both:

```
auth-server:
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

pis-api:
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** Only the steps below prove the two apps work **together**.

### Step 2 · The discovery document

Where a client finds every other endpoint. `pis-api` reads it at startup.

```bash
curl -s $AUTH/.well-known/openid-configuration | jq -c '{issuer, token_endpoint, jwks_uri}'
```

**You should see** `issuer: "http://localhost:9100"` and the endpoints:

```
{"issuer":"http://localhost:9100","token_endpoint":"http://localhost:9100/oauth2/token","jwks_uri":"http://localhost:9100/oauth2/jwks"}
```

> **If not:** Connection refused: start `auth-server`.

### Step 3 · The public key, and never the private one

RS256: anyone may check a signature; only the auth server can make one.

```bash
curl -s $AUTH/oauth2/jwks | jq -c '.keys[0] | {kty, kid, has_private_part: has("d")}'
```

**You should see** `kty: "RSA"`, a `kid`, `has_private_part: false`:

```
{"kty":"RSA","kid":"f6e3b14f-be86-4e4c-8402-e7c46a8673a2","has_private_part":false}
```

### Step 4 · Client credentials: the reporting job

A machine, no person: a scope and no roles.

```bash
RT=$(curl -s -u pis-reporting:reporting-secret-123 -d grant_type=client_credentials -d scope=suppliers.read $AUTH/oauth2/token | jq -r .access_token)
echo "$RT" | jq -R 'split(".") | .[1] | gsub("-";"+") | gsub("_";"/") | @base64d | fromjson | {sub, scope, roles}' -c
```

**You should see** `sub: "pis-reporting"`, `scope: ["suppliers.read"]`, `roles: null`:

```
{"sub":"pis-reporting","scope":["suppliers.read"],"roles":null}
```

> **If not:** `401 invalid_client`: the secret doesn't match `PIS_REPORTING_SECRET`.

### Step 5 · The job may read suppliers, and nothing else

Scopes, checked by the resource server.

```bash
curl -s -o /dev/null -w 'suppliers:    %{http_code}\n' -H "Authorization: Bearer $RT" $API/api/v1/suppliers
curl -s -o /dev/null -w 'requisitions: %{http_code}\n' -H "Authorization: Bearer $RT" $API/api/v1/requisitions
```

**You should see** `suppliers: 200`, `requisitions: 403`:

```
suppliers:    200
requisitions: 403
```

> **If not:** Every call 401: `pis-api`'s `issuer-uri` doesn't match exactly, e.g. `127.0.0.1` vs `localhost`.

### Step 6 · Log in as a person, in a browser (PKCE)

Make the PKCE pair, open the printed URL, log in as `officer` / `officer123`. The browser then fails to load `127.0.0.1:3000/callback?code=…`; nothing runs on port 3000, and that's fine: **copy the `code` value from the address bar.**

```bash
VERIFIER=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | base64 | tr '/+' '_-' | tr -d '=\n')
echo "$AUTH/oauth2/authorize?response_type=code&client_id=pis-web&redirect_uri=http://127.0.0.1:3000/callback&scope=openid%20profile%20pis&state=xyz&code_challenge=$CHALLENGE&code_challenge_method=S256"
```

**You should see** A long URL to open in your browser:

```
http://localhost:9100/oauth2/authorize?response_type=code&client_id=pis-web&redirect_uri=http://127.0.0.1:3000/callback&scope=openid%20profile%20pis&state=xyz&code_challenge=E2Klxt7wakdB-iyG2jlzfJLgmXXL9j251L62nh5Gryo&code_challenge_method=S256
```

### Step 7 · Exchange the code for tokens

Within 5 minutes of step 5. The `code_verifier` proves you started the login.

```bash
CODE=paste-the-code-here
TOKENS=$(curl -s -d grant_type=authorization_code -d "code=$CODE" -d redirect_uri=http://127.0.0.1:3000/callback \
  -d client_id=pis-web -d "code_verifier=$VERIFIER" $AUTH/oauth2/token)
echo "$TOKENS" | jq -c '{token_type, expires_in, scope, has_id_token: has("id_token"), has_refresh_token: has("refresh_token")}'
AT=$(echo "$TOKENS" | jq -r .access_token)
```

**You should see** An access token and an ID token, and no refresh token: `pis-web` is a public client:

```
{"token_type":"Bearer","expires_in":299,"scope":"openid profile pis","has_id_token":true,"has_refresh_token":false}
```

> **If not:** `invalid_grant`: the code was already used, is older than 5 minutes, or `VERIFIER` is from a different terminal.

### Step 8 · PIS accepts the auth server's token

PIS never saw the password.

```bash
curl -s -H "Authorization: Bearer $AT" $API/api/v1/me | jq -c '{subject, client, authorities}'
curl -s -o /dev/null -w 'approve: %{http_code}\n' -H "Authorization: Bearer $AT" -X POST $API/api/v1/requisitions/$ID/approve
```

**You should see** `subject: "officer"`, `ROLE_OFFICER` among the authorities, then `approve: 403`:

```
{"subject":"officer","client":["pis-web"],"authorities":["ROLE_OFFICER","SCOPE_openid","SCOPE_pis","SCOPE_profile"]}
approve: 403
```

### Step 9 · Basic auth is gone

The only way into `pis-api` is a token.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:officer123 $API/api/v1/suppliers
```

**You should see** `401`:

```
401
```

✅ **Lecture 4 passes** when every step above matches.

---

## When a step doesn't match

1. **Read the status code first.** 401 means *who are you?* failed (no, wrong or
   expired credentials). 403 means *what may you do?* failed (logged in, but not
   allowed). 404 on an approve or delete with the fake `$ID` is a **success**: it
   means security let the request through.
2. **Check the variables:** `echo "$BASE $OFFICER_PW"`. A new terminal has none of them.
3. **Tokens expire.** From Lesson 3B an access token lasts 5 minutes. If a
   token step suddenly returns 401, log in again (run that lesson's login step).
4. **Read the app's log** in the terminal where `mvn spring-boot:run` is running.
5. **Compare with the demo.** `pis-security-demo/` (port 8099) has every lesson
   finished. Run the same step against it with `BASE=http://localhost:8099`: if it
   passes there, the difference is in your code.
