package com.collector.controle.pricecheck.domain;

/**
 * Statistiques de prix d'une catégorie (vue category_price_stats du catalogue).
 * {@code stddevCents} est null sous 30 articles : la vue refuse de conclure sur un petit échantillon.
 */
public record PriceStats(long sampleSize, double medianCents, Double stddevCents) {
}
