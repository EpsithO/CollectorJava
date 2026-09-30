package com.collector.notification.notification.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.notification.notification.application.port.ArticleVersionRepository;
import com.collector.notification.notification.application.port.FollowRepository;
import com.collector.notification.notification.application.port.NotificationRepository;
import com.collector.notification.notification.domain.Notification;
import com.collector.notification.notification.domain.PriceChange;

/** US-029 CA-3 et CA-4 : un changement de prix devient une notification pour chaque abonné. */
@Service
public class NotifyPriceChange {

    private final ArticleVersionRepository versions;
    private final FollowRepository follows;
    private final NotificationRepository notifications;
    private final Clock clock;

    public NotifyPriceChange(ArticleVersionRepository versions, FollowRepository follows,
                             NotificationRepository notifications, Clock clock) {
        this.versions = versions;
        this.follows = follows;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Livraison « au moins une fois » et sans ordre garanti : deux price.changed du même article
     * peuvent arriver inversés ou en double. La version d'agrégat règle les deux cas : seul un
     * événement plus récent que le dernier vu produit des notifications. Renvoie leur nombre.
     */
    @Transactional
    public int execute(PriceChange change) {
        if (!versions.advance(change.articleId(), change.aggregateVersion())) {
            return 0;                                        // périmé ou déjà traité : rien à notifier
        }
        List<UUID> followers = follows.followersOf(change.articleId());
        List<Notification> created = followers.stream()
                .map(member -> Notification.forPriceChange(member, change, clock.instant()))
                .toList();
        notifications.saveAll(created);
        return created.size();
    }
}
