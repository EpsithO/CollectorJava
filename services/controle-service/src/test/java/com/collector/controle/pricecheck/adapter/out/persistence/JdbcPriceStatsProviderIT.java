package com.collector.controle.pricecheck.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.collector.controle.AbstractIntegrationTest;
import com.collector.controle.pricecheck.application.port.PriceStatsProvider;
import com.collector.controle.pricecheck.domain.PriceCheck;
import com.collector.controle.pricecheck.domain.PriceCheckPolicy;

/** L'adaptateur réel contre la vue du catalogue, avec le jeu de données (35 articles par catégorie). */
class JdbcPriceStatsProviderIT extends AbstractIntegrationTest {

    @Autowired PriceStatsProvider provider;
    @Autowired JdbcClient jdbc;

    private UUID sneakers() {
        return jdbc.sql("SELECT id FROM category WHERE slug = 'sneakers'").query(UUID.class).single();
    }

    @Test
    void readsTheSeededStatisticsOfACategory() {
        var stats = provider.forCategory(sneakers()).orElseThrow();

        assertThat(stats.sampleSize()).isEqualTo(35);
        assertThat(stats.medianCents()).isCloseTo(25_000, org.assertj.core.data.Offset.offset(1.0));
        assertThat(stats.stddevCents()).isBetween(4_000.0, 6_000.0);
    }

    @Test
    void theSeedMakesTheRuleDecideAsDocumented() {
        var stats = provider.forCategory(sneakers()).orElseThrow();

        assertThat(PriceCheckPolicy.evaluate(26_000, stats).verdict()).isEqualTo(PriceCheck.Verdict.PUBLIE);
        assertThat(PriceCheckPolicy.evaluate(100_000, stats).verdict()).isEqualTo(PriceCheck.Verdict.EN_REVUE);
    }

    @Test
    void anUnknownCategoryHasNoStatistics() {
        assertThat(provider.forCategory(UUID.randomUUID())).isEmpty();
    }
}
