package com.collector.catalogue.shared.adapter.out.outbox;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** collector_outbox_pending : alerte si le relais ne suit plus (runbook, docs/exploitation.md). */
@Component
class OutboxMetrics {

    OutboxMetrics(MeterRegistry registry, OutboxStore store) {
        Gauge.builder("collector.outbox.pending", store, OutboxStore::pendingCount)
                .description("Événements en attente de publication")
                .register(registry);
    }
}
