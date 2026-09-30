package com.collector.controle.pricecheck.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.collector.controle.pricecheck.application.port.PriceStatsProvider;
import com.collector.controle.pricecheck.application.port.VerdictPublisher;
import com.collector.controle.pricecheck.domain.ArticleSubmitted;
import com.collector.controle.pricecheck.domain.PriceCheck;
import com.collector.controle.pricecheck.domain.PriceCheckPolicy;
import com.collector.controle.pricecheck.domain.PriceStats;

/** Cas d'usage : un article est soumis, on décide et on publie le verdict. */
@Service
public class CheckSubmittedArticle {

    private final PriceStatsProvider stats;
    private final VerdictPublisher verdicts;

    public CheckSubmittedArticle(PriceStatsProvider stats, VerdictPublisher verdicts) {
        this.stats = stats;
        this.verdicts = verdicts;
    }

    // Aucune transaction : le service n'écrit rien en base. Si une publication échoue,
    // l'exception remet le message entrant en file (5 fois, puis lettres mortes) ; le verdict
    // est alors recalculé, ce qui est sans danger (même entrée, même décision).
    public void execute(ArticleSubmitted article) {
        Optional<PriceStats> categoryStats = stats.forCategory(article.categoryId());
        PriceCheck check = categoryStats
                .map(s -> PriceCheckPolicy.evaluate(article.priceCents(), s))
                .orElseGet(PriceCheck::insufficientSample);

        // L'alerte d'abord : si le verdict part mais pas l'alerte, la fraude passe inaperçue.
        // Dans l'autre sens, un article resterait EN_CONTROLE, ce qui se voit et se rejoue.
        if (check.isOutlier()) {
            verdicts.raiseFraudAlert(article, check, categoryStats.orElseThrow());
        }
        verdicts.publishVerdict(article, check);
    }
}
