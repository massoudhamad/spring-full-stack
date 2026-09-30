#!/usr/bin/env bash
# Lesson 3C smoke test: permissions and method security.
# Creates officer and approver if missing, and two requisitions per run.
BASE=http://localhost:8099
JSON='Content-Type: application/json'
REQ='{"department":"ICT","requestedBy":"Smoke Test","justification":"Lesson 3C smoke test","requiredByDate":"2030-12-31","items":[{"description":"Laptop","quantity":1,"unit":"PIECE","estimatedUnitPrice":2500000.00}]}'

check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if echo "$got" | grep -qE "^($want)$"; then echo "PASS  $got  $desc"; else echo "FAIL  $got  $desc (expected $want)"; fi
}
field() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
token() { curl -s -X POST -H "$JSON" -d "{\"username\":\"$1\",\"password\":\"$2\"}" "$BASE/api/v1/auth/login" | field accessToken; }
b64url_decode() { local s; s=$(printf '%s' "$1" | tr '_-' '/+'); while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done; printf '%s' "$s" | base64 -d; }
raise() {   # raise <token>  → creates and submits a requisition, prints its id
  local id; id=$(curl -s -X POST -H "$JSON" -H "Authorization: Bearer $1" -d "$REQ" "$BASE/api/v1/requisitions" | field id)
  curl -s -o /dev/null -X POST -H "Authorization: Bearer $1" "$BASE/api/v1/requisitions/$id/submit"
  echo "$id"
}

echo "--- accounts and permissions"
ADMIN=$(token admin 'admin123')
check "201|409" "officer account exists"   -H "Authorization: Bearer $ADMIN" -X POST -H "$JSON" \
      -d '{"username":"officer","password":"officer123","fullName":"Neema Said","roles":["OFFICER"]}' "$BASE/api/v1/users"
check "201|409" "approver account exists"  -H "Authorization: Bearer $ADMIN" -X POST -H "$JSON" \
      -d '{"username":"approver","password":"approver123","fullName":"Juma Ali","roles":["APPROVER"]}' "$BASE/api/v1/users"
OFFICER=$(token officer 'officer123'); APPROVER=$(token approver 'approver123')
ME=$(curl -s -H "Authorization: Bearer $APPROVER" "$BASE/api/v1/users/me")
echo "$ME" | grep -q '"requisition:approve"' && ! echo "$ME" | grep -q '"requisition:write"' \
  && echo "PASS       /me: approver has requisition:approve, not requisition:write" || echo "FAIL       /me: $ME"
b64url_decode "$(echo "$APPROVER" | cut -d. -f2)" | grep -q '"permissions":\[' \
  && echo "PASS       the approver's token carries a permissions claim" || echo "FAIL       no permissions claim"

echo "--- URL rules check permissions"
check 403 "approver cannot raise a requisition"      -X POST -H "$JSON" -H "Authorization: Bearer $APPROVER" -d "$REQ" "$BASE/api/v1/requisitions"
R1=$(raise "$OFFICER")
[ -n "$R1" ] && echo "PASS       officer raised and submitted requisition ${R1:0:8}…" || echo "FAIL       officer could not raise a requisition"
check 403 "officer cannot approve"                    -X POST -H "Authorization: Bearer $OFFICER" "$BASE/api/v1/requisitions/$R1/approve"
check 200 "approver approves the officer's"           -X POST -H "Authorization: Bearer $APPROVER" "$BASE/api/v1/requisitions/$R1/approve"

echo "--- method security: nobody approves their own"
R2=$(raise "$ADMIN")
check 403 "admin cannot approve their own (403, not 500)" -X POST -H "Authorization: Bearer $ADMIN" "$BASE/api/v1/requisitions/$R2/approve"
check 403 "...nor reject it"                          -X POST -H "Authorization: Bearer $ADMIN" "$BASE/api/v1/requisitions/$R2/reject"
check 200 "approver approves the admin's"             -X POST -H "Authorization: Bearer $APPROVER" "$BASE/api/v1/requisitions/$R2/approve"
