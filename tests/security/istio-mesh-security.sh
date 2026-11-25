#!/usr/bin/env bash
# Separate workload-identity enforcement from application JWT authorization.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/../lib/jwt.sh"
jwt_init
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-ecommerce-abl}"
KUBE_CONTEXT="${KUBE_CONTEXT:-ecommerce-abl}"
NAMESPACE="${NAMESPACE:-ecommerce}"
BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
passed=0
pf_pid=''
non_mesh_pod="mtls-probe-$$"
cart_probe_pod="rbac-cart-probe-$$"
non_mesh_created=false
cart_probe_created=false
kctl() { minikube -p "$MINIKUBE_PROFILE" kubectl -- --context="$KUBE_CONTEXT" "$@"; }
cleanup() {
  if [[ -n "$pf_pid" ]]; then kill "$pf_pid" 2>/dev/null || true; wait "$pf_pid" 2>/dev/null || true; fi
  if [[ "$non_mesh_created" == true ]]; then kctl -n default delete pod "$non_mesh_pod" --wait=false >/dev/null 2>&1 || true; fi
  if [[ "$cart_probe_created" == true ]]; then kctl -n "$NAMESPACE" delete pod "$cart_probe_pod" --wait=false >/dev/null 2>&1 || true; fi
}
pass() { ((passed+=1)); printf 'PASS: %s\n' "$1"; }
fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }

jwt_preflight
jwt_refresh admin
pa_mode=$(kctl -n "$NAMESPACE" get peerauthentication -o jsonpath='{.items[0].spec.mtls.mode}')
[[ "$pa_mode" == STRICT ]] || fail "Expected STRICT, got '$pa_mode'"
pass 'PeerAuthentication is STRICT'
# A pod startup/image failure must not count as a successful security denial.
create_probe() {
  local name=$1 namespace=$2 mesh=$3 account=$4
  kctl -n "$namespace" apply -f - <<YAML
apiVersion: v1
kind: Pod
metadata:
  name: $name
  namespace: $namespace
  labels:
    sidecar.istio.io/inject: "$mesh"
spec:
  serviceAccountName: $account
  automountServiceAccountToken: false
  restartPolicy: Never
  containers:
    - name: probe
      image: curlimages/curl:8.10.1
      command: [sleep, "600"]
      resources:
        requests: {cpu: 10m, memory: 16Mi}
        limits: {cpu: 100m, memory: 64Mi}
YAML
}
# Keep JWT out of pod manifests, kubectl command arguments and logs: pass only through stdin.
probe() {
  local namespace=$1 pod=$2 method=$3 url=$4 token=${5:-}
  printf '%s\n' "$token" | kctl -n "$namespace" exec -i "$pod" -c probe -- sh -c '
    IFS= read -r token
    if [ -n "$token" ]; then
      curl -sS --connect-timeout 5 --max-time 10 -w "\n%{http_code}" -X "$1" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "$3" "$2"
    else
      curl -sS --connect-timeout 5 --max-time 10 -w "\n%{http_code}" -X "$1" "$2"
    fi
  ' sh "$method" "$url" '{"name":"Mesh must deny this admin request","price":1000,"stock":1}'
}
trap cleanup EXIT
create_probe "$non_mesh_pod" default false default
non_mesh_created=true
kctl -n default wait --for=condition=Ready "pod/$non_mesh_pod" --timeout=180s
set +e
non_mesh_result=$(probe default "$non_mesh_pod" GET "http://abl-product-service.$NAMESPACE.svc.cluster.local:8080/api/v1/products" "$ADMIN_TOKEN")
non_mesh_exit=$?
set -e
[[ "$non_mesh_exit" != 0 && "${non_mesh_result##*$'\n'}" == 000 ]] || fail 'Expected transport rejection from non-mesh pod, not an application 401/403'
pass 'Non-mesh traffic is blocked before application HTTP (consistent with STRICT/network isolation)'

create_probe "$cart_probe_pod" "$NAMESPACE" true abl-cart-service
cart_probe_created=true
kctl -n "$NAMESPACE" wait --for=condition=Ready "pod/$cart_probe_pod" --timeout=180s
jwt_refresh admin
result=$(probe "$NAMESPACE" "$cart_probe_pod" GET "http://abl-product-service.$NAMESPACE.svc.cluster.local:8080/api/v1/products" "$ADMIN_TOKEN")
[[ "${result##*$'\n'}" == 200 ]] || fail 'Mesh-allowed GET with valid JWT must return 200'
pass 'Cart identity + valid JWT can GET Product'
result=$(probe "$NAMESPACE" "$cart_probe_pod" GET "http://abl-product-service.$NAMESPACE.svc.cluster.local:8080/api/v1/products")
[[ "${result##*$'\n'}" == 401 && "$result" == *UNAUTHORIZED* ]] || fail 'Mesh-allowed GET without JWT must be rejected by backend with 401'
pass 'Workload identity alone does not replace a user JWT'
for target in product payment; do
  jwt_refresh admin
  if [[ "$target" == product ]]; then method=POST; path=/api/v1/products; else method=GET; path='/api/v2/payments?orderId=1'; fi
  # ADMIN could pass backend role/ownership checks; denial must come from Envoy RBAC.
  result=$(probe "$NAMESPACE" "$cart_probe_pod" "$method" "http://abl-$target-service.$NAMESPACE.svc.cluster.local:8080$path" "$ADMIN_TOKEN")
  [[ "${result##*$'\n'}" == 403 && "$result" == *'RBAC: access denied'* ]] || fail "Expected Envoy RBAC denial for Cart -> $method $target, not backend JWT denial"
  pass "Istio RBAC blocks Cart -> $method $target despite valid ADMIN JWT"
done

code=$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $CUSTOMER_TOKEN" "$BASE_URL/actuator/health")
[[ "$code" == 404 ]] || fail "Gateway actuator isolation expected 404, got $code"
pass 'Gateway does not expose actuator'
kctl -n "$NAMESPACE" port-forward deployment/abl-product-service 19090:9090 >/dev/null 2>&1 &
pf_pid=$!
for _ in {1..15}; do
  kill -0 "$pf_pid" 2>/dev/null || fail 'Management port-forward failed'
  code=$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:19090/actuator/health/readiness 2>/dev/null) || code=000
  [[ "$code" != 200 ]] || break
  sleep 1
done
[[ "$code" == 200 ]] || fail 'Management readiness must remain accessible without JWT'
pass 'Management readiness remains available to probes'
printf 'All %d Istio mesh security assertions passed.\n' "$passed"
