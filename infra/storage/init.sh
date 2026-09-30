#!/bin/sh
# Initialisation idempotente de Garage par son API d'administration (port 3903).
# Même script pour compose, les tests d'intégration et le Job Kubernetes.
# NON VÉRIFIÉ : les chemins /v2/* suivent la référence de l'API d'administration
# de Garage v2 ; à confirmer contre la version épinglée au premier lancement.
set -eu

: "${GARAGE_ADMIN_TOKEN:?manquant}"
: "${CATALOGUE_S3_ACCESS_KEY:?manquant}"
: "${CATALOGUE_S3_SECRET_KEY:?manquant}"
ADMIN="${GARAGE_ADMIN_URL:-http://storage:3903}"
S3="${GARAGE_S3_URL:-http://storage:3900}"
BUCKET="${BUCKET:-collector-photos}"
CORS_ORIGIN="${CORS_ORIGIN:-http://localhost:5173}"

api() { # api METHOD PATH [JSON]
  curl -fsS -X "$1" -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" -H 'Content-Type: application/json' \
    ${3:+-d "$3"} "$ADMIN$2"
}

echo "Attente du nœud Garage…"
i=0
until api GET /v2/GetClusterStatus >/dev/null 2>&1; do
  i=$((i + 1)); [ "$i" -gt 60 ] && { echo "Garage injoignable" >&2; exit 1; }
  sleep 2
done

# Disposition : attribuer de la capacité au nœud s'il n'en a pas encore.
status="$(api GET /v2/GetClusterStatus)"
node_id="$(echo "$status" | jq -r '.nodes[0].id')"
if [ "$(echo "$status" | jq -r '.nodes[0].role // empty')" = "" ]; then
  api POST /v2/UpdateClusterLayout \
    "{\"roles\":[{\"id\":\"$node_id\",\"zone\":\"dc1\",\"capacity\":1073741824,\"tags\":[]}]}" >/dev/null
  version="$(api GET /v2/GetClusterLayout | jq -r '.version')"
  api POST /v2/ApplyClusterLayout "{\"version\":$((version + 1))}" >/dev/null
fi

# Clé du catalogue importée depuis le .env (pas générée : les services la connaissent).
api POST /v2/ImportKey \
  "{\"name\":\"catalogue\",\"accessKeyId\":\"$CATALOGUE_S3_ACCESS_KEY\",\"secretAccessKey\":\"$CATALOGUE_S3_SECRET_KEY\"}" \
  >/dev/null 2>&1 || echo "clé déjà importée"

# Bucket privé (alias global), droits de la clé sur CE bucket seulement.
api POST /v2/CreateBucket "{\"globalAlias\":\"$BUCKET\"}" >/dev/null 2>&1 || echo "bucket déjà créé"
bucket_id="$(api GET "/v2/GetBucketInfo?globalAlias=$BUCKET" | jq -r '.id')"
api POST /v2/AllowBucketKey \
  "{\"bucketId\":\"$bucket_id\",\"accessKeyId\":\"$CATALOGUE_S3_ACCESS_KEY\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":false}}" \
  >/dev/null

# CORS : PutBucketCors exige un propriétaire. Clé temporaire, supprimée ensuite.
tmp="$(api POST /v2/CreateKey '{"name":"cors-tmp"}')"
tmp_id="$(echo "$tmp" | jq -r '.accessKeyId')"
tmp_secret="$(echo "$tmp" | jq -r '.secretAccessKey')"
trap 'api POST "/v2/DeleteKey?id=$tmp_id" >/dev/null 2>&1 || true' EXIT
api POST /v2/AllowBucketKey \
  "{\"bucketId\":\"$bucket_id\",\"accessKeyId\":\"$tmp_id\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":true}}" >/dev/null
AWS_ACCESS_KEY_ID="$tmp_id" AWS_SECRET_ACCESS_KEY="$tmp_secret" AWS_DEFAULT_REGION=garage \
  aws --endpoint-url "$S3" s3api put-bucket-cors --bucket "$BUCKET" --cors-configuration \
  "{\"CORSRules\":[{\"AllowedOrigins\":[\"$CORS_ORIGIN\"],\"AllowedMethods\":[\"GET\",\"PUT\",\"HEAD\"],\"AllowedHeaders\":[\"*\"],\"MaxAgeSeconds\":3000}]}"

echo "Stockage initialisé (bucket $BUCKET)."
