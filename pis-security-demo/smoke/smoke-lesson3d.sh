#!/usr/bin/env bash
# Lesson 3D smoke test: roles and permissions in the database.
# Creates an AUDITOR role and an "auditor" account (password auditor123) if missing,
# and leaves AUDITOR read-only at the end, so it can be run again.
BASE=http://localhost:8099
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
