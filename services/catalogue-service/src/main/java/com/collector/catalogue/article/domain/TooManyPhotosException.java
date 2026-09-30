package com.collector.catalogue.article.domain;

import com.collector.catalogue.shared.domain.DomainException;

public class TooManyPhotosException extends DomainException {

    private static final long serialVersionUID = 1L;

    public TooManyPhotosException() {
        super(Kind.CONFLICT, "too_many_photos", "At most " + PhotoPolicy.MAX_PER_ARTICLE + " photos per article");
    }
}
