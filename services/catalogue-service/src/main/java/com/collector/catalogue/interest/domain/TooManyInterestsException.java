package com.collector.catalogue.interest.domain;

import com.collector.catalogue.shared.domain.DomainException;

public class TooManyInterestsException extends DomainException {

    private static final long serialVersionUID = 1L;

    public TooManyInterestsException(int requested) {
        super(Kind.RULE_VIOLATED, "too_many_interests",
                "At most " + InterestSelection.MAX_INTERESTS + " interests, " + requested + " requested");
    }
}
