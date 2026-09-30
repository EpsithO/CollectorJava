package com.collector.catalogue.article.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.shared.domain.InvalidRequestException;

class ArticleTest {

    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Article draft() {
        return Article.draft(UUID.randomUUID(), SELLER, UUID.randomUUID(), "Air Jordan 1",
                "Paire neuve jamais portée, taille 42", 26_000, 500, Map.of(), NOW);
    }

    private static Article withValidatedPhoto(Article article) {
        return article.withPendingPhoto(Photo.pending(article.id(), "image/jpeg", 1000).validated());
    }

    @Test
    void newDraftStartsInBrouillonWithoutPhotosOrVersion() {
        Article article = draft();

        assertThat(article.status()).isEqualTo(ArticleStatus.BROUILLON);
        assertThat(article.photos()).isEmpty();
        assertThat(article.version()).isNull();
        assertThat(article.currency()).isEqualTo("EUR");
    }

    @Test
    void draftRefusesContactInfoInTitleAndDescription() {
        assertThatThrownBy(() -> Article.draft(UUID.randomUUID(), SELLER, UUID.randomUUID(), "mail a@b.fr",
                "description correcte", 100, 0, null, NOW)).isInstanceOf(ContactInfoForbiddenException.class);
        assertThatThrownBy(() -> Article.draft(UUID.randomUUID(), SELLER, UUID.randomUUID(), "Titre correct",
                "appelez le 06 12 34 56 78", 100, 0, null, NOW)).isInstanceOf(ContactInfoForbiddenException.class);
    }

    @Test
    void draftRefusesNonPositivePriceAndNegativeShipping() {
        assertThatThrownBy(() -> Article.draft(UUID.randomUUID(), SELLER, UUID.randomUUID(), "Titre",
                "description correcte", 0, 0, null, NOW)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Article.draft(UUID.randomUUID(), SELLER, UUID.randomUUID(), "Titre",
                "description correcte", 100, -1, null, NOW)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void ownershipIsCheckedAgainstTheSeller() {
        Article article = draft();

        assertThat(article.isOwnedBy(SELLER)).isTrue();
        assertThat(article.isOwnedBy(OTHER)).isFalse();
        article.requireOwner(SELLER);
        assertThatThrownBy(() -> article.requireOwner(OTHER)).isInstanceOf(NotOwnerException.class);
    }

    @Test
    void submitWithoutValidatedPhotoIsRefused() {                     // CA-6
        assertThatThrownBy(() -> draft().submit())
                .isInstanceOf(PhotoRequiredException.class)
                .extracting("code").isEqualTo("photo_required");

        Article pendingOnly = draft();
        Article withPending = pendingOnly.withPendingPhoto(Photo.pending(pendingOnly.id(), "image/png", 10));
        assertThatThrownBy(withPending::submit).isInstanceOf(PhotoRequiredException.class);
    }

    @Test
    void submitMovesDraftToEnControle() {                             // CA-1
        Article submitted = withValidatedPhoto(draft()).submit();

        assertThat(submitted.status()).isEqualTo(ArticleStatus.EN_CONTROLE);
        assertThat(submitted.validatedPhotoCount()).isEqualTo(1);
    }

    @Test
    void submitTwiceIsAConflict() {
        Article submitted = withValidatedPhoto(draft()).submit();

        assertThatThrownBy(submitted::submit).isInstanceOf(InvalidStatusException.class);
    }

    @Test
    void photosCanOnlyBeAddedToADraftAndAtMostEight() {
        Article article = draft();
        for (int i = 0; i < PhotoPolicy.MAX_PER_ARTICLE; i++) {
            article = article.withPendingPhoto(Photo.pending(article.id(), "image/jpeg", 10));
        }
        Article full = article;
        assertThatThrownBy(() -> full.withPendingPhoto(Photo.pending(full.id(), "image/jpeg", 10)))
                .isInstanceOf(TooManyPhotosException.class)
                .extracting("code").isEqualTo("too_many_photos");

        Article submitted = withValidatedPhoto(draft()).submit();
        assertThatThrownBy(() -> submitted.withPendingPhoto(Photo.pending(submitted.id(), "image/jpeg", 10)))
                .isInstanceOf(InvalidStatusException.class);
    }

    @Test
    void priceChangesOnlyWhenPublishedOrInReview() {                  // CA-4
        Article published = publishedWithPrice(10_000);
        assertThat(published.changePrice(9_000).priceCents()).isEqualTo(9_000);

        Article inReview = withStatus(published, ArticleStatus.EN_REVUE);
        assertThat(inReview.changePrice(9_000).priceCents()).isEqualTo(9_000);

        assertThatThrownBy(() -> draft().changePrice(1_000)).isInstanceOf(InvalidStatusException.class);
        assertThatThrownBy(() -> published.changePrice(0)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void attributesAndPhotosAreDefensivelyCopied() {
        Article article = new Article(UUID.randomUUID(), SELLER, UUID.randomUUID(), "t", "d", 1, 0, "EUR",
                null, ArticleStatus.BROUILLON, null, null, NOW, null, null);

        assertThat(article.attributes()).isEmpty();
        assertThat(article.photos()).isEqualTo(List.of());
    }

    private static Article publishedWithPrice(long price) {
        Article base = draft();
        return new Article(base.id(), SELLER, base.categoryId(), base.title(), base.description(), price, 0, "EUR",
                Map.of(), ArticleStatus.PUBLIE, null, NOW, NOW, 3L, List.of());
    }

    private static Article withStatus(Article a, ArticleStatus status) {
        return new Article(a.id(), a.sellerId(), a.categoryId(), a.title(), a.description(), a.priceCents(),
                a.shippingCents(), a.currency(), a.attributes(), status, a.anomalyScore(), a.publishedAt(),
                a.createdAt(), a.version(), a.photos());
    }
}
