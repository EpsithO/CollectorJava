package com.collector.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** US-014 : mise en ligne d'un article avec contrôle automatisé. */
public class ArticleSteps {

    private final World world;

    public ArticleSteps(World world) {
        this.world = world;
    }

    @Given("un brouillon dans la catégorie {string} au prix de {int} centimes avec {int} photo(s)")
    public void aDraftWithPhotos(String category, int priceCents, int photos) {
        world.articleId = Articles.draftWithPhotos(world.token, category, priceCents, photos);
        world.articleOwner = world.user;
    }

    @Given("un article publié appartenant à {string}")
    public void aPublishedArticleOwnedBy(String seller) {
        // Un article réellement publié par le contrôle : indépendant des données de démonstration,
        // donc le scénario est rejouable (un même prix demandé deux fois ne produirait pas d'événement).
        world.articleId = Articles.publishedArticle(world.tokenOf(seller));
        world.articleOwner = seller;
    }

    @When("il soumet le brouillon")
    public void heSubmitsTheDraft() {
        world.markEvents();
        world.response = Articles.submit(world.token, world.articleId);
    }

    @When("il crée un brouillon dans la catégorie {string} au prix de {int} centimes")
    public void heCreatesADraft(String category, int priceCents) {
        world.response = Http.on(Env.api(), world.token)
                .body(Articles.draftBody(category, priceCents)).post("/api/v1/articles");
    }

    @When("{string} modifie le prix de cet article à {int} centimes")
    public void someoneChangesThePrice(String user, int priceCents) {
        world.markEvents();
        world.response = Http.on(Env.api(), world.tokenOf(user))
                .body(java.util.Map.of("price_cents", priceCents))
                .patch("/api/v1/articles/" + world.articleId + "/price");
    }

    @Then("l'article passe au statut {string} en moins de {int} secondes")
    public void theArticleReachesTheStatus(String status, int seconds) {
        assertThat(Articles.awaitStatus(tokenOfOwner(), world.articleId, status, Duration.ofSeconds(seconds)))
                .as("l'article %s devait atteindre %s en %d s", world.articleId, status, seconds)
                .isTrue();
    }

    @Then("l'article a le statut {string}")
    public void theArticleHasTheStatus(String status) {
        String current = Http.on(Env.api(), tokenOfOwner()).get("/api/v1/articles/" + world.articleId).path("status");
        assertThat(current).isEqualTo(status);
    }

    @Then("le prix de cet article est {int} centimes")
    public void theArticlePriceIs(int priceCents) {
        Integer current = Http.on(Env.api(), tokenOfOwner()).get("/api/v1/articles/" + world.articleId).path("price_cents");
        assertThat(current).isEqualTo(priceCents);
    }

    // Les brouillons ne sont visibles que de leur vendeur : on les relit avec son jeton.
    private String tokenOfOwner() {
        assertThat(world.articleId).as("aucun article dans ce scénario").isNotNull();
        return world.tokenOf(world.articleOwner);
    }

    /** Pour les autres classes d'étapes : identifiant d'un article inconnu. */
    static UUID unknownId() {
        return UUID.randomUUID();
    }
}
