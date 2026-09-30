package com.collector.catalogue.ping.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.ping.application.port.PingRepository;
import com.collector.catalogue.ping.domain.Ping;
import com.collector.catalogue.ping.domain.PingCreated;
import com.collector.catalogue.shared.application.port.DomainEventPublisher;

@Service
public class CreatePing {

    private final PingRepository pings;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CreatePing(PingRepository pings, DomainEventPublisher events, Clock clock) {
        this.pings = pings;
        this.events = events;
        this.clock = clock;
    }

    // Une transaction : la ligne et son événement sont enregistrés ensemble, ou pas
    // du tout. Broker arrêté : l'API répond 201, l'événement partira à son retour.
    @Transactional
    public Ping execute(String payload) {
        Ping ping = pings.save(Ping.create(payload, clock.instant()));
        events.publish(new PingCreated(ping.id(), ping.payload(), ping.createdAt()));
        return ping;
    }
}
