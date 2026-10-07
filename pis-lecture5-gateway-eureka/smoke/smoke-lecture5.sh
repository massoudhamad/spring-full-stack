#!/usr/bin/env bash
# Lecture 5 smoke test: Eureka, two services that find each other, and the API gateway.
# Needs all five running: discovery-server, supplier-service on 8201 AND 8211,
# purchase-order-service, api-gateway (see the README).
EUREKA=${EUREKA:-http://localhost:8761}
GATEWAY=${GATEWAY:-http://localhost:8300}
JSON='Content-Type: application/json'

pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; }
check() {   # check <expected status> <description> <curl args...>
  local want=$1 desc=$2; shift 2
  local got; got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  [ "$got" = "$want" ] && echo "PASS  $got  $desc" || echo "FAIL  $got  $desc (expected $want)"
}
instances() { curl -s -H 'Accept: application/json' "$EUREKA/eureka/apps/$1" | grep -o '"instanceId":"[^"]*"' | cut -d'"' -f4 | sort | tr '\n' ' '; }
served_by() { curl -s -D - -o /dev/null "$@" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-served-by"{print $2}'; }
order() { curl -s -X POST -H "$JSON" -d "{\"supplierId\":\"$1\",\"item\":\"Laptop\",\"quantity\":2,\"unitPrice\":2500000.00}" "$GATEWAY/api/purchase-orders"; }

echo "--- Eureka: who is registered?"
s=$(instances SUPPLIER-SERVICE); [ "$(echo $s | wc -w)" -ge 2 ] && pass "     supplier-service: $s" || fail "     supplier-service needs 2 copies (8201 and 8211), found: $s"
p=$(instances PURCHASE-ORDER-SERVICE); [ -n "$p" ] && pass "     purchase-order-service: $p" || fail "     purchase-order-service is not registered"
g=$(instances API-GATEWAY); [ -n "$g" ] && pass "     api-gateway: $g" || fail "     api-gateway is not registered"

echo "--- the gateway routes by path"
r=$(curl -s "$GATEWAY/actuator/gateway/routes" | grep -o '"uri":"lb://[^"]*"' | cut -d'"' -f4 | tr '\n' ' ')
echo "$r" | grep -q 'lb://supplier-service' && echo "$r" | grep -q 'lb://purchase-order-service' && pass "     routes: $r" || fail "     routes: $r"
check 200 "GET /api/suppliers through the gateway"              "$GATEWAY/api/suppliers"
h=$(curl -s -D - -o /dev/null "$GATEWAY/api/suppliers/S-001" | tr -d '\r' | grep -i '^x-gateway:')
[ -n "$h" ] && pass "     the gateway marks its responses ($h)" || fail "     no X-Gateway header"
check 404 "a path no route matches"                             "$GATEWAY/api/invoices"

echo "--- load balancing: the gateway spreads calls over both copies"
seen=$(for i in 1 2 3 4 5 6; do served_by "$GATEWAY/api/suppliers"; done | sort -u | tr '\n' ' ')
[ "$(echo $seen | wc -w)" -ge 2 ] && pass "     6 calls answered by: $seen" || fail "     6 calls answered only by: $seen"

echo "--- service to service: purchase-order-service finds supplier-service BY NAME"
o1=$(order S-001); o2=$(order S-001)
echo "$o1" | grep -q '"supplierName":"Kisiwa ICT Consultants"' && pass "201  order created after asking supplier-service" || fail "     order: $o1"
c1=$(echo "$o1" | grep -o '"checkedBy":"[^"]*"' | cut -d'"' -f4); c2=$(echo "$o2" | grep -o '"checkedBy":"[^"]*"' | cut -d'"' -f4)
[ -n "$c1" ] && [ "$c1" != "$c2" ] && pass "     two orders were checked by two copies: $c1, $c2" || fail "     both orders checked by: $c1 $c2"
check 422 "a SUSPENDED supplier is refused (rule needs the other service)" -X POST -H "$JSON" \
      -d '{"supplierId":"S-003","item":"Cement","quantity":10,"unitPrice":18000}' "$GATEWAY/api/purchase-orders"
check 422 "an unknown supplier is refused"                       -X POST -H "$JSON" \
      -d '{"supplierId":"S-999","item":"Cement","quantity":10,"unitPrice":18000}' "$GATEWAY/api/purchase-orders"
