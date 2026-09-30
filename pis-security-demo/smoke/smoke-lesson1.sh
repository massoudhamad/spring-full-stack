#!/usr/bin/env bash
# Lesson 1 smoke test: HTTP Basic and role rules.
BASE=http://localhost:8099
ID=99999999-9999-9999-9999-999999999999   # does not exist: 404 means "got past security"

check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got
  got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if [ "$got" = "$want" ]; then echo "PASS  $got  $desc"; else echo "FAIL  $got  $desc (expected $want)"; fi
}

check 200 "health check is public"             "$BASE/actuator/health"
check 401 "no credentials"                     "$BASE/api/v1/suppliers"
check 401 "wrong password"                     -u officer:wrong "$BASE/api/v1/suppliers"
check 200 "officer can read"                   -u 'officer:officer123' "$BASE/api/v1/suppliers"
check 403 "officer cannot approve"             -u 'officer:officer123' -X POST "$BASE/api/v1/requisitions/$ID/approve"
check 404 "approver can approve"               -u 'approver:approver123' -X POST "$BASE/api/v1/requisitions/$ID/approve"
check 403 "approver cannot create a supplier"  -u 'approver:approver123' -X POST -H 'Content-Type: application/json' -d '{}' "$BASE/api/v1/suppliers"
check 403 "officer cannot delete"              -u 'officer:officer123' -X DELETE "$BASE/api/v1/suppliers/$ID"
check 404 "admin can delete"                   -u 'admin:admin123' -X DELETE "$BASE/api/v1/suppliers/$ID"
