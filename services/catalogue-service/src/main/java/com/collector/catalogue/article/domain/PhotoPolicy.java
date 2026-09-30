package com.collector.catalogue.article.domain;

import java.util.Set;

import com.collector.catalogue.shared.domain.InvalidRequestException;

/** Règles sur les photos : types acceptés, taille maximale, nombre maximal par article. */
public final class PhotoPolicy {

    public static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_PER_ARTICLE = 8;

    private PhotoPolicy() {
    }

    public static boolean isAcceptable(String contentType, long sizeBytes) {
        return contentType != null && ALLOWED_TYPES.contains(contentType.toLowerCase(java.util.Locale.ROOT))
                && sizeBytes > 0 && sizeBytes <= MAX_BYTES;
    }

    /** Vérification avant de délivrer une URL d'envoi. */
    public static void requireAcceptable(String contentType, long sizeBytes) {
        if (!isAcceptable(contentType, sizeBytes)) {
            throw new InvalidRequestException("Photo must be jpeg, png or webp, at most " + MAX_BYTES + " bytes");
        }
    }
}
