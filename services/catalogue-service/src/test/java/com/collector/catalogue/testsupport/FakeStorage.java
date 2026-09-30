package com.collector.catalogue.testsupport;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.collector.catalogue.shared.application.port.PhotoStorage;

public class FakeStorage implements PhotoStorage {

    public final Map<String, StoredObject> objects = new HashMap<>();
    public final List<String> deleted = new ArrayList<>();

    /** Simule l'envoi d'une photo par le navigateur vers le stockage. */
    public void upload(String key, String contentType, long sizeBytes) {
        objects.put(key, new StoredObject(key, contentType, sizeBytes));
    }

    @Override
    public PresignedUpload prepareUpload(String key, String contentType) {
        return new PresignedUpload(URI.create("https://storage.test/upload/" + key),
                Map.of("Content-Type", contentType), Instant.parse("2026-01-01T00:05:00Z"));
    }

    @Override
    public Optional<StoredObject> stat(String key) {
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public URI downloadUrl(String key) {
        return URI.create("https://storage.test/read/" + key);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        deleted.add(key);
    }
}
