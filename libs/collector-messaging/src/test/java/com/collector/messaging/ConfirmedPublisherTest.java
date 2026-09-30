package com.collector.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ConfirmedPublisherTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final ConfirmedPublisher publisher = new ConfirmedPublisher(rabbit);

    private static OutgoingMessage message() {
        return new OutgoingMessage(UUID.randomUUID(), EventTypes.PING_CREATED, "{}");
    }

    private void broker(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(new org.springframework.amqp.core.ReturnedMessage(
                        new Message(new byte[0]), 312, "NO_ROUTE", Topology.EXCHANGE, "k"));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, null));
            return null;
        }).when(rabbit).send(eq(Topology.EXCHANGE), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void ackedMessageIsConfirmed() {
        broker(true, false);
        OutgoingMessage m = message();
        assertThat(publisher.publish(m, Duration.ofSeconds(1))).isTrue();
    }

    @Test
    void nackedMessageIsNotConfirmed() {
        broker(false, false);
        assertThat(publisher.publishAll(List.of(message()), Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void returnedMessageIsNotConfirmed() {
        broker(true, true);
        assertThat(publisher.publishAll(List.of(message()), Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void silentBrokerTimesOut() {
        OutgoingMessage m = message();
        assertThat(publisher.publish(m, Duration.ofMillis(50))).isFalse();
    }
}
