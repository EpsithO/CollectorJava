package com.collector.notification.testsupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.collector.notification.notification.application.port.ArticleVersionRepository;
import com.collector.notification.notification.application.port.FollowRepository;
import com.collector.notification.notification.application.port.InterestRepository;
import com.collector.notification.notification.application.port.NotificationRepository;
import com.collector.notification.notification.domain.FollowChange;
import com.collector.notification.notification.domain.Notification;

/**
 * Doublure écrite à la main des quatre ports : elle reproduit les règles que le SQL applique
 * (dernier changed_at gagne, version strictement croissante, unicité membre-article-version).
 */
public class InMemoryStore implements FollowRepository, ArticleVersionRepository, NotificationRepository,
        InterestRepository {

    private record Key(UUID member, UUID article) {
    }

    public final Map<Key, FollowChange> follows = new HashMap<>();
    public final Map<UUID, Long> versions = new HashMap<>();
    public final List<Notification> notifications = new ArrayList<>();
    public final Map<UUID, Set<UUID>> interests = new HashMap<>();

    @Override
    public boolean apply(FollowChange change) {
        Key key = new Key(change.memberId(), change.articleId());
        FollowChange known = follows.get(key);
        if (known != null && !known.changedAt().isBefore(change.changedAt())) {
            return false;
        }
        follows.put(key, change);
        return true;
    }

    @Override
    public List<UUID> followersOf(UUID articleId) {
        return follows.values().stream()
                .filter(f -> f.articleId().equals(articleId) && f.following())
                .map(FollowChange::memberId)
                .toList();
    }

    @Override
    public boolean advance(UUID articleId, long version) {
        Long last = versions.get(articleId);
        if (last != null && last >= version) {
            return false;
        }
        versions.put(articleId, version);
        return true;
    }

    @Override
    public void saveAll(List<Notification> toSave) {
        for (Notification n : toSave) {
            boolean exists = notifications.stream().anyMatch(o -> o.memberId().equals(n.memberId())
                    && o.articleId().equals(n.articleId()) && o.aggregateVersion() == n.aggregateVersion());
            if (!exists) {
                notifications.add(n);
            }
        }
    }

    @Override
    public List<Notification> findFor(UUID memberId, boolean unreadOnly) {
        return notifications.stream()
                .filter(n -> n.memberId().equals(memberId))
                .filter(n -> !unreadOnly || !n.isRead())
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .toList();
    }

    @Override
    public boolean markRead(UUID memberId, UUID notificationId, Instant now) {
        for (int i = 0; i < notifications.size(); i++) {
            Notification n = notifications.get(i);
            if (n.id().equals(notificationId) && n.memberId().equals(memberId)) {
                if (!n.isRead()) {
                    notifications.set(i, new Notification(n.id(), n.memberId(), n.articleId(), n.articleTitle(),
                            n.oldPriceCents(), n.newPriceCents(), n.currency(), n.aggregateVersion(), n.createdAt(), now));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void replace(UUID memberId, Set<UUID> categoryIds) {
        interests.put(memberId, Set.copyOf(categoryIds));
    }
}
