package com.collector.catalogue.interest.application.port;

import java.util.Set;
import java.util.UUID;

/**
 * Port propre à cette fonctionnalité (et non le port de {@code category}) : les hexagones
 * restent indépendants, ArchUnit interdit les cycles entre eux.
 */
public interface CategoryCatalog {

    /** Parmi les identifiants donnés, ceux qui existent. */
    Set<UUID> existing(Set<UUID> categoryIds);
}
