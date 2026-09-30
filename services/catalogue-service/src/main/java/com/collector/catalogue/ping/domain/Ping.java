package com.collector.catalogue.ping.domain;

import java.time.Instant;
import java.util.Objects;

/** Démonstration de la chaîne outbox vers RabbitMQ ; l'identifiant est attribué à l'enregistrement. */
public record Ping(Long id, String payload, Instant createdAt) {

    public Ping {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Ping create(String payload, Instant now) {
        return new Ping(null, payload, now);
    }
}
