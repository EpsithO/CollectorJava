package com.collector.catalogue.article.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.shared.domain.InvalidRequestException;

/**
 * Applique le verdict du contrôle (article.checked). Idempotent : seul un article encore
 * EN_CONTROLE change ; un doublon (livraison au moins une fois) ne fait rien.
 */
@Service
public class ApplyVerdict {

    private final ArticleRepository articles;
    private final Clock clock;

    public ApplyVerdict(ArticleRepository articles, Clock clock) {
        this.articles = articles;
        this.clock = clock;
    }

    /** Renvoie le délai soumission → verdict si appliqué, vide pour un doublon. */
    @Transactional
    public Optional<Duration> execute(UUID articleId, ArticleStatus verdict, Double anomalyScore,
                                   String reason) {
        if (verdict != ArticleStatus.PUBLIE && verdict != ArticleStatus.EN_REVUE) {
            throw new InvalidRequestException("Verdict must be PUBLIE or EN_REVUE");
        }
        return articles.applyVerdict(articleId, verdict, anomalyScore, reason, clock.instant())
                .map(submittedAt -> Duration.between(submittedAt, clock.instant()));
    }
}
