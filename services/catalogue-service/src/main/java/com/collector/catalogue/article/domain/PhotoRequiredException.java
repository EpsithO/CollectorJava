package com.collector.catalogue.article.domain;

import com.collector.catalogue.shared.domain.DomainException;

/** CA-6 : un brouillon sans photo valide ne peut pas être soumis. */
public class PhotoRequiredException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PhotoRequiredException() {
        super(Kind.RULE_VIOLATED, "photo_required", "At least one valid photo is required");
    }
}
