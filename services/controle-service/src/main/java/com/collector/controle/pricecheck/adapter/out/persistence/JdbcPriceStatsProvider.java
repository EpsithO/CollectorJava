package com.collector.controle.pricecheck.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.collector.controle.pricecheck.application.port.PriceStatsProvider;
import com.collector.controle.pricecheck.domain.PriceStats;

/**
 * Lecture seule (rôle controle_app, SELECT sur la seule vue category_price_stats). Compromis
 * assumé du POC (ADR 0006) : en V2, un modèle de lecture alimenté par les événements remplace
 * cet adaptateur, sans toucher au domaine ni au cas d'usage.
 */
@Component
class JdbcPriceStatsProvider implements PriceStatsProvider {

    private final JdbcClient jdbc;

    JdbcPriceStatsProvider(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PriceStats> forCategory(UUID categoryId) {
        return jdbc.sql("""
                    SELECT sample_size, median_cents, stddev_cents
                    FROM category_price_stats WHERE category_id = :id
                    """)
                .param("id", categoryId)
                .query((rs, n) -> new PriceStats(rs.getLong("sample_size"), rs.getDouble("median_cents"),
                        rs.getObject("stddev_cents", Double.class)))
                .optional();
    }
}
