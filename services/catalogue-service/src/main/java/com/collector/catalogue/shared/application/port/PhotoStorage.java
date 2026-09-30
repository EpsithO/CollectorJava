package com.collector.catalogue.shared.application.port;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface PhotoStorage {

    PresignedUpload prepareUpload(String key, String contentType);

    Optional<StoredObject> stat(String key);

    URI downloadUrl(String key);

    void delete(String key);

    record PresignedUpload(URI url, Map<String, String> headers, Instant expiresAt) {
    }

    record StoredObject(String key, String contentType, long sizeBytes) {
    }
}
