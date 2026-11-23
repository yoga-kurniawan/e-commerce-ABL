#!/usr/bin/env bash
# Downstream failover test: scale abl-cart-service to 0 -> verify 503 fast-fail on Order -> recover 200 OK.
set -euo pipefail

MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-ecommerce-abl}"
KUBE_CONTEXT="${KUBE_CONTEXT:-ecommerce-abl}"
NAMESPACE="${NAMESPACE:-ecommerce}"
BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
passed=0

kctl() {
  minikube -p "$MINIKUBE_PROFILE" kubectl -- --context="$KUBE_CONTEXT" "$@"
}

cleanup() {
  kctl -n "$NAMESPACE" scale deployment/abl-cart-service --replicas=1 >/dev/null 2>&1 || true
}
trap cleanup EXIT

pass() {
  ((passed += 1))
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

# 1. Scale abl-cart-service to 0 replicas and wait for pods to terminate
kctl -n "$NAMESPACE" scale deployment/abl-cart-service --replicas=0
kctl -n "$NAMESPACE" wait --for=delete pod -l app.kubernetes.io/name=cart-service --timeout=90s || true
pass "Scaled abl-cart-service to 0 replicas"

# 2. Verify POST /api/v1/orders fast-fails with 503
order_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -d '{"cartId":1,"customerId":101}' \
  "$BASE_URL/api/v1/orders")
[[ "$order_status" == "503" ]] || fail "Expected HTTP 503 fast-fail when cart-service is down, got $order_status"
pass "POST /api/v1/orders fast-failed with HTTP 503 while abl-cart-service is down"

# 3. Scale abl-cart-service back to 1 and wait for Ready
kctl -n "$NAMESPACE" scale deployment/abl-cart-service --replicas=1
kctl -n "$NAMESPACE" rollout status deployment/abl-cart-service --timeout=180s
pass "Scaled abl-cart-service back to 1 ready replica"

# 4. Verify recovery (200 OK)
cart_body=$(mktemp)
cart_create_status=$(curl -sS -o "$cart_body" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -d '{"customerId":101}' \
  "$BASE_URL/api/v1/carts")
[[ "$cart_create_status" == "201" ]] || { rm -f "$cart_body"; fail "Expected 201 on cart recovery, got $cart_create_status"; }
cart_id=$(sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$cart_body" | head -n1)
rm -f "$cart_body"

recover_status=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/carts/$cart_id")
[[ "$recover_status" == "200" ]] || fail "Expected HTTP 200 after cart-service recovery, got $recover_status"
pass "Downstream recovery verified: GET /api/v1/carts/$cart_id returned HTTP 200"

printf 'All %d downstream failover assertions passed.\n' "$passed"
