package com.collector.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** US-033 : l'administrateur traite les articles mis en revue par le contrôle. */
public class ReviewSteps {

    private final World world;

    public ReviewSteps(World world) {
        this.world = world;
    }

    @Given("{string} met en vente un article à un prix anormal")
    public void aSellerListsAnOutlier(String seller) {
        world.articleId = Articles.articleInReview(world.tokenOf(seller));
        world.articleOwner = seller;
    }

    @When("il consulte la file de revue")
    public void heViewsTheReviewQueue() {
        world.response = Http.on(Env.api(), world.token).get("/api/v1/admin/reviews");
    }

    @When("il valide cet article")
    public void heApprovesTheArticle() {
        world.markEvents();
        world.response = decide(Map.of("decision", "VALIDER"));
    }

    @When("il rejette cet article avec le motif {string}")
    public void heRejectsTheArticle(String reason) {
        world.markEvents();
        world.response = decide(Map.of("decision", "REJETER", "reason", reason));
    }

    @When("il rejette cet article sans motif")
    public void heRejectsWithoutReason() {
        world.response = decide(Map.of("decision", "REJETER"));
    }

    @When("il décide de valider un article inconnu")
    public void heApprovesAnUnknownArticle() {
        world.response = Http.on(Env.api(), world.token).body(Map.of("decision", "VALIDER"))
                .post("/api/v1/admin/reviews/" + ArticleSteps.unknownId());
    }

    @Then("la file de revue contient cet article avec son score d'anomalie")
    public void theQueueContainsTheArticle() {
        List<Map<String, Object>> items = world.response.jsonPath().getList("$");
        Map<String, Object> mine = items.stream()
                .filter(i -> world.articleId.toString().equals(((Map<?, ?>) i.get("article")).get("id")))
                .findFirst().orElse(null);
        assertThat(mine).as("l'article %s doit figurer dans la file de revue", world.articleId).isNotNull();
        assertThat(((Number) mine.get("anomaly_score")).doubleValue()).isGreaterThan(3.0);
        assertThat(mine.get("check_reason")).isEqualTo("price_outlier");
    }

    @Then("la file de revue ne contient plus cet article")
    public void theQueueNoLongerContainsTheArticle() {
        List<String> ids = Http.on(Env.api(), world.tokenOf("admin")).get("/api/v1/admin/reviews")
                .then().statusCode(200).extract().jsonPath().getList("article.id");
        assertThat(ids).doesNotContain(world.articleId.toString());
    }

    @Then("son vendeur voit le motif {string} sur l'article")
    public void theSellerSeesTheReason(String reason) {
        List<Map<String, Object>> mine = Http.on(Env.api(), world.tokenOf(world.articleOwner))
                .get("/api/v1/me/articles").then().statusCode(200).extract().jsonPath().getList("$");
        Map<String, Object> article = mine.stream()
                .filter(a -> world.articleId.toString().equals(a.get("id"))).findFirst().orElseThrow();
        assertThat(article.get("status")).isEqualTo("REJETE");
        assertThat(article.get("review_reason")).isEqualTo(reason);
    }

    private io.restassured.response.Response decide(Map<String, Object> body) {
        return Http.on(Env.api(), world.token).body(body).post("/api/v1/admin/reviews/" + world.articleId);
    }
}
