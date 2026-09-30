package com.collector.catalogue.article.domain;

import java.util.Objects;
import java.util.UUID;

/** Référence à un objet du stockage : jamais le binaire. */
public record Photo(UUID id, String storageKey, String contentType, long sizeBytes, PhotoStatus status) {

    public Photo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(status, "status");
    }

    /** Clé d'objet choisie par le serveur : articles/{article_id}/{photo_id}. */
    public static Photo pending(UUID articleId, String contentType, long sizeBytes) {
        UUID id = UUID.randomUUID();
        return new Photo(id, "articles/" + articleId + "/" + id, contentType, sizeBytes, PhotoStatus.EN_ATTENTE);
    }

    public boolean isValidated() {
        return status == PhotoStatus.VALIDEE;
    }

    public Photo validated() {
        return new Photo(id, storageKey, contentType, sizeBytes, PhotoStatus.VALIDEE);
    }
}
