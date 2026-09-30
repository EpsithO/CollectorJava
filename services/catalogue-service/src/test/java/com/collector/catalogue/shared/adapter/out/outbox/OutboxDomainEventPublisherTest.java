package com.collector.catalogue.shared.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.collector.catalogue.article.domain.ArticleReviewed;
import com.collector.catalogue.article.domain.ArticleSubmitted;
import com.collector.catalogue.article.domain.FollowChanged;
import com.collector.catalogue.article.domain.PriceChanged;
import com.collector.catalogue.interest.domain.InterestsUpdated;
import com.collector.catalogue.ping.domain.PingCreated;
import com.collector.catalogue.shared.domain.DomainEvent;
import com.collector.messaging.EventSchemas;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test de contrat côté producteur, sans Docker : chaque événement du domaine, sérialisé comme
 * en production (snake_case), doit respecter son schéma JSON. C'est ce qui rend le contrat réel.
 */
class OutboxDomainEventPublisherTest {

    private static final UUID ID = UUID.fromString("8f0c7a1e-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final OutboxDomainEventPublisher publisher =
            new OutboxDomainEventPublisher(repository, json, Clock.fixed(NOW, ZoneOffset.UTC));

    private String publish(DomainEvent event) {
        publisher.publish(event);
        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(saved.capture());
        return saved.getValue().payload();
    }

    @Test
    void everyDomainEventRespectsItsJsonSchema() {
        List<DomainEvent> events = List.of(
                new ArticleSubmitted(ID, ID, ID, 26_000, "EUR", 1),
                new PriceChanged(ID, 3, ID, ID, "Air Jordan 1", 25_000, 24_000, "EUR"),
                new ArticleReviewed(ID, ID, "REJETE", "photos floues", ID),
                new ArticleReviewed(ID, ID, "PUBLIE", null, ID),
                new FollowChanged(ID, ID, true, NOW),
                new InterestsUpdated(ID, List.of(ID)),
                new PingCreated(1, "bonjour", NOW));

        for (DomainEvent event : events) {
            String body = publishFresh(event);
            assertThat(EventSchemas.validate(event.type(), 1, body)).as(event.type() + " : " + body).isEmpty();
        }
    }

    @Test
    void envelopeIsSnakeCaseWithTheEventTypeAsRoutingKey() {
        String body = publish(new PriceChanged(ID, 3, ID, ID, "Air Jordan 1", 25_000, 24_000, "EUR"));

        assertThat(body).contains("\"event_id\"", "\"occurred_at\":\"2026-01-01T00:00:00Z\"", "\"type\":\"price.changed\"",
                "\"aggregate_version\":3", "\"old_price_cents\":25000", "\"new_price_cents\":24000");
        // Le champ type() de l'interface n'est pas du contenu : il ne doit pas fuiter dans data.
        assertThat(json.readTree(body).path("data").has("type")).isFalse();
    }

    @Test
    void anEventThatBreaksTheContractNeverReachesTheOutbox() {
        // Devise de 4 lettres : refusée par le schéma de article.submitted.
        assertThatThrownBy(() -> publisher.publish(new ArticleSubmitted(ID, ID, ID, 26_000, "EURO", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    private String publishFresh(DomainEvent event) {
        org.mockito.Mockito.clearInvocations(repository);
        return publish(event);
    }
}
