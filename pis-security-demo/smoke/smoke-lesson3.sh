#!/usr/bin/env bash
# Lesson 3 smoke test: log in once, then use a JWT.
BASE=http://localhost:8099
JSON='Content-Type: application/json'
ID=99999999-9999-9999-9999-999999999999

check() {   # check <expected status, e.g. 200 or "201|409"> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got
  got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if echo "$got" | grep -qE "^($want)$"; then echo "PASS  $got  $desc"; else echo "FAIL  $got  $desc (expected $want)"; fi
}
login() {   # login <username> <password>  → prints the token, or nothing
  curl -s -X POST -H "$JSON" -d "{\"username\":\"$1\",\"password\":\"$2\"}" "$BASE/api/v1/auth/login" \
    | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4
}
b64url_decode() { local s; s=$(printf '%s' "$1" | tr '_-' '/+'); while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done; printf '%s' "$s" | base64 -d; }
b64url_encode() { base64 | tr '/+' '_-' | tr -d '=\n'; }

echo "--- log in"
check 200 "login with the right password"      -X POST -H "$JSON" -d '{"username":"admin","password":"admin123"}' "$BASE/api/v1/auth/login"
check 401 "login with a wrong password"         -X POST -H "$JSON" -d '{"username":"admin","password":"wrong"}' "$BASE/api/v1/auth/login"
ADMIN_TOKEN=$(login admin 'admin123')
check "201|409" "officer account exists"        -H "Authorization: Bearer $ADMIN_TOKEN" -X POST -H "$JSON" \
      -d '{"username":"officer","password":"officer123","fullName":"Neema Said","roles":["OFFICER"]}' "$BASE/api/v1/users"
check "201|409" "approver account exists"       -H "Authorization: Bearer $ADMIN_TOKEN" -X POST -H "$JSON" \
      -d '{"username":"approver","password":"approver123","fullName":"Juma Ali","roles":["APPROVER"]}' "$BASE/api/v1/users"
OFFICER_TOKEN=$(login officer 'officer123')
APPROVER_TOKEN=$(login approver 'approver123')
echo "      officer token payload: $(b64url_decode "$(echo "$OFFICER_TOKEN" | cut -d. -f2)")"

echo "--- use the token"
check 401 "no token"                            "$BASE/api/v1/suppliers"
check 200 "officer token opens the API"         -H "Authorization: Bearer $OFFICER_TOKEN" "$BASE/api/v1/suppliers"
check 200 "/users/me knows who the token is"    -H "Authorization: Bearer $OFFICER_TOKEN" "$BASE/api/v1/users/me"
check 403 "officer token cannot approve"        -H "Authorization: Bearer $OFFICER_TOKEN" -X POST "$BASE/api/v1/requisitions/$ID/approve"
check 404 "approver token can approve"          -H "Authorization: Bearer $APPROVER_TOKEN" -X POST "$BASE/api/v1/requisitions/$ID/approve"
check 401 "a made-up token"                     -H "Authorization: Bearer not.a.token" "$BASE/api/v1/suppliers"

echo "--- forge a token"
HEADER=$(echo "$OFFICER_TOKEN" | cut -d. -f1)
PAYLOAD=$(b64url_decode "$(echo "$OFFICER_TOKEN" | cut -d. -f2)")
SIGNATURE=$(echo "$OFFICER_TOKEN" | cut -d. -f3)
FORGED="$HEADER.$(printf '%s' "${PAYLOAD/OFFICER/ADMIN}" | b64url_encode).$SIGNATURE"
check 403 "officer token cannot delete"         -H "Authorization: Bearer $OFFICER_TOKEN" -X DELETE "$BASE/api/v1/suppliers/$ID"
check 401 "same token edited to say ADMIN"      -H "Authorization: Bearer $FORGED" -X DELETE "$BASE/api/v1/suppliers/$ID"

echo "--- the trade-off"
OFFICER_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/users" | grep -o '"id":"[^"]*","username":"officer"' | cut -d'"' -f4)
check 200 "admin disables officer"              -H "Authorization: Bearer $ADMIN_TOKEN" -X PATCH -H "$JSON" -d '{"enabled":false}' "$BASE/api/v1/users/$OFFICER_ID/enabled"
check 401 "disabled officer cannot log in"      -X POST -H "$JSON" -d '{"username":"officer","password":"officer123"}' "$BASE/api/v1/auth/login"
check 401 "...nor use Basic"                    -u 'officer:officer123' "$BASE/api/v1/suppliers"
check 200 "...but the old token still works"    -H "Authorization: Bearer $OFFICER_TOKEN" "$BASE/api/v1/suppliers"
check 200 "admin enables officer again"         -H "Authorization: Bearer $ADMIN_TOKEN" -X PATCH -H "$JSON" -d '{"enabled":true}' "$BASE/api/v1/users/$OFFICER_ID/enabled"
