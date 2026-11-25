#!/usr/bin/env bash
# Downstream failover test: scale abl-cart-service to 0 -> verify 503 fast-fail on Order -> recover 200 OK.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/../lib/jwt.sh"
jwt_init customer

MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-ecommerce-abl}"
KUBE_CONTEXT="${KUBE_CONTEXT:-ecommerce-abl}"
NAMESPACE="${NAMESPACE:-ecommerce}"
BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
passed=0

kctl() {
  minikube -p "$MINIKUBE_PROFILE" kubectl -- --context="$KUBE_CONTEXT" "$@"
}

cleanup() {
  kctl -n "$NAMESPACE" scale deployment/abl-cart-service --replicas="$original_replicas" >/dev/null 2>&1 || true
}

pass() {
  ((passed += 1))
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

jwt_preflight
original_replicas=$(kctl -n "$NAMESPACE" get deployment/abl-cart-service -o jsonpath='{.spec.replicas}')
[[ "$original_replicas" =~ ^[1-9][0-9]*$ ]] || fail "Deployment harus aktif sebelum fault test"
trap cleanup EXIT
jwt_refresh customer

# 1. Scale abl-cart-service to 0 replicas and wait for pods to terminate
kctl -n "$NAMESPACE" scale deployment/abl-cart-service --replicas=0
kctl -n "$NAMESPACE" wait --for=delete pod -l app.kubernetes.io/name=cart-service --timeout=90s || true
pass "Scaled abl-cart-service to 0 replicas"

# 2. Verify POST /api/v1/orders fast-fails with 503
jwt_refresh customer
order_status=$(curl -sS -H "Authorization: Bearer $CUSTOMER_TOKEN" -o /dev/null -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -d "{\"cartId\":1,\"customerId\":$CUSTOMER_ID}" \
  "$BASE_URL/api/v1/orders")
[[ "$order_status" == "503" ]] || fail "Expected HTTP 503 fast-fail when cart-service is down, got $order_status"
pass "POST /api/v1/orders fast-failed with HTTP 503 while abl-cart-service is down"

# 3. Restore the original replica count and wait for Ready.
kctl -n "$NAMESPACE" scale deployment/abl-cart-service --replicas="$original_replicas"
kctl -n "$NAMESPACE" rollout status deployment/abl-cart-service --timeout=180s
pass "Restored Cart to $original_replicas ready replicas"

jwt_refresh customer

# 4. Verify recovery (200 OK)
cart_body=$(mktemp)
cart_create_status=$(curl -sS -H "Authorization: Bearer $CUSTOMER_TOKEN" -o "$cart_body" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":$CUSTOMER_ID}" \
  "$BASE_URL/api/v1/carts")
[[ "$cart_create_status" == "201" ]] || { rm -f "$cart_body"; fail "Expected 201 on cart recovery, got $cart_create_status"; }
cart_id=$(sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$cart_body" | head -n1)
rm -f "$cart_body"

recover_status=$(curl -sS -H "Authorization: Bearer $CUSTOMER_TOKEN" -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/carts/$cart_id")
[[ "$recover_status" == "200" ]] || fail "Expected HTTP 200 after cart-service recovery, got $recover_status"
pass "Downstream recovery verified: GET /api/v1/carts/$cart_id returned HTTP 200"

printf 'All %d downstream failover assertions passed.\n' "$passed"
