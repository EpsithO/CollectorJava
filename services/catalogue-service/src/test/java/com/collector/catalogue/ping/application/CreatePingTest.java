package com.collector.catalogue.ping.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.ping.domain.Ping;
import com.collector.catalogue.ping.domain.PingCreated;
import com.collector.catalogue.shared.domain.DomainEvent;

class CreatePingTest {

    private final List<DomainEvent> published = new ArrayList<>();
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final CreatePing createPing = new CreatePing(
            ping -> new Ping(42L, ping.payload(), ping.createdAt()),
            published::add,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void savesThePingAndPublishesItsEvent() {
        Ping ping = createPing.execute("bonjour");

        assertThat(ping.id()).isEqualTo(42L);
        assertThat(published).singleElement().isEqualTo(new PingCreated(42L, "bonjour", now));
        assertThat(published.getFirst().type()).isEqualTo("ping.created");
    }
}
