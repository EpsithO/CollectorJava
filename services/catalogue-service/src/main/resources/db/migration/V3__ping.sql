-- Démonstration de la chaîne outbox -> RabbitMQ (POST /api/v1/pings).
CREATE TABLE ping (
    id          bigserial PRIMARY KEY,
    payload     text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
