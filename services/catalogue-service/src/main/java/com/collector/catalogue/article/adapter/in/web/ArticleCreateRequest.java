package com.collector.catalogue.article.adapter.in.web;

import java.util.Map;
import java.util.UUID;

import com.collector.catalogue.article.application.DraftCommand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Validation TECHNIQUE (forme et bornes du JSON) ; les règles métier (coordonnées interdites,
 * catégorie existante) sont dans le domaine et le cas d'usage. Champs inconnus refusés
 * (fail-on-unknown-properties).
 */
record ArticleCreateRequest(
        @NotBlank @Size(min = 3, max = 120) String title,
        @NotBlank @Size(min = 10, max = 5000) String description,
        @NotNull UUID categoryId,
        @Min(1) @Max(100_000_000) long priceCents,
        @Min(0) @Max(10_000_000) Long shippingCents,
        Map<String, Object> attributes) {

    DraftCommand toCommand() {
        return new DraftCommand(title, description, categoryId, priceCents,
                shippingCents == null ? 0 : shippingCents, attributes);
    }
}
