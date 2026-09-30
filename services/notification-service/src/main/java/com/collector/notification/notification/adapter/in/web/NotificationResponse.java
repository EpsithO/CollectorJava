package com.collector.notification.notification.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.collector.notification.notification.domain.Notification;

/** Contrat d'API : le membre n'y figure pas (c'est toujours l'appelant). */
record NotificationResponse(UUID id, String type, UUID articleId, String articleTitle, long oldPriceCents,
                            long newPriceCents, String currency, boolean priceDrop, Instant createdAt,
                            Instant readAt) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.id(), "PRICE_CHANGED", n.articleId(), n.articleTitle(),
                n.oldPriceCents(), n.newPriceCents(), n.currency(), n.isPriceDrop(), n.createdAt(), n.readAt());
    }
}
