package com.collector.catalogue.category.domain;

import java.util.Objects;
import java.util.UUID;

/** Java pur : ni Spring, ni JPA, ni Jackson. */
public record Category(UUID id, String slug, String label, UUID parentId) {

    public Category {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(label, "label");
    }

    public boolean isRoot() {
        return parentId == null;
    }
}
