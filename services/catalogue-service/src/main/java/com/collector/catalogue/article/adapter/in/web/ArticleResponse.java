package com.collector.catalogue.article.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.collector.catalogue.article.application.ArticleView;
import com.collector.catalogue.article.domain.Article;

/**
 * Le contrat d'API ne suit ni le domaine ni le schéma. Le score d'anomalie n'est
 * volontairement pas exposé : c'est une information anti-fraude interne.
 */
record ArticleResponse(UUID id, UUID sellerId, UUID categoryId, String title, String description,
                       long priceCents, long shippingCents, String currency, Map<String, Object> attributes,
                       String status, String reviewReason, Instant publishedAt, Instant createdAt,
                       List<PhotoResponse> photos) {

    record PhotoResponse(UUID id, URI url) {
    }

    static ArticleResponse from(ArticleView view) {
        Article a = view.article();
        return new ArticleResponse(a.id(), a.sellerId(), a.categoryId(), a.title(), a.description(),
                a.priceCents(), a.shippingCents(), a.currency(), a.attributes(), a.status().name(), a.reviewReason(),
                a.publishedAt(), a.createdAt(),
                view.photos().stream().map(p -> new PhotoResponse(p.id(), p.url())).toList());
    }
}
