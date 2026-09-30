package com.collector.catalogue.shared.adapter.out.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @JdbcTypeCode(SqlTypes.JSON)            // colonne jsonb : enveloppe déjà sérialisée
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEvent() {
        // requis par JPA
    }

    public OutboxEvent(UUID eventId, String routingKey, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.routingKey = routingKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /** Enveloppe JSON déjà sérialisée (utile aux tests de contrat). */
    public String payload() {
        return payload;
    }

    // Pas de setter : la réservation (claimed_until) et la publication
    // (published_at) sont gérées par OutboxStore, en SQL, hors de JPA.
}
