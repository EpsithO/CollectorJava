package com.collector.catalogue.shared.adapter.out.outbox;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.shared.application.port.DomainEventPublisher;
import com.collector.catalogue.shared.domain.DomainEvent;
import com.collector.messaging.EventEnvelope;
import com.collector.messaging.EventSchemas;

import tools.jackson.databind.json.JsonMapper;

/** Implémente le port par l'outbox : l'événement est écrit avec la donnée. */
@Component
class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    OutboxDomainEventPublisher(OutboxEventRepository outbox, JsonMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    // MANDATORY : refuse d'écrire un événement hors d'une transaction métier.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        EventEnvelope<DomainEvent> envelope = EventEnvelope.of(event.type(), event, clock.instant());
        String body = json.writeValueAsString(envelope);
        // Contrat d'abord : un événement non conforme ne quitte jamais le service
        // (l'exception annule aussi la donnée métier, qui serait sinon orpheline).
        EventSchemas.requireValid(event.type(), envelope.version(), body);
        outbox.save(new OutboxEvent(envelope.eventId(), event.type(), body, envelope.occurredAt()));
    }
}
