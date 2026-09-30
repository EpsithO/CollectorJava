package com.collector.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

/**
 * Validation des messages contre les schémas JSON du contrat (docs/events.md).
 * Un schéma par type et par version : {@code schemas/<type>.v<version>.json}.
 */
public final class EventSchemas {

    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

    private EventSchemas() {
    }

    /** Liste des erreurs de conformité ; vide si le message respecte le contrat. */
    public static List<String> validate(String type, int version, String json) {
        return schema(type, version).validate(json, InputFormat.JSON).stream()
                .map(Object::toString)
                .toList();
    }

    public static List<String> validate(String type, int version, byte[] json) {
        return validate(type, version, new String(json, StandardCharsets.UTF_8));
    }

    /** Lève {@link IllegalArgumentException} si le message ne respecte pas le schéma. */
    public static void requireValid(String type, int version, String json) {
        List<String> errors = validate(type, version, json);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(type + " v" + version + " non conforme : " + errors);
        }
    }

    private static Schema schema(String type, int version) {
        return CACHE.computeIfAbsent(type + ".v" + version, key -> {
            try (InputStream in = EventSchemas.class.getResourceAsStream("/schemas/" + key + ".json")) {
                if (in == null) {
                    throw new IllegalArgumentException("Schéma inconnu : " + key);
                }
                return REGISTRY.getSchema(in);
            } catch (IOException e) {
                throw new IllegalStateException("Schéma illisible : " + key, e);
            }
        });
    }
}
