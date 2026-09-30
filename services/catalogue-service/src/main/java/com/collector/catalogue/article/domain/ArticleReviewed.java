package com.collector.catalogue.article.domain;

import java.util.UUID;

import com.collector.catalogue.shared.domain.DomainEvent;

/**
 * article.reviewed : décision d'un administrateur sur un article en revue (US-033).
 * {@code decision} vaut PUBLIE ou REJETE ; {@code reviewedBy} est le sub Keycloak de l'admin.
 */
public record ArticleReviewed(UUID articleId, UUID sellerId, String decision, String reason,
                              UUID reviewedBy) implements DomainEvent {

    @Override
    public String type() {
        return "article.reviewed";
    }
}
