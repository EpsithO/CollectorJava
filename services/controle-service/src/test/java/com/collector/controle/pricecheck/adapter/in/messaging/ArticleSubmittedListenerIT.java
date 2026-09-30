package com.collector.controle.pricecheck.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.collector.controle.AbstractIntegrationTest;
import com.collector.messaging.EventSchemas;
import com.collector.messaging.EventTypes;
import com.collector.messaging.Topology;

/**
 * Le service de bout en bout : article.submitted entrant, verdict conforme au schéma sortant.
 * Les files article.checked et fraude.alerts sont lues comme le feraient le catalogue et l'anti-fraude.
 */
class ArticleSubmittedListenerIT extends AbstractIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    private UUID sneakers() {
        return jdbc.sql("SELECT id FROM category WHERE slug = 'sneakers'").query(UUID.class).single();
    }

    private void submit(UUID articleId, long priceCents) {
        String body = """
                {"event_id":"%s","type":"article.submitted","version":1,"occurred_at":"2026-01-01T00:00:00Z",
                 "data":{"article_id":"%s","seller_id":"%s","category_id":"%s","price_cents":%d,
                         "currency":"EUR","photo_count":1}}"""
                .formatted(UUID.randomUUID(), articleId, UUID.randomUUID(), sneakers(), priceCents);
        rabbit.send(Topology.EXCHANGE, EventTypes.ARTICLE_SUBMITTED, MessageBuilder.withBody(body.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
    }

    private Message awaitMessageFor(String queue, UUID articleId) {
        Message[] found = new Message[1];
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message message = rabbit.receive(queue);
            if (message != null && new String(message.getBody()).contains(articleId.toString())) {
                found[0] = message;
            }
            assertThat(found[0]).isNotNull();
        });
        return found[0];
    }

    @Test
    void consistentPriceProducesAPublieVerdictAndNoAlert() {                 // CA-2
        UUID articleId = UUID.randomUUID();

        submit(articleId, 26_000);

        Message verdict = awaitMessageFor(Topology.CATALOGUE_ARTICLE_CHECKED, articleId);
        assertThat(EventSchemas.validate(EventTypes.ARTICLE_CHECKED, 1, verdict.getBody())).isEmpty();
        assertThat(new String(verdict.getBody())).contains("\"verdict\":\"PUBLIE\"");
    }

    @Test
    void outlierProducesAnAlertAndAnEnRevueVerdict() {                       // CA-3
        UUID articleId = UUID.randomUUID();

        submit(articleId, 100_000);

        Message alert = awaitMessageFor(Topology.FRAUDE_ALERTS, articleId);
        assertThat(EventSchemas.validate(EventTypes.FRAUD_ALERT, 1, alert.getBody())).isEmpty();
        Message verdict = awaitMessageFor(Topology.CATALOGUE_ARTICLE_CHECKED, articleId);
        assertThat(new String(verdict.getBody())).contains("\"verdict\":\"EN_REVUE\"", "price_outlier");
    }

    @Test
    void unreadableMessageGoesStraightToTheDeadLetterQueue() {
        rabbit.send(Topology.EXCHANGE, EventTypes.ARTICLE_SUBMITTED, MessageBuilder.withBody("pas du json".getBytes()).build());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rabbit.receive(Topology.DEAD_LETTER_QUEUE)).isNotNull());
    }
}
