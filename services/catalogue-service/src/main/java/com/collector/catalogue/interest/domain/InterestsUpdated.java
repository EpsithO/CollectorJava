package com.collector.catalogue.interest.domain;

import java.util.List;
import java.util.UUID;

import com.collector.catalogue.shared.domain.DomainEvent;

public record InterestsUpdated(UUID memberId, List<UUID> categoryIds) implements DomainEvent {

    @Override
    public String type() {
        return "interests.updated";
    }
}
