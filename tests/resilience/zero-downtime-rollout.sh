#!/usr/bin/env bash
# Zero-downtime rollout resilience test for abl-product-service.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/../lib/jwt.sh"
jwt_init customer

MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-ecommerce-abl}"
KUBE_CONTEXT="${KUBE_CONTEXT:-ecommerce-abl}"
NAMESPACE="${NAMESPACE:-ecommerce}"
BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
passed=0
probe_pid=""
probe_log=""

kctl() {
  minikube -p "$MINIKUBE_PROFILE" kubectl -- --context="$KUBE_CONTEXT" "$@"
}

cleanup() {
  if [[ -n "$probe_pid" ]] && kill -0 "$probe_pid" 2>/dev/null; then
    kill "$probe_pid" 2>/dev/null || true
    wait "$probe_pid" 2>/dev/null || true
  fi
  rm -f -- "${probe_log:-}"
  kctl -n "$NAMESPACE" scale deployment/abl-product-service --replicas="$original_replicas" >/dev/null 2>&1 || true
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
original_replicas=$(kctl -n "$NAMESPACE" get deployment/abl-product-service -o jsonpath='{.spec.replicas}')
[[ "$original_replicas" =~ ^[1-9][0-9]*$ ]] || fail "Deployment harus aktif sebelum fault test"
trap cleanup EXIT

# 1. Keep at least two replicas without reducing an existing larger deployment.
rollout_replicas=$((original_replicas > 2 ? original_replicas : 2))
kctl -n "$NAMESPACE" scale deployment/abl-product-service --replicas="$rollout_replicas"
kctl -n "$NAMESPACE" rollout status deployment/abl-product-service --timeout=180s
pass "Prepared $rollout_replicas ready Product replicas"

# 2. Start background HTTP probe against GET /api/v1/products
probe_log=$(mktemp)
(
  while true; do
    jwt_refresh customer || { printf 'AUTH_REFRESH_FAILED\n' >> "$probe_log"; exit 1; }
    code=$(curl -sS -H "Authorization: Bearer $CUSTOMER_TOKEN" -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/products" || echo "000")
    printf '%s\n' "$code" >> "$probe_log"
    sleep 0.2
  done
) &
probe_pid=$!

# 3. Trigger rolling restart and wait for completion
kctl -n "$NAMESPACE" rollout restart deployment/abl-product-service
kctl -n "$NAMESPACE" rollout status deployment/abl-product-service --timeout=180s
pass "Rolling restart of abl-product-service completed"

# 4. Stop probe and verify zero non-200 responses
kill "$probe_pid" 2>/dev/null || true
wait "$probe_pid" 2>/dev/null || true
probe_pid=""

total_requests=$(wc -l < "$probe_log" | tr -d ' ')
non_200=$(grep -vc '^200$' "$probe_log" || true)
[[ "$total_requests" -gt 0 ]] || fail "Background probe recorded 0 requests"
[[ "$non_200" -eq 0 ]] || fail "Detected $non_200 non-200 responses out of $total_requests during rollout"
pass "Zero downtime verified: $total_requests/ $total_requests requests returned HTTP 200"

# 5. Restore the original replica count.
kctl -n "$NAMESPACE" scale deployment/abl-product-service --replicas="$original_replicas"
kctl -n "$NAMESPACE" rollout status deployment/abl-product-service --timeout=120s
pass "Restored Product to $original_replicas replicas"

printf 'All %d zero-downtime rollout assertions passed.\n' "$passed"
