package com.collector.catalogue.article.adapter.in.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.collector.catalogue.article.application.ApplyVerdict;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.messaging.EventSchemas;
import com.collector.messaging.EventTypes;
import com.collector.messaging.Topology;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Consomme article.checked et applique le verdict, de façon idempotente (livraison au moins une fois). */
@Component
class ArticleCheckedListener {

    private static final Logger log = LoggerFactory.getLogger(ArticleCheckedListener.class);

    private final ApplyVerdict applyVerdict;
    private final JsonMapper json;
    private final MeterRegistry metrics;

    ArticleCheckedListener(ApplyVerdict applyVerdict, JsonMapper json, MeterRegistry metrics) {
        this.applyVerdict = applyVerdict;
        this.json = json;
        this.metrics = metrics;
    }

    // Acquittement automatique au retour : on ne revient qu'une fois le verdict écrit.
    // Exception transitoire = remise en file (5 fois, puis lettres mortes) ; message
    // illisible ou non conforme : inutile de le rejouer, lettres mortes directes.
    @RabbitListener(queues = Topology.CATALOGUE_ARTICLE_CHECKED)
    void on(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        UUID articleId;
        ArticleStatus verdict;
        Double score;
        String reason;
        try {
            List<String> errors = EventSchemas.validate(EventTypes.ARTICLE_CHECKED, 1, body);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(errors.toString());
            }
            JsonNode data = json.readTree(body).path("data");
            articleId = UUID.fromString(data.path("article_id").asString());
            verdict = ArticleStatus.valueOf(data.path("verdict").asString());
            score = data.path("anomaly_score").isNumber() ? data.path("anomaly_score").asDouble() : null;
            reason = data.path("reason").isString() ? data.path("reason").asString() : null;
        } catch (RuntimeException e) {
            throw new AmqpRejectAndDontRequeueException("article.checked illisible ou non conforme", e);
        }

        applyVerdict.execute(articleId, verdict, score, reason).ifPresentOrElse(
                delay -> {
                    metrics.counter("collector.articles.verdict", "verdict", verdict.name()).increment();
                    Timer.builder("collector.article.check.duration")
                            .description("Soumission jusqu'au verdict appliqué")
                            .publishPercentileHistogram()
                            .register(metrics)
                            .record(delay);
                },
                () -> log.info("Verdict déjà appliqué pour l'article {} : doublon ignoré", articleId));
    }
}
