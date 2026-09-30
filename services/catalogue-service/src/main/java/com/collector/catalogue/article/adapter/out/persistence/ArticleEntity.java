package com.collector.catalogue.article.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.Photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entité JPA de l'adaptateur : le schéma peut évoluer sans toucher au domaine.
 * Les conversions sont explicites (toDomain / fromDomain), sans MapStruct.
 */
@Entity
@Table(name = "article")
class ArticleEntity {

    @Id
    private UUID id;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "shipping_cents", nullable = false)
    private long shippingCents;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String attributes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleStatus status;

    @Column(name = "anomaly_score", precision = 6, scale = 3)
    private BigDecimal anomalyScore;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "check_reason")
    private String checkReason;

    @Column(name = "review_reason")
    private String reviewReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    // null = nouvel article (INSERT) ; sinon verrou optimiste, incrémenté par Hibernate.
    @Version
    private Long version;

    protected ArticleEntity() {
        // requis par JPA
    }

    static ArticleEntity fromDomain(Article article, JsonMapper json) {
        ArticleEntity e = new ArticleEntity();
        e.id = article.id();
        e.sellerId = article.sellerId();
        e.categoryId = article.categoryId();
        e.title = article.title();
        e.description = article.description();
        e.priceCents = article.priceCents();
        e.shippingCents = article.shippingCents();
        e.currency = article.currency();
        e.attributes = json.writeValueAsString(article.attributes());
        e.status = article.status();
        e.anomalyScore = article.anomalyScore() == null ? null : BigDecimal.valueOf(article.anomalyScore());
        e.publishedAt = article.publishedAt();
        e.createdAt = article.createdAt();
        e.checkReason = article.checkReason();
        e.reviewReason = article.reviewReason();
        e.reviewedBy = article.reviewedBy();
        e.version = article.version();
        return e;
    }

    @SuppressWarnings("unchecked")
    Article toDomain(List<Photo> photos, JsonMapper json) {
        return new Article(id, sellerId, categoryId, title, description, priceCents, shippingCents,
                currency.trim(), json.readValue(attributes, java.util.Map.class), status,
                anomalyScore == null ? null : anomalyScore.doubleValue(), publishedAt, createdAt, version, photos,
                checkReason, reviewReason, reviewedBy);
    }

    UUID getId() {
        return id;
    }
}
