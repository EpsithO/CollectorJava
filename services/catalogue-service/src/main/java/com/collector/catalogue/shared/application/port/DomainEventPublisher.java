package com.collector.catalogue.shared.application.port;

import com.collector.catalogue.shared.domain.DomainEvent;

public interface DomainEventPublisher {

    /** Appelé dans la transaction du cas d'usage : l'événement suit le sort de la donnée. */
    void publish(DomainEvent event);
}
