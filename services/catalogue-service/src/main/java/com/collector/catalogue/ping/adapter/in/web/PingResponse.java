package com.collector.catalogue.ping.adapter.in.web;

import java.time.Instant;

import com.collector.catalogue.ping.domain.Ping;

record PingResponse(long id, String payload, Instant createdAt) {

    static PingResponse from(Ping ping) {
        return new PingResponse(ping.id(), ping.payload(), ping.createdAt());
    }
}
