#!/usr/bin/env bash
# Lesson 3E smoke test: our own filters in the security filter chain.
# Guesses passwords until this machine is blocked, then waits for the block to
# lift, so it takes a little over a minute. Run it on its own: at least a minute
# after any other smoke test, because their wrong passwords count too.
BASE=http://localhost:8099
OFFICER='officer:officer123'
MAX=${MAX:-5}                       # pis.security.login-limit.max-failures (LOGIN_MAX_FAILURES)
JSON='Content-Type: application/json'

check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  [ "$got" = "$want" ] && echo "PASS  $got  $desc" || echo "FAIL  $got  $desc (expected $want)"
}

echo "--- before: the right password works"
check 200 "officer reads suppliers"                          -u "$OFFICER" "$BASE/api/v1/suppliers"

echo "--- guessing: $MAX wrong passwords, each one a 401"
for i in $(seq 1 "$MAX"); do
  check 401 "wrong password $i of $MAX"                      -u "officer:guess$i" "$BASE/api/v1/suppliers"
done

echo "--- blocked: every login attempt from this address is refused"
check 429 "even the right password"                          -u "$OFFICER" "$BASE/api/v1/suppliers"
check 429 "and the login endpoint"                           -X POST -H "$JSON" \
      -d '{"username":"officer","password":"officer123"}' "$BASE/api/v1/auth/login"
wait=$(curl -s -D - -o /dev/null -u "$OFFICER" "$BASE/api/v1/suppliers" | tr -d '\r' | awk -F': ' 'tolower($1)=="retry-after"{print $2}')
[ -n "$wait" ] && echo "PASS       Retry-After: $wait seconds" || echo "FAIL       no Retry-After header"
type=$(curl -s -D - -o /dev/null -u "$OFFICER" "$BASE/api/v1/suppliers" | tr -d '\r' | awk -F': ' 'tolower($1)=="content-type"{print $2}')
[ "$type" = "application/problem+json" ] && echo "PASS       the 429 is a problem detail ($type)" || echo "FAIL       content type: $type"

echo "--- not logins: still answered as usual"
check 401 "no credentials is a 401, not a 429"               "$BASE/api/v1/suppliers"
check 200 "health"                                           "$BASE/actuator/health"

echo "--- waiting ${wait:-60} seconds for the block to lift"
sleep "${wait:-60}"
check 200 "the right password works again"                   -u "$OFFICER" "$BASE/api/v1/suppliers"
