#!/usr/bin/env bash
# Istio Service Mesh security verification:
# 1. PeerAuthentication STRICT rejects plaintext requests from non-mesh pods.
# 2. L7 AuthorizationPolicy enforces SPIFFE-based RBAC between services.
# 3. /actuator/* is isolated from Ingress Gateway (404) and exposed on port 9090 (200).
set -euo pipefail

MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-ecommerce-abl}"
KUBE_CONTEXT="${KUBE_CONTEXT:-ecommerce-abl}"
NAMESPACE="${NAMESPACE:-ecommerce}"
BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
passed=0
pf_pid=""

cleanup() {
  if [[ -n "$pf_pid" ]] && kill -0 "$pf_pid" 2>/dev/null; then
    kill "$pf_pid" 2>/dev/null || true
    wait "$pf_pid" 2>/dev/null || true
  fi
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

kctl() {
  minikube -p "$MINIKUBE_PROFILE" kubectl -- --context="$KUBE_CONTEXT" "$@"
}

# 1. Verify PeerAuthentication STRICT mode
pa_mode=$(kctl -n "$NAMESPACE" get peerauthentication -o jsonpath='{.items[0].spec.mtls.mode}')
[[ "$pa_mode" == "STRICT" ]] || fail "Expected PeerAuthentication STRICT, got '$pa_mode'"
pass "PeerAuthentication mode in $NAMESPACE is STRICT"

# 2. Non-mesh pod in 'default' namespace must be rejected by mTLS STRICT
non_mesh_pod="mtls-probe-$$"
if kctl -n default run "$non_mesh_pod" --image=curlimages/curl:8.10.1 --restart=Never --rm -i --quiet -- \
  curl -sS --max-time 5 "http://abl-product-service.${NAMESPACE}.svc.cluster.local:8080/api/v1/products" >/dev/null 2>&1; then
  fail "Non-mesh pod in default namespace was able to reach abl-product-service"
fi
pass "Non-mesh pod in default namespace rejected under PeerAuthentication STRICT"

# 3. L7 AuthorizationPolicy checks from abl-cart-service pod
cart_pod=$(kctl -n "$NAMESPACE" get pod -l app.kubernetes.io/component=cart -o jsonpath='{.items[0].metadata.name}')
[[ -n "$cart_pod" ]] || fail "No running pod found for abl-cart-service"

get_product_code=$(kctl -n "$NAMESPACE" exec "$cart_pod" -c cart-service -- \
  curl -sS -o /dev/null -w '%{http_code}' "http://abl-product-service.${NAMESPACE}.svc.cluster.local:8080/api/v1/products")
[[ "$get_product_code" == "200" ]] || fail "Expected 200 for cart -> GET product, got $get_product_code"
pass "L7 RBAC allows abl-cart-service GET /api/v1/products (HTTP 200)"

post_product_code=$(kctl -n "$NAMESPACE" exec "$cart_pod" -c cart-service -- \
  curl -sS -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d '{"name":"Blocked","price":1000,"stock":1}' \
  "http://abl-product-service.${NAMESPACE}.svc.cluster.local:8080/api/v1/products")
[[ "$post_product_code" == "403" ]] || fail "Expected 403 for cart -> POST product, got $post_product_code"
pass "L7 RBAC denies abl-cart-service POST /api/v1/products (HTTP 403)"

get_payment_code=$(kctl -n "$NAMESPACE" exec "$cart_pod" -c cart-service -- \
  curl -sS -o /dev/null -w '%{http_code}' "http://abl-payment-service.${NAMESPACE}.svc.cluster.local:8080/api/v1/payments/1")
[[ "$get_payment_code" == "403" ]] || fail "Expected 403 for cart -> GET payment, got $get_payment_code"
pass "L7 RBAC denies abl-cart-service GET /api/v1/payments/1 (HTTP 403)"

# 4. /actuator/* isolation: 404 on Gateway 18080, 200 on management port 9090
gw_actuator_code=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health")
[[ "$gw_actuator_code" == "404" ]] || fail "Expected 404 for /actuator/health on gateway, got $gw_actuator_code"
pass "Gateway 18080 blocks /actuator/health (HTTP 404)"

kctl -n "$NAMESPACE" port-forward deployment/abl-product-service 19090:9090 >/dev/null 2>&1 &
pf_pid=$!
for _ in {1..15}; do
  if curl -sS -o /dev/null http://127.0.0.1:19090/actuator/health/readiness 2>/dev/null; then
    break
  fi
  sleep 1
done

mgmt_code=$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:19090/actuator/health/readiness)
[[ "$mgmt_code" == "200" ]] || fail "Expected 200 on management port 9090 readiness, got $mgmt_code"
pass "Management port 9090 exposes /actuator/health/readiness (HTTP 200)"

printf 'All %d Istio mesh security assertions passed.\n' "$passed"
