package com.collector.notification.notification.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.collector.notification.notification.application.port.ArticleVersionRepository;
import com.collector.notification.notification.application.port.FollowRepository;
import com.collector.notification.notification.application.port.InterestRepository;
import com.collector.notification.notification.application.port.NotificationRepository;
import com.collector.notification.notification.domain.FollowChange;
import com.collector.notification.notification.domain.Notification;

/**
 * Un seul adaptateur JDBC pour les quatre ports : des tables d'association sans comportement ne
 * méritent pas d'entités JPA. Toute la gestion de l'ordre et des doublons est faite en SQL, par des
 * instructions atomiques (INSERT ... ON CONFLICT ... WHERE), donc sûres avec plusieurs réplicas.
 */
@Component
class NotificationJdbcAdapter implements FollowRepository, ArticleVersionRepository, NotificationRepository,
        InterestRepository {

    private final JdbcClient jdbc;

    NotificationJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // --- abonnements ----------------------------------------------------------------------

    // Le dernier changed_at gagne : le WHERE du DO UPDATE refuse un changement plus ancien ou identique.
    @Override
    public boolean apply(FollowChange change) {
        return jdbc.sql("""
                    INSERT INTO notification_follow (member_id, article_id, following, changed_at)
                    VALUES (:member, :article, :following, :at)
                    ON CONFLICT (member_id, article_id) DO UPDATE
                        SET following = EXCLUDED.following, changed_at = EXCLUDED.changed_at
                        WHERE notification_follow.changed_at < EXCLUDED.changed_at
                    """)
                .param("member", change.memberId())
                .param("article", change.articleId())
                .param("following", change.following())
                .param("at", Timestamp.from(change.changedAt()))
                .update() > 0;
    }

    @Override
    public List<UUID> followersOf(UUID articleId) {
        return jdbc.sql("SELECT member_id FROM notification_follow WHERE article_id = :article AND following")
                .param("article", articleId)
                .query(UUID.class)
                .list();
    }

    // --- versions d'agrégat -------------------------------------------------------------------

    // Une seule instruction : deux réplicas qui traitent le même article en parallèle ne peuvent
    // pas tous deux « avancer » la même version.
    @Override
    public boolean advance(UUID articleId, long version) {
        return jdbc.sql("""
                    INSERT INTO notification_article_version (article_id, last_version)
                    VALUES (:article, :version)
                    ON CONFLICT (article_id) DO UPDATE SET last_version = EXCLUDED.last_version
                        WHERE notification_article_version.last_version < EXCLUDED.last_version
                    """)
                .param("article", articleId)
                .param("version", version)
                .update() > 0;
    }

    // --- notifications --------------------------------------------------------------------------

    @Override
    public void saveAll(List<Notification> notifications) {
        for (Notification n : notifications) {
            jdbc.sql("""
                        INSERT INTO notification (id, member_id, article_id, article_title, old_price_cents,
                                                  new_price_cents, currency, aggregate_version, created_at)
                        VALUES (:id, :member, :article, :title, :old, :new, :currency, :version, :at)
                        ON CONFLICT (member_id, article_id, aggregate_version) DO NOTHING
                        """)
                    .param("id", n.id())
                    .param("member", n.memberId())
                    .param("article", n.articleId())
                    .param("title", n.articleTitle())
                    .param("old", n.oldPriceCents())
                    .param("new", n.newPriceCents())
                    .param("currency", n.currency())
                    .param("version", n.aggregateVersion())
                    .param("at", Timestamp.from(n.createdAt()))
                    .update();
        }
    }

    @Override
    public List<Notification> findFor(UUID memberId, boolean unreadOnly) {
        return jdbc.sql("""
                    SELECT id, member_id, article_id, article_title, old_price_cents, new_price_cents, currency,
                           aggregate_version, created_at, read_at
                    FROM notification
                    WHERE member_id = :member AND (:all OR read_at IS NULL)
                    ORDER BY created_at DESC, id
                    """)
                .param("member", memberId)
                .param("all", !unreadOnly)
                .query((rs, row) -> new Notification(
                        rs.getObject("id", UUID.class), rs.getObject("member_id", UUID.class),
                        rs.getObject("article_id", UUID.class), rs.getString("article_title"),
                        rs.getLong("old_price_cents"), rs.getLong("new_price_cents"),
                        rs.getString("currency").trim(), rs.getLong("aggregate_version"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()))
                .list();
    }

    // Le member_id dans le WHERE est le contrôle de propriété : la notification d'un autre ne change pas.
    // COALESCE : marquer deux fois garde la première date de lecture (idempotent).
    @Override
    public boolean markRead(UUID memberId, UUID notificationId, Instant now) {
        return jdbc.sql("""
                    UPDATE notification SET read_at = COALESCE(read_at, :now)
                    WHERE id = :id AND member_id = :member
                    """)
                .param("now", Timestamp.from(now))
                .param("id", notificationId)
                .param("member", memberId)
                .update() > 0;
    }

    // --- centres d'intérêt ------------------------------------------------------------------------

    @Override
    public void replace(UUID memberId, Set<UUID> categoryIds) {
        jdbc.sql("DELETE FROM notification_interest WHERE member_id = :member").param("member", memberId).update();
        for (UUID categoryId : categoryIds) {
            jdbc.sql("INSERT INTO notification_interest (member_id, category_id) VALUES (:member, :category)")
                    .param("member", memberId)
                    .param("category", categoryId)
                    .update();
        }
    }
}
