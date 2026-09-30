-- Outbox transactionnelle : l'événement est écrit dans la même transaction que
-- la donnée métier, puis publié sur RabbitMQ par le relais (réservation par bail).
CREATE TABLE outbox_event (
    id            bigserial PRIMARY KEY,
    event_id      uuid NOT NULL UNIQUE,
    routing_key   text NOT NULL,
    payload       jsonb NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    -- Bail de réservation : un autre réplica ne reprend pas un lot en cours d'envoi.
    claimed_until timestamptz,
    published_at  timestamptz
);

-- Le relais ne lit que les événements en attente : index partiel, petit quelle
-- que soit la taille de l'historique.
CREATE INDEX idx_outbox_pending ON outbox_event (id) WHERE published_at IS NULL;
