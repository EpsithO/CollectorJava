package com.collector.catalogue.shared.adapter.out.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("collector.outbox")
record OutboxProperties(long relayIntervalMs, int batchSize, Duration lease, Duration confirmTimeout) {
}
