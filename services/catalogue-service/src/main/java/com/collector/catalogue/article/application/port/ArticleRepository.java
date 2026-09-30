package com.collector.catalogue.article.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;

/** Port sortant : exprimé par les besoins des cas d'usage, pas par le schéma. */
public interface ArticleRepository {

    /** Crée ou met à jour ; renvoie l'article relu (version à jour, photos comprises). */
    Article save(Article article);

    Optional<Article> findById(UUID id);

    /** Articles PUBLIE, récents d'abord. */
    ArticlePage findPublished(int page, int size);

    ArticlePage findPublishedInCategory(UUID categoryId, int page, int size);

    /** Articles d'un statut donné, les plus anciens d'abord (file de revue, US-033). */
    List<Article> findByStatusOldestFirst(ArticleStatus status);

    /** Tous les articles d'un vendeur, filtrés par statut si fourni (null = tous). */
    List<Article> findBySeller(UUID sellerId, ArticleStatus statusOrNull);

    /**
     * Applique le verdict du contrôle, seulement si l'article est encore EN_CONTROLE
     * (idempotence). Renvoie la date de soumission si le verdict a été appliqué, vide
     * si c'était un doublon.
     */
    Optional<Instant> applyVerdict(UUID articleId, ArticleStatus verdict, Double anomalyScore, String reason,
                                   Instant now);

    record ArticlePage(List<Article> items, long total) {
    }
}
