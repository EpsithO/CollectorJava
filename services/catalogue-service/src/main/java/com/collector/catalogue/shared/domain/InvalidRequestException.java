package com.collector.catalogue.shared.domain;

/** Requête incorrecte détectée par le domaine (400). */
public class InvalidRequestException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(Kind.INVALID_INPUT, "invalid_request", message);
    }
}
