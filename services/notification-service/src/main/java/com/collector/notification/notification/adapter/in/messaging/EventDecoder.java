package com.collector.notification.notification.adapter.in.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import com.collector.messaging.EventSchemas;
import com.collector.notification.notification.domain.FollowChange;
import com.collector.notification.notification.domain.PriceChange;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Décode les messages entrants : enveloppe et schéma d'abord, on ne fait confiance qu'à un message
 * conforme. Un message illisible est rejeté sans remise en file (lettres mortes directes) : le rejouer
 * ne le réparerait pas.
 */
@Component
class EventDecoder {

    private final JsonMapper json;

    EventDecoder(JsonMapper json) {
        this.json = json;
    }

    PriceChange priceChange(Message message) {
        JsonNode data = data("price.changed", message);
        return new PriceChange(uuid(data, "article_id"), data.path("aggregate_version").asLong(),
                data.path("title").asString(), data.path("old_price_cents").asLong(),
                data.path("new_price_cents").asLong(), data.path("currency").asString());
    }

    FollowChange followChange(Message message) {
        JsonNode data = data("follow.changed", message);
        return new FollowChange(uuid(data, "member_id"), uuid(data, "article_id"),
                data.path("following").asBoolean(), Instant.parse(data.path("changed_at").asString()));
    }

    record Interests(UUID memberId, Set<UUID> categoryIds) {
    }

    Interests interests(Message message) {
        JsonNode data = data("interests.updated", message);
        Set<UUID> categories = new HashSet<>();
        data.path("category_ids").forEach(id -> categories.add(UUID.fromString(id.asString())));
        return new Interests(uuid(data, "member_id"), categories);
    }

    private JsonNode data(String type, Message message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            List<String> errors = EventSchemas.validate(type, 1, body);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(errors.toString());
            }
            return json.readTree(body).path("data");
        } catch (RuntimeException e) {
            throw new AmqpRejectAndDontRequeueException(type + " illisible ou non conforme", e);
        }
    }

    private static UUID uuid(JsonNode data, String field) {
        return UUID.fromString(data.path(field).asString());
    }
}
