package com.collector.notification.notification.domain;

import java.util.UUID;

/** Données utiles de price.changed ; aggregateVersion départage deux événements inversés ou rejoués. */
public record PriceChange(UUID articleId, long aggregateVersion, String title, long oldPriceCents,
                          long newPriceCents, String currency) {
}
