package com.collector.acceptance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;

/**
 * Observe les événements SANS voler les messages des consommateurs : une file d'écoute `tests.audit`,
 * liée à l'échange collector.events avec `#`, reçoit une COPIE de chaque événement. On la lit par
 * l'API de management (POST /api/queues/%2F/tests.audit/get) et on la supprime à la fin.
 * Lire directement les files des services retirerait les messages que les services attendent.
 *
 * <p>Un curseur permet de ne considérer que les événements arrivés APRÈS une action : « aucun
 * événement n'est publié » ne doit pas être trompé par ceux d'une étape précédente du scénario.
 */
public final class EventAudit {

    private static final String QUEUE = "tests.audit";
    private static final String VHOST = "%2F";
    private static boolean declared;
    private static final List<JsonPath> seen = new ArrayList<>();

    private EventAudit() {
    }

    private static io.restassured.specification.RequestSpecification management() {
        Http.configure();
        return RestAssured.given().baseUri(Env.rabbitManagement())
                .auth().preemptive().basic(Env.rabbitUser(), Env.rabbitPassword())
                .contentType("application/json");
    }

    public static synchronized boolean isActive() {
        return declared;
    }

    /** Déclare la file d'écoute et la lie à l'échange (idempotent). À appeler avant l'action testée. */
    public static synchronized void start() {
        if (declared) {
            return;
        }
        management().body(Map.of("durable", false, "auto_delete", false))
                .put("/api/queues/" + VHOST + "/" + QUEUE).then().statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(201), org.hamcrest.Matchers.is(204)));
        management().body(Map.of("routing_key", "#"))
                .post("/api/bindings/" + VHOST + "/e/collector.events/q/" + QUEUE)
                .then().statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(201), org.hamcrest.Matchers.is(204)));
        declared = true;
    }

    /** Supprime la file d'écoute (fin d'exécution). */
    public static synchronized void stop() {
        if (!declared) {
            return;
        }
        management().delete("/api/queues/" + VHOST + "/" + QUEUE);
        declared = false;
        seen.clear();
    }

    /** Position courante : les événements déjà reçus sont derrière, seuls les suivants comptent. */
    public static synchronized int cursor() {
        drain();
        return seen.size();
    }

    /** Attend un événement du type donné, arrivé après le curseur, vérifiant le prédicat ; null si aucun. */
    public static JsonPath await(String type, Duration timeout, int fromIndex, Predicate<JsonPath> matching) {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            synchronized (EventAudit.class) {
                drain();
                for (int i = fromIndex; i < seen.size(); i++) {
                    JsonPath event = seen.get(i);
                    if (type.equals(event.getString("type")) && matching.test(event)) {
                        return event;
                    }
                }
            }
            sleep(250);
        } while (System.nanoTime() < deadline);
        return null;
    }

    private static void drain() {
        List<Map<String, Object>> messages = management()
                .body(Map.of("count", 100, "ackmode", "ack_requeue_false", "encoding", "auto"))
                .post("/api/queues/" + VHOST + "/" + QUEUE + "/get")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        for (Map<String, Object> message : messages) {
            seen.add(new JsonPath((String) message.get("payload")));
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
