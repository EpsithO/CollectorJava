package com.collector.acceptance;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/** Configuration REST Assured partagée : confiance TLS de la recette, jetons Keycloak, appels simples. */
public final class Http {

    private static boolean configured;

    private Http() {
    }

    /** Une seule fois par exécution : magasin de confiance de la recette (ca.crt) ou mode relâché. */
    public static synchronized void configure() {
        if (configured) {
            return;
        }
        String caFile = System.getProperty("collector.tls.ca");
        try {
            if (caFile != null && !caFile.isBlank()) {
                KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
                trust.load(null, null);
                try (InputStream in = new FileInputStream(caFile)) {
                    X509Certificate ca = (X509Certificate) CertificateFactory.getInstance("X.509")
                            .generateCertificate(in);
                    trust.setCertificateEntry("collector-ca", ca);
                }
                RestAssured.config = RestAssuredConfig.config().sslConfig(SSLConfig.sslConfig().trustStore(trust));
            } else if (Boolean.getBoolean("collector.tls.relaxed")) {
                RestAssured.useRelaxedHTTPSValidation();     // recette kind jetable uniquement
            }
        } catch (Exception e) {
            throw new IllegalStateException("Configuration TLS impossible", e);
        }
        configured = true;
    }

    /** Jeton d'accès par le flux mot de passe du client de test (dev uniquement, jamais en production). */
    public static String token(String username) {
        configure();
        return RestAssured.given()
                .baseUri(Env.keycloak())
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("client_id", "collector-tests")
                .formParam("username", username)
                .formParam("password", Env.testUserPassword())
                .post("/realms/collector/protocol/openid-connect/token")
                .then().statusCode(200)
                .extract().path("access_token");
    }

    /** Requête sur une base donnée, avec jeton si fourni (null = utilisateur non authentifié). */
    public static RequestSpecification on(String baseUri, String tokenOrNull) {
        configure();
        RequestSpecification spec = RestAssured.given().baseUri(baseUri).contentType("application/json");
        return tokenOrNull == null ? spec : spec.auth().oauth2(tokenOrNull);
    }

    public static Response send(String method, String baseUri, String path, String tokenOrNull, Object bodyOrNull) {
        RequestSpecification spec = on(baseUri, tokenOrNull);
        if (bodyOrNull != null) {
            spec = spec.body(bodyOrNull);
        }
        return spec.request(method, path);
    }

    public static Map<String, String> noHeaders() {
        return Map.of();
    }
}
