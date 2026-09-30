package com.collector.catalogue.article.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleReviewed;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.FollowChanged;
import com.collector.catalogue.article.domain.InvalidStatusException;
import com.collector.catalogue.article.domain.ReviewDecision;
import com.collector.catalogue.shared.domain.DomainEvent;
import com.collector.catalogue.shared.domain.InvalidRequestException;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;
import com.collector.catalogue.testsupport.FakeMembers;
import com.collector.catalogue.testsupport.FakeStorage;
import com.collector.catalogue.testsupport.InMemoryArticles;

/** US-033 (revue admin) et US-029 (suivre un article) : cas d'usage avec doublures, sans Spring. */
class ReviewAndFollowUseCasesTest {

    private static final UUID ADMIN_SUB = UUID.randomUUID();
    private static final UUID BUYER_SUB = UUID.randomUUID();
    private static final Member ADMIN = new Member(ADMIN_SUB.toString(), "Admin", "admin@test.local");
    private static final Member BUYER = new Member(BUYER_SUB.toString(), "Acheteur", "a@test.local");
    private static final Instant NOW = Instant.parse("2026-02-01T00:00:00Z");

    private final FakeMembers members = new FakeMembers();
    private final InMemoryArticles articles = new InMemoryArticles();
    private final List<DomainEvent> published = new ArrayList<>();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ArticleViews views = new ArticleViews(new FakeStorage());
    private final ReviewArticle reviewArticle = new ReviewArticle(members, articles, published::add, views, clock);
    private final ListPendingReviews listPendingReviews = new ListPendingReviews(articles, views);
    private final FollowArticle followArticle = new FollowArticle(articles, published::add, clock);

    private Article put(ArticleStatus status, String title, Instant createdAt) {
        return articles.put(new Article(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), title,
                "Description correcte", 100_000, 0, "EUR", Map.of(), status, 5.2, null, createdAt, null, List.of(),
                "price_outlier", null, null));
    }

    // --- US-033 --------------------------------------------------------------------------

    @Test
    void reviewQueueListsOnlyArticlesInReviewOldestFirst() {                   // CA-1
        put(ArticleStatus.EN_REVUE, "Récent", NOW.minusSeconds(10));
        put(ArticleStatus.EN_REVUE, "Ancien", NOW.minusSeconds(1000));
        put(ArticleStatus.PUBLIE, "Publié", NOW.minusSeconds(5000));

        assertThat(listPendingReviews.execute()).extracting(v -> v.article().title())
                .containsExactly("Ancien", "Récent");
        assertThat(listPendingReviews.execute().getFirst().article().checkReason()).isEqualTo("price_outlier");
    }

    @Test
    void approvingPublishesTheArticleAndTheEvent() {                            // CA-2
        Article article = put(ArticleStatus.EN_REVUE, "Suspect", NOW);

        ArticleView view = reviewArticle.execute(ADMIN, article.id(), ReviewDecision.VALIDER, null);

        assertThat(view.article().status()).isEqualTo(ArticleStatus.PUBLIE);
        assertThat(view.article().publishedAt()).isEqualTo(NOW);
        assertThat(view.article().reviewedBy()).isEqualTo(members.findId(ADMIN.subject()).orElseThrow());
        assertThat(published).singleElement().isInstanceOfSatisfying(ArticleReviewed.class, e -> {
            assertThat(e.decision()).isEqualTo("PUBLIE");
            assertThat(e.reason()).isNull();
            assertThat(e.reviewedBy()).isEqualTo(ADMIN_SUB);
            assertThat(e.type()).isEqualTo("article.reviewed");
        });
    }

    @Test
    void rejectingNeedsAReasonAndKeepsItForTheSeller() {                        // CA-3
        Article article = put(ArticleStatus.EN_REVUE, "Suspect", NOW);

        assertThatThrownBy(() -> reviewArticle.execute(ADMIN, article.id(), ReviewDecision.REJETER, "  "))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(published).isEmpty();

        ArticleView view = reviewArticle.execute(ADMIN, article.id(), ReviewDecision.REJETER, " Photos floues ");

        assertThat(view.article().status()).isEqualTo(ArticleStatus.REJETE);
        assertThat(view.article().reviewReason()).isEqualTo("Photos floues");
        assertThat(published).singleElement().isInstanceOfSatisfying(ArticleReviewed.class, e -> {
            assertThat(e.decision()).isEqualTo("REJETE");
            assertThat(e.reason()).isEqualTo("Photos floues");
        });
    }

    @Test
    void onlyArticlesInReviewCanBeDecided() {                                   // CA-4
        Article published = put(ArticleStatus.PUBLIE, "Déjà publié", NOW);

        assertThatThrownBy(() -> reviewArticle.execute(ADMIN, published.id(), ReviewDecision.VALIDER, null))
                .isInstanceOf(InvalidStatusException.class);
        assertThatThrownBy(() -> reviewArticle.execute(ADMIN, published.id(), ReviewDecision.REJETER, "motif"))
                .isInstanceOf(InvalidStatusException.class);
        assertThatThrownBy(() -> reviewArticle.execute(ADMIN, UUID.randomUUID(), ReviewDecision.VALIDER, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(this.published).isEmpty();
    }

    @Test
    void decidingTwiceIsAConflictNotADoubleEvent() {
        Article article = put(ArticleStatus.EN_REVUE, "Suspect", NOW);
        reviewArticle.execute(ADMIN, article.id(), ReviewDecision.VALIDER, null);

        assertThatThrownBy(() -> reviewArticle.execute(ADMIN, article.id(), ReviewDecision.REJETER, "trop tard"))
                .isInstanceOf(InvalidStatusException.class);
        assertThat(published).hasSize(1);
    }

    // --- US-029 --------------------------------------------------------------------------

    @Test
    void followingAPublishedArticlePublishesFollowChangedWithTheKeycloakSubject() {    // CA-1
        Article article = put(ArticleStatus.PUBLIE, "Air Jordan", NOW);

        followArticle.follow(BUYER, article.id());

        assertThat(published).singleElement().isInstanceOfSatisfying(FollowChanged.class, e -> {
            assertThat(e.memberId()).isEqualTo(BUYER_SUB);
            assertThat(e.articleId()).isEqualTo(article.id());
            assertThat(e.following()).isTrue();
            assertThat(e.changedAt()).isEqualTo(NOW);
            assertThat(e.type()).isEqualTo("follow.changed");
        });
    }

    @Test
    void anUnpublishedArticleCannotBeFollowedAndDoesNotExistForTheBuyer() {
        Article draft = put(ArticleStatus.BROUILLON, "Brouillon", NOW);
        Article inReview = put(ArticleStatus.EN_REVUE, "En revue", NOW);

        assertThatThrownBy(() -> followArticle.follow(BUYER, draft.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> followArticle.follow(BUYER, inReview.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> followArticle.follow(BUYER, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        assertThat(published).isEmpty();
    }

    @Test
    void unfollowingPublishesFollowingFalseEvenIfTheArticleIsNoLongerPublished() {     // CA-2
        Article sold = put(ArticleStatus.VENDU, "Vendu", NOW);

        followArticle.unfollow(BUYER, sold.id());

        assertThat(published).singleElement().isInstanceOfSatisfying(FollowChanged.class,
                e -> assertThat(e.following()).isFalse());
        assertThatThrownBy(() -> followArticle.unfollow(BUYER, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
