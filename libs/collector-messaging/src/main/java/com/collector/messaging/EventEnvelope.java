package com.collector.messaging;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Enveloppe commune de tous les événements (champs en snake_case dans le JSON).
 *
 * <p>Les annotations {@code @JsonProperty} rendent le nommage indépendant de la
 * configuration Jackson de chaque service : le contrat ne dépend pas d'un réglage.
 */
public record EventEnvelope<T>(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") int version,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("data") T data) {

    public static <T> EventEnvelope<T> of(String type, T data, Instant now) {
        return new EventEnvelope<>(UUID.randomUUID(), type, 1, now, data);
    }
}
