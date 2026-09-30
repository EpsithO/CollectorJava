package com.collector.notification.notification.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.notification.notification.application.port.NotificationRepository;
import com.collector.notification.shared.domain.NotFoundException;

/** POST /me/notifications/{id}/read (CA-5). */
@Service
public class MarkNotificationRead {

    private final NotificationRepository notifications;
    private final Clock clock;

    public MarkNotificationRead(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    // 404 et non 403 pour la notification d'un autre : on ne révèle pas son existence.
    @Transactional
    public void execute(UUID memberId, UUID notificationId) {
        if (!notifications.markRead(memberId, notificationId, clock.instant())) {
            throw new NotFoundException("Notification");
        }
    }
}
