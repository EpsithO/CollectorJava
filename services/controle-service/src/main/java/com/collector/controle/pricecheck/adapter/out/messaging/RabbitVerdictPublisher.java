package com.collector.controle.pricecheck.adapter.out.messaging;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.collector.controle.pricecheck.application.port.VerdictPublisher;
import com.collector.controle.pricecheck.domain.ArticleSubmitted;
import com.collector.controle.pricecheck.domain.PriceCheck;
import com.collector.controle.pricecheck.domain.PriceStats;
import com.collector.messaging.ConfirmedPublisher;
import com.collector.messaging.EventEnvelope;
import com.collector.messaging.EventSchemas;
import com.collector.messaging.EventTypes;
import com.collector.messaging.OutgoingMessage;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pas d'outbox ici : le service n'écrit rien en base. La garantie vient de l'ordre « publier
 * (confirmé) puis acquitter » : si le pod tombe entre les deux, l'événement entrant est
 * redélivré et le verdict republié ; le catalogue l'ignore (application idempotente).
 */
@Component
class RabbitVerdictPublisher implements VerdictPublisher {

    private final ConfirmedPublisher publisher;
    private final JsonMapper json;
    private final Clock clock;
    private final VerdictProperties props;
    private final MeterRegistry metrics;

    RabbitVerdictPublisher(ConfirmedPublisher publisher, JsonMapper json, Clock clock, VerdictProperties props,
                           MeterRegistry metrics) {
        this.publisher = publisher;
        this.json = json;
        this.clock = clock;
        this.props = props;
        this.metrics = metrics;
    }

    @Override
    public void publishVerdict(ArticleSubmitted article, PriceCheck check) {
        send(EventTypes.ARTICLE_CHECKED, new ArticleChecked(article.articleId(), check.verdict().name(),
                check.anomalyScore(), check.reason()));
    }

    @Override
    public void raiseFraudAlert(ArticleSubmitted article, PriceCheck check, PriceStats stats) {
        send(EventTypes.FRAUD_ALERT, new FraudAlert(article.articleId(), article.sellerId(), "PRIX_ANORMAL",
                check.anomalyScore(), Map.of("price_cents", article.priceCents(),
                        "median_cents", stats.medianCents(), "stddev_cents", stats.stddevCents())));
        metrics.counter("collector.fraud.alerts").increment();
    }

    // Enveloppe, validation contre le schéma, publication confirmée : une exception remet le
    // message entrant en file plutôt que de perdre le verdict en silence.
    private void send(String type, Object data) {
        EventEnvelope<Object> envelope = EventEnvelope.of(type, data, clock.instant());
        String body = json.writeValueAsString(envelope);
        EventSchemas.requireValid(type, envelope.version(), body);
        UUID eventId = envelope.eventId();
        if (!publisher.publish(new OutgoingMessage(eventId, type, body), props.confirmTimeout())) {
            throw new IllegalStateException(type + " non confirmé par le broker");
        }
    }

    record ArticleChecked(UUID articleId, String verdict, double anomalyScore, String reason) {
    }

    record FraudAlert(UUID articleId, UUID sellerId, String kind, double score, Map<String, Object> details) {
    }
}
