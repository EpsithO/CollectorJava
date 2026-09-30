package com.collector.catalogue.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.collector.catalogue.AbstractIntegrationTest;
import com.collector.catalogue.ping.application.CreatePing;
import com.collector.messaging.EventSchemas;
import com.collector.messaging.EventTypes;
import com.collector.messaging.Topology;

/** La chaîne la plus importante du socle : cas d'usage, outbox, relais, broker, contrat. */
class OutboxIT extends AbstractIntegrationTest {

    @Autowired CreatePing createPing;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    @Test
    void pingIsPublishedThroughOutbox() {
        createPing.execute("it");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message message = rabbit.receive(Topology.CATALOGUE_PING);
            assertThat(message).isNotNull();
            assertThat(EventSchemas.validate(EventTypes.PING_CREATED, 1, message.getBody())).isEmpty();
        });
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL")
                        .query(Long.class).single()).isZero());
    }
}
