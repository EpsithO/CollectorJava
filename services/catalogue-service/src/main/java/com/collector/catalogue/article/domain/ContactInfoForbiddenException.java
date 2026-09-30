package com.collector.catalogue.article.domain;

import com.collector.catalogue.shared.domain.DomainException;

public class ContactInfoForbiddenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ContactInfoForbiddenException() {
        super(Kind.RULE_VIOLATED, "contact_info_forbidden", "Contact details are not allowed in an article");
    }
}
