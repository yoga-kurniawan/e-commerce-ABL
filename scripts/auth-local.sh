#!/usr/bin/env bash
# Provision the local identity provider without putting credentials in Git/output.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
kube() { minikube -p ecommerce-abl kubectl -- --context=ecommerce-abl "$@"; }
case "${1:-help}" in
  up)
    kube create namespace identity --dry-run=client -o yaml | kube apply -f -
    if ! kube -n identity get secret ecommerce-idp-credentials >/dev/null 2>&1; then
      if [[ -n $(kube -n identity get pvc ecommerce-idp-data --ignore-not-found -o name) ]]; then
        echo 'PVC sudah ada tetapi Secret hilang; pulihkan kredensial lama.' >&2; exit 1
      fi
      python3 - <<'PY'
from pathlib import Path
import os, secrets
p=Path('.secrets/auth.env'); p.parent.mkdir(exist_ok=True,mode=0o700)
if not p.exists():
    fd=os.open(p, os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    values={'KC_BOOTSTRAP_ADMIN_USERNAME':'bootstrap-admin','KC_BOOTSTRAP_ADMIN_PASSWORD':secrets.token_hex(24),
            'CUSTOMER_A_PASSWORD':secrets.token_hex(24),'CUSTOMER_B_PASSWORD':secrets.token_hex(24),'API_ADMIN_PASSWORD':secrets.token_hex(24)}
    with os.fdopen(fd,'w') as f:
        for k,v in values.items(): f.write(f'{k}={v}\n')
PY
      kube -n identity create secret generic ecommerce-idp-credentials --from-env-file=.secrets/auth.env
    fi
    kube -n identity create configmap ecommerce-idp-realm --from-file=realm.json=deploy/auth/realm.json --from-file=user-profile.json=deploy/auth/user-profile.json --dry-run=client -o yaml | kube apply -f -
    kube apply -f deploy/auth/keycloak.yaml
    kube -n identity rollout status deployment/ecommerce-idp --timeout=300s
    # Disable unmanaged profile attributes; customer_id is writable only by realm admins.
    kube -n identity exec -i deployment/ecommerce-idp -- sh -c '
      cat > /tmp/user-profile.json
      /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" --config /tmp/kcadm.config >/dev/null
      /opt/keycloak/bin/kcadm.sh update users/profile -r ecommerce -f /tmp/user-profile.json --config /tmp/kcadm.config
    ' < deploy/auth/user-profile.json
    echo 'Identity provider siap. Password lokal: .secrets/auth.env (jangan dikirim/commit).'
    ;;
  gateway) kube -n identity port-forward --address 127.0.0.1 service/ecommerce-idp 18090:8080 ;;
  *) echo 'Usage: bash scripts/auth-local.sh up | gateway'; exit 1 ;;
esac
