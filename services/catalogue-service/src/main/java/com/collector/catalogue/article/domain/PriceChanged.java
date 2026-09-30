package com.collector.catalogue.article.domain;

import java.util.UUID;

import com.collector.catalogue.shared.domain.DomainEvent;

/**
 * price.changed : consommé par l'anti-fraude et la notification (CA-4). Porte
 * aggregateVersion : deux événements reçus inversés se départagent par la version.
 * Le titre évite au notification-service de rappeler le catalogue pour écrire son message.
 */
public record PriceChanged(UUID articleId, long aggregateVersion, UUID sellerId, UUID categoryId, String title,
                           long oldPriceCents, long newPriceCents, String currency) implements DomainEvent {

    @Override
    public String type() {
        return "price.changed";
    }
}
