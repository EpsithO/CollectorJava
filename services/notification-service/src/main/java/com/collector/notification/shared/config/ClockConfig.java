package com.collector.notification.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfig {

    // Horloge injectée : les tests contrôlent le temps.
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
