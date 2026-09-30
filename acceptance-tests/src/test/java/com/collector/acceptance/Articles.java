package com.collector.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.response.Response;

/** Gestes réutilisés par plusieurs user stories : créer un brouillon, envoyer une photo, soumettre, attendre un statut. */
public final class Articles {

    private static byte[] jpeg;

    private Articles() {
    }

    public static synchronized byte[] jpeg() {
        if (jpeg == null) {
            try (InputStream in = Articles.class.getResourceAsStream("/photo.jpg.b64")) {
                jpeg = Base64.getMimeDecoder().decode(new String(in.readAllBytes()));
            } catch (IOException e) {
                throw new IllegalStateException("Photo de test illisible", e);
            }
        }
        return jpeg;
    }

    public static UUID categoryId(String slug) {
        List<Map<String, Object>> all = Http.on(Env.api(), null).get("/api/v1/categories")
                .then().statusCode(200).extract().jsonPath().getList("$");
        return all.stream().filter(c -> slug.equals(c.get("slug"))).map(c -> UUID.fromString((String) c.get("id")))
                .findFirst().orElseThrow(() -> new IllegalStateException("Catégorie inconnue : " + slug));
    }

    public static Map<String, Object> draftBody(String categorySlug, long priceCents) {
        return Map.of("title", "Air Jordan 1 test " + UUID.randomUUID().toString().substring(0, 8),
                "description", "Paire neuve jamais portée, boîte d'origine",
                "category_id", categoryId(categorySlug).toString(),
                "price_cents", priceCents, "shipping_cents", 500, "attributes", Map.of("taille", 42));
    }

    /** POST /articles ; renvoie la réponse (201 attendu). */
    public static Response createDraft(String token, String categorySlug, long priceCents) {
        return Http.on(Env.api(), token).body(draftBody(categorySlug, priceCents)).post("/api/v1/articles");
    }

    /**
     * Demande une URL d'envoi, puis envoie réellement la photo sur l'URL pré-signée avec les en-têtes
     * signés : le binaire ne transite jamais par l'API.
     */
    public static void uploadPhoto(String token, UUID articleId) {
        byte[] bytes = jpeg();
        Response slot = Http.on(Env.api(), token)
                .body(Map.of("content_type", "image/jpeg", "size_bytes", bytes.length))
                .post("/api/v1/articles/" + articleId + "/photos");
        slot.then().statusCode(201);
        String url = slot.path("upload_url");
        Map<String, String> headers = slot.path("upload_headers");

        // Content-Type signé : REST Assured ne doit pas y ajouter « ;charset=… », sinon la signature est invalide.
        Response put = RestAssured.given()
                .config(RestAssured.config().encoderConfig(
                        EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .headers(headers)
                .body(bytes)
                .put(URI.create(url));
        if (put.statusCode() / 100 != 2) {
            throw new IllegalStateException("Envoi de la photo refusé par le stockage : HTTP " + put.statusCode());
        }
    }

    public static Response submit(String token, UUID articleId) {
        return Http.on(Env.api(), token).post("/api/v1/articles/" + articleId + "/submission");
    }

    /** Brouillon + n photos envoyées ; renvoie l'identifiant de l'article. */
    public static UUID draftWithPhotos(String token, String categorySlug, long priceCents, int photos) {
        Response created = createDraft(token, categorySlug, priceCents);
        created.then().statusCode(201);
        UUID id = UUID.fromString(created.path("id"));
        for (int i = 0; i < photos; i++) {
            uploadPhoto(token, id);
        }
        return id;
    }

    /** Relit l'article (avec le jeton de son vendeur) jusqu'à ce qu'il atteigne le statut, ou échoue. */
    public static boolean awaitStatus(String token, UUID articleId, String status, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            String current = Http.on(Env.api(), token).get("/api/v1/articles/" + articleId).path("status");
            if (status.equals(current)) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.nanoTime() < deadline);
        return false;
    }

    /** Un article réellement publié : brouillon à un prix cohérent, photo, soumission, contrôle automatique. */
    public static UUID publishedArticle(String token) {
        UUID id = draftWithPhotos(token, "sneakers", 26_000, 1);
        submit(token, id).then().statusCode(200);
        if (!awaitStatus(token, id, "PUBLIE", Duration.ofSeconds(15))) {
            throw new IllegalStateException("L'article n'a pas été publié par le contrôle automatique");
        }
        return id;
    }

    /** Un article mis en revue : prix très au-dessus de la médiane des sneakers (règle des 3 écarts-types). */
    public static UUID articleInReview(String token) {
        UUID id = draftWithPhotos(token, "sneakers", 100_000, 1);
        submit(token, id).then().statusCode(200);
        if (!awaitStatus(token, id, "EN_REVUE", Duration.ofSeconds(15))) {
            throw new IllegalStateException("L'article n'est pas passé EN_REVUE");
        }
        return id;
    }
}
