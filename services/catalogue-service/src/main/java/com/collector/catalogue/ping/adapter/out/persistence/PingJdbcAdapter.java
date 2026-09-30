package com.collector.catalogue.ping.adapter.out.persistence;

import java.sql.Timestamp;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.collector.catalogue.ping.application.port.PingRepository;
import com.collector.catalogue.ping.domain.Ping;

@Component
class PingJdbcAdapter implements PingRepository {

    private final JdbcClient jdbc;

    PingJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Ping save(Ping ping) {
        Long id = jdbc.sql("INSERT INTO ping (payload, created_at) VALUES (:payload, :at) RETURNING id")
                .param("payload", ping.payload())
                .param("at", Timestamp.from(ping.createdAt()))
                .query(Long.class)
                .single();
        return new Ping(id, ping.payload(), ping.createdAt());
    }
}
