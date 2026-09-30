package com.collector.catalogue.article.application.port;

import java.util.Optional;
import java.util.UUID;

/** Port propre à la fonctionnalité article : les hexagones restent indépendants. */
public interface CategoryCatalog {

    boolean exists(UUID categoryId);

    Optional<UUID> idBySlug(String slug);
}
