package com.collector.catalogue.article.domain;

import com.collector.catalogue.shared.domain.DomainException;

/** Photo présente dans le stockage mais d'un type ou d'une taille refusés. */
public class InvalidPhotoException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidPhotoException() {
        super(Kind.RULE_VIOLATED, "invalid_photo", "A photo has an unsupported type or size");
    }
}
