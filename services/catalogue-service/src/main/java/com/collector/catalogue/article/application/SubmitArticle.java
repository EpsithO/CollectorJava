package com.collector.catalogue.article.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.ArticleSubmitted;
import com.collector.catalogue.article.domain.InvalidPhotoException;
import com.collector.catalogue.article.domain.Photo;
import com.collector.catalogue.article.domain.PhotoPolicy;
import com.collector.catalogue.shared.application.port.DomainEventPublisher;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.application.port.PhotoStorage;
import com.collector.catalogue.shared.application.port.PhotoStorage.StoredObject;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;

/**
 * POST /articles/{id}/submission (CA-1, CA-6) : vérifie les photos dans le stockage,
 * passe l'article EN_CONTROLE et écrit article.submitted dans l'outbox, dans la même
 * transaction.
 */
@Service
public class SubmitArticle {

    private final MemberDirectory members;
    private final ArticleRepository articles;
    private final PhotoStorage storage;
    private final DomainEventPublisher events;
    private final ArticleViews views;

    public SubmitArticle(MemberDirectory members, ArticleRepository articles, PhotoStorage storage,
                         DomainEventPublisher events, ArticleViews views) {
        this.members = members;
        this.articles = articles;
        this.storage = storage;
        this.events = events;
        this.views = views;
    }

    @Transactional
    public ArticleView execute(Member member, UUID articleId) {
        UUID memberId = members.ensureMember(member);
        Article article = articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article"));
        article.requireOwner(memberId);

        Article checked = article.withPhotos(verifyPhotos(article));
        Article submitted = checked.submit();                // 422 photo_required, 409 invalid_status

        Article saved = articles.save(submitted);
        events.publish(new ArticleSubmitted(saved.id(), saved.sellerId(), saved.categoryId(),
                saved.priceCents(), saved.currency(), saved.validatedPhotoCount()));
        return views.of(saved);
    }

    // HEAD de chaque photo en attente : existe, type autorisé, taille ≤ 5 Mo.
    // Une photo présente mais refusée est effacée du stockage. Si plus aucune photo
    // n'est valable, invalid_photo (une photo refusée) prime sur photo_required (aucune envoyée,
    // levée par Article.submit()).
    private List<Photo> verifyPhotos(Article article) {
        if (article.status() != ArticleStatus.BROUILLON) {
            return article.photos();                         // submit() répondra 409 invalid_status
        }
        List<Photo> result = new ArrayList<>();
        boolean refused = false;
        for (Photo photo : article.photos()) {
            if (photo.isValidated()) {
                result.add(photo);
                continue;
            }
            Optional<StoredObject> stored = storage.stat(photo.storageKey());
            if (stored.isEmpty()) {
                result.add(photo);                           // jamais envoyée : reste en attente
            } else if (PhotoPolicy.isAcceptable(stored.get().contentType(), stored.get().sizeBytes())) {
                result.add(photo.validated());
            } else {
                storage.delete(photo.storageKey());
                result.add(photo);
                refused = true;
            }
        }
        if (refused && result.stream().noneMatch(Photo::isValidated)) {
            throw new InvalidPhotoException();
        }
        return result;                                       // aucune photo : submit() répondra 422 photo_required
    }
}
