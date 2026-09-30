package com.collector.notification.notification.application.port;

import java.util.Set;
import java.util.UUID;

public interface InterestRepository {

    /** Remplace la copie des centres d'intérêt d'un membre (base de US-026, hors POC). */
    void replace(UUID memberId, Set<UUID> categoryIds);
}
