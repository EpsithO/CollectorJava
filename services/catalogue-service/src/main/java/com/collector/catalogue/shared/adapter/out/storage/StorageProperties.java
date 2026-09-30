package com.collector.catalogue.shared.adapter.out.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("collector.storage")
record StorageProperties(URI endpoint, URI publicEndpoint, String region, String bucket,
                         String accessKey, String secretKey,
                         Duration uploadTtl, Duration downloadTtl) {
}
