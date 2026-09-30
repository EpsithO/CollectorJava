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
import com.collector.catalogue.article.domain.ArticleSubmitted;
import com.collector.catalogue.article.domain.InvalidPhotoException;
import com.collector.catalogue.article.domain.InvalidStatusException;
import com.collector.catalogue.article.domain.NotOwnerException;
import com.collector.catalogue.article.domain.Photo;
import com.collector.catalogue.article.domain.PhotoRequiredException;
import com.collector.catalogue.shared.domain.DomainEvent;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;
import com.collector.catalogue.testsupport.FakeMembers;
import com.collector.catalogue.testsupport.FakeStorage;
import com.collector.catalogue.testsupport.InMemoryArticles;

class SubmitArticleTest {

    private static final Member SELLER = new Member(UUID.randomUUID().toString(), "Vendeur", "v@test.local");
    private static final Member OTHER = new Member(UUID.randomUUID().toString(), "Autre", "o@test.local");

    private final FakeMembers members = new FakeMembers();
    private final InMemoryArticles articles = new InMemoryArticles();
    private final FakeStorage storage = new FakeStorage();
    private final List<DomainEvent> published = new ArrayList<>();
    private final SubmitArticle submitArticle = new SubmitArticle(members, articles, storage, published::add,
            new ArticleViews(storage));

    private final UUID sellerId = members.register(SELLER);
    private final UUID otherId = members.register(OTHER);

    /** Brouillon enregistré avec les photos demandées (clé, envoyée ou non, type, taille). */
    private Article draftWithPhotos(Photo... photos) {
        Article draft = Article.draft(UUID.randomUUID(), sellerId, UUID.randomUUID(), "Air Jordan 1",
                "Paire neuve jamais portée", 26_000, 0, Map.of(), Instant.parse("2026-01-01T00:00:00Z"));
        return articles.put(draft.withPhotos(List.of(photos)));
    }

    private Photo pendingPhoto(Article any) {
        return Photo.pending(any.id(), "image/jpeg", 1000);
    }

    @Test
    void submitsDraftWithAnUploadedValidPhotoAndPublishesEvent() {          // CA-1
        Article draft = draftWithPhotos();
        Photo photo = pendingPhoto(draft);
        articles.put(draft.withPhotos(List.of(photo)));
        storage.upload(photo.storageKey(), "image/jpeg", 2_000);

        ArticleView view = submitArticle.execute(SELLER, draft.id());

        assertThat(view.article().status()).isEqualTo(ArticleStatus.EN_CONTROLE);
        assertThat(view.photos()).hasSize(1);
        assertThat(published).singleElement().isInstanceOfSatisfying(ArticleSubmitted.class, e -> {
            assertThat(e.articleId()).isEqualTo(draft.id());
            assertThat(e.photoCount()).isEqualTo(1);
            assertThat(e.priceCents()).isEqualTo(26_000);
            assertThat(e.type()).isEqualTo("article.submitted");
        });
    }

    @Test
    void draftWithoutPhotoIsRefusedAndStaysDraft() {                        // CA-6
        Article draft = draftWithPhotos();

        assertThatThrownBy(() -> submitArticle.execute(SELLER, draft.id()))
                .isInstanceOf(PhotoRequiredException.class);
        assertThat(articles.stored.get(draft.id()).status()).isEqualTo(ArticleStatus.BROUILLON);
        assertThat(published).isEmpty();
    }

    @Test
    void photoNeverUploadedCountsAsMissing() {
        Article draft = draftWithPhotos();
        articles.put(draft.withPhotos(List.of(pendingPhoto(draft))));           // réservée mais jamais envoyée

        assertThatThrownBy(() -> submitArticle.execute(SELLER, draft.id()))
                .isInstanceOf(PhotoRequiredException.class);
    }

    @Test
    void photoOfUnsupportedTypeIsRefusedAndDeletedFromStorage() {
        Article draft = draftWithPhotos();
        Photo photo = Photo.pending(draft.id(), "image/jpeg", 1000);
        articles.put(draft.withPhotos(List.of(photo)));
        storage.upload(photo.storageKey(), "image/gif", 1000);                  // le navigateur a triché

        assertThatThrownBy(() -> submitArticle.execute(SELLER, draft.id()))
                .isInstanceOf(InvalidPhotoException.class)
                .extracting("code").isEqualTo("invalid_photo");
        assertThat(storage.deleted).containsExactly(photo.storageKey());
        assertThat(published).isEmpty();
    }

    @Test
    void oneGoodPhotoIsEnoughEvenIfAnotherIsRefused() {
        Article draft = draftWithPhotos();
        Photo good = Photo.pending(draft.id(), "image/png", 1000);
        Photo bad = Photo.pending(draft.id(), "image/png", 1000);
        articles.put(draft.withPhotos(List.of(good, bad)));
        storage.upload(good.storageKey(), "image/png", 1000);
        storage.upload(bad.storageKey(), "image/png", 99L * 1024 * 1024);       // trop lourde

        ArticleView view = submitArticle.execute(SELLER, draft.id());

        assertThat(view.article().status()).isEqualTo(ArticleStatus.EN_CONTROLE);
        assertThat(view.article().validatedPhotoCount()).isEqualTo(1);
        assertThat(storage.deleted).containsExactly(bad.storageKey());
    }

    @Test
    void anotherSellerIsForbidden() {                                        // CA-5
        Article draft = draftWithPhotos();

        assertThatThrownBy(() -> submitArticle.execute(OTHER, draft.id())).isInstanceOf(NotOwnerException.class);
        assertThat(otherId).isNotEqualTo(sellerId);
    }

    @Test
    void unknownArticleIsNotFound() {
        assertThatThrownBy(() -> submitArticle.execute(SELLER, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void alreadySubmittedArticleIsAConflictNotAPhotoError() {
        Article draft = draftWithPhotos();
        Photo photo = Photo.pending(draft.id(), "image/jpeg", 1000).validated();
        articles.put(draft.withPhotos(List.of(photo)).submit());

        assertThatThrownBy(() -> submitArticle.execute(SELLER, draft.id()))
                .isInstanceOf(InvalidStatusException.class);
        assertThat(published).isEmpty();
    }
}
