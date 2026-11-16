#!/usr/bin/env bash
# Orchestration regression checks with command doubles; never contacts a cluster.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
mkdir -p "$scratch/bin" "$scratch/repo/scripts"
cp "$ROOT/scripts/deploy-local.sh" "$scratch/repo/scripts/"
export MOCK_LOG="$scratch/commands.log"
export PATH="$scratch/bin:$PATH"

cat > "$scratch/bin/minikube" <<'STUB'
#!/usr/bin/env bash
printf 'minikube %s\n' "$*" >> "$MOCK_LOG"
case " $* " in
  *' port-forward '*)
    [[ ${MOCK_FORWARD_FAIL:-false} != true ]] || exit 1
    echo 'Forwarding from 127.0.0.1:19191 -> 8080'
    echo "FORWARD_PID=$$" >> "$MOCK_LOG"
    while :; do sleep 1; done
    ;;
  *' get deployments '*) printf '%s\n' "${MOCK_DEPLOYMENTS:-}" ;;
  *' get secret '*go-template*) printf '%s\n' "${MOCK_SECRET_KEYS:-username password}" ;;
  *' get secret '*) [[ ${MOCK_MISSING_SECRET:-false} == true ]] || echo secret/existing ;;
  *' get pvc '*) [[ ${MOCK_EXISTING_PVC:-false} != true ]] || echo persistentvolumeclaim/existing ;;
  *' rollout status '*) [[ ${MOCK_ROLLOUT_FAIL:-false} != true ]] || exit 1 ;;
  *' get deployment istiod '*) echo deployment.apps/istiod ;;
esac
STUB
cat > "$scratch/bin/helm" <<'STUB'
#!/usr/bin/env bash
printf 'helm %s\n' "$*" >> "$MOCK_LOG"
case "$1" in
  list) echo abl ;;
  get) printf 'services:\n  product:\n    image:\n      tag: previous-tested-image\n' ;;
  lint) [[ ${MOCK_LINT_FAIL:-false} != true ]] || exit 1 ;;
  template) printf 'kind: List\nitems: []\n' ;;
  upgrade)
    for ((i=1; i<=$#; i++)); do
      if [[ ${!i} == */existing-values.yaml ]]; then
        grep -q previous-tested-image "${!i}" || exit 1
        echo 'preserved previous image override' >> "$MOCK_LOG"
      fi
    done
    ;;
esac
STUB
cat > "$scratch/bin/docker" <<'STUB'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "$MOCK_LOG"
[[ " $* " != *' --format '* ]] || echo linux
exit 0
STUB
cat > "$scratch/bin/istioctl" <<'STUB'
#!/usr/bin/env bash
printf 'istioctl %s\n' "$*" >> "$MOCK_LOG"
STUB
cat > "$scratch/bin/openssl" <<'STUB'
#!/usr/bin/env bash
echo 'random-test-password-never-log'
STUB
cat > "$scratch/bin/curl" <<'STUB'
#!/usr/bin/env bash
printf 'curl %s\n' "$*" >> "$MOCK_LOG"
while [[ $# -gt 0 ]]; do
  if [[ "$1" == --output ]]; then printf '[]' > "$2"; shift; fi
  shift
done
printf '%s' "${MOCK_HTTP_STATUS:-200}"
STUB
chmod +x "$scratch/bin/"*

passed=0
run_script() {
  : > "$MOCK_LOG"
  bash "$scratch/repo/scripts/deploy-local.sh" "$@" > "$scratch/output" 2>&1
}
ok() { ((passed+=1)); printf 'PASS %s\n' "$1"; }
fail() { cat "$scratch/output" >&2; echo "FAIL $*" >&2; exit 1; }
contains() { grep -Fq -- "$1" "$MOCK_LOG" || fail "Missing command: $1"; }
absent() { if grep -Fq -- "$1" "$MOCK_LOG"; then fail "Unexpected command: $1"; fi; }

run_script --help || fail help
[[ ! -s "$MOCK_LOG" ]] || fail 'help contacted tools'
ok 'help works without infrastructure'

if run_script up typo; then fail 'invalid stage accepted'; fi
[[ ! -s "$MOCK_LOG" ]] || fail 'invalid stage contacted tools'
ok 'invalid stage rejected before infrastructure access'

export MOCK_DEPLOYMENTS=abl-payment-service
if run_script up product --skip-build; then fail 'downgrade accepted'; fi
absent 'helm upgrade'; absent ' apply '; absent 'docker build'
ok 'stage downgrade rejected before mutation'
unset MOCK_DEPLOYMENTS

run_script up cart --skip-build || fail 'cumulative cart'
contains 'statefulset/product-db'; contains 'statefulset/cart-db'
contains 'stages/cart.yaml'; contains 'preserved previous image override'
contains 'istio/cart-route.yaml'
absent 'statefulset/order-db'; absent 'docker build'; absent 'create secret'
ok 'cumulative stage preserves prior values and existing secrets'

export MOCK_LINT_FAIL=true
if run_script up product --skip-build; then fail 'lint failure ignored'; fi
absent 'helm upgrade'; absent ' apply '
ok 'lint failure stops before cluster writes'
unset MOCK_LINT_FAIL

export MOCK_SECRET_KEYS=username
if run_script up product --skip-build; then fail 'incomplete secret accepted'; fi
absent 'helm upgrade'; absent 'create secret'
ok 'incomplete existing secret is rejected without rotation'
unset MOCK_SECRET_KEYS

export MOCK_MISSING_SECRET=true MOCK_EXISTING_PVC=true
if run_script up product --skip-build; then fail 'lost secret on existing PVC accepted'; fi
absent 'create secret'; absent 'helm upgrade'
ok 'existing PVC blocks accidental credential regeneration'
unset MOCK_EXISTING_PVC

run_script up product --skip-build || fail 'fresh secret'
contains 'create secret generic product-db-credentials'
if grep -q random-test-password-never-log "$MOCK_LOG" "$scratch/output"; then fail 'credential leaked'; fi
ok 'new database receives a secret without logging its password'
unset MOCK_MISSING_SECRET

export MOCK_ROLLOUT_FAIL=true
if run_script up product --skip-build; then fail 'rollout failure ignored'; fi
absent 'helm upgrade'; absent 'istio/product-route.yaml'
ok 'database rollout failure stops application and ingress deployment'
unset MOCK_ROLLOUT_FAIL

run_script up product || fail build
contains 'docker build'; contains 'image load ecommerce/product-service:local-'
contains 'services.product.image.digest='
ok 'build uses a new image tag and clears a previous digest override'

run_script init || fail init
absent 'istioctl install'
contains 'namespace.yaml'
ok 'init retains the existing Istio installation'

run_script check || fail check
contains 'http://127.0.0.1:19191/api/v1/products'
child_pid=$(sed -n 's/^FORWARD_PID=//p' "$MOCK_LOG")
if kill -0 "$child_pid" 2>/dev/null; then fail 'temporary forward left running'; fi
ok 'gateway GET check uses an ephemeral port and cleans up forwarding'

export MOCK_HTTP_STATUS=302
if run_script check; then fail 'HTTP redirect accepted as success'; fi
ok 'gateway check requires exactly HTTP 200'
unset MOCK_HTTP_STATUS

export MOCK_FORWARD_FAIL=true
if run_script check; then fail 'failed forwarding ignored'; fi
absent 'curl '
ok 'gateway check stops when port-forward exits'
unset MOCK_FORWARD_FAIL

printf '%s orchestration checks passed (command doubles; no real deployment).\n' "$passed"
