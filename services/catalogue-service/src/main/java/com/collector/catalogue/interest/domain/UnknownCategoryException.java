package com.collector.catalogue.interest.domain;

import java.util.Set;
import java.util.UUID;

import com.collector.catalogue.shared.domain.DomainException;

public class UnknownCategoryException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnknownCategoryException(Set<UUID> unknown) {
        super(Kind.RULE_VIOLATED, "unknown_category", "Unknown categories: " + unknown);
    }
}
