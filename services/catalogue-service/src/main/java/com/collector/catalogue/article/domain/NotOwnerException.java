package com.collector.catalogue.article.domain;

import com.collector.catalogue.shared.domain.DomainException;

/** CA-5 : un vendeur ne touche pas à l'article d'un autre. */
public class NotOwnerException extends DomainException {

    private static final long serialVersionUID = 1L;

    public NotOwnerException() {
        super(Kind.FORBIDDEN, "forbidden", "Not allowed");
    }
}
