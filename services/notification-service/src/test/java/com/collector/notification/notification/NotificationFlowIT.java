package com.collector.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.collector.messaging.EventTypes;
import com.collector.messaging.Topology;
import com.collector.notification.AbstractIntegrationTest;
import com.collector.notification.notification.application.ListNotifications;
import com.collector.notification.notification.application.NotifyPriceChange;
import com.collector.notification.notification.application.RecordFollowChange;
import com.collector.notification.notification.domain.FollowChange;
import com.collector.notification.notification.domain.PriceChange;

/**
 * US-029 avec PostgreSQL et RabbitMQ réels : les instructions SQL atomiques tiennent leurs
 * promesses (ordre, doublons, propriété) et le service consomme bien les événements du catalogue.
 */
class NotificationFlowIT extends AbstractIntegrationTest {

    @Autowired NotifyPriceChange notifyPriceChange;
    @Autowired RecordFollowChange recordFollowChange;
    @Autowired ListNotifications listNotifications;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    private void publish(String type, String data) {
        String body = """
                {"event_id":"%s","type":"%s","version":1,"occurred_at":"2026-01-01T00:00:00Z","data":%s}"""
                .formatted(UUID.randomUUID(), type, data);
        rabbit.send(Topology.EXCHANGE, type, MessageBuilder.withBody(body.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
    }

    @Test
    void sqlKeepsTheLatestFollowChangeAndTheLatestPriceWhateverTheOrder() {
        UUID member = UUID.randomUUID();
        UUID article = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-03-01T10:00:00Z");

        // « Ne suit plus » (t0+10) reçu avant « suit » (t0) : l'abonné ne doit pas revenir.
        assertThat(recordFollowChange.execute(new FollowChange(member, article, false, t0.plusSeconds(10)))).isTrue();
        assertThat(recordFollowChange.execute(new FollowChange(member, article, true, t0))).isFalse();
        assertThat(notifyPriceChange.execute(new PriceChange(article, 2, "Titre", 100, 90, "EUR"))).isZero();

        recordFollowChange.execute(new FollowChange(member, article, true, t0.plusSeconds(20)));
        assertThat(notifyPriceChange.execute(new PriceChange(article, 4, "Titre", 90, 80, "EUR"))).isEqualTo(1);
        assertThat(notifyPriceChange.execute(new PriceChange(article, 3, "Titre", 100, 90, "EUR"))).isZero();
        assertThat(notifyPriceChange.execute(new PriceChange(article, 4, "Titre", 90, 80, "EUR"))).isZero();

        assertThat(listNotifications.execute(member, false)).singleElement()
                .satisfies(n -> assertThat(n.newPriceCents()).isEqualTo(80));
    }

    @Test
    void consumesFollowAndPriceEventsFromTheBus() {
        UUID member = UUID.randomUUID();
        UUID article = UUID.randomUUID();
        publish(EventTypes.FOLLOW_CHANGED, """
                {"member_id":"%s","article_id":"%s","following":true,"changed_at":"2026-03-01T10:00:00Z"}"""
                .formatted(member, article));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM notification_follow WHERE article_id = :a AND following")
                        .param("a", article).query(Long.class).single()).isEqualTo(1));

        publish(EventTypes.PRICE_CHANGED, """
                {"article_id":"%s","aggregate_version":2,"seller_id":"%s","category_id":"%s","title":"Air Jordan 1",
                 "old_price_cents":25000,"new_price_cents":24000,"currency":"EUR"}"""
                .formatted(article, UUID.randomUUID(), UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(listNotifications.execute(member, true)).singleElement().satisfies(n -> {
                    assertThat(n.articleTitle()).isEqualTo("Air Jordan 1");
                    assertThat(n.oldPriceCents()).isEqualTo(25_000);
                }));
    }

    @Test
    void copiesInterestsAndUnreadableMessagesGoToTheDeadLetterQueue() {
        UUID member = UUID.randomUUID();
        List<UUID> categories = List.of(UUID.randomUUID(), UUID.randomUUID());
        publish(EventTypes.INTERESTS_UPDATED, """
                {"member_id":"%s","category_ids":["%s","%s"]}""".formatted(member, categories.get(0), categories.get(1)));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM notification_interest WHERE member_id = :m")
                        .param("m", member).query(Long.class).single()).isEqualTo(2));

        rabbit.send(Topology.EXCHANGE, EventTypes.PRICE_CHANGED, MessageBuilder.withBody("pas du json".getBytes()).build());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rabbit.receive(Topology.DEAD_LETTER_QUEUE)).isNotNull());
    }
}
