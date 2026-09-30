#!/usr/bin/env bash
# Lecture 4 smoke test: auth-server (tokens) + pis-api (resource server).
# Runs every OAuth2 flow with plain curl, including the browser login.
AUTH=${AUTH:-http://localhost:9100}
API=${API:-http://localhost:8181}
REDIRECT=http://127.0.0.1:3000/callback
OFFICER_PASSWORD=${OFFICER_PASSWORD:-officer123}
APPROVER_PASSWORD=${APPROVER_PASSWORD:-approver123}
BFF_SECRET=${PIS_BFF_SECRET:-bff-secret-123}
REPORTING_SECRET=${PIS_REPORTING_SECRET:-reporting-secret-123}
ID=99999999-9999-9999-9999-999999999999

pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; }
check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if echo "$got" | grep -qE "^($want)$"; then echo "PASS  $got  $desc"; else echo "FAIL  $got  $desc (expected $want)"; fi
}
field()   { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
b64url_decode() { local s; s=$(printf '%s' "$1" | tr '_-' '/+'); while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done; printf '%s' "$s" | base64 -d; }
claims()  { b64url_decode "$(echo "$1" | cut -d. -f2)"; }

# The browser part of the authorization-code flow, done with curl and a cookie jar.
# Sets CODE and VERIFIER.   authorize <client_id> <username> <password>
authorize() {
  local jar csrf challenge next
  jar=$(mktemp)
  VERIFIER=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)                      # PKCE secret, stays with the client
  challenge=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | base64 | tr '/+' '_-' | tr -d '=\n')
  local url="$AUTH/oauth2/authorize?response_type=code&client_id=$1&redirect_uri=$REDIRECT&scope=openid%20profile%20pis&state=s123&code_challenge=$challenge&code_challenge_method=S256"
  curl -s -c "$jar" -b "$jar" -H 'Accept: text/html' -o /dev/null "$url"                 # 1. not logged in: remember the request, go to /login
  csrf=$(curl -s -c "$jar" -b "$jar" "$AUTH/login" | grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed 's/.*value="//; s/"$//')
  next=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' \
         --data-urlencode "username=$2" --data-urlencode "password=$3" --data-urlencode "_csrf=$csrf" "$AUTH/login")   # 2. the user logs in
  CODE=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' "$next" | grep -o 'code=[^&]*' | cut -d= -f2)  # 3. back to the app with a code
  rm -f "$jar"
}

echo "--- the authorization server"
check 200 "discovery document"                     "$AUTH/.well-known/openid-configuration"
check 200 "public keys (JWKS)"                     "$AUTH/oauth2/jwks"

echo "--- client credentials: the reporting job, no user"
RT=$(curl -s -u "pis-reporting:$REPORTING_SECRET" -d grant_type=client_credentials -d scope=suppliers.read "$AUTH/oauth2/token" | field access_token)
[ -n "$RT" ] && pass "     reporting token: $(claims "$RT" | grep -o '"sub":"[^"]*"\|"scope":\[[^]]*\]' | tr '\n' ' ')" || fail "     no reporting token"
check 200 "reporting job reads suppliers"          -H "Authorization: Bearer $RT" "$API/api/v1/suppliers"
check 403 "reporting job cannot read requisitions" -H "Authorization: Bearer $RT" "$API/api/v1/requisitions"
check 401 "wrong client secret"                    -u "pis-reporting:wrong" -d grant_type=client_credentials "$AUTH/oauth2/token"

echo "--- authorization code + PKCE: pis-web, a public browser app"
authorize pis-web officer "$OFFICER_PASSWORD"
[ -n "$CODE" ] && pass "     officer logged in, got code ${CODE:0:12}…" || fail "     no authorization code"
GOOD_VERIFIER=$VERIFIER
check 400 "code with the WRONG PKCE verifier"      -d grant_type=authorization_code -d "code=$CODE" -d "redirect_uri=$REDIRECT" \
      -d client_id=pis-web -d code_verifier=wrong-verifier-wrong-verifier-wrong-verifier-123 "$AUTH/oauth2/token"
authorize pis-web officer "$OFFICER_PASSWORD"
WEB=$(curl -s -d grant_type=authorization_code -d "code=$CODE" -d "redirect_uri=$REDIRECT" -d client_id=pis-web \
      -d "code_verifier=$VERIFIER" "$AUTH/oauth2/token")
AT=$(echo "$WEB" | field access_token)
[ -n "$AT" ] && pass "     officer token: $(claims "$AT" | grep -o '"sub":"[^"]*"\|"roles":\[[^]]*\]' | tr '\n' ' ')" || fail "     token exchange: $WEB"
echo "$WEB" | grep -q '"id_token"'      && pass "     id_token issued (OpenID Connect)" || fail "     no id_token"
echo "$WEB" | grep -q '"refresh_token"' && fail "     public client got a refresh token" || pass "     no refresh token for a public client"
check 200 "/userinfo on the auth server"           -H "Authorization: Bearer $AT" "$AUTH/userinfo"

echo "--- pis-api trusts the auth server's token"
check 401 "no token"                               "$API/api/v1/suppliers"
check 401 "Basic auth is gone"                     -u "officer:$OFFICER_PASSWORD" "$API/api/v1/suppliers"
check 200 "/me"                                    -H "Authorization: Bearer $AT" "$API/api/v1/me"
check 200 "officer reads requisitions"             -H "Authorization: Bearer $AT" "$API/api/v1/requisitions"
check 403 "officer cannot approve"                 -H "Authorization: Bearer $AT" -X POST "$API/api/v1/requisitions/$ID/approve"
TIN=$(printf '%03d-%03d-%03d' $((RANDOM % 1000)) $((RANDOM % 1000)) $((RANDOM % 1000)))
BODY=$(curl -s -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -X POST "$API/api/v1/suppliers" \
  -d "{\"name\":\"OAuth Smoke Supplier $TIN\",\"tin\":\"$TIN\",\"registrationNumber\":\"OAUTH-$TIN\",\"category\":\"GOODS\",\"email\":\"smoke@example.co.tz\"}")
echo "$BODY" | grep -q '"createdBy":"officer"' && pass "201  new supplier createdBy = officer (the token's sub)" || fail "     createdBy: $BODY"

echo "--- authorization code + PKCE + secret: pis-bff, which gets refresh tokens"
authorize pis-bff approver "$APPROVER_PASSWORD"
BFF=$(curl -s -u "pis-bff:$BFF_SECRET" -d grant_type=authorization_code -d "code=$CODE" -d "redirect_uri=$REDIRECT" \
      -d "code_verifier=$VERIFIER" "$AUTH/oauth2/token")
BA=$(echo "$BFF" | field access_token); R1=$(echo "$BFF" | field refresh_token)
[ -n "$R1" ] && pass "     confidential client got a refresh token" || fail "     no refresh token: $BFF"
check 404 "approver can approve"                   -H "Authorization: Bearer $BA" -X POST "$API/api/v1/requisitions/$ID/approve"
R2=$(curl -s -u "pis-bff:$BFF_SECRET" -d grant_type=refresh_token -d "refresh_token=$R1" "$AUTH/oauth2/token" | field refresh_token)
[ -n "$R2" ] && [ "$R2" != "$R1" ] && pass "     refresh returned a NEW refresh token (rotation)" || fail "     refresh failed"
check 400 "old refresh token again"                -u "pis-bff:$BFF_SECRET" -d grant_type=refresh_token -d "refresh_token=$R1" "$AUTH/oauth2/token"
check 401 "refresh without the client secret"      -d grant_type=refresh_token -d "refresh_token=$R2" -d client_id=pis-bff "$AUTH/oauth2/token"

echo "--- a replayed code: the auth server revokes, PIS doesn't notice"
authorize pis-web officer "$OFFICER_PASSWORD"
RAT=$(curl -s -d grant_type=authorization_code -d "code=$CODE" -d "redirect_uri=$REDIRECT" -d client_id=pis-web \
      -d "code_verifier=$VERIFIER" "$AUTH/oauth2/token" | field access_token)
check 400 "the same code a second time"            -d grant_type=authorization_code -d "code=$CODE" -d "redirect_uri=$REDIRECT" \
      -d client_id=pis-web -d "code_verifier=$VERIFIER" "$AUTH/oauth2/token"
check 401 "...so the auth server revoked its token" -H "Authorization: Bearer $RAT" "$AUTH/userinfo"
check 200 "...but PIS still accepts it (JWT: signature only)" -H "Authorization: Bearer $RAT" "$API/api/v1/me"

echo "--- a token from somewhere else"
FORGED="$(echo "$AT" | cut -d. -f1).$(printf '%s' "$(claims "$AT" | sed 's/OFFICER/ADMIN/')" | base64 | tr '/+' '_-' | tr -d '=\n').$(echo "$AT" | cut -d. -f3)"
check 401 "officer token edited to ADMIN"          -H "Authorization: Bearer $FORGED" -X DELETE "$API/api/v1/suppliers/$ID"
