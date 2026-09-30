package com.collector.notification.notification.domain;

import java.time.Instant;
import java.util.UUID;

/** Un acheteur (sub Keycloak) suit ou ne suit plus un article ; le changement le plus récent gagne. */
public record FollowChange(UUID memberId, UUID articleId, boolean following, Instant changedAt) {
}
