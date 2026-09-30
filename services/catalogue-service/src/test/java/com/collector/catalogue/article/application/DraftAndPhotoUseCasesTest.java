package com.collector.catalogue.article.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.article.application.port.CategoryCatalog;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.ContactInfoForbiddenException;
import com.collector.catalogue.article.domain.NotOwnerException;
import com.collector.catalogue.article.domain.PhotoPolicy;
import com.collector.catalogue.article.domain.TooManyPhotosException;
import com.collector.catalogue.shared.domain.InvalidRequestException;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;
import com.collector.catalogue.testsupport.FakeMembers;
import com.collector.catalogue.testsupport.FakeStorage;
import com.collector.catalogue.testsupport.InMemoryArticles;

class DraftAndPhotoUseCasesTest {

    private static final Member SELLER = new Member(UUID.randomUUID().toString(), "Vendeur", "v@test.local");
    private static final Member OTHER = new Member(UUID.randomUUID().toString(), "Autre", "o@test.local");
    private static final UUID SNEAKERS = UUID.randomUUID();

    private final FakeMembers members = new FakeMembers();
    private final InMemoryArticles articles = new InMemoryArticles();
    private final FakeStorage storage = new FakeStorage();
    private final ArticleViews views = new ArticleViews(storage);
    private final CategoryCatalog categories = new CategoryCatalog() {
        @Override
        public boolean exists(UUID categoryId) {
            return SNEAKERS.equals(categoryId);
        }

        @Override
        public Optional<UUID> idBySlug(String slug) {
            return "sneakers".equals(slug) ? Optional.of(SNEAKERS) : Optional.empty();
        }
    };
    private final CreateDraft createDraft = new CreateDraft(members, categories, articles, views,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    private final RequestPhotoUpload requestPhotoUpload = new RequestPhotoUpload(members, articles, storage);

    private DraftCommand command(String title, String description) {
        return new DraftCommand(title, description, SNEAKERS, 26_000, 0, Map.of("taille", 42));
    }

    @Test
    void createsADraftAndTheMemberOnFirstUse() {
        ArticleView view = createDraft.execute(SELLER, command("Air Jordan 1", "Paire neuve jamais portée"));

        assertThat(view.article().status()).isEqualTo(ArticleStatus.BROUILLON);
        assertThat(view.article().sellerId()).isEqualTo(members.findId(SELLER.subject()).orElseThrow());
        assertThat(view.article().attributes()).containsEntry("taille", 42);
        assertThat(view.photos()).isEmpty();
        assertThat(articles.stored).hasSize(1);
    }

    @Test
    void unknownCategoryIsABadRequest() {
        var bad = new DraftCommand("Air Jordan 1", "Paire neuve jamais portée", UUID.randomUUID(), 100, 0, null);

        assertThatThrownBy(() -> createDraft.execute(SELLER, bad)).isInstanceOf(InvalidRequestException.class);
        assertThat(articles.stored).isEmpty();
    }

    @Test
    void contactInfoInTheDescriptionIsRefused() {
        assertThatThrownBy(() -> createDraft.execute(SELLER, command("Air Jordan 1", "Appelez-moi au 06 12 34 56 78")))
                .isInstanceOf(ContactInfoForbiddenException.class);
        assertThat(articles.stored).isEmpty();
    }

    @Test
    void photoUploadReservesASlotAndReturnsAPresignedUrl() {
        UUID articleId = createDraft.execute(SELLER, command("Air Jordan 1", "Paire neuve jamais portée"))
                .article().id();

        var upload = requestPhotoUpload.execute(SELLER, articleId, "image/jpeg", 2_000);

        var saved = articles.stored.get(articleId);
        assertThat(saved.photos()).singleElement().satisfies(photo -> {
            assertThat(photo.id()).isEqualTo(upload.photoId());
            assertThat(photo.isValidated()).isFalse();
            assertThat(photo.storageKey()).isEqualTo("articles/" + articleId + "/" + photo.id());
        });
        assertThat(upload.upload().url().toString()).endsWith(saved.photos().getFirst().storageKey());
        assertThat(upload.upload().headers()).containsEntry("Content-Type", "image/jpeg");
    }

    @Test
    void photoUploadRefusesUnsupportedTypeAndSize() {
        UUID articleId = createDraft.execute(SELLER, command("Air Jordan 1", "Paire neuve jamais portée"))
                .article().id();

        assertThatThrownBy(() -> requestPhotoUpload.execute(SELLER, articleId, "image/gif", 100))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> requestPhotoUpload.execute(SELLER, articleId, "image/png", PhotoPolicy.MAX_BYTES + 1))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void photoUploadIsLimitedToEightPhotos() {
        UUID articleId = createDraft.execute(SELLER, command("Air Jordan 1", "Paire neuve jamais portée"))
                .article().id();
        for (int i = 0; i < PhotoPolicy.MAX_PER_ARTICLE; i++) {
            requestPhotoUpload.execute(SELLER, articleId, "image/jpeg", 100);
        }

        assertThatThrownBy(() -> requestPhotoUpload.execute(SELLER, articleId, "image/jpeg", 100))
                .isInstanceOf(TooManyPhotosException.class);
    }

    @Test
    void photoUploadIsReservedToTheOwner() {
        UUID articleId = createDraft.execute(SELLER, command("Air Jordan 1", "Paire neuve jamais portée"))
                .article().id();

        assertThatThrownBy(() -> requestPhotoUpload.execute(OTHER, articleId, "image/jpeg", 100))
                .isInstanceOf(NotOwnerException.class);
        assertThatThrownBy(() -> requestPhotoUpload.execute(SELLER, UUID.randomUUID(), "image/jpeg", 100))
                .isInstanceOf(NotFoundException.class);
        assertThat(Set.copyOf(articles.stored.keySet())).containsExactly(articleId);
    }
}
