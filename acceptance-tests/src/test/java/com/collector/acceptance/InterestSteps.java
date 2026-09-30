package com.collector.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** US-021 : paramétrer ses centres d'intérêt (exemple guidé, docs/guide/). */
public class InterestSteps {

    private final World world;

    public InterestSteps(World world) {
        this.world = world;
    }

    // Un seul libellé : Cucumber ignore le mot-clé (Étant donné, Quand, Alors), @When sert donc aussi au contexte.
    @When("il choisit les centres d'intérêt {string}")
    public void heChoosesInterests(String slugs) {
        world.markEvents();
        world.response = replace(idsOf(slugs));
    }

    @When("il choisit les centres d'intérêt {string} et une catégorie inexistante")
    public void heChoosesInterestsAndAnUnknownCategory(String slugs) {
        List<String> ids = new ArrayList<>(idsOf(slugs));
        ids.add(UUID.randomUUID().toString());
        world.response = replace(ids);
    }

    @When("il choisit {int} centres d'intérêt")
    public void heChoosesNInterests(int count) {
        List<String> ids = Stream.generate(() -> UUID.randomUUID().toString()).limit(count).toList();
        world.response = replace(ids);
    }

    @When("il consulte ses centres d'intérêt")
    public void heViewsHisInterests() {
        world.response = Http.on(Env.api(), world.token).get("/api/v1/me/interests");
    }

    @Then("ses centres d'intérêt sont {string}")
    public void hisInterestsAre(String labels) {
        List<String> expected = labels.isBlank() ? List.of()
                : Arrays.stream(labels.split(",")).map(String::trim).toList();
        List<String> actual = Http.on(Env.api(), world.token).get("/api/v1/me/interests")
                .then().statusCode(200).extract().jsonPath().getList("label");
        assertThat(actual).containsExactlyElementsOf(expected);       // triés par libellé
    }

    private io.restassured.response.Response replace(List<String> categoryIds) {
        return Http.on(Env.api(), world.token).body(Map.of("category_ids", categoryIds)).put("/api/v1/me/interests");
    }

    private static List<String> idsOf(String slugs) {
        if (slugs.isBlank()) {
            return List.of();
        }
        return Arrays.stream(slugs.split(",")).map(String::trim)
                .map(slug -> Articles.categoryId(slug).toString()).toList();
    }
}
