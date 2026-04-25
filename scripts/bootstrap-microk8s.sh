#!/usr/bin/env bash

set -euo pipefail

MICROK8S_CHANNEL="${MICROK8S_CHANNEL:-1.33}"
TARGET_USER="${TARGET_USER:-ubuntu}"
TARGET_HOME="$(getent passwd "${TARGET_USER}" | cut -d: -f6)"

if [[ -z "${TARGET_HOME}" ]]; then
  echo "Unable to determine home directory for user ${TARGET_USER}" >&2
  exit 1
fi

sudo snap install microk8s --classic --channel="${MICROK8S_CHANNEL}"
sudo usermod -a -G microk8s "${TARGET_USER}"

sudo mkdir -p "${TARGET_HOME}/.kube"
sudo chown -R "${TARGET_USER}:${TARGET_USER}" "${TARGET_HOME}/.kube"
sudo chmod 0700 "${TARGET_HOME}/.kube"

sudo microk8s status --wait-ready
sudo microk8s enable dns hostpath-storage
sudo snap alias microk8s.kubectl kubectl

if ! grep -q "alias kubectl='microk8s kubectl'" "${TARGET_HOME}/.bashrc" 2>/dev/null; then
  echo "alias kubectl='microk8s kubectl'" | sudo tee -a "${TARGET_HOME}/.bashrc" >/dev/null
fi

cat <<EOF
MicroK8s installation is complete.

Next steps:
1. Log out and back in so the ${TARGET_USER} user picks up the microk8s group membership.
2. Run: microk8s kubectl get nodes
3. Deploy ArgoCD and the Wordsmith manifests.
EOF
