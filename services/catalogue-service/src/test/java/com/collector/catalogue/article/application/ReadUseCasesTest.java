package com.collector.catalogue.article.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.article.application.port.CategoryCatalog;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.Photo;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;
import com.collector.catalogue.testsupport.FakeMembers;
import com.collector.catalogue.testsupport.FakeStorage;
import com.collector.catalogue.testsupport.InMemoryArticles;

class ReadUseCasesTest {

    private static final Member SELLER = new Member(UUID.randomUUID().toString(), "Vendeur", "v@test.local");
    private static final Member STRANGER = new Member(UUID.randomUUID().toString(), "Curieux", "c@test.local");
    private static final UUID SNEAKERS = UUID.randomUUID();
    private static final UUID POSTERS = UUID.randomUUID();

    private final FakeMembers members = new FakeMembers();
    private final InMemoryArticles articles = new InMemoryArticles();
    private final FakeStorage storage = new FakeStorage();
    private final ArticleViews views = new ArticleViews(storage);
    private final CategoryCatalog categories = new CategoryCatalog() {
        @Override
        public boolean exists(UUID categoryId) {
            return true;
        }

        @Override
        public Optional<UUID> idBySlug(String slug) {
            return "sneakers".equals(slug) ? Optional.of(SNEAKERS) : Optional.empty();
        }
    };
    private final GetArticle getArticle = new GetArticle(members, articles, views);
    private final ListArticles listArticles = new ListArticles(articles, categories, views);
    private final ListMyArticles listMyArticles = new ListMyArticles(members, articles, views);
    private final UUID sellerId = members.register(SELLER);

    private Article put(ArticleStatus status, UUID category, String title, Photo... photos) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return articles.put(new Article(UUID.randomUUID(), sellerId, category, title, "Description correcte",
                10_000, 0, "EUR", Map.of(), status, null, now, now, null, List.of(photos)));
    }

    @Test
    void publishedArticleIsVisibleToEveryoneWithSignedPhotoUrlsForValidatedPhotosOnly() {
        Photo valid = Photo.pending(UUID.randomUUID(), "image/jpeg", 10).validated();
        Photo pending = Photo.pending(UUID.randomUUID(), "image/jpeg", 10);
        Article article = put(ArticleStatus.PUBLIE, SNEAKERS, "Air Jordan", valid, pending);

        ArticleView view = getArticle.execute(Optional.empty(), article.id());

        assertThat(view.photos()).singleElement().satisfies(link -> {
            assertThat(link.id()).isEqualTo(valid.id());
            assertThat(link.url().toString()).endsWith(valid.storageKey());
        });
    }

    @Test
    void unpublishedArticleIsVisibleToItsSellerOnly() {
        Article draft = put(ArticleStatus.BROUILLON, SNEAKERS, "Brouillon");

        assertThat(getArticle.execute(Optional.of(SELLER), draft.id()).article().id()).isEqualTo(draft.id());
        assertThatThrownBy(() -> getArticle.execute(Optional.empty(), draft.id()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> getArticle.execute(Optional.of(STRANGER), draft.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownArticleIsNotFound() {
        assertThatThrownBy(() -> getArticle.execute(Optional.empty(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void catalogueListsPublishedArticlesOnlyAndFiltersByCategorySlug() {
        put(ArticleStatus.PUBLIE, SNEAKERS, "Sneakers publiées");
        put(ArticleStatus.PUBLIE, POSTERS, "Poster publié");
        put(ArticleStatus.EN_REVUE, SNEAKERS, "En revue");
        put(ArticleStatus.BROUILLON, SNEAKERS, "Brouillon");

        assertThat(listArticles.execute(Optional.empty(), 0, 20).total()).isEqualTo(2);
        var sneakers = listArticles.execute(Optional.of("sneakers"), 0, 20);
        assertThat(sneakers.items()).extracting(v -> v.article().title()).containsExactly("Sneakers publiées");
        assertThat(listArticles.execute(Optional.of("inconnue"), 0, 20).items()).isEmpty();
    }

    @Test
    void catalogueIsPaginated() {
        for (int i = 0; i < 5; i++) {
            put(ArticleStatus.PUBLIE, SNEAKERS, "Article " + i);
        }

        var secondPage = listArticles.execute(Optional.empty(), 1, 2);

        assertThat(secondPage.items()).hasSize(2);
        assertThat(secondPage.total()).isEqualTo(5);
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.size()).isEqualTo(2);
    }

    @Test
    void myArticlesListsAllStatusesAndFiltersByStatus() {
        put(ArticleStatus.PUBLIE, SNEAKERS, "Publié");
        put(ArticleStatus.BROUILLON, SNEAKERS, "Brouillon");

        assertThat(listMyArticles.execute(SELLER, null)).hasSize(2);
        assertThat(listMyArticles.execute(SELLER, ArticleStatus.BROUILLON))
                .extracting(v -> v.article().title()).containsExactly("Brouillon");
    }

    @Test
    void myArticlesOfAnUnknownMemberIsEmptyAndCreatesNothing() {
        assertThat(listMyArticles.execute(STRANGER, null)).isEmpty();
        assertThat(members.findId(STRANGER.subject())).isEmpty();
    }
}
