package com.collector.catalogue.article.domain;

import com.collector.catalogue.shared.domain.DomainException;

public class InvalidStatusException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidStatusException(ArticleStatus current, String action) {
        super(Kind.CONFLICT, "invalid_status", "Cannot " + action + " an article in status " + current);
    }
}
