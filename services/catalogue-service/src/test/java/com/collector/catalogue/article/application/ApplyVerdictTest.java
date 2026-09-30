package com.collector.catalogue.article.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.shared.domain.InvalidRequestException;
import com.collector.catalogue.testsupport.InMemoryArticles;

class ApplyVerdictTest {

    private static final Instant NOW = InMemoryArticles.SUBMITTED_AT.plusMillis(700);

    private final InMemoryArticles articles = new InMemoryArticles();
    private final ApplyVerdict applyVerdict = new ApplyVerdict(articles, Clock.fixed(NOW, ZoneOffset.UTC));

    private Article inControl() {
        Instant t = InMemoryArticles.SUBMITTED_AT;
        return articles.put(new Article(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Titre",
                "Description correcte", 26_000, 0, "EUR", Map.of(), ArticleStatus.EN_CONTROLE, null, null, t,
                null, List.of()));
    }

    @Test
    void appliesVerdictAndReportsTheCheckDuration() {                            // CA-2
        Article article = inControl();

        var delay = applyVerdict.execute(article.id(), ArticleStatus.PUBLIE, 0.4, null);

        assertThat(delay).contains(Duration.ofMillis(700));
        assertThat(articles.stored.get(article.id()).status()).isEqualTo(ArticleStatus.PUBLIE);
        assertThat(articles.stored.get(article.id()).anomalyScore()).isEqualTo(0.4);
    }

    @Test
    void outlierGoesToReview() {                                                 // CA-3
        Article article = inControl();

        applyVerdict.execute(article.id(), ArticleStatus.EN_REVUE, 5.2, "price_outlier");

        assertThat(articles.stored.get(article.id()).status()).isEqualTo(ArticleStatus.EN_REVUE);
    }

    @Test
    void secondDeliveryIsANoOp() {
        Article article = inControl();
        applyVerdict.execute(article.id(), ArticleStatus.PUBLIE, 0.4, null);

        var second = applyVerdict.execute(article.id(), ArticleStatus.EN_REVUE, 9.0, "price_outlier");

        assertThat(second).isEmpty();
        assertThat(articles.stored.get(article.id()).status()).isEqualTo(ArticleStatus.PUBLIE);
    }

    @Test
    void unknownArticleIsIgnored() {
        assertThat(applyVerdict.execute(UUID.randomUUID(), ArticleStatus.PUBLIE, 0.0, null)).isEmpty();
    }

    @Test
    void onlyPublieOrEnRevueAreValidVerdicts() {
        assertThatThrownBy(() -> applyVerdict.execute(UUID.randomUUID(), ArticleStatus.REJETE, 0.0, null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
