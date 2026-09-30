package com.collector.catalogue.article.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.InvalidStatusException;
import com.collector.catalogue.article.domain.NotOwnerException;
import com.collector.catalogue.article.domain.PriceChanged;
import com.collector.catalogue.shared.domain.DomainEvent;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;
import com.collector.catalogue.testsupport.FakeMembers;
import com.collector.catalogue.testsupport.FakeStorage;
import com.collector.catalogue.testsupport.InMemoryArticles;

class ChangePriceTest {

    private static final Member SELLER = new Member(UUID.randomUUID().toString(), "Vendeur", "v@test.local");
    private static final Member OTHER = new Member(UUID.randomUUID().toString(), "Autre", "o@test.local");

    private final FakeMembers members = new FakeMembers();
    private final InMemoryArticles articles = new InMemoryArticles();
    private final List<DomainEvent> published = new ArrayList<>();
    private final ChangePrice changePrice = new ChangePrice(members, articles, published::add,
            new ArticleViews(new FakeStorage()));
    private final UUID sellerId = members.register(SELLER);

    private Article articleIn(ArticleStatus status, long price) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return articles.put(new Article(UUID.randomUUID(), sellerId, UUID.randomUUID(), "Air Jordan 1",
                "Paire neuve jamais portée", price, 0, "EUR", Map.of(), status, null, now, now, null, List.of()));
    }

    @Test
    void changesThePriceAndPublishesPriceChangedWithTheNewVersion() {          // CA-4
        Article article = articleIn(ArticleStatus.PUBLIE, 25_000);

        ArticleView view = changePrice.execute(SELLER, article.id(), 24_000);

        assertThat(view.article().priceCents()).isEqualTo(24_000);
        assertThat(published).singleElement().isInstanceOfSatisfying(PriceChanged.class, e -> {
            assertThat(e.oldPriceCents()).isEqualTo(25_000);
            assertThat(e.newPriceCents()).isEqualTo(24_000);
            assertThat(e.aggregateVersion()).isEqualTo(view.article().version()).isGreaterThan(article.version());
            assertThat(e.type()).isEqualTo("price.changed");
        });
    }

    @Test
    void samePricePublishesNothing() {
        Article article = articleIn(ArticleStatus.PUBLIE, 25_000);

        changePrice.execute(SELLER, article.id(), 25_000);

        assertThat(published).isEmpty();
        assertThat(articles.stored.get(article.id()).version()).isEqualTo(article.version());
    }

    @Test
    void anotherSellerIsForbiddenAndNothingChanges() {                          // CA-5
        Article article = articleIn(ArticleStatus.PUBLIE, 25_000);
        members.register(OTHER);

        assertThatThrownBy(() -> changePrice.execute(OTHER, article.id(), 1_000)).isInstanceOf(NotOwnerException.class);
        assertThat(articles.stored.get(article.id()).priceCents()).isEqualTo(25_000);
        assertThat(published).isEmpty();
    }

    @Test
    void draftPriceCannotBeChangedThroughThisRoute() {
        Article draft = articleIn(ArticleStatus.BROUILLON, 25_000);

        assertThatThrownBy(() -> changePrice.execute(SELLER, draft.id(), 1_000))
                .isInstanceOf(InvalidStatusException.class);
    }

    @Test
    void unknownArticleIsNotFound() {
        assertThatThrownBy(() -> changePrice.execute(SELLER, UUID.randomUUID(), 1_000))
                .isInstanceOf(NotFoundException.class);
    }
}
