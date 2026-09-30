package com.collector.controle.pricecheck.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.collector.controle.pricecheck.domain.ArticleSubmitted;
import com.collector.controle.pricecheck.domain.PriceCheck;
import com.collector.controle.pricecheck.domain.PriceCheckPolicy;
import com.collector.controle.pricecheck.domain.PriceStats;
import com.collector.messaging.ConfirmedPublisher;
import com.collector.messaging.EventSchemas;
import com.collector.messaging.EventTypes;
import com.collector.messaging.OutgoingMessage;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** Contrat côté producteur : les messages du contrôle respectent les schémas d'article.checked et fraud.alert. */
class RabbitVerdictPublisherTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ArticleSubmitted ARTICLE =
            new ArticleSubmitted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100_000);
    private static final PriceStats STATS = new PriceStats(35, 25_000, 4_800.0);

    private final ConfirmedPublisher confirmed = mock(ConfirmedPublisher.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final RabbitVerdictPublisher publisher = new RabbitVerdictPublisher(confirmed,
            JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build(),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
            new VerdictProperties(TIMEOUT), metrics);

    private OutgoingMessage sent() {
        ArgumentCaptor<OutgoingMessage> captor = ArgumentCaptor.forClass(OutgoingMessage.class);
        verify(confirmed).publish(captor.capture(), eq(TIMEOUT));
        return captor.getValue();
    }

    @Test
    void verdictMessageRespectsTheArticleCheckedSchema() {
        when(confirmed.publish(any(), any())).thenReturn(true);
        PriceCheck check = PriceCheckPolicy.evaluate(100_000, STATS);

        publisher.publishVerdict(ARTICLE, check);

        OutgoingMessage message = sent();
        assertThat(message.routingKey()).isEqualTo(EventTypes.ARTICLE_CHECKED);
        assertThat(EventSchemas.validate(EventTypes.ARTICLE_CHECKED, 1, message.json())).isEmpty();
        assertThat(message.json()).contains("\"verdict\":\"EN_REVUE\"", "\"reason\":\"price_outlier\"",
                ARTICLE.articleId().toString());
    }

    @Test
    void publishedVerdictWithoutReasonStillRespectsTheSchema() {
        when(confirmed.publish(any(), any())).thenReturn(true);

        publisher.publishVerdict(ARTICLE, PriceCheckPolicy.evaluate(26_000, STATS));

        assertThat(EventSchemas.validate(EventTypes.ARTICLE_CHECKED, 1, sent().json())).isEmpty();
    }

    @Test
    void fraudAlertRespectsItsSchemaAndIsCounted() {
        when(confirmed.publish(any(), any())).thenReturn(true);

        publisher.raiseFraudAlert(ARTICLE, PriceCheckPolicy.evaluate(100_000, STATS), STATS);

        OutgoingMessage message = sent();
        assertThat(message.routingKey()).isEqualTo(EventTypes.FRAUD_ALERT);
        assertThat(EventSchemas.validate(EventTypes.FRAUD_ALERT, 1, message.json())).isEmpty();
        assertThat(message.json()).contains("\"kind\":\"PRIX_ANORMAL\"", "\"median_cents\":25000.0",
                "\"price_cents\":100000");
        assertThat(metrics.counter("collector.fraud.alerts").count()).isEqualTo(1.0);
    }

    @Test
    void anUnconfirmedPublicationRaisesSoTheIncomingMessageIsRetried() {
        when(confirmed.publish(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> publisher.publishVerdict(ARTICLE, PriceCheck.insufficientSample()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non confirmé");
        assertThatThrownBy(() -> publisher.raiseFraudAlert(ARTICLE, PriceCheckPolicy.evaluate(100_000, STATS), STATS))
                .isInstanceOf(IllegalStateException.class);
        assertThat(metrics.counter("collector.fraud.alerts").count()).isZero();   // seule une alerte publiée est comptée
    }
}
