package com.collector.controle.pricecheck.adapter.out.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("collector.publish")
record VerdictProperties(Duration confirmTimeout) {
}
