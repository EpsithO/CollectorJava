package com.collector.notification.notification.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.notification.notification.application.port.NotificationRepository;
import com.collector.notification.notification.domain.Notification;

/** GET /me/notifications : un membre ne voit jamais les notifications des autres (CA-5). */
@Service
public class ListNotifications {

    private final NotificationRepository notifications;

    public ListNotifications(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<Notification> execute(UUID memberId, boolean unreadOnly) {
        return notifications.findFor(memberId, unreadOnly);
    }
}
