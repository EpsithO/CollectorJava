package com.collector.catalogue.testsupport;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;

/** Doublure écrite à la main du port : documente ce qu'on attend d'un dépôt. */
public class InMemoryArticles implements ArticleRepository {

    public static final Instant SUBMITTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    public final Map<UUID, Article> stored = new LinkedHashMap<>();

    public Article put(Article article) {
        return save(article);
    }

    @Override
    public Article save(Article article) {
        long nextVersion = article.version() == null ? 0 : article.version() + 1;
        Article saved = new Article(article.id(), article.sellerId(), article.categoryId(), article.title(),
                article.description(), article.priceCents(), article.shippingCents(), article.currency(),
                article.attributes(), article.status(), article.anomalyScore(), article.publishedAt(),
                article.createdAt(), nextVersion, article.photos(), article.checkReason(), article.reviewReason(),
                article.reviewedBy());
        stored.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<Article> findById(UUID id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public ArticlePage findPublished(int page, int size) {
        return page(stored.values().stream().filter(Article::isPublished).toList(), page, size);
    }

    @Override
    public ArticlePage findPublishedInCategory(UUID categoryId, int page, int size) {
        return page(stored.values().stream()
                .filter(Article::isPublished)
                .filter(a -> a.categoryId().equals(categoryId))
                .toList(), page, size);
    }

    @Override
    public List<Article> findByStatusOldestFirst(ArticleStatus status) {
        return stored.values().stream()
                .filter(a -> a.status() == status)
                .sorted(Comparator.comparing(Article::createdAt))
                .toList();
    }

    @Override
    public List<Article> findBySeller(UUID sellerId, ArticleStatus statusOrNull) {
        return stored.values().stream()
                .filter(a -> a.sellerId().equals(sellerId))
                .filter(a -> statusOrNull == null || a.status() == statusOrNull)
                .sorted(Comparator.comparing(Article::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<Instant> applyVerdict(UUID articleId, ArticleStatus verdict, Double anomalyScore, String reason, Instant now) {
        Article article = stored.get(articleId);
        if (article == null || article.status() != ArticleStatus.EN_CONTROLE) {
            return Optional.empty();
        }
        stored.put(articleId, new Article(article.id(), article.sellerId(), article.categoryId(), article.title(),
                article.description(), article.priceCents(), article.shippingCents(), article.currency(),
                article.attributes(), verdict, anomalyScore, now, article.createdAt(),
                article.version() + 1, article.photos(), reason, null, null));
        return Optional.of(SUBMITTED_AT);
    }

    private static ArticlePage page(List<Article> all, int page, int size) {
        List<Article> items = all.stream().skip((long) page * size).limit(size).toList();
        return new ArticlePage(items, all.size());
    }
}
