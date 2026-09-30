package com.collector.catalogue.article.domain;

import java.util.UUID;

import com.collector.catalogue.shared.domain.DomainEvent;

/** article.submitted : déclenche le contrôle automatique (docs/events.md). */
public record ArticleSubmitted(UUID articleId, UUID sellerId, UUID categoryId, long priceCents,
                               String currency, int photoCount) implements DomainEvent {

    @Override
    public String type() {
        return "article.submitted";
    }
}
