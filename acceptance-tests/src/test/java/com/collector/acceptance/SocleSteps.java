package com.collector.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.collector.messaging.EventSchemas;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.path.json.JsonPath;

/** Socle : routes publiques, erreurs sans détail technique, chaîne outbox vers RabbitMQ, topologie. */
public class SocleSteps {

    private final World world;
    private JsonPath pingEvent;

    public SocleSteps(World world) {
        this.world = world;
    }

    @When("il consulte la liste des catégories")
    public void heViewsTheCategories() {
        world.response = Http.on(Env.api(), world.token).get("/api/v1/categories");
    }

    @When("il envoie un ping {string}")
    public void heSendsAPing(String payload) {
        world.markEvents();
        world.response = Http.on(Env.api(), world.token).body(Map.of("payload", payload)).post("/api/v1/pings");
    }

    @When("il envoie un ping sans contenu")
    public void heSendsAnEmptyPing() {
        world.response = Http.on(Env.api(), world.token).body(Map.of("payload", "")).post("/api/v1/pings");
    }

    @When("il envoie un corps illisible sur les pings")
    public void heSendsGarbage() {
        world.response = Http.on(Env.api(), world.token).body("{pas du json").post("/api/v1/pings");
    }

    @Then("la liste contient les catégories de démonstration")
    public void theListContainsTheSeededCategories() {
        List<String> slugs = world.response.jsonPath().getList("slug");
        assertThat(slugs).contains("sneakers", "posters", "figurines", "cassettes", "bd");
    }

    @Then("un événement {string} conforme à son schéma est publié sous {int} secondes")
    public void aConformingPingEventIsPublished(String type, int seconds) {
        long id = ((Number) world.response.path("id")).longValue();
        pingEvent = EventAudit.await(type, Duration.ofSeconds(seconds), world.eventCursor,
                e -> ((Number) e.get("data.id")).longValue() == id);
        assertThat(pingEvent).as("événement %s pour le ping %d", type, id).isNotNull();
        assertThat(EventSchemas.validate(type, 1, pingEvent.prettify())).isEmpty();
    }

    @Then("la topologie de l'échange {string} contient les liaisons")
    public void theTopologyContains(String exchange, DataTable table) {
        assertThat(exchange).isEqualTo("collector.events");
        for (Map<String, String> row : table.asMaps()) {
            assertThat(CommonSteps.bindingsOf(row.get("clé de routage")))
                    .as("files liées à %s", row.get("clé de routage"))
                    .contains(row.get("file"));
        }
    }
}
