package com.collector.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.restassured.path.json.JsonPath;

/** Étapes communes à toutes les user stories : identité, code de réponse, champs, événements. */
public class CommonSteps {

    private final World world;

    public CommonSteps(World world) {
        this.world = world;
    }

    // --- identité -------------------------------------------------------------------------

    @Given("un vendeur authentifié {string}")
    public void aSellerAuthenticated(String username) {
        world.authenticateAs(username);
    }

    @Given("l'utilisateur authentifié {string}")
    public void theAuthenticatedUser(String username) {
        world.authenticateAs(username);
    }

    @Given("un utilisateur non authentifié")
    public void anAnonymousUser() {
        world.anonymous();
    }

    // --- vérifications de la dernière réponse -----------------------------------------------

    @Then("le code de réponse est {int}")
    public void theResponseCodeIs(int code) {
        assertThat(world.response.statusCode())
                .as("réponse : %s", world.response.asString())
                .isEqualTo(code);
    }

    @Then("le champ {string} vaut {string}")
    public void theFieldIs(String field, String expected) {
        assertThat(world.response.jsonPath().getString(field)).isEqualTo(expected);
    }

    @Then("la réponse ne révèle aucun détail technique")
    public void theResponseHasNoTechnicalDetail() {
        String body = world.response.asString();
        assertThat(body).doesNotContain("Exception", "at com.", "at org.", "SELECT ", "java.", "stack");
        assertThat(world.response.contentType()).startsWith("application/problem+json");
        assertThat(world.response.jsonPath().getString("code")).isNotBlank();
    }

    // --- événements observés par la file d'écoute --------------------------------------------

    @Then("un événement {string} est publié pour cet article sous {int} secondes")
    public void anEventIsPublishedForThisArticle(String type, int seconds) {
        JsonPath event = EventAudit.await(type, Duration.ofSeconds(seconds), world.eventCursor,
                e -> world.articleId.toString().equals(e.getString("data.article_id")));
        assertThat(event).as("événement %s pour l'article %s", type, world.articleId).isNotNull();
    }

    @Then("un événement {string} est publié sous {int} secondes")
    public void anEventIsPublishedForTheUser(String type, int seconds) {
        String member = Jwt.subject(world.token);
        JsonPath event = EventAudit.await(type, Duration.ofSeconds(seconds), world.eventCursor,
                e -> member.equals(e.getString("data.member_id")));
        assertThat(event).as("événement %s pour le membre %s", type, member).isNotNull();
    }

    @Then("aucun événement {string} n'est publié sous {int} secondes")
    public void noEventIsPublished(String type, int seconds) {
        String member = Jwt.subject(world.token);
        JsonPath event = EventAudit.await(type, Duration.ofSeconds(seconds), world.eventCursor,
                e -> member.equals(e.getString("data.member_id")));
        assertThat(event).as("aucun événement %s attendu", type).isNull();
    }

    @Then("la clé {string} est routée vers les files {string} et {string}")
    public void theKeyIsRoutedToTheQueues(String routingKey, String first, String second) {
        List<String> destinations = bindingsOf(routingKey);
        assertThat(destinations).contains(first, second);
    }

    /** Files liées à l'échange pour une clé de routage, d'après l'API de management RabbitMQ. */
    static List<String> bindingsOf(String routingKey) {
        Http.configure();
        List<Map<String, Object>> bindings = io.restassured.RestAssured.given()
                .baseUri(Env.rabbitManagement())
                .auth().preemptive().basic(Env.rabbitUser(), Env.rabbitPassword())
                .get("/api/exchanges/%2F/collector.events/bindings/source")
                .then().statusCode(200).extract().jsonPath().getList("$");
        return bindings.stream()
                .filter(b -> routingKey.equals(b.get("routing_key")) && "queue".equals(b.get("destination_type")))
                .map(b -> (String) b.get("destination"))
                .toList();
    }
}
