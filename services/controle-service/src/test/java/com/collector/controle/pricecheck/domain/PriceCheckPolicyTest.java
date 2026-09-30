package com.collector.controle.pricecheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Les cas limites de la règle des 3 écarts-types. Jeu de données de référence : sneakers,
 * médiane 250 € (25 000 centimes), écart-type 48 € (4 800 centimes), 35 articles.
 */
class PriceCheckPolicyTest {

    private static final PriceStats SNEAKERS = new PriceStats(35, 25_000, 4_800.0);

    @Test
    void consistentPriceIsPublished() {                                      // CA-2
        PriceCheck check = PriceCheckPolicy.evaluate(26_000, SNEAKERS);

        assertThat(check.verdict()).isEqualTo(PriceCheck.Verdict.PUBLIE);
        assertThat(check.anomalyScore()).isCloseTo(1_000 / 4_800.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(check.reason()).isNull();
        assertThat(check.isOutlier()).isFalse();
    }

    @Test
    void oneThousandEurosForSneakersIsSentToReview() {                       // CA-3
        PriceCheck check = PriceCheckPolicy.evaluate(100_000, SNEAKERS);

        assertThat(check.verdict()).isEqualTo(PriceCheck.Verdict.EN_REVUE);
        assertThat(check.anomalyScore()).isGreaterThan(15);
        assertThat(check.reason()).isEqualTo("price_outlier");
        assertThat(check.isOutlier()).isTrue();
    }

    @Test
    void aVeryLowPriceIsJustAsSuspicious() {                                 // appât, contrefaçon
        assertThat(PriceCheckPolicy.evaluate(1_000, SNEAKERS).verdict()).isEqualTo(PriceCheck.Verdict.EN_REVUE);
    }

    @Test
    void exactlyThreeStandardDeviationsIsPublishedAndOneCentMoreIsNot() {
        long threeSigmaAbove = 25_000 + 3 * 4_800;                           // 39 400

        assertThat(PriceCheckPolicy.evaluate(threeSigmaAbove, SNEAKERS).verdict())
                .isEqualTo(PriceCheck.Verdict.PUBLIE);
        assertThat(PriceCheckPolicy.evaluate(threeSigmaAbove + 1, SNEAKERS).verdict())
                .isEqualTo(PriceCheck.Verdict.EN_REVUE);
        long threeSigmaBelow = 25_000 - 3 * 4_800;                           // 10 600
        assertThat(PriceCheckPolicy.evaluate(threeSigmaBelow, SNEAKERS).verdict())
                .isEqualTo(PriceCheck.Verdict.PUBLIE);
        assertThat(PriceCheckPolicy.evaluate(threeSigmaBelow - 1, SNEAKERS).verdict())
                .isEqualTo(PriceCheck.Verdict.EN_REVUE);
    }

    @ParameterizedTest(name = "échantillon {0}, écart-type {1} : on ne conclut pas")
    @CsvSource(nullValues = "null", value = {
            "29,   4800.0",     // un article de moins que le seuil de 30
            "1,    4800.0",
            "35,   null",       // la vue renvoie NULL sous le seuil
            "35,   0.0",        // pas de dispersion : division par zéro évitée
            "35,   -1.0"})
    void insufficientOrDegenerateStatsPublishWithoutJudging(long sampleSize, Double stddev) {
        PriceCheck check = PriceCheckPolicy.evaluate(10_000_000, new PriceStats(sampleSize, 25_000, stddev));

        assertThat(check.verdict()).isEqualTo(PriceCheck.Verdict.PUBLIE);
        assertThat(check.reason()).isEqualTo("insufficient_sample");
        assertThat(check.anomalyScore()).isZero();
    }

    @Test
    void theThresholdOfThirtyIsInclusive() {
        PriceCheck check = PriceCheckPolicy.evaluate(100_000, new PriceStats(30, 25_000, 4_800.0));

        assertThat(check.verdict()).isEqualTo(PriceCheck.Verdict.EN_REVUE);
    }

    @Test
    void insufficientSampleHelperIsPublishedWithAZeroScore() {
        PriceCheck check = PriceCheck.insufficientSample();

        assertThat(check.verdict()).isEqualTo(PriceCheck.Verdict.PUBLIE);
        assertThat(check.anomalyScore()).isZero();
        assertThat(check.isOutlier()).isFalse();
    }
}
