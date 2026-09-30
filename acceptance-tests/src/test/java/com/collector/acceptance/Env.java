package com.collector.acceptance;

import java.util.Map;

/**
 * Adresses de la pile selon l'environnement : -Dcollector.env=local (compose) ou recette (kind,
 * HTTPS avec l'autorité locale). Les adresses sont surchargeables une à une par variable
 * d'environnement (COLLECTOR_API_URL, COLLECTOR_NOTIFICATION_URL, COLLECTOR_KEYCLOAK_URL,
 * COLLECTOR_RABBIT_URL).
 *
 * <p>HTTPS en recette : l'autorité est celle de cert-manager. Deux options :
 * <ul>
 *   <li>recommandée : {@code kubectl -n collector get secret collector-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > ca.crt},
 *       puis {@code -Dcollector.tls.ca=ca.crt} : le certificat est ajouté au magasin de confiance des tests ;</li>
 *   <li>recette kind jetable seulement : {@code -Dcollector.tls.relaxed=true} désactive la vérification.</li>
 * </ul>
 */
public final class Env {

    private static final String NAME = System.getProperty("collector.env", "local");

    private static final Map<String, Map<String, String>> DEFAULTS = Map.of(
            "local", Map.of(
                    "api", "http://localhost:8080",
                    "notification", "http://localhost:8082",
                    "keycloak", "http://localhost:8081",
                    "rabbit", "http://localhost:15672"),
            "recette", Map.of(
                    "api", "https://api.collector.local",
                    "notification", "https://api.collector.local",
                    "keycloak", "https://auth.collector.local",
                    "rabbit", "http://localhost:15672"));

    private Env() {
    }

    public static String name() {
        return NAME;
    }

    public static String api() {
        return url("api", "COLLECTOR_API_URL");
    }

    /** En recette, l'Ingress route /api/v1/me/notifications vers le notification-service sur le même hôte. */
    public static String notification() {
        return url("notification", "COLLECTOR_NOTIFICATION_URL");
    }

    public static String keycloak() {
        return url("keycloak", "COLLECTOR_KEYCLOAK_URL");
    }

    public static String rabbitManagement() {
        return url("rabbit", "COLLECTOR_RABBIT_URL");
    }

    /** Compte de management RabbitMQ (mêmes variables que compose). */
    public static String rabbitUser() {
        return System.getenv().getOrDefault("RABBITMQ_USER", "collector");
    }

    public static String rabbitPassword() {
        return required("RABBITMQ_PASSWORD");
    }

    public static String testUserPassword() {
        return required("KC_TEST_USER_PASSWORD");
    }

    private static String url(String key, String variable) {
        String override = System.getenv(variable);
        if (override != null && !override.isBlank()) {
            return override;
        }
        Map<String, String> defaults = DEFAULTS.get(NAME);
        if (defaults == null) {
            throw new IllegalStateException("collector.env inconnu : " + NAME + " (local ou recette)");
        }
        return defaults.get(key);
    }

    // Un secret absent arrête les tests avec un message clair : aucune valeur par défaut.
    private static String required(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variable d'environnement manquante : " + variable
                    + " (charger le .env : set -a; . ./.env; set +a)");
        }
        return value;
    }
}
