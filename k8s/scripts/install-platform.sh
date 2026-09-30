#!/usr/bin/env bash
# Installe la plateforme (hors application) sur un cluster existant :
#   Traefik (Helm, redirection HTTP vers HTTPS), cert-manager (Helm), RabbitMQ Cluster Operator, metrics-server (HPA).
#
# Usage : ./k8s/scripts/install-platform.sh <minikube|kind|cloud>
#
# Versions : par défaut les plus récentes au moment de l'exécution ; le script AFFICHE ce qui est installé.
# Pour figer (recommandé après le premier déploiement réussi, règle « versions épinglées ») :
#   TRAEFIK_CHART_VERSION=… CERT_MANAGER_CHART_VERSION=… RABBITMQ_OPERATOR_VERSION=v… METRICS_SERVER_VERSION=v…
#
# Ne PAS activer l'addon « ingress » de Minikube : c'est ingress-nginx, qui n'est plus maintenu (ADR 0012).
set -euo pipefail

target="${1:?usage: install-platform.sh <minikube|kind|cloud>}"
case "$target" in
  minikube | kind | cloud) ;;
  *) echo "Cible inconnue : $target (attendu : minikube, kind ou cloud)" >&2; exit 1 ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

for tool in kubectl helm; do
  command -v "$tool" >/dev/null || { echo "$tool est introuvable (voir CLAUDE.md A5)" >&2; exit 1; }
done

version_flag() { [ -n "${1:-}" ] && printf -- '--version %s' "$1" || true; }

echo "==> Dépôts Helm"
helm repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
helm repo add jetstack https://charts.jetstack.io >/dev/null 2>&1 || true
helm repo update >/dev/null

echo "==> Traefik (Ingress, redirection HTTP vers HTTPS)"
traefik_values=(-f k8s/platform/traefik-values.yaml)
if [ "$target" = kind ]; then
  traefik_values+=(-f k8s/platform/traefik-values-kind.yaml)   # NodePorts 30080/30443, joints par extraPortMappings
fi
# shellcheck disable=SC2046
helm upgrade --install traefik traefik/traefik \
  --namespace traefik --create-namespace \
  "${traefik_values[@]}" \
  $(version_flag "${TRAEFIK_CHART_VERSION:-}") \
  --wait --timeout 5m

echo "==> cert-manager (autorité locale pour la démo et la recette)"
# shellcheck disable=SC2046
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  -f k8s/platform/cert-manager-values.yaml \
  $(version_flag "${CERT_MANAGER_CHART_VERSION:-}") \
  --wait --timeout 5m

echo "==> RabbitMQ Cluster Operator"
if [ -n "${RABBITMQ_OPERATOR_VERSION:-}" ]; then
  operator_url="https://github.com/rabbitmq/cluster-operator/releases/download/${RABBITMQ_OPERATOR_VERSION}/cluster-operator.yml"
else
  operator_url="https://github.com/rabbitmq/cluster-operator/releases/latest/download/cluster-operator.yml"
fi
kubectl apply -f "$operator_url"
kubectl -n rabbitmq-system rollout status deployment/rabbitmq-cluster-operator --timeout=5m

echo "==> metrics-server (nécessaire à l'HPA)"
case "$target" in
  minikube)
    minikube addons enable metrics-server ${MINIKUBE_PROFILE:+-p "$MINIKUBE_PROFILE"}
    ;;
  kind)
    if [ -n "${METRICS_SERVER_VERSION:-}" ]; then
      ms_url="https://github.com/kubernetes-sigs/metrics-server/releases/download/${METRICS_SERVER_VERSION}/components.yaml"
    else
      ms_url="https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml"
    fi
    kubectl apply -f "$ms_url"
    # Les kubelets de kind ont un certificat auto-signé.
    kubectl -n kube-system patch deployment metrics-server --type=json \
      -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' || true
    kubectl -n kube-system rollout status deployment/metrics-server --timeout=3m
    ;;
  cloud)
    echo "    (cluster managé : metrics-server est normalement fourni)"
    ;;
esac

echo "==> Versions installées"
helm list -A --filter '^(traefik|cert-manager)$'
kubectl -n rabbitmq-system get deployment rabbitmq-cluster-operator \
  -o jsonpath='    operateur RabbitMQ : {.spec.template.spec.containers[0].image}{"\n"}'
echo "Plateforme prête pour la cible « $target »."
