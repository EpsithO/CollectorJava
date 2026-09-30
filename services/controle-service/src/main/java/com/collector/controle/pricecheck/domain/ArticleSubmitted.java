package com.collector.controle.pricecheck.domain;

import java.util.UUID;

/** Données utiles de l'événement article.submitted : le contrôle n'a besoin de rien d'autre. */
public record ArticleSubmitted(UUID articleId, UUID sellerId, UUID categoryId, long priceCents) {
}
