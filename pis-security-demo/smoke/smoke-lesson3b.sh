#!/usr/bin/env bash
# Lesson 3B smoke test: refresh tokens, rotation, reuse detection, logout.
# Needs the officer account (the Lesson 2 or 3 smoke test creates it).
BASE=http://localhost:8099
JSON='Content-Type: application/json'

check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got
  got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if echo "$got" | grep -qE "^($want)$"; then echo "PASS  $got  $desc"; else echo "FAIL  $got  $desc (expected $want)"; fi
}
field() { grep -o "\"$1\":\"[^\"]*\"" | cut -d'"' -f4; }                 # field <name>  < json
login()   { curl -s -X POST -H "$JSON" -d "{\"username\":\"$1\",\"password\":\"$2\"}" "$BASE/api/v1/auth/login"; }
refresh() { curl -s -X POST -H "$JSON" -d "{\"refreshToken\":\"$1\"}" "$BASE/api/v1/auth/refresh"; }
refresh_status() { check "$1" "$2" -X POST -H "$JSON" -d "{\"refreshToken\":\"$3\"}" "$BASE/api/v1/auth/refresh"; }

echo "--- rotation"
R1=$(login officer 'officer123' | field refreshToken)
[ -n "$R1" ] && echo "PASS       login returned a refresh token" || echo "FAIL       login returned no refresh token"
PAIR=$(refresh "$R1"); A2=$(echo "$PAIR" | field accessToken); R2=$(echo "$PAIR" | field refreshToken)
[ -n "$R2" ] && [ "$R2" != "$R1" ] && echo "PASS       refresh returned a NEW refresh token" || echo "FAIL       refresh: $PAIR"
check 200 "the new access token works"                 -H "Authorization: Bearer $A2" "$BASE/api/v1/users/me"

echo "--- reuse detection"
refresh_status 401 "old refresh token used again (theft?)"    "$R1"
refresh_status 401 "...so the newest one is revoked too"      "$R2"

echo "--- logout"
R3=$(login officer 'officer123' | field refreshToken)
check 204 "logout"                                     -X POST -H "$JSON" -d "{\"refreshToken\":\"$R3\"}" "$BASE/api/v1/auth/logout"
refresh_status 401 "refresh after logout"                     "$R3"

echo "--- disabled account"
ADMIN_TOKEN=$(login admin 'admin123' | field accessToken)
R4=$(login officer 'officer123' | field refreshToken)
OFFICER_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/users" | grep -o '"id":"[^"]*","username":"officer"' | cut -d'"' -f4)
check 200 "admin disables officer"                     -H "Authorization: Bearer $ADMIN_TOKEN" -X PATCH -H "$JSON" -d '{"enabled":false}' "$BASE/api/v1/users/$OFFICER_ID/enabled"
refresh_status 401 "disabled officer cannot refresh"          "$R4"
check 200 "admin enables officer again"                -H "Authorization: Bearer $ADMIN_TOKEN" -X PATCH -H "$JSON" -d '{"enabled":true}' "$BASE/api/v1/users/$OFFICER_ID/enabled"
refresh_status 401 "...and that family stays revoked"         "$R4"
