package com.collector.catalogue.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class ClockConfig {

    // Horloge injectée : les tests contrôlent le temps.
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
