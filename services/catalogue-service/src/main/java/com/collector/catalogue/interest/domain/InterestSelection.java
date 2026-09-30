package com.collector.catalogue.interest.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Centres d'intérêt d'un membre : un ensemble de catégories, sans doublon, borné.
 * Objet de valeur immuable : s'il existe, il est valide.
 */
public record InterestSelection(Set<UUID> categoryIds) {

    public static final int MAX_INTERESTS = 10;

    public InterestSelection {
        Objects.requireNonNull(categoryIds, "categoryIds");
        if (categoryIds.size() > MAX_INTERESTS) {
            throw new TooManyInterestsException(categoryIds.size());
        }
        categoryIds = Set.copyOf(categoryIds);   // copie immuable : personne ne la modifie après validation
    }

    /** Les doublons disparaissent ici : la règle « doublons ignorés » est portée par le type. */
    public static InterestSelection of(Collection<UUID> categoryIds) {
        return new InterestSelection(new HashSet<>(categoryIds));
    }

    public static InterestSelection empty() {
        return new InterestSelection(Set.of());
    }

    /** Comparaison d'ensembles : l'ordre d'envoi n'a pas d'importance (CA-6). */
    public boolean sameAs(InterestSelection other) {
        return categoryIds.equals(other.categoryIds);
    }
}
