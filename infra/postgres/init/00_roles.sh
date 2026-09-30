#!/bin/sh
# Rôles applicatifs à moindre privilège, un par service. Les GRANT sont dans les migrations Flyway
# (V4__grants.sql pour le catalogue et le contrôle, V100 pour la notification).
# Pas de "set -eu" : l'entrypoint postgres *source* les scripts non exécutables.
: "${CATALOGUE_DB_PASSWORD:?CATALOGUE_DB_PASSWORD manquant}"
: "${CONTROLE_DB_PASSWORD:?CONTROLE_DB_PASSWORD manquant}"
: "${NOTIFICATION_DB_PASSWORD:?NOTIFICATION_DB_PASSWORD manquant}"
: "${MONITORING_DB_PASSWORD:?MONITORING_DB_PASSWORD manquant}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v catalogue_pw="$CATALOGUE_DB_PASSWORD" \
     -v controle_pw="$CONTROLE_DB_PASSWORD" \
     -v notification_pw="$NOTIFICATION_DB_PASSWORD" \
     -v monitoring_pw="$MONITORING_DB_PASSWORD" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE ROLE catalogue_app     LOGIN PASSWORD :'catalogue_pw';
CREATE ROLE controle_app      LOGIN PASSWORD :'controle_pw';
CREATE ROLE notification_app  LOGIN PASSWORD :'notification_pw';
CREATE ROLE monitoring        LOGIN PASSWORD :'monitoring_pw';
GRANT pg_monitor TO monitoring;
GRANT CONNECT ON DATABASE :"DBNAME" TO catalogue_app, controle_app, notification_app;
SQL
