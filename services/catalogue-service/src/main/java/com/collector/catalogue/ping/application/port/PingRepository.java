package com.collector.catalogue.ping.application.port;

import com.collector.catalogue.ping.domain.Ping;

public interface PingRepository {

    /** Renvoie le ping avec son identifiant. */
    Ping save(Ping ping);
}
