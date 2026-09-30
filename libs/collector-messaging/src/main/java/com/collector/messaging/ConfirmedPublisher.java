package com.collector.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Publication avec accusés du broker, utilisée par les deux services. */
public class ConfirmedPublisher {

    private final RabbitTemplate rabbit;

    public ConfirmedPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /**
     * Envoie TOUT le lot, puis attend les confirmations avec UN délai global :
     * l'attente dure au plus {@code timeout}, quelle que soit la taille du lot.
     * Renvoie les événements confirmés par le broker ET reçus par au moins une file.
     */
    public Set<UUID> publishAll(List<OutgoingMessage> messages, Duration timeout) {
        Map<UUID, CorrelationData> pending = new LinkedHashMap<>();
        for (OutgoingMessage m : messages) {
            CorrelationData correlation = new CorrelationData(m.eventId().toString());
            rabbit.send(Topology.EXCHANGE, m.routingKey(), toAmqp(m), correlation);
            pending.put(m.eventId(), correlation);
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        Set<UUID> confirmed = new HashSet<>();
        for (Map.Entry<UUID, CorrelationData> entry : pending.entrySet()) {
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                CorrelationData.Confirm confirm =
                        entry.getValue().getFuture().get(remaining, TimeUnit.NANOSECONDS);
                // basic.return précède toujours basic.ack : getReturned() est à jour ici.
                if (confirm.ack() && entry.getValue().getReturned() == null) {
                    confirmed.add(entry.getKey());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException e) {
                // non confirmé : l'appelant réessaiera
            }
        }
        return confirmed;
    }

    /** Un seul message (controle-service, après traitement d'un événement entrant). */
    public boolean publish(OutgoingMessage message, Duration timeout) {
        return publishAll(List.of(message), timeout).contains(message.eventId());
    }

    private static Message toAmqp(OutgoingMessage m) {
        return MessageBuilder.withBody(m.json().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(m.eventId().toString())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
