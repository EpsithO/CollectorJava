package com.collector.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

/** US-029 : suivre un article et recevoir les notifications de variation de prix (notification-service). */
public class FollowSteps {

    private final World world;

    public FollowSteps(World world) {
        this.world = world;
    }

    @When("{string} suit cet article")
    public void someoneFollows(String user) {
        world.markEvents();
        world.response = Http.on(Env.api(), world.tokenOf(user)).put("/api/v1/articles/" + world.articleId + "/follow");
    }

    @When("{string} ne suit plus cet article")
    public void someoneUnfollows(String user) {
        world.markEvents();
        world.response = Http.on(Env.api(), world.tokenOf(user)).delete("/api/v1/articles/" + world.articleId + "/follow");
    }

    @When("il suit un article qui n'existe pas")
    public void heFollowsAnUnknownArticle() {
        world.response = Http.on(Env.api(), world.token).put("/api/v1/articles/" + ArticleSteps.unknownId() + "/follow");
    }

    @When("il suit cet article")
    public void heFollows() {
        world.response = Http.on(Env.api(), world.token).put("/api/v1/articles/" + world.articleId + "/follow");
    }

    @When("il consulte ses notifications")
    public void heViewsHisNotifications() {
        world.response = Http.on(Env.notification(), world.token).get("/api/v1/me/notifications");
    }

    @When("{string} marque sa notification pour cet article comme lue")
    public void someoneMarksItRead(String user) {
        UUID id = awaitNotification(user, null, Duration.ofSeconds(1)).id;
        world.notificationId = id;
        world.response = Http.on(Env.notification(), world.tokenOf(user))
                .post("/api/v1/me/notifications/" + id + "/read");
    }

    @When("{string} tente de marquer cette notification comme lue")
    public void someoneTriesToMarkItRead(String user) {
        world.response = Http.on(Env.notification(), world.tokenOf(user))
                .post("/api/v1/me/notifications/" + world.notificationId + "/read");
    }

    @Then("{string} voit une notification pour cet article avec l'ancien prix {int} et le nouveau prix {int} sous {int} secondes")
    public void someoneSeesTheNotification(String user, int oldPrice, int newPrice, int seconds) {
        Seen seen = awaitNotification(user, newPrice, Duration.ofSeconds(seconds));
        assertThat(seen).as("notification de %s pour le prix %d", user, newPrice).isNotNull();
        assertThat(seen.oldPrice).isEqualTo(oldPrice);
        world.notificationId = seen.id;
    }

    @Then("{string} voit une notification pour cet article au nouveau prix {int} sous {int} secondes")
    public void someoneSeesTheLatestPrice(String user, int newPrice, int seconds) {
        assertThat(awaitNotification(user, newPrice, Duration.ofSeconds(seconds)))
                .as("notification de %s pour le prix %d", user, newPrice).isNotNull();
    }

    @Then("{string} ne voit aucune notification pour cet article pendant {int} secondes")
    public void someoneSeesNothing(String user, int seconds) {
        assertThat(awaitNotification(user, null, Duration.ofSeconds(seconds))).isNull();
    }

    @Then("{string} a au plus {int} notifications pour cet article")
    public void someoneHasAtMost(String user, int max) {
        assertThat(forArticle(user, false)).hasSizeLessThanOrEqualTo(max);
    }

    @Then("cette notification n'apparaît plus dans les notifications non lues de {string}")
    public void itIsNoLongerUnread(String user) {
        assertThat(forArticle(user, true)).extracting(n -> n.id).doesNotContain(world.notificationId);
        assertThat(forArticle(user, false)).extracting(n -> n.id).contains(world.notificationId);
    }

    @Then("la liste de notifications ne contient aucune notification de {string}")
    public void theListHasNoOthers(String user) {
        List<Map<String, Object>> all = world.response.jsonPath().getList("$");
        assertThat(all).noneMatch(n -> world.articleId.toString().equals(n.get("article_id")));
    }

    // --- aide -------------------------------------------------------------------------------

    private record Seen(UUID id, long oldPrice, long newPrice) {
    }

    /** Notifications de l'utilisateur pour l'article courant (les plus récentes d'abord). */
    private List<Seen> forArticle(String user, boolean unreadOnly) {
        Response response = Http.on(Env.notification(), world.tokenOf(user))
                .queryParam("unread", unreadOnly).get("/api/v1/me/notifications");
        response.then().statusCode(200);
        List<Map<String, Object>> all = response.jsonPath().getList("$");
        return all.stream()
                .filter(n -> world.articleId.toString().equals(n.get("article_id")))
                .map(n -> new Seen(UUID.fromString((String) n.get("id")),
                        ((Number) n.get("old_price_cents")).longValue(), ((Number) n.get("new_price_cents")).longValue()))
                .toList();
    }

    /** Attend une notification (du prix donné si non null) ; null si rien n'arrive dans le délai. */
    private Seen awaitNotification(String user, Integer newPrice, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            for (Seen seen : forArticle(user, false)) {
                if (newPrice == null || seen.newPrice == newPrice) {
                    return seen;
                }
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } while (System.nanoTime() < deadline);
        return null;
    }
}
