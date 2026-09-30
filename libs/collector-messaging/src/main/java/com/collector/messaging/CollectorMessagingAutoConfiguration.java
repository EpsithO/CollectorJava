package com.collector.messaging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Source de vérité unique de la topologie : RabbitAdmin la déclare à la connexion.
 * Deux déclarations d'une même file avec des arguments différents sont refusées (406).
 */
@AutoConfiguration
public class CollectorMessagingAutoConfiguration {

    @Bean
    ConfirmedPublisher confirmedPublisher(RabbitTemplate rabbitTemplate) {
        return new ConfirmedPublisher(rabbitTemplate);
    }

    @Bean
    Declarables collectorTopology() {
        TopicExchange events = new TopicExchange(Topology.EXCHANGE, true, false);
        TopicExchange dlx = new TopicExchange(Topology.DEAD_LETTER_EXCHANGE, true, false);
        Queue deadLetters = QueueBuilder.durable(Topology.DEAD_LETTER_QUEUE).quorum().build();

        List<Declarable> declarables = new ArrayList<>(List.of(
                events, dlx, deadLetters,
                BindingBuilder.bind(deadLetters).to(dlx).with("#")));

        // Une file par consommateur et par type : ajouter un consommateur = une ligne.
        bind(declarables, events, Topology.CONTROLE_ARTICLE_SUBMITTED, EventTypes.ARTICLE_SUBMITTED);
        bind(declarables, events, Topology.CATALOGUE_ARTICLE_CHECKED, EventTypes.ARTICLE_CHECKED);
        bind(declarables, events, Topology.FRAUDE_ALERTS, EventTypes.FRAUD_ALERT);
        bind(declarables, events, Topology.FRAUDE_PRICE_CHANGED, EventTypes.PRICE_CHANGED);
        bind(declarables, events, Topology.NOTIFICATION_PRICE_CHANGED, EventTypes.PRICE_CHANGED);
        bind(declarables, events, Topology.NOTIFICATION_INTERESTS_UPDATED, EventTypes.INTERESTS_UPDATED);
        bind(declarables, events, Topology.NOTIFICATION_ARTICLE_REVIEWED, EventTypes.ARTICLE_REVIEWED);
        bind(declarables, events, Topology.NOTIFICATION_FOLLOW_CHANGED, EventTypes.FOLLOW_CHANGED);
        bind(declarables, events, Topology.CATALOGUE_PING, EventTypes.PING_CREATED);

        return new Declarables(declarables);
    }

    // File quorum répliquée ; après 5 échecs, le message part en lettres mortes.
    private static void bind(List<Declarable> declarables, TopicExchange exchange,
                             String queueName, String routingKey) {
        Queue queue = QueueBuilder.durable(queueName)
                .quorum()
                .deadLetterExchange(Topology.DEAD_LETTER_EXCHANGE)
                .deliveryLimit(Topology.DELIVERY_LIMIT)
                .build();
        declarables.add(queue);
        declarables.add(BindingBuilder.bind(queue).to(exchange).with(routingKey));
    }
}
