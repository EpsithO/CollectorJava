#!/usr/bin/env bash
# Déploie un overlay Kustomize : secrets, ConfigMaps, images, application, attente des rollouts.
#
# Usage : ./k8s/scripts/deploy.sh <minikube|ci|sandbox|cloud>
#
# Secrets (jamais dans le dépôt, jamais de valeur par défaut) :
#   1. .env à la racine, s'il existe (modèle : .env.example) ;
#   2. sinon le Secret collector-secrets déjà présent dans le cluster est CONSERVÉ (jamais régénéré) ;
#   3. sinon, en CI seulement (variable CI ou overlay ci), des valeurs aléatoires sont générées et écrites dans
#      k8s/.generated-secrets.env (ignoré par git) pour que les tests d'acceptation puissent se connecter.
#
# Images :
#   - CATALOGUE_IMAGE, CONTROLE_IMAGE, NOTIFICATION_IMAGE (dépôt@sha256:…) : déployées telles quelles, par
#     digest (chaîne de confiance : le pipeline les a vérifiées avec cosign avant d'appeler ce script) ;
#   - absentes : construites localement puis chargées dans Minikube ou kind (démo, recette locale, nocturne) ;
#   - overlay cloud : les quatre variables (dont STORAGE_INIT_IMAGE) sont OBLIGATOIRES.
#   - GHCR_TOKEN (et GHCR_USER) : crée le secret de pull du registre GHCR pour le ServiceAccount par défaut.
set -euo pipefail

overlay="${1:?usage: deploy.sh <minikube|ci|sandbox|cloud>}"
case "$overlay" in
  minikube | ci | sandbox | cloud) ;;
  *) echo "Overlay inconnu : $overlay (attendu : minikube, ci, sandbox ou cloud)" >&2; exit 1 ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
ns=collector
k() { kubectl -n "$ns" "$@"; }
rand() { openssl rand -hex "$1"; }

command -v kubectl >/dev/null || { echo "kubectl est introuvable" >&2; exit 1; }

on_error() {
  echo "ÉCHEC du déploiement : état du namespace $ns" >&2
  kubectl -n "$ns" get pods,jobs 2>&1 | tail -40 >&2 || true
}
trap on_error ERR

# --- 0. Namespace -------------------------------------------------------------------------------------------
echo "==> Namespace"
kubectl apply -k k8s/base/namespace
until kubectl -n "$ns" get serviceaccount default >/dev/null 2>&1; do sleep 1; done

# --- 1. Overlay sandbox : RabbitMQ et PerfTest seulement -----------------------------------------------------------
if [ "$overlay" = sandbox ]; then
  echo "==> Bac à sable RabbitMQ"
  k delete job perftest --ignore-not-found
  kubectl apply -k k8s/overlays/sandbox
  k wait --for=condition=AllReplicasReady rabbitmqcluster/rabbitmq --timeout=10m
  echo "Cluster RabbitMQ prêt. Mesure : kubectl -n $ns logs -f job/perftest"
  exit 0
fi

# --- 2. Secret collector-secrets --------------------------------------------------------------------------------------
ensure_secrets() {
  local have_env=""
  if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
    have_env=1
  fi

  if [ -z "$have_env" ]; then
    if k get secret collector-secrets >/dev/null 2>&1; then
      echo "    Secret collector-secrets existant conservé (aucun .env)"
      return 0
    fi
    if [ "$overlay" != ci ] && [ -z "${CI:-}" ]; then
      echo "Aucun .env : copier .env.example en .env et le remplir (voir CLAUDE.md A2-3)." >&2
      exit 1
    fi
    echo "    Génération de valeurs aléatoires (CI)"
    POSTGRES_PASSWORD="$(rand 16)"
    CATALOGUE_DB_PASSWORD="$(rand 16)"
    CONTROLE_DB_PASSWORD="$(rand 16)"
    NOTIFICATION_DB_PASSWORD="$(rand 16)"
    MONITORING_DB_PASSWORD="$(rand 16)"
    KEYCLOAK_ADMIN_PASSWORD="$(rand 16)"
    KC_TEST_USER_PASSWORD="$(rand 16)"
    GARAGE_RPC_SECRET="$(rand 32)"                  # 64 caractères hexadécimaux
    GARAGE_ADMIN_TOKEN="$(rand 24)"
    CATALOGUE_S3_ACCESS_KEY="GK$(rand 12)"          # format Garage : GK + 24 hexadécimaux
    CATALOGUE_S3_SECRET_KEY="$(rand 32)"            # 64 caractères hexadécimaux
    GRAFANA_ADMIN_PASSWORD="$(rand 16)"
    umask 077
    {
      echo "KC_TEST_USER_PASSWORD=$KC_TEST_USER_PASSWORD"
      echo "KEYCLOAK_ADMIN_PASSWORD=$KEYCLOAK_ADMIN_PASSWORD"
      echo "GRAFANA_ADMIN_PASSWORD=$GRAFANA_ADMIN_PASSWORD"
    } > k8s/.generated-secrets.env
  fi

  local missing=()
  for var in POSTGRES_PASSWORD CATALOGUE_DB_PASSWORD CONTROLE_DB_PASSWORD NOTIFICATION_DB_PASSWORD \
             MONITORING_DB_PASSWORD KEYCLOAK_ADMIN_PASSWORD KC_TEST_USER_PASSWORD GARAGE_RPC_SECRET \
             GARAGE_ADMIN_TOKEN CATALOGUE_S3_ACCESS_KEY CATALOGUE_S3_SECRET_KEY GRAFANA_ADMIN_PASSWORD; do
    [ -n "${!var:-}" ] || missing+=("$var")
  done
  if [ "${#missing[@]}" -gt 0 ]; then
    echo "Variables manquantes (pas de valeur par défaut) : ${missing[*]}" >&2
    exit 1
  fi

  kubectl -n "$ns" create secret generic collector-secrets \
    --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
    --from-literal=CATALOGUE_DB_PASSWORD="$CATALOGUE_DB_PASSWORD" \
    --from-literal=CONTROLE_DB_PASSWORD="$CONTROLE_DB_PASSWORD" \
    --from-literal=NOTIFICATION_DB_PASSWORD="$NOTIFICATION_DB_PASSWORD" \
    --from-literal=MONITORING_DB_PASSWORD="$MONITORING_DB_PASSWORD" \
    --from-literal=KEYCLOAK_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" \
    --from-literal=KC_TEST_USER_PASSWORD="$KC_TEST_USER_PASSWORD" \
    --from-literal=GARAGE_RPC_SECRET="$GARAGE_RPC_SECRET" \
    --from-literal=GARAGE_ADMIN_TOKEN="$GARAGE_ADMIN_TOKEN" \
    --from-literal=CATALOGUE_S3_ACCESS_KEY="$CATALOGUE_S3_ACCESS_KEY" \
    --from-literal=CATALOGUE_S3_SECRET_KEY="$CATALOGUE_S3_SECRET_KEY" \
    --from-literal=GRAFANA_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" \
    --dry-run=client -o yaml | kubectl apply -f -
}
echo "==> Secret collector-secrets"
ensure_secrets

# --- 3. ConfigMaps (une seule source de vérité : les fichiers du dépôt) ------------------------------------------------
cm() { # cm <nom> <options --from-file…>
  local name="$1"; shift
  kubectl -n "$ns" create configmap "$name" "$@" --dry-run=client -o yaml | kubectl apply -f -
}
echo "==> ConfigMaps"
cm keycloak-realm                  --from-file=collector-realm.json=infra/keycloak/collector-realm.json
cm postgres-init                   --from-file=infra/postgres/init
cm garage-config                   --from-file=garage.toml=infra/storage/garage.toml
cm prometheus-rules                --from-file=rules.yml=infra/prometheus/rules.yml
cm grafana-datasources             --from-file=infra/grafana/provisioning/datasources
cm flyway-migrations-catalogue     --from-file=services/catalogue-service/src/main/resources/db/migration
cm flyway-migrations-notification  --from-file=services/notification-service/src/main/resources/db/migration
cm flyway-seed                     --from-file=services/catalogue-service/src/main/resources/db/seed

# --- 4. Images ------------------------------------------------------------------------------------------------------------
context="$(kubectl config current-context)"
build_and_load() { # build_and_load <nom:tag> <contexte> [options docker build…]
  local name="$1" dir="$2"; shift 2
  case "$context" in
    kind-*)
      docker build "$@" -t "$name" "$dir"
      kind load docker-image "$name" --name "${KIND_CLUSTER:-${context#kind-}}"
      ;;
    *)
      command -v minikube >/dev/null || { echo "Ni kind ni minikube : fournir ${name} par une variable *_IMAGE" >&2; exit 1; }
      minikube ${MINIKUBE_PROFILE:+-p "$MINIKUBE_PROFILE"} image build "$@" -t "$name" "$dir"
      ;;
  esac
}

# Pas de tableau associatif : bash 3.2 (macOS) n'en a pas.
image_var() {
  case "$1" in
    catalogue-service) echo CATALOGUE_IMAGE ;;
    controle-service) echo CONTROLE_IMAGE ;;
    notification-service) echo NOTIFICATION_IMAGE ;;
  esac
}
echo "==> Images"
for service in catalogue-service controle-service notification-service; do
  var="$(image_var "$service")"
  if [ -n "${!var:-}" ]; then
    echo "    $service : ${!var}"
  elif [ "$overlay" = cloud ]; then
    echo "$var est obligatoire pour l'overlay cloud (dépôt@sha256:… signé)" >&2; exit 1
  else
    echo "    $service : construction locale"
    build_and_load "collector/$service:dev" . --build-arg "SERVICE=$service"
  fi
done
# Image d'initialisation du stockage (alpine + curl + jq + aws-cli, infra/storage/Dockerfile).
if [ -n "${STORAGE_INIT_IMAGE:-}" ]; then
  echo "    storage-init : $STORAGE_INIT_IMAGE"
elif [ "$overlay" = cloud ]; then
  echo "STORAGE_INIT_IMAGE est obligatoire pour l'overlay cloud" >&2; exit 1
else
  echo "    storage-init : construction locale"
  build_and_load collector/storage-init:dev infra/storage
fi

# Accès au registre GHCR (images privées) pour le ServiceAccount par défaut.
if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "==> Secret de pull GHCR"
  kubectl -n "$ns" create secret docker-registry ghcr-pull \
    --docker-server=ghcr.io \
    --docker-username="${GHCR_USER:-${GITHUB_ACTOR:-token}}" \
    --docker-password="$GHCR_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f -
  kubectl -n "$ns" patch serviceaccount default -p '{"imagePullSecrets":[{"name":"ghcr-pull"}]}'
fi

# --- 5. Kustomization temporaire : références d'images (par digest) ---------------------------------------------------
gen="$(mktemp -d "$root/k8s/.generated-XXXXXX")"
trap 'rm -rf "$gen"' EXIT
{
  echo "apiVersion: kustomize.config.k8s.io/v1beta1"
  echo "kind: Kustomization"
  echo "resources:"
  echo "  - ../overlays/$overlay"
  echo "images:"
  for pair in "collector/catalogue-service:CATALOGUE_IMAGE" "collector/controle-service:CONTROLE_IMAGE" \
              "collector/notification-service:NOTIFICATION_IMAGE" "collector/storage-init:STORAGE_INIT_IMAGE"; do
    name="${pair%%:*}"
    var="${pair##*:}"
    ref="${!var:-}"
    [ -n "$ref" ] || continue
    echo "  - name: $name"
    if [[ "$ref" == *@* ]]; then
      echo "    newName: ${ref%@*}"
      echo "    digest: ${ref#*@}"
    else
      echo "    newName: ${ref%:*}"
      echo "    newTag: ${ref##*:}"
    fi
  done
} > "$gen/kustomization.yaml"
# Sans aucune image à remplacer, « images: » resterait vide : on le retire.
if [ "$(tail -n 1 "$gen/kustomization.yaml")" = "images:" ]; then
  sed -i.bak '$ d' "$gen/kustomization.yaml" && rm -f "$gen/kustomization.yaml.bak"
fi

# --- 6. Application ---------------------------------------------------------------------------------------------------------
echo "==> Application de l'overlay $overlay"
k delete job flyway storage-init --ignore-not-found      # les Jobs sont immuables : recréés à chaque déploiement
kubectl apply -k "$gen"

# --- 7. Attente des rollouts ---------------------------------------------------------------------------------------------------
echo "==> Attente : PostgreSQL, RabbitMQ, migrations, stockage, services"
k rollout status statefulset/postgres --timeout=5m
k rollout status statefulset/storage --timeout=5m
k wait --for=condition=AllReplicasReady rabbitmqcluster/rabbitmq --timeout=10m
k wait --for=condition=complete job/flyway --timeout=10m
k wait --for=condition=complete job/storage-init --timeout=10m
for deployment in keycloak catalogue controle notification prometheus grafana postgres-exporter; do
  k rollout status "deployment/$deployment" --timeout=10m
done

echo
echo "Déploiement terminé (overlay $overlay)."
k get pods,hpa
