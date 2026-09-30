package com.collector.catalogue.category.adapter.in.web;

import java.util.UUID;

import com.collector.catalogue.category.domain.Category;

/** DTO : le contrat d'API ne suit ni le domaine ni le schéma. */
record CategoryResponse(UUID id, String slug, String label, UUID parentId) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.id(), category.slug(), category.label(), category.parentId());
    }
}
