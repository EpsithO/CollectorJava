package com.collector.notification.notification.adapter.in.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.collector.messaging.Topology;
import com.collector.notification.notification.application.NotifyPriceChange;
import com.collector.notification.notification.application.RecordFollowChange;
import com.collector.notification.notification.application.RecordInterests;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Les consommateurs du notification-service : une file par type d'événement (une ligne dans la
 * topologie par consommateur, c'est tout l'intérêt du bus). Acquittement automatique au retour :
 * on ne revient qu'une fois l'effet écrit en base. Exception transitoire = remise en file
 * (5 fois, puis lettres mortes) ; chaque traitement est idempotent (livraison au moins une fois).
 */
@Component
class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

    private final EventDecoder decoder;
    private final NotifyPriceChange notifyPriceChange;
    private final RecordFollowChange recordFollowChange;
    private final RecordInterests recordInterests;
    private final MeterRegistry metrics;

    NotificationListeners(EventDecoder decoder, NotifyPriceChange notifyPriceChange,
                          RecordFollowChange recordFollowChange, RecordInterests recordInterests,
                          MeterRegistry metrics) {
        this.decoder = decoder;
        this.notifyPriceChange = notifyPriceChange;
        this.recordFollowChange = recordFollowChange;
        this.recordInterests = recordInterests;
        this.metrics = metrics;
    }

    @RabbitListener(queues = Topology.NOTIFICATION_PRICE_CHANGED)
    void onPriceChanged(Message message) {
        int created = notifyPriceChange.execute(decoder.priceChange(message));
        metrics.counter("collector.notifications.created").increment(created);
        log.info("price.changed traité : {} notification(s) créée(s)", created);
    }

    @RabbitListener(queues = Topology.NOTIFICATION_FOLLOW_CHANGED)
    void onFollowChanged(Message message) {
        recordFollowChange.execute(decoder.followChange(message));
    }

    @RabbitListener(queues = Topology.NOTIFICATION_INTERESTS_UPDATED)
    void onInterestsUpdated(Message message) {
        EventDecoder.Interests interests = decoder.interests(message);
        recordInterests.execute(interests.memberId(), interests.categoryIds());
    }

    // Le vendeur sera prévenu de la décision de l'admin en V2 (il faudrait son sub dans l'événement).
    // La file existe et doit être vidée : un événement publié sans file liée bloquerait l'outbox.
    @RabbitListener(queues = Topology.NOTIFICATION_ARTICLE_REVIEWED)
    void onArticleReviewed(Message message) {
        metrics.counter("collector.reviews.seen").increment();
    }
}
