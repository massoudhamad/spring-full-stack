#!/usr/bin/env bash
# Lesson 3F smoke test: URL rules in the database.
# Adds a rule at priority 65 and deletes it again, so it can be run again. It
# never touches the seeded rules.
BASE=http://localhost:8099
ADMIN='admin:admin123'
OFFICER='officer:officer123'
APPROVER='approver:approver123'
JSON='Content-Type: application/json'

check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  [ "$got" = "$want" ] && echo "PASS  $got  $desc" || echo "FAIL  $got  $desc (expected $want)"
}
rule_at() { curl -s -u "$ADMIN" "$BASE/api/v1/url-rules" | grep -o "{\"id\":[0-9]*,\"priority\":$1," | grep -o '[0-9]*' | head -1; }

# Left over from an interrupted run?
old=$(rule_at 65); [ -n "$old" ] && curl -s -o /dev/null -u "$ADMIN" -X DELETE "$BASE/api/v1/url-rules/$old"

echo "--- the rules are data"
n=$(curl -s -u "$ADMIN" "$BASE/api/v1/url-rules" | grep -o '"priority"' | wc -l | tr -d ' ')
[ "$n" -ge 12 ] && echo "PASS       $n rules in url_rule" || echo "FAIL       $n rules in url_rule (expected 12)"
d=$(curl -s -u "$ADMIN" "$BASE/api/v1/url-rules/match?method=PATCH&path=/api/v1/suppliers/x/status" | grep -o '"decision":"[^"]*"')
[ "$d" = '"decision":"needs supplier:approve (rule 30)"' ] && echo "PASS       a status change: rule 30 decides" || echo "FAIL       a status change: $d"
check 403 "no rule matches: refused, even for the admin"         -u "$ADMIN" "$BASE/api/v1/reports"
check 403 "an officer can't see the rules"                       -u "$OFFICER" "$BASE/api/v1/url-rules"

echo "--- change what an endpoint needs, no restart"
check 200 "officer reads requisitions"                           -u "$OFFICER" "$BASE/api/v1/requisitions"
check 201 "admin: only approvers may read requisitions"          -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":65,"method":"GET","pattern":"/api/v1/requisitions/**","permission":"requisition:approve","description":"Smoke test: only approvers may read requisitions"}'
check 403 "officer, on the next request"                         -u "$OFFICER" "$BASE/api/v1/requisitions"
check 200 "approver still can"                                   -u "$APPROVER" "$BASE/api/v1/requisitions"
check 204 "admin deletes the rule"                               -u "$ADMIN" -X DELETE "$BASE/api/v1/url-rules/$(rule_at 65)"
check 200 "officer can read again"                               -u "$OFFICER" "$BASE/api/v1/requisitions"

echo "--- guard rails"
check 422 "no rules for admin endpoints"                         -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":5,"pattern":"/api/v1/roles/**","permission":"supplier:read","description":"x"}'
check 422 "a pattern Spring can't read"                          -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":5,"pattern":"/api/v1/**/approve","permission":"supplier:read","description":"x"}'
check 409 "a priority that's taken"                              -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":50,"pattern":"/api/v1/reports/**","permission":"supplier:read","description":"x"}'
check 400 "an unknown permission"                                -u "$ADMIN" -X POST -H "$JSON" "$BASE/api/v1/url-rules" \
      -d '{"priority":5,"pattern":"/api/v1/reports/**","permission":"supplier:fly","description":"x"}'
