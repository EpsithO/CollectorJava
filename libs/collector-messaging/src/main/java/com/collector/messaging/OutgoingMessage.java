package com.collector.messaging;

import java.util.UUID;

/** Message prêt à partir : enveloppe déjà sérialisée en JSON. */
public record OutgoingMessage(UUID eventId, String routingKey, String json) {
}
