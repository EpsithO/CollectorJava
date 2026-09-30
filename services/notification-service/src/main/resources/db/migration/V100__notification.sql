-- Schéma du notification-service (US-029). Numérotation à partir de 100 : le job Flyway applique les
-- migrations du catalogue (V1 à V9x) et celles-ci dans le même historique, sans collision de version.
-- Ces tables sont la copie PROPRE du service : il ne lit jamais les tables du catalogue, il se nourrit
-- des événements (follow.changed, price.changed, interests.updated).

-- Abonnements : le changement le plus récent (changed_at) gagne, quel que soit l'ordre d'arrivée.
CREATE TABLE notification_follow (
    member_id   uuid        NOT NULL,          -- sub Keycloak
    article_id  uuid        NOT NULL,
    following   boolean     NOT NULL,
    changed_at  timestamptz NOT NULL,
    PRIMARY KEY (member_id, article_id)
);
CREATE INDEX idx_notification_follow_article ON notification_follow (article_id) WHERE following;

-- Dernière version d'agrégat vue par article : un price.changed de version inférieure ou égale
-- est ignoré (événements inversés ou rejoués, ADR 0005).
CREATE TABLE notification_article_version (
    article_id   uuid   PRIMARY KEY,
    last_version bigint NOT NULL
);

CREATE TABLE notification (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         uuid        NOT NULL,
    article_id        uuid        NOT NULL,
    article_title     text        NOT NULL,
    old_price_cents   bigint      NOT NULL,
    new_price_cents   bigint      NOT NULL,
    currency          char(3)     NOT NULL,
    aggregate_version bigint      NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    read_at           timestamptz,
    -- Filet d'idempotence : une redélivrance ne crée jamais une seconde notification.
    UNIQUE (member_id, article_id, aggregate_version)
);
CREATE INDEX idx_notification_member ON notification (member_id, created_at DESC);

-- Copie des centres d'intérêt (base de la future notification d'un nouvel article, US-026).
CREATE TABLE notification_interest (
    member_id   uuid NOT NULL,
    category_id uuid NOT NULL,
    PRIMARY KEY (member_id, category_id)
);

-- Moindre privilège : un rôle dédié, limité aux tables de ce service.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'notification_app') THEN
        GRANT USAGE ON SCHEMA public TO notification_app;
        GRANT SELECT, INSERT, UPDATE ON notification_follow, notification_article_version, notification
            TO notification_app;
        GRANT SELECT, INSERT, DELETE ON notification_interest TO notification_app;
    END IF;
END
$$;
