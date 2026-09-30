package com.collector.catalogue.shared.adapter.out.outbox;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.collector.messaging.ConfirmedPublisher;
import com.collector.messaging.OutgoingMessage;

@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore store;
    private final ConfirmedPublisher publisher;
    private final Clock clock;
    private final OutboxProperties props;

    OutboxRelay(OutboxStore store, ConfirmedPublisher publisher, Clock clock, OutboxProperties props) {
        this.store = store;
        this.publisher = publisher;
        this.clock = clock;
        this.props = props;
    }

    // Au moins une fois : un événement confirmé mais pas encore marqué (pod tué
    // entre les deux) sera republié à l'expiration du bail. Consommateurs idempotents.
    //
    // PAS D'ORDRE GARANTI entre événements : plusieurs réplicas relaient en
    // parallèle et un lot non confirmé est repris plus tard ; deux price.changed
    // du même article peuvent donc arriver inversés. Les consommateurs qui en
    // dépendent comparent aggregate_version (docs/events.md, ADR 0005).
    @Scheduled(fixedDelayString = "${collector.outbox.relay-interval-ms}")
    public void relay() {
        try {
            List<OutgoingMessage> batch = store.claim(props.batchSize(), props.lease());      // 1. réserver
            if (batch.isEmpty()) {
                return;
            }
            Set<UUID> confirmed = publisher.publishAll(batch, props.confirmTimeout());        // 2. envoyer, sans connexion SQL
            store.markPublished(confirmed, clock.instant());                                  // 3. marquer
            if (confirmed.size() < batch.size()) {
                log.warn("{} événement(s) non confirmé(s), repris à l'expiration du bail",
                        batch.size() - confirmed.size());
            }
        } catch (RuntimeException e) {
            // Base ou broker indisponible : le relais réessaiera au prochain tour.
            log.warn("Relais de l'outbox en échec : {}", e.toString());
        }
    }
}
