package com.collector.controle.pricecheck.application.port;

import java.util.Optional;
import java.util.UUID;

import com.collector.controle.pricecheck.domain.PriceStats;

/** Port sortant : d'où viennent les statistiques (aujourd'hui la base du catalogue, demain un modèle de lecture propre). */
public interface PriceStatsProvider {

    /** Vide si la catégorie n'a aucun article publié. */
    Optional<PriceStats> forCategory(UUID categoryId);
}
