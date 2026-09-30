package com.collector.notification.notification.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.collector.notification.notification.domain.Notification;

public interface NotificationRepository {

    /** Enregistre ; une notification déjà connue (même membre, article et version) est ignorée. */
    void saveAll(List<Notification> notifications);

    /** Notifications d'un membre, les plus récentes d'abord. */
    List<Notification> findFor(UUID memberId, boolean unreadOnly);

    /** Marque lue ; faux si elle n'existe pas ou n'appartient pas à ce membre. Idempotent. */
    boolean markRead(UUID memberId, UUID notificationId, Instant now);
}
