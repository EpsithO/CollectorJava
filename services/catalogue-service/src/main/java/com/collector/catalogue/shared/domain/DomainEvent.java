package com.collector.catalogue.shared.domain;

public interface DomainEvent {

    /** Clé de routage (docs/events.md). */
    String type();
}
