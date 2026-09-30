package com.collector.notification.shared.domain;

public class NotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String what) {
        super(Kind.NOT_FOUND, "not_found", what + " not found");
    }
}
