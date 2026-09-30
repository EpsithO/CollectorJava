package com.collector.notification.shared.domain;

/**
 * Erreur métier : le domaine dit la NATURE de l'erreur, l'adaptateur web la traduit en HTTP.
 * Volontairement dupliquée du catalogue : aucun code métier n'est partagé entre services.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Kind { NOT_FOUND, FORBIDDEN, INVALID_INPUT }

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
