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
| [3D · Roles and permissions in the database](#lesson-3d--roles-and-permissions-in-the-database) | `RoleApiTest` (10 tests) | yes | 9 |
| [3E · The security filter chain](#lesson-3e--the-security-filter-chain) | `FilterChainTest`, `LoginLimitTest`, `LoginRateLimitFilterTest` (13 tests) | yes | 7 |
| [3F · URL rules in the database](#lesson-3f--url-rules-in-the-database) | `UrlRuleTest` (11 tests) | yes | 9 |
| [4 · OAuth2 with Spring Authorization Server](#lecture-4--oauth2-with-spring-authorization-server) | both apps (8 + 15 tests) | both apps | 9 |
| [5 · API gateway and service discovery](#lecture-5--api-gateway-and-service-discovery) | four apps (24 tests) | all five apps | 10 |

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
  "totalElements": 17,
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
{"username":"admin","roles":["ADMIN","APPROVER","OFFICER"],"createdBy":"system"}
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
{"name":"Test Supplier 071-825-056","createdBy":"officer","updatedBy":"officer"}
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
 approver | {bcrypt}$2a$10$zMRa3nf… | APPROVER
 officer  | {bcrypt}$2a$10$btafbf0… | OFFICER
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
{"sub":"officer","roles":["OFFICER"],"iss":"pis","exp":"2026-09-30T12:33:48Z"}
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
{"expiresIn":300,"refreshToken":"8L1ykaoWEPtnz6YKbZbu4jXvMfvfcCSOGXbF-6zjSuc"}
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
R1=8L1ykaoWEPtnz6YKbZbu4jXvMfvfcCSOGXbF-6zjSuc
R2=QBOpn48Yd4CbKyzEgZRNR6KtM9j8bn02hlzWwHVySgc
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
 7ed2feb4f883… | f    | t
 4c15fdbebf88… | f    | t
 2bfe55eddec0… | t    | t
 3224c342c334… | f    | f
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
531 395 351
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

## Lesson 3D — Roles and permissions in the database

**You need:** The Lesson 3D code (migration V7 runs on start), and the officer from Lesson 2.

### Step 1 · Run this lesson's unit tests

They use the test database, not the running app.

```bash
mvn test -Dtest=RoleApiTest
```

**You should see** `Tests run: 10, Failures: 0` and `BUILD SUCCESS`:

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** `There are test failures`: open `target/surefire-reports/` for the failing test. `No tests were executed`: this lesson's test class doesn't exist yet.

### Step 2 · The roles are now rows in the database

The three roles from Lesson 3C, seeded by migration V7 with the same permissions.

```bash
psql -d $DB -c "SELECT r.name, r.built_in, count(rp.permission) AS permissions
  FROM role r LEFT JOIN role_permission rp ON rp.role = r.name GROUP BY r.name, r.built_in ORDER BY r.name;"
```

**You should see** ADMIN with 13 permissions, APPROVER with 6, OFFICER with 8, all built in:

```
   name   | built_in | permissions 
----------+----------+-------------
 ADMIN    | t        |          13
 APPROVER | t        |           6
 OFFICER  | t        |           8
(3 rows)
```

> **If not:** `relation "role" does not exist`: the app hasn't started since you added V7.

### Step 3 · Every permission the code knows

Permissions stay in code: this list comes from the `Permission` enum.

```bash
curl -s -u admin:$ADMIN_PW $BASE/api/v1/permissions | jq -c
```

**You should see** 13 permissions, including the new `role:manage`:

```
["supplier:read","supplier:write","supplier:approve","requisition:read","requisition:write","requisition:approve","purchase-order:read","purchase-order:write","invoice:read","invoice:write","record:delete","user:manage","role:manage"]
```

> **If not:** `403`: the admin role lacks `role:manage`. Check that migration V7 ran.

### Step 4 · Create a role while PIS is running

No code change, no restart.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/roles \
  -d '{"name":"AUDITOR","description":"Internal audit: reads everything",
       "permissions":["supplier:read","requisition:read","purchase-order:read","invoice:read"]}' \
  | jq -c '{name, builtIn, permissions, users}'
```

**You should see** `201`: `builtIn: false`, four read permissions, `users: 0`:

```
{"name":"AUDITOR","builtIn":false,"permissions":["invoice:read","purchase-order:read","requisition:read","supplier:read"],"users":0}
```

> **If not:** `409`: AUDITOR already exists. Carry on.

### Step 5 · Give someone the role

The new account gets exactly the role's permissions.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/users \
  -d '{"username":"auditor","password":"auditor123","fullName":"Zuhura Audit","roles":["AUDITOR"]}' \
  | jq -c '{username, roles, permissions}'
```

**You should see** The auditor with `roles: ["AUDITOR"]` and the four permissions:

```
{"username":"auditor","roles":["AUDITOR"],"permissions":["invoice:read","purchase-order:read","requisition:read","supplier:read"]}
```

> **If not:** `422 Unknown role: AUDITOR`: step 3 didn't work.

### Step 6 · The auditor can read, and cannot write

```bash
TIN=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
SUPPLIER="{\"name\":\"Audit Test $TIN\",\"tin\":\"$TIN\",\"registrationNumber\":\"AUDIT-$TIN\",\"category\":\"GOODS\",\"email\":\"audit@example.co.tz\"}"
curl -s -o /dev/null -w 'read:  %{http_code}\n' -u auditor:auditor123 $BASE/api/v1/suppliers
curl -s -o /dev/null -w 'write: %{http_code}\n' -u auditor:auditor123 -X POST -H "$JSON" -d "$SUPPLIER" $BASE/api/v1/suppliers
```

**You should see** `read: 200`, `write: 403`:

```
read:  200
write: 403
```

### Step 7 · Change the role while the auditor is logged in

Grant `supplier:write`. A Basic login sees it on its next request. A token issued before the change doesn't, until it's refreshed.

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

**You should see** The role with `supplier:write` and `users: 1`, then `Basic login: 201`, `old token: 403`, `after refresh: 201`:

```
{"name":"AUDITOR","permissions":["invoice:read","purchase-order:read","requisition:read","supplier:read","supplier:write"],"users":1}
Basic login:     201
old token:       403
after refresh:   201
```

> **If not:** `old token: 201`: the permissions aren't in the token. Check `TokenService`.

### Step 8 · The guard rails

Three changes PIS refuses, each with a clear error.

```bash
curl -s -u admin:$ADMIN_PW -X PUT -H "$JSON" -d '{"permissions":["supplier:read"]}' $BASE/api/v1/roles/ADMIN/permissions | jq -c '{status, detail}'
curl -s -u admin:$ADMIN_PW -X DELETE $BASE/api/v1/roles/AUDITOR | jq -c '{status, detail}'
curl -s -u admin:$ADMIN_PW -X PUT -H "$JSON" -d '{"permissions":["supplier:fly"]}' $BASE/api/v1/roles/AUDITOR/permissions | jq -c '{status, title}'
```

**You should see** `422` for ADMIN, `422` for a role in use, `400` for an unknown permission:

```
{"status":422,"detail":"The ADMIN role always has every permission and can't be changed"}
{"status":422,"detail":"Role AUDITOR is held by 1 user(s). Take it away from them first."}
{"status":400,"title":"Malformed request"}
```

> **If not:** A `500` for the last one: the `HttpMessageNotReadableException` handler is missing.

### Step 9 · Put AUDITOR back to read-only

Leave the role as the lesson found it.

```bash
curl -s -u admin:$ADMIN_PW -X PUT -H "$JSON" $BASE/api/v1/roles/AUDITOR/permissions \
  -d '{"permissions":["supplier:read","requisition:read","purchase-order:read","invoice:read"]}' | jq -c '{name, permissions}'
```

**You should see** The four read permissions again:

```
{"name":"AUDITOR","permissions":["invoice:read","purchase-order:read","requisition:read","supplier:read"]}
```

✅ **Lesson 3D passes** when every step above matches.

---

## Lesson 3E — The security filter chain

**You need:** The Lesson 3E code, and the officer and approver from Lesson 2. Steps 4–7 count failed logins and lock your machine out for a minute: do them last, and at least a minute after any other lesson's steps, because their wrong passwords count too.

### Step 1 · Run this lesson's unit tests

They use the test database, not the running app.

```bash
mvn test -Dtest='FilterChainTest,LoginLimitTest,LoginRateLimitFilterTest' | grep -E 'Tests run:|BUILD'
```

**You should see** 13 tests, no failures, and `BUILD SUCCESS`:

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.168 s -- in tz.co.hmy.pis.security.LoginRateLimitFilterTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.129 s -- in tz.co.hmy.pis.FilterChainTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.078 s -- in tz.co.hmy.pis.LoginLimitTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** `No tests were executed`: this lesson's test classes don't exist yet. A failure in `the_chain_is_a_list_of_filters…`: a filter is in the wrong place (`addFilterBefore` / `addFilterAfter`).

### Step 2 · Watch a request go through the chain

Stop the app (Ctrl+C) and start it with the filter chain's trace switched on:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--logging.level.org.springframework.security.web.FilterChainProxy=TRACE
```

Then send one request:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/suppliers
```

**You should see** `200` here, and in the **app's terminal** the 16 filters it went through, in order. Ours are 6 and 9:

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

> **If not:** Fewer lines: the trace switch has a typo. `LoginRateLimitFilter` or `RequestLogFilter` missing: they aren't added in `SecurityConfig`.

### Step 3 · The request log names the user

Four requests: allowed, refused by a rule, no credentials, and a JWT.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/suppliers
curl -s -o /dev/null -w '%{http_code}\n' -u officer:$OFFICER_PW -X PATCH -H "$JSON" -d '{"status":"ACTIVE"}' $BASE/api/v1/suppliers/$ID/status
curl -s -o /dev/null -w '%{http_code}\n' $BASE/api/v1/requisitions
TOKEN=$(curl -s -X POST -H "$JSON" -d "{\"username\":\"approver\",\"password\":\"$APPROVER_PW\"}" $BASE/api/v1/auth/login | jq -r .accessToken)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" $BASE/api/v1/invoices
```

**You should see** `200`, `403`, `401`, `200`, and in the **app's terminal** one `RequestLogFilter` line for each request, with its status and who sent it. The login itself is logged too, as `anonymous`: nobody is logged in *while* logging in:

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

> **If not:** No `RequestLogFilter` lines: it isn't added in `SecurityConfig`. The same line twice per request: it's a `@Component` **and** a plain `Filter` (Common mistakes).

### Step 4 · A wrong password never reaches the request log

`BasicAuthenticationFilter` (8) answers 401 itself and stops the chain. Our rate-limit filter (6) is outside it, so it sees the 401 on the way back.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u officer:wrong $BASE/api/v1/suppliers
```

**You should see** `401`, and in the **app's terminal** only `Failed login 1 of 5`. No `RequestLogFilter` line. (`0:0:0:0:0:0:0:1` is `localhost` in IPv6):

```
401
```

In the app's terminal:

```
LoginRateLimitFilter: Failed login 1 of 5 from 0:0:0:0:0:0:0:1
```

> **If not:** A `RequestLogFilter` line for this request: the request log was added before `BasicAuthenticationFilter`.

### Step 5 · Guessing passwords gets you blocked

Four more wrong guesses make five failures. After that, every login attempt from this address is refused, even with the right password, and even at the login endpoint.

```bash
for i in 1 2 3 4; do curl -s -o /dev/null -w "guess $i: %{http_code}\n" -u officer:guess$i $BASE/api/v1/suppliers; done
curl -s -D - -u officer:$OFFICER_PW $BASE/api/v1/suppliers | grep -iE '^(HTTP|retry-after)|title'
curl -s -X POST -H "$JSON" -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PW\"}" $BASE/api/v1/auth/login | jq -c '{status, title}'
```

**You should see** Four `401`s, then `429` with a `Retry-After` header, for the right password and for the login endpoint. The app's terminal counts up to `Failed login 5 of 5`:

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

> **If not:** `401` instead of `429`: the limit is higher (check `LOGIN_MAX_FAILURES`), or more than a minute passed since the first wrong password.

### Step 6 · Requests that aren't logins still work

Only login attempts are blocked. A request with no credentials isn't a guess.

```bash
curl -s -o /dev/null -w 'no credentials: %{http_code}\n' $BASE/api/v1/suppliers
curl -s -o /dev/null -w 'health:         %{http_code}\n' $BASE/actuator/health
```

**You should see** `401` (not `429`) and `200`:

```
no credentials: 401
health:         200
```

### Step 7 · A minute later, the block lifts

The window is one minute from the **first** failure: the single wrong password before the guessing.

```bash
sleep 60
curl -s -o /dev/null -w 'right password: %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/suppliers
```

**You should see** `200`:

```
right password: 200
```

> **If not:** `429`: wait the number of seconds in `Retry-After`, then try again.

✅ **Lesson 3E passes** when every step above matches.

---

## Lesson 3F — URL rules in the database

**You need:** The Lesson 3F code (migration V8 runs on start), and the officer and approver from Lesson 2. Steps 5–8 change the rules: do them in order, and don't skip step 8, which puts rule 30 back.

### Step 1 · Run this lesson's unit tests

They use the test database, not the running app.

```bash
mvn test -Dtest=UrlRuleTest | grep -E 'Tests run:|BUILD'
```

**You should see** 11 tests, no failures, and `BUILD SUCCESS`:

```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.804 s -- in tz.co.hmy.pis.UrlRuleTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **If not:** `No tests were executed`: `UrlRuleTest` doesn't exist yet. Run the **whole** suite too (`mvn test`): every other test class checks the seeded rules.

### Step 2 · The rules are rows

Migration V8 moved the rules out of `SecurityConfig`, in the same order.

```bash
psql -d $DB -c "SELECT priority, coalesce(http_method, 'ANY') AS method, pattern, permission
  FROM url_rule ORDER BY priority;"
```

**You should see** The 12 rules, from `10 POST /api/v1/requisitions/*/approve` to `120 ANY /api/v1/invoices/**`:

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

> **If not:** `relation "url_rule" does not exist`: the app hasn't started since you added V8.

### Step 3 · The same, through the API

Permissions are shown as authorities, like everywhere else in the API.

```bash
curl -s -u admin:$ADMIN_PW $BASE/api/v1/url-rules | jq -r '.[] | "\(.priority) \(.method) \(.pattern) -> \(.permission)"'
```

**You should see** The same 12 rules:

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

> **If not:** `403`: the admin lacks `role:manage`, or `/api/v1/url-rules/**` is missing from the `role:manage` line in `SecurityConfig`.

### Step 4 · Which rule decides?

Ask for a request without sending it. The first matching rule decides; a request that matches none is refused, even for the admin, who has every permission.

```bash
curl -s -u admin:$ADMIN_PW "$BASE/api/v1/url-rules/match?method=PATCH&path=/api/v1/suppliers/$ID/status" | jq -c '{decision}'
curl -s -u admin:$ADMIN_PW "$BASE/api/v1/url-rules/match?method=GET&path=/api/v1/reports" | jq -c '{decision}'
curl -s -o /dev/null -w 'admin, GET /api/v1/reports: %{http_code}\n' -u admin:$ADMIN_PW $BASE/api/v1/reports
```

**You should see** Rule 30 decides the status change; nothing matches `/api/v1/reports`, so even the admin gets `403`:

```
{"decision":"needs supplier:approve (rule 30)"}
{"decision":"no rule matches: denied"}
admin, GET /api/v1/reports: 403
```

### Step 5 · Change what an endpoint needs, while PIS is running

A new rule at priority 65 sits before rule 70 (any reader may read requisitions), so it wins: only approvers may read them now.

```bash
curl -s -o /dev/null -w 'officer, before: %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/requisitions
RULE=$(curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":65,"method":"GET","pattern":"/api/v1/requisitions/**","permission":"requisition:approve","description":"Only approvers may read requisitions"}')
echo "$RULE" | jq -c '{id, priority, method, pattern, permission, updatedBy}'; RULE_ID=$(echo "$RULE" | jq .id)
curl -s -o /dev/null -w 'officer, after:  %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/requisitions
curl -s -o /dev/null -w 'approver, after: %{http_code}\n' -u approver:$APPROVER_PW $BASE/api/v1/requisitions
```

**You should see** `200` for the officer, the new rule (recorded as changed by `admin`), then `403` for the officer and `200` for the approver. No restart:

```
officer, before: 200
{"id":13,"priority":65,"method":"GET","pattern":"/api/v1/requisitions/**","permission":"requisition:approve","updatedBy":"admin"}
officer, after:  403
approver, after: 200
```

> **If not:** `409`: a rule already uses priority 65 (an earlier run). Delete it, or use another priority between 60 and 70.

### Step 6 · Take it away again

The officer can read requisitions on the very next request.

```bash
curl -s -o /dev/null -w 'delete the rule: %{http_code}\n' -u admin:$ADMIN_PW -X DELETE $BASE/api/v1/url-rules/$RULE_ID
curl -s -o /dev/null -w 'officer:         %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/requisitions
```

**You should see** `204`, then `200`:

```
delete the rule: 204
officer:         200
```

### Step 7 · Order matters: deleting a rule can OPEN an endpoint

Rule 30 says a status change needs `supplier:approve`. Delete it, and rule 60 (any method on suppliers: `supplier:write`) decides instead. Officers have `supplier:write`. `$ID` is a supplier that doesn't exist, so a `404` proves the request got **past** security without changing anything.

```bash
curl -s -o /dev/null -w 'officer changes a status, before: %{http_code}\n' -u officer:$OFFICER_PW -X PATCH -H "$JSON" -d '{"status":"ACTIVE"}' $BASE/api/v1/suppliers/$ID/status
R30=$(curl -s -u admin:$ADMIN_PW $BASE/api/v1/url-rules | jq '.[] | select(.priority == 30) | .id')
curl -s -o /dev/null -w 'delete rule 30: %{http_code}\n' -u admin:$ADMIN_PW -X DELETE $BASE/api/v1/url-rules/$R30
curl -s -u admin:$ADMIN_PW "$BASE/api/v1/url-rules/match?method=PATCH&path=/api/v1/suppliers/$ID/status" | jq -c '{decision}'
curl -s -o /dev/null -w 'officer changes a status, after:  %{http_code}\n' -u officer:$OFFICER_PW -X PATCH -H "$JSON" -d '{"status":"ACTIVE"}' $BASE/api/v1/suppliers/$ID/status
```

**You should see** `403`, then rule 60 decides and the officer gets `404`: past security:

```
officer changes a status, before: 403
delete rule 30: 204
{"decision":"needs supplier:write (rule 60)"}
officer changes a status, after:  404
```

### Step 8 · Put rule 30 back

Same priority, same pattern, same permission.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":30,"method":"PATCH","pattern":"/api/v1/suppliers/*/status","permission":"supplier:approve","description":"Activating or suspending a supplier"}' | jq -c '{priority, pattern, permission}'
curl -s -o /dev/null -w 'officer changes a status: %{http_code}\n' -u officer:$OFFICER_PW -X PATCH -H "$JSON" -d '{"status":"ACTIVE"}' $BASE/api/v1/suppliers/$ID/status
```

**You should see** Rule 30 again, and `403` for the officer:

```
{"priority":30,"pattern":"/api/v1/suppliers/*/status","permission":"supplier:approve"}
officer changes a status: 403
```

> **If not:** Don't skip this step: without rule 30, every officer can activate suppliers.

### Step 9 · The guard rails

Four changes PIS refuses.

```bash
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":5,"pattern":"/api/v1/roles/**","permission":"supplier:read","description":"x"}' | jq -c '{status, detail}'
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":5,"pattern":"/api/v1/**/approve","permission":"supplier:read","description":"x"}' | jq -c '{status, detail}'
curl -s -u admin:$ADMIN_PW -X POST -H "$JSON" $BASE/api/v1/url-rules \
  -d '{"priority":50,"pattern":"/api/v1/reports/**","permission":"supplier:read","description":"x"}' | jq -c '{status, detail}'
curl -s -o /dev/null -w 'officer reads the rules: %{http_code}\n' -u officer:$OFFICER_PW $BASE/api/v1/url-rules
```

**You should see** An admin endpoint (`422`), a pattern Spring can't read (`422`), a priority that's taken (`409`), and an officer (`403`):

```
{"status":422,"detail":"/api/v1/roles/** is an admin endpoint. Those are protected in code, so a rule can never lock the admins out"}
{"status":422,"detail":"Pattern /api/v1/**/approve is not valid: No more pattern data allowed after {*...} or ** pattern element"}
{"status":409,"detail":"Priority 50 is already used by rule 5 (/api/v1/suppliers/**)"}
officer reads the rules: 403
```

✅ **Lesson 3F passes** when every step above matches.

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
http://localhost:9100/oauth2/authorize?response_type=code&client_id=pis-web&redirect_uri=http://127.0.0.1:3000/callback&scope=openid%20profile%20pis&state=xyz&code_challenge=fcYDCgNXKnwhJ6BubIZqS-0JwfjHfnHcum235y9Y8Y0&code_challenge_method=S256
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

## Lecture 5 — API gateway and service discovery

**You need:** All five apps of `pis-lecture5-gateway-eureka/` running: discovery-server first, then supplier-service on 8201 **and** 8211, purchase-order-service, and api-gateway (see its README). No database.

Set these variables too:

```bash
EUREKA=http://localhost:8761      # discovery-server
GATEWAY=http://localhost:8300     # api-gateway: the only address a client needs
JSON='Content-Type: application/json'
```

### Step 1 · Run the unit tests of all four apps

They need nothing running: each app is tested on its own, with a stub standing in for the others.

```bash
cd pis-lecture5-gateway-eureka
for app in discovery-server supplier-service purchase-order-service api-gateway; do
  (cd $app && mvn -q test > /dev/null && echo "$app: all tests pass" || echo "$app: TESTS FAILED")
done
```

**You should see** All four say `all tests pass` (2 + 6 + 9 + 7 = 24 tests):

```
discovery-server: all tests pass
supplier-service: all tests pass
purchase-order-service: all tests pass
api-gateway: all tests pass
```

> **If not:** Run each app's `mvn test` on its own to see which test failed.

### Step 2 · Who is registered in Eureka?

Every app registers itself on start-up. Two copies of supplier-service are running.

```bash
curl -s -H 'Accept: application/json' $EUREKA/eureka/apps \
  | jq -r '.applications.application[] | "\(.name): \([.instance[].instanceId] | sort | join(", "))"'
```

**You should see** SUPPLIER-SERVICE with two instances (8201 and 8211), PURCHASE-ORDER-SERVICE and API-GATEWAY:

```
SUPPLIER-SERVICE: supplier-service:8201, supplier-service:8211
API-GATEWAY: api-gateway:8300
PURCHASE-ORDER-SERVICE: purchase-order-service:8202
```

> **If not:** A service is missing: it hasn't started yet, or it wasn't started at all. Its own log says "registration status: 204" once it has registered.

### Step 3 · The gateway's routes

Each route points at a service NAME (`lb://`), not an address. Two carry the API; two fetch each service's API description for Swagger UI.

```bash
curl -s $GATEWAY/actuator/gateway/routes | jq -c '.[] | {route_id, uri}'
```

**You should see** Four routes: `suppliers`, `purchase-orders`, and the two `-docs` routes, all with `lb://` URIs:

```
{"route_id":"suppliers","uri":"lb://supplier-service"}
{"route_id":"purchase-orders","uri":"lb://purchase-order-service"}
{"route_id":"supplier-service-docs","uri":"lb://supplier-service"}
{"route_id":"purchase-order-service-docs","uri":"lb://purchase-order-service"}
```

> **If not:** `404`: the `gateway` endpoint isn't switched on (`management.endpoint.gateway.access`).

### Step 4 · A request through the gateway

The client only knows port 8300. The response says which copy answered, and that it came through the gateway.

```bash
curl -s -D - -o /dev/null $GATEWAY/api/suppliers/S-001 | grep -iE '^(HTTP|x-served-by|x-gateway)'
```

**You should see** `200`, an `X-Served-By` header, and `X-Gateway: pis-api-gateway`:

```
HTTP/1.1 200 OK
X-Served-By: supplier-service:8201
X-Gateway: pis-api-gateway
```

> **If not:** `503`: no copy of supplier-service is registered (yet). Wait a few seconds after starting it.

### Step 5 · Load balancing: six calls, two copies

Round robin: the gateway asks Eureka for the copies and takes turns.

```bash
for i in 1 2 3 4 5 6; do
  curl -s -D - -o /dev/null $GATEWAY/api/suppliers | grep -i '^x-served-by'
done
```

**You should see** The two copies taking turns:

```
X-Served-By: supplier-service:8211
X-Served-By: supplier-service:8201
X-Served-By: supplier-service:8211
X-Served-By: supplier-service:8201
X-Served-By: supplier-service:8211
X-Served-By: supplier-service:8201
```

> **If not:** Always the same copy: the second copy isn't registered, or the load balancer's 5-second cache hasn't refreshed yet.

### Step 6 · A purchase order: one service asks the other, by name

purchase-order-service calls `http://supplier-service/...`. Eureka turns the name into a running copy, and `checkedBy` shows which one.

```bash
curl -s -X POST -H "$JSON" $GATEWAY/api/purchase-orders \
  -d '{"supplierId":"S-001","item":"Laptop","quantity":2,"unitPrice":2500000.00}' | jq -c '{id, supplierName, total, checkedBy}'
```

**You should see** `201` with the supplier's name and `checkedBy` naming a copy of supplier-service:

```
{"id":"PO-0007","supplierName":"Kisiwa ICT Consultants","total":5000000.00,"checkedBy":"supplier-service:8201"}
```

> **If not:** `503 Supplier service unavailable`: no copy of supplier-service is registered.

### Step 7 · The next order is checked by the other copy

Load balancing works between services too, not just at the gateway.

```bash
for i in 1 2; do
  curl -s -X POST -H "$JSON" $GATEWAY/api/purchase-orders \
    -d '{"supplierId":"S-002","item":"Paper","quantity":5,"unitPrice":12000}' | jq -c '{id, checkedBy}'
done
```

**You should see** Two orders, two different `checkedBy` copies:

```
{"id":"PO-0008","checkedBy":"supplier-service:8211"}
{"id":"PO-0009","checkedBy":"supplier-service:8201"}
```

### Step 8 · A rule that needs the other service

Only an ACTIVE supplier can receive an order, and only supplier-service knows the status.

```bash
curl -s -X POST -H "$JSON" $GATEWAY/api/purchase-orders \
  -d '{"supplierId":"S-003","item":"Cement","quantity":10,"unitPrice":18000}' | jq -c '{status, detail}'
curl -s -X POST -H "$JSON" $GATEWAY/api/purchase-orders \
  -d '{"supplierId":"S-999","item":"Cement","quantity":10,"unitPrice":18000}' | jq -c '{status, detail}'
```

**You should see** `422` for the suspended supplier and `422` for the unknown one:

```
{"status":422,"detail":"Supplier S-003 is SUSPENDED, so it can't receive purchase orders"}
{"status":422,"detail":"Unknown supplier S-999"}
```

### Step 9 · Swagger UI through the gateway

One Swagger UI for both services. Its drop-down lists each service's description, fetched through the gateway, and each description names the gateway as its server, so "Try it out" goes through the gateway too.

```bash
curl -s $GATEWAY/v3/api-docs/swagger-config | jq -c '.urls[]'
for svc in supplier-service purchase-order-service; do
  curl -s $GATEWAY/$svc/v3/api-docs | jq -c '{title: .info.title, servers: [.servers[].url], paths: (.paths | keys)}'
done
```

**You should see** Both services in the drop-down, and both descriptions with the server `http://localhost:8300`:

```
{"url":"/purchase-order-service/v3/api-docs","name":"purchase-order-service"}
{"url":"/supplier-service/v3/api-docs","name":"supplier-service"}
{"title":"Supplier Service","servers":["http://localhost:8300"],"paths":["/api/suppliers","/api/suppliers/{id}"]}
{"title":"Purchase Order Service","servers":["http://localhost:8300"],"paths":["/api/purchase-orders","/api/purchase-orders/{id}"]}
```

Then open http://localhost:8300/swagger-ui.html in a browser. Pick **supplier-service** in the "Select a definition" drop-down, open `GET /api/suppliers`, press **Try it out**, then **Execute**. The response headers include `x-gateway: pis-api-gateway`: the request went through the gateway.

> **If not:** Server `http://localhost:8201`: `forward-headers-strategy` is missing in the service, or `trusted-proxies` in the gateway. Server ending in `/supplier-service`: `x-forwarded.prefix-enabled: false` is missing. A 404 or 503 for a description: see Common mistakes in the lecture.

### Step 10 · Stop one copy: nothing breaks

Press **Ctrl+C** in the terminal running supplier-service on **8211**, then run this straight away. It unregisters from Eureka as it stops, and the gateway's Retry filter covers the few seconds before everyone has noticed.

```bash
for i in $(seq 1 10); do
  curl -s -o /dev/null -w '%{http_code} ' $GATEWAY/api/suppliers; sleep 0.5
done; echo
curl -s -H 'Accept: application/json' $EUREKA/eureka/apps/SUPPLIER-SERVICE | jq -r '[.application.instance[].instanceId] | join(", ")'
```

**You should see** Ten `200`s, and only `supplier-service:8201` left in Eureka. Start 8211 again afterwards:

```
200 200 200 200 200 200 200 200 200 200 
supplier-service:8201
```

> **If not:** Some `500`s: the `Retry` default filter is missing from the gateway's `application.yml`.

✅ **Lecture 5 passes** when every step above matches.

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
