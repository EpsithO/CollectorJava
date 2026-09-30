package com.collector.notification.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Notification de l'espace personnel : le prix d'un article suivi a changé (US-029). */
public record Notification(UUID id, UUID memberId, UUID articleId, String articleTitle, long oldPriceCents,
                           long newPriceCents, String currency, long aggregateVersion, Instant createdAt,
                           Instant readAt) {

    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(articleTitle, "articleTitle");
    }

    /** Une notification non lue, pour un suiveur donné. */
    public static Notification forPriceChange(UUID memberId, PriceChange change, Instant now) {
        return new Notification(UUID.randomUUID(), memberId, change.articleId(), change.title(),
                change.oldPriceCents(), change.newPriceCents(), change.currency(), change.aggregateVersion(), now, null);
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Baisse de prix : la bonne nouvelle que l'acheteur attendait. */
    public boolean isPriceDrop() {
        return newPriceCents < oldPriceCents;
    }
}
