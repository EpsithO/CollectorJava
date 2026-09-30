package com.collector.controle.pricecheck.application.port;

import com.collector.controle.pricecheck.domain.ArticleSubmitted;
import com.collector.controle.pricecheck.domain.PriceCheck;
import com.collector.controle.pricecheck.domain.PriceStats;

public interface VerdictPublisher {

    /** Publie article.checked ; lève une exception si le broker ne confirme pas (le message sera rejoué). */
    void publishVerdict(ArticleSubmitted article, PriceCheck check);

    /** Publie fraud.alert pour l'anti-fraude ; même règle de confirmation. */
    void raiseFraudAlert(ArticleSubmitted article, PriceCheck check, PriceStats stats);
}
