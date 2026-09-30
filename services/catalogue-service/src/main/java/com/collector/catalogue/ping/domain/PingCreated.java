package com.collector.catalogue.ping.domain;

import java.time.Instant;

import com.collector.catalogue.shared.domain.DomainEvent;

public record PingCreated(long id, String payload, Instant createdAt) implements DomainEvent {

    @Override
    public String type() {
        return "ping.created";
    }
}
