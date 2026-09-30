package com.collector.catalogue.article.domain;

import java.time.Instant;
import java.util.UUID;

import com.collector.catalogue.shared.domain.DomainEvent;

/**
 * follow.changed : un acheteur suit ou ne suit plus un article (US-029). {@code memberId} est le
 * sub Keycloak : l'identité commune à tous les services. Le catalogue ne garde aucun état de suivi,
 * il le transmet ; le notification-service tient sa propre copie (le plus récent changedAt gagne).
 */
public record FollowChanged(UUID memberId, UUID articleId, boolean following, Instant changedAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "follow.changed";
    }
}
