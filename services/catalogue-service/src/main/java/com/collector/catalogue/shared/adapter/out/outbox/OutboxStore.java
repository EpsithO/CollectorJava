package com.collector.catalogue.shared.adapter.out.outbox;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.collector.messaging.OutgoingMessage;

/** Accès du relais, par requêtes unitaires (autocommit) : jamais de transaction longue. */
@Component
class OutboxStore {

    private final JdbcClient jdbc;

    OutboxStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Réservation par BAIL : une seule instruction, donc atomique et brève.
    // SKIP LOCKED évite que deux réplicas se disputent les mêmes lignes ; le bail
    // (claimed_until) évite qu'un autre réplica reprenne un lot en cours d'envoi.
    // Si le pod meurt, le bail expire et un autre réplica reprend le lot.
    List<OutgoingMessage> claim(int limit, Duration lease) {
        return jdbc.sql("""
                    UPDATE outbox_event
                    SET claimed_until = now() + make_interval(secs => :lease)
                    WHERE id IN (
                        SELECT id FROM outbox_event
                        WHERE published_at IS NULL
                          AND (claimed_until IS NULL OR claimed_until < now())
                        ORDER BY id
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED)
                    RETURNING event_id, routing_key, payload::text AS payload
                    """)
                .param("lease", lease.toSeconds())
                .param("limit", limit)
                .query((rs, row) -> new OutgoingMessage(rs.getObject("event_id", UUID.class),
                        rs.getString("routing_key"), rs.getString("payload")))
                .list();
    }

    void markPublished(Collection<UUID> eventIds, Instant at) {
        if (eventIds.isEmpty()) {
            return;                              // « IN () » est invalide en SQL
        }
        jdbc.sql("UPDATE outbox_event SET published_at = :at WHERE event_id IN (:ids)")
                .param("at", Timestamp.from(at))
                .param("ids", eventIds)
                .update();
    }

    long pendingCount() {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL")
                .query(Long.class)
                .single();
    }
}
