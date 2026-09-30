package com.collector.acceptance;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.restassured.path.json.JsonPath;

/** Lit le claim « sub » d'un jeton (sans vérifier la signature : les tests n'en ont pas besoin). */
final class Jwt {

    private Jwt() {
    }

    static String subject(String token) {
        if (token == null) {
            throw new IllegalStateException("Aucun jeton : l'utilisateur n'est pas authentifié");
        }
        String payload = token.split("\\.")[1];
        return new JsonPath(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8))
                .getString("sub");
    }
}
