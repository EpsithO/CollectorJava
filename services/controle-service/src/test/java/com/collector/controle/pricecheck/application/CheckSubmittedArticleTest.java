package com.collector.controle.pricecheck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.controle.pricecheck.application.port.PriceStatsProvider;
import com.collector.controle.pricecheck.application.port.VerdictPublisher;
import com.collector.controle.pricecheck.domain.ArticleSubmitted;
import com.collector.controle.pricecheck.domain.PriceCheck;
import com.collector.controle.pricecheck.domain.PriceStats;

/** Cas d'usage avec doublures écrites à la main : ni Spring, ni base, ni broker. */
class CheckSubmittedArticleTest {

    private static final UUID SNEAKERS = UUID.randomUUID();
    private static final PriceStats STATS = new PriceStats(35, 25_000, 4_800.0);

    private final List<String> calls = new ArrayList<>();
    private final RecordingPublisher publisher = new RecordingPublisher();

    private ArticleSubmitted article(long priceCents) {
        return new ArticleSubmitted(UUID.randomUUID(), UUID.randomUUID(), SNEAKERS, priceCents);
    }

    private CheckSubmittedArticle useCaseWith(PriceStatsProvider provider) {
        return new CheckSubmittedArticle(provider, publisher);
    }

    @Test
    void consistentPricePublishesOnlyTheVerdict() {                          // CA-2
        useCaseWith(category -> Optional.of(STATS)).execute(article(26_000));

        assertThat(calls).containsExactly("verdict:PUBLIE");
    }

    @Test
    void outlierRaisesTheAlertBeforeThePublishedVerdict() {                  // CA-3
        useCaseWith(category -> Optional.of(STATS)).execute(article(100_000));

        // L'ordre compte : une alerte perdue passerait la fraude sous silence.
        assertThat(calls).containsExactly("alert:price_outlier", "verdict:EN_REVUE");
        assertThat(publisher.statsSeenByAlert).isEqualTo(STATS);
    }

    @Test
    void categoryWithoutPublishedArticleIsPublishedWithoutJudging() {
        useCaseWith(category -> Optional.empty()).execute(article(99_999_999));

        assertThat(calls).containsExactly("verdict:PUBLIE");
        assertThat(publisher.lastCheck.reason()).isEqualTo("insufficient_sample");
    }

    @Test
    void nothingIsPublishedIfTheStatsCannotBeRead() {
        PriceStatsProvider broken = category -> {
            throw new IllegalStateException("base indisponible");
        };

        assertThatThrownBy(() -> useCaseWith(broken).execute(article(26_000)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).isEmpty();                                         // le message sera rejoué
    }

    @Test
    void aFailedAlertPublicationStopsBeforeTheVerdict() {
        publisher.failOnAlert = true;

        assertThatThrownBy(() -> useCaseWith(category -> Optional.of(STATS)).execute(article(100_000)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).isEmpty();                                         // pas de verdict sans l'alerte
    }

    @Test
    void theSameInputAlwaysGivesTheSameDecision() {                          // rejeu sans danger
        CheckSubmittedArticle useCase = useCaseWith(category -> Optional.of(STATS));
        ArticleSubmitted submitted = article(100_000);

        useCase.execute(submitted);
        useCase.execute(submitted);

        assertThat(calls).containsExactly("alert:price_outlier", "verdict:EN_REVUE",
                "alert:price_outlier", "verdict:EN_REVUE");
    }

    private final class RecordingPublisher implements VerdictPublisher {
        boolean failOnAlert;
        PriceStats statsSeenByAlert;
        PriceCheck lastCheck;

        @Override
        public void publishVerdict(ArticleSubmitted article, PriceCheck check) {
            lastCheck = check;
            calls.add("verdict:" + check.verdict());
        }

        @Override
        public void raiseFraudAlert(ArticleSubmitted article, PriceCheck check, PriceStats stats) {
            if (failOnAlert) {
                throw new IllegalStateException("broker injoignable");
            }
            statsSeenByAlert = stats;
            calls.add("alert:" + check.reason());
        }
    }
}
