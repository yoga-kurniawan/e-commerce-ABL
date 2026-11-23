#!/usr/bin/env bash
# Local Minikube orchestration; manifests and Helm remain the configuration source.
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
PROFILE=ecommerce-abl
NAMESPACE=ecommerce
RELEASE=abl
SERVICES=(product cart order payment)
work_dir=''
forward_pid=''

cleanup() {
  if [[ -n "$forward_pid" ]]; then
    kill "$forward_pid" 2>/dev/null || true
    wait "$forward_pid" 2>/dev/null || true
  fi
  if [[ -n "$work_dir" ]]; then rm -rf -- "$work_dir"; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'printf "Deployment berhenti pada baris %s. Periksa error di atas; data/PVC tidak dihapus.\n" "$LINENO" >&2' ERR

die() { printf '%s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "Tool belum tersedia di PATH: $1"; }
kube() { minikube -p "$PROFILE" kubectl -- --context="$PROFILE" "$@"; }
usage() {
  cat <<'EOF'
Usage: bash scripts/deploy-local.sh COMMAND
  init                         Start Minikube, install/check Istio, label namespace
  up product|cart|order|payment [--skip-build|--only=<service>]
                               Deploy cumulative stage, databases, and ingress
  status                       Show workloads, PVC, services, and Istio proxies
  gateway                      Foreground gateway port-forward on 127.0.0.1:18080
  check                        Temporary gateway port-forward + GET checks across active services

Requires Bash, Docker, Minikube, Helm 3, istioctl; openssl for new random secrets.
init: LOCAL_CPUS=4 LOCAL_MEMORY=5120mb (only used when cluster is not running).
Optional LOCAL_CNI passes --cni=<cni> to minikube start.
Existing Secret is reused. New Secret uses .secrets/<service>-db.env if present,
otherwise a random password is generated for a new database. No secret rotation.
up builds every enabled application with a unique tag; --skip-build reuses images;
--only=<service> builds only one specified service from the stage.
No cluster/PVC deletion or automatic rollback. Local development only.
EOF
}
cluster_ready() {
  minikube status -p "$PROFILE" >/dev/null || die 'Cluster belum aktif. Jalankan init dahulu.'
  kube wait --for=condition=Ready node --all --timeout=120s
}
namespace_ready() {
  kube apply -f "$ROOT/deploy/kubernetes/namespace.yaml"
}
istio_ready() {
  kube -n istio-system rollout status deployment/istiod --timeout=180s
  kube -n istio-system rollout status deployment/istio-ingressgateway --timeout=180s
}
init_cluster() {
  need docker; need istioctl
  docker info >/dev/null
  [[ $(docker info --format '{{.OSType}}') == linux ]] || die 'Docker harus memakai Linux containers.'
  if ! minikube status -p "$PROFILE" >/dev/null 2>&1; then
    local -a start_args=(start -p "$PROFILE" --driver=docker --cpus="${LOCAL_CPUS:-4}" --memory="${LOCAL_MEMORY:-5120mb}")
    if [[ -n "${LOCAL_CNI:-}" ]]; then
      start_args+=(--cni="$LOCAL_CNI")
    fi
    minikube "${start_args[@]}"
  fi
  cluster_ready
  istioctl x precheck --context="$PROFILE"
  # Do not silently upgrade an existing control plane when CLI version changes.
  if [[ -z $(kube -n istio-system get deployment istiod --ignore-not-found -o name) ]]; then
    istioctl install --context="$PROFILE" -f "$ROOT/deploy/istio/install.yaml" -y
  fi
  istio_ready
  namespace_ready
  istioctl version --context="$PROFILE"
  printf 'Cluster dan mesh dasar siap. Lanjutkan: bash scripts/deploy-local.sh up product\n'
}
ensure_secret() {
  local service=$1 secret_name="${1}-db-credentials" keys secret_file generated_password
  if [[ -n $(kube -n "$NAMESPACE" get secret "$secret_name" --ignore-not-found -o name) ]]; then
    keys=$(kube -n "$NAMESPACE" get secret "$secret_name" -o 'go-template={{if .data.username}}username{{end}} {{if .data.password}}password{{end}}')
    [[ "$keys" == 'username password' ]] || die "Secret $secret_name harus berisi username dan password."
    printf 'Memakai Secret yang tersedia: %s\n' "$secret_name"
    return
  fi
  # Never generate credentials against an initialized volume after a Secret was lost.
  [[ -z $(kube -n "$NAMESPACE" get pvc "data-${service}-db-0" --ignore-not-found -o name) ]] ||
    die "PVC $service sudah ada tetapi Secret hilang. Pulihkan kredensial lama terlebih dahulu."
  secret_file="$ROOT/.secrets/${service}-db.env"
  if [[ -f "$secret_file" ]]; then
    need git
    git -C "$ROOT" check-ignore -q -- ".secrets/${service}-db.env" || die "File kredensial belum di-ignore: $secret_file"
  else
    need openssl
    secret_file="$work_dir/${service}-db.env"
    generated_password=$(openssl rand -hex 24)
    [[ -n "$generated_password" ]] || die 'Generator password mengembalikan nilai kosong.'
    printf 'username=%s_user\npassword=%s\n' "$service" "$generated_password" > "$secret_file"
  fi
  kube -n "$NAMESPACE" create secret generic "$secret_name" --from-env-file="$secret_file"
  keys=$(kube -n "$NAMESPACE" get secret "$secret_name" -o 'go-template={{if .data.username}}username{{end}} {{if .data.password}}password{{end}}')
  [[ "$keys" == 'username password' ]] || die "Secret $secret_name tidak lengkap; periksa file kredensial lokal."
}
deploy_stage() {
  local stage=$1 skip_build=$2 only_service=${3:-} count=0 service existing deployments tag
  local -a selected=() values_args=() image_args=()
  for service in "${SERVICES[@]}"; do
    selected+=("$service"); ((count+=1))
    [[ "$service" != "$stage" ]] || break
  done
  if [[ -n "$only_service" ]]; then
    local found=false
    for service in "${selected[@]}"; do
      if [[ "$service" == "$only_service" ]]; then found=true; break; fi
    done
    [[ "$found" == true ]] || die "Service --only=$only_service tidak termasuk dalam stage $stage."
  fi
  need helm; need istioctl
  cluster_ready
  istio_ready
  # Read all currently deployed release workloads before mutating namespace/DBs.
  deployments=$(kube -n "$NAMESPACE" get deployments -l "app.kubernetes.io/instance=$RELEASE" -o 'jsonpath={range .items[*]}{.metadata.name}{"\n"}{end}')
  for service in "${SERVICES[@]:count}"; do
    if grep -Fxq "${RELEASE}-${service}-service" <<< "$deployments"; then
      die "Stage $stage akan menonaktifkan $service yang sudah terpasang. Pilih stage yang sama atau lebih tinggi."
    fi
  done
  existing=$(helm list --kube-context "$PROFILE" -n "$NAMESPACE" --all --filter '^abl$' -q)
  if [[ -n "$existing" ]]; then
    # Preserve previous image/resource overrides during stage upgrades and re-runs.
    helm get values "$RELEASE" --kube-context "$PROFILE" -n "$NAMESPACE" -o yaml > "$work_dir/existing-values.yaml"
    values_args+=(-f "$work_dir/existing-values.yaml")
  fi
  values_args+=(-f "$ROOT/deploy/helm/ecommerce/values-minikube.yaml")
  values_args+=(-f "$ROOT/deploy/helm/ecommerce/stages/${stage}.yaml")
  if [[ "$skip_build" == false ]]; then
    need docker
    docker info >/dev/null
    tag="local-$(date -u +%Y%m%d%H%M%S)-${RANDOM}"
    for service in "${selected[@]}"; do
      if [[ -n "$only_service" && "$service" != "$only_service" ]]; then
        continue
      fi
      docker build -f "$ROOT/services/${service}-service/Dockerfile" -t "ecommerce/${service}-service:$tag" "$ROOT"
      minikube -p "$PROFILE" image load "ecommerce/${service}-service:$tag"
      image_args+=(--set-string "services.${service}.image.repository=ecommerce/${service}-service"
        --set-string "services.${service}.image.tag=$tag" --set-string "services.${service}.image.digest=")
    done
  fi
  helm lint "$ROOT/deploy/helm/ecommerce" "${values_args[@]}" "${image_args[@]}"
  helm template "$RELEASE" "$ROOT/deploy/helm/ecommerce" --namespace "$NAMESPACE" \
    "${values_args[@]}" "${image_args[@]}" > "$work_dir/applications.yaml"
  kube apply --dry-run=server -f "$work_dir/applications.yaml"
  namespace_ready
  for service in "${selected[@]}"; do
    ensure_secret "$service"
    kube apply --dry-run=server -f "$ROOT/deploy/kubernetes/${service}-db.yaml"
    kube apply -f "$ROOT/deploy/kubernetes/${service}-db.yaml"
    kube -n "$NAMESPACE" rollout status "statefulset/${service}-db" --timeout=180s
  done
  helm upgrade --install "$RELEASE" "$ROOT/deploy/helm/ecommerce" \
    --kube-context "$PROFILE" --namespace "$NAMESPACE" \
    "${values_args[@]}" "${image_args[@]}" --wait --timeout 5m
  for service in "${selected[@]}"; do
    kube -n "$NAMESPACE" rollout status "deployment/${RELEASE}-${service}-service" --timeout=180s
  done
  kube apply --dry-run=server -f "$ROOT/deploy/istio/gateway.yaml"
  kube apply -f "$ROOT/deploy/istio/gateway.yaml"
  kube -n "$NAMESPACE" delete virtualservice product-route cart-route order-route payment-route --ignore-not-found
  istioctl analyze --context="$PROFILE" -n "$NAMESPACE"
  printf 'Stage %s terpasang. Jalankan check untuk pemeriksaan via gateway; uji alur bisnis tetap diperlukan.\n' "$stage"
}
check_gateway() {
  local port='' attempt status
  local -a paths=(/api/v1/products /api/v2/products)
  need curl
  cluster_ready
  local deployments
  deployments=$(kube -n "$NAMESPACE" get deployments -l "app.kubernetes.io/instance=$RELEASE" -o 'jsonpath={range .items[*]}{.metadata.name}{"\n"}{end}')
  if grep -Fxq "${RELEASE}-order-service" <<< "$deployments"; then
    paths+=(/api/v2/orders?customerId=1)
  fi
  if grep -Fxq "${RELEASE}-payment-service" <<< "$deployments"; then
    paths+=(/api/v2/payments?orderId=1)
  fi
  minikube -p "$PROFILE" kubectl -- --context="$PROFILE" -n istio-system \
    port-forward --address 127.0.0.1 service/istio-ingressgateway :80 > "$work_dir/forward.log" 2>&1 &
  forward_pid=$!
  for ((attempt=0; attempt<30; attempt++)); do
    kill -0 "$forward_pid" 2>/dev/null || { cat "$work_dir/forward.log" >&2; die 'Port-forward gateway gagal.'; }
    port=$(sed -n 's/.*127\.0\.0\.1:\([0-9]*\) ->.*/\1/p' "$work_dir/forward.log" | head -n 1)
    [[ -z "$port" ]] || break
    sleep 1
  done
  [[ -n "$port" ]] || die 'Timeout menunggu port-forward gateway.'
  for path in "${paths[@]}"; do
    status=$(curl --fail --show-error --silent --connect-timeout 5 --max-time 20 \
      --output "$work_dir/response.json" --write-out '%{http_code}' "http://127.0.0.1:$port$path")
    [[ "$status" == 200 ]] || die "GET $path: HTTP $status, diharapkan 200."
    cat "$work_dir/response.json"
    printf '\nGET %s via gateway: berhasil\n' "$path"
  done
}

command_name=${1:-help}
stage=''
skip_build=false
only_service=''
case "$command_name" in
  help|-h|--help) usage; exit 0 ;;
  init|status|gateway|check) [[ $# == 1 ]] || die 'Argumen berlebih; gunakan --help.' ;;
  up)
    [[ $# -ge 2 ]] || die 'Gunakan: up {product|cart|order|payment} [--skip-build|--only=<service>]'
    stage="$2"
    case "$stage" in product|cart|order|payment) ;; *) die "Stage tidak dikenal: $stage" ;; esac
    shift 2
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --skip-build) skip_build=true ;;
        --only=*) only_service="${1#--only=}" ;;
        --only) shift; [[ $# -gt 0 ]] || die 'Opsi --only membutuhkan nama service'; only_service="$1" ;;
        *) die "Opsi tidak dikenal: $1" ;;
      esac
      shift
    done
    if [[ "$skip_build" == true && -n "$only_service" ]]; then
      die 'Opsi --skip-build dan --only tidak dapat digunakan bersamaan.'
    fi
    if [[ -n "$only_service" ]]; then
      case "$only_service" in product|cart|order|payment) ;; *) die "Service tidak dikenal untuk --only: $only_service" ;; esac
    fi
    ;;
  *) die "Command tidak dikenal: $command_name (gunakan --help)" ;;
esac
need minikube
umask 077
work_dir=$(mktemp -d)
case "$command_name" in
  init) init_cluster ;;
  up) deploy_stage "$stage" "$skip_build" "$only_service" ;;
  status)
    need istioctl
    cluster_ready
    kube -n "$NAMESPACE" get deployments,statefulsets,pods,pvc,services
    istioctl proxy-status --context="$PROFILE"
    ;;
  gateway)
    cluster_ready
    kube -n istio-system port-forward --address 127.0.0.1 service/istio-ingressgateway 18080:80
    ;;
  check) check_gateway ;;
esac
