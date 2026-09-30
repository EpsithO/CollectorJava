package com.collector.notification.notification.application.port;

import java.util.List;
import java.util.UUID;

import com.collector.notification.notification.domain.FollowChange;

public interface FollowRepository {

    /**
     * Enregistre le changement si aucun plus récent n'est déjà connu (le dernier changed_at gagne,
     * quel que soit l'ordre d'arrivée). Renvoie faux si le changement était périmé.
     */
    boolean apply(FollowChange change);

    /** Abonnés actuels d'un article (sub Keycloak). */
    List<UUID> followersOf(UUID articleId);
}
