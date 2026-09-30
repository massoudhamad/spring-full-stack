#!/usr/bin/env bash
# Lesson 2 smoke test: users in the database, and who did what.
# Creates one "Smoke Test Supplier" per run in the database you point it at.
BASE=http://localhost:8099
ADMIN='admin:admin123'
OFFICER='officer:officer123'
APPROVER='approver:approver123'
JSON='Content-Type: application/json'
ID=99999999-9999-9999-9999-999999999999

check() {   # check <expected status, e.g. 200 or "201|409"> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got
  got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if echo "$got" | grep -qE "^($want)$"; then echo "PASS  $got  $desc"; else echo "FAIL  $got  $desc (expected $want)"; fi
}

echo "--- accounts"
check 200       "admin logs in from app_user"              -u "$ADMIN" "$BASE/api/v1/users/me"
check "201|409" "admin creates officer (409: exists)"      -u "$ADMIN" -X POST -H "$JSON" \
      -d '{"username":"officer","password":"officer123","fullName":"Neema Said","roles":["OFFICER"]}' "$BASE/api/v1/users"
check "201|409" "admin creates approver (409: exists)"     -u "$ADMIN" -X POST -H "$JSON" \
      -d '{"username":"approver","password":"approver123","fullName":"Juma Ali","roles":["APPROVER"]}' "$BASE/api/v1/users"
check 200       "officer logs in"                          -u "$OFFICER" "$BASE/api/v1/users/me"
check 403       "officer cannot list users"                -u "$OFFICER" "$BASE/api/v1/users"
check 404       "approver can approve"                     -u "$APPROVER" -X POST "$BASE/api/v1/requisitions/$ID/approve"

echo "--- who did it"
TIN=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
BODY=$(curl -s -u "$OFFICER" -X POST -H "$JSON" \
  -d "{\"name\":\"Smoke Test Supplier $TIN\",\"tin\":\"$TIN\",\"registrationNumber\":\"SMOKE-$TIN\",\"category\":\"GOODS\",\"email\":\"smoke@example.co.tz\"}" \
  "$BASE/api/v1/suppliers")
if echo "$BODY" | grep -q '"createdBy":"officer"'; then echo "PASS  201  new supplier has createdBy = officer"
else echo "FAIL       new supplier createdBy: $BODY"; fi

echo "--- disable and enable"
OFFICER_ID=$(curl -s -u "$ADMIN" "$BASE/api/v1/users" | grep -o '"id":"[^"]*","username":"officer"' | cut -d'"' -f4)
check 200 "admin disables officer"       -u "$ADMIN" -X PATCH -H "$JSON" -d '{"enabled":false}' "$BASE/api/v1/users/$OFFICER_ID/enabled"
check 401 "disabled officer is refused"  -u "$OFFICER" "$BASE/api/v1/suppliers"
check 200 "admin enables officer again"  -u "$ADMIN" -X PATCH -H "$JSON" -d '{"enabled":true}' "$BASE/api/v1/users/$OFFICER_ID/enabled"
check 200 "officer is back"              -u "$OFFICER" "$BASE/api/v1/suppliers"
