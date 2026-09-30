package com.collector.catalogue.article.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.Photo;

import tools.jackson.databind.json.JsonMapper;

@Component
class ArticlePersistenceAdapter implements ArticleRepository {

    private final ArticleJpaRepository articles;
    private final PhotoJpaRepository photos;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    ArticlePersistenceAdapter(ArticleJpaRepository articles, PhotoJpaRepository photos, JdbcClient jdbc,
                              JsonMapper json) {
        this.articles = articles;
        this.photos = photos;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Article save(Article article) {
        // saveAndFlush : la version incrémentée est lue ici, avant que l'événement ne la porte.
        ArticleEntity saved = articles.saveAndFlush(ArticleEntity.fromDomain(article, json));

        Map<UUID, PhotoEntity> existing = photos
                .findByArticleIdInOrderByArticleIdAscPositionAsc(List.of(saved.getId())).stream()
                .collect(Collectors.toMap(PhotoEntity::getId, p -> p));
        for (Photo photo : article.photos()) {
            PhotoEntity entity = existing.get(photo.id());
            if (entity == null) {
                photos.save(PhotoEntity.fromDomain(saved.getId(), photo));
            } else {
                entity.apply(photo);                         // entité gérée : flush à la fin de la transaction
            }
        }
        photos.flush();
        return load(List.of(saved)).getFirst();
    }

    @Override
    public Optional<Article> findById(UUID id) {
        return articles.findById(id).map(entity -> load(List.of(entity)).getFirst());
    }

    @Override
    public ArticlePage findPublished(int page, int size) {
        return toPage(articles.findByStatusOrderByPublishedAtDesc(ArticleStatus.PUBLIE, PageRequest.of(page, size)));
    }

    @Override
    public ArticlePage findPublishedInCategory(UUID categoryId, int page, int size) {
        return toPage(articles.findByStatusAndCategoryIdOrderByPublishedAtDesc(
                ArticleStatus.PUBLIE, categoryId, PageRequest.of(page, size)));
    }

    @Override
    public List<Article> findByStatusOldestFirst(ArticleStatus status) {
        return load(articles.findByStatusOrderByCreatedAtAsc(status));
    }

    @Override
    public List<Article> findBySeller(UUID sellerId, ArticleStatus statusOrNull) {
        List<ArticleEntity> found = statusOrNull == null
                ? articles.findBySellerIdOrderByCreatedAtDesc(sellerId)
                : articles.findBySellerIdAndStatusOrderByCreatedAtDesc(sellerId, statusOrNull);
        return load(found);
    }

    // Idempotence : la clause status = 'EN_CONTROLE' fait d'un doublon une mise à jour de 0 ligne.
    // La jointure sur l'ancienne ligne (old) relit updated_at AVANT la mise à jour (le trigger le
    // réécrit) : c'est la date de soumission, pour mesurer le délai de contrôle.
    @Override
    public Optional<Instant> applyVerdict(UUID articleId, ArticleStatus verdict, Double anomalyScore, String reason,
                                   Instant now) {
        return jdbc.sql("""
                    UPDATE article a
                    SET status = :status,
                        anomaly_score = :score,
                        check_reason = :reason,
                        reviewed_at = :now,
                        published_at = CASE WHEN :status = 'PUBLIE' THEN :now ELSE a.published_at END,
                        version = a.version + 1
                    FROM article old
                    WHERE a.id = :id AND old.id = a.id AND a.status = 'EN_CONTROLE'
                    RETURNING old.updated_at AS submitted_at
                    """)
                .param("status", verdict.name())
                .param("score", anomalyScore == null ? null : java.math.BigDecimal.valueOf(anomalyScore))
                .param("reason", reason)
                .param("now", Timestamp.from(now))
                .param("id", articleId)
                .query((rs, n) -> rs.getTimestamp("submitted_at").toInstant())
                .optional();
    }

    private ArticlePage toPage(Page<ArticleEntity> page) {
        return new ArticlePage(load(page.getContent()), page.getTotalElements());
    }

    // Une seule requête pour les photos de toute la liste (pas de N+1).
    private List<Article> load(List<ArticleEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = entities.stream().map(ArticleEntity::getId).toList();
        Map<UUID, List<Photo>> byArticle = new HashMap<>();
        for (PhotoEntity photo : photos.findByArticleIdInOrderByArticleIdAscPositionAsc(ids)) {
            byArticle.computeIfAbsent(photo.getArticleId(), k -> new ArrayList<>()).add(photo.toDomain());
        }
        return entities.stream()
                .map(e -> e.toDomain(byArticle.getOrDefault(e.getId(), List.of()), json))
                .toList();
    }
}
