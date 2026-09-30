package com.collector.catalogue.shared.domain;

/**
 * Le domaine exprime la NATURE de l'erreur, pas un code HTTP : c'est l'adaptateur
 * web qui traduit (ApiExceptionHandler). Le domaine reste utilisable hors HTTP
 * (consommateur RabbitMQ, batch…).
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Kind { INVALID_INPUT, NOT_FOUND, FORBIDDEN, CONFLICT, RULE_VIOLATED }

    private final Kind kind;
    private final String code;

    protected DomainException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
