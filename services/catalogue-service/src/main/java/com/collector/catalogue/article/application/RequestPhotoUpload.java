package com.collector.catalogue.article.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.Photo;
import com.collector.catalogue.article.domain.PhotoPolicy;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.application.port.PhotoStorage;
import com.collector.catalogue.shared.application.port.PhotoStorage.PresignedUpload;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;

/**
 * POST /articles/{id}/photos : réserve un emplacement et délivre une URL d'envoi
 * pré-signée. Le binaire ne transite jamais par l'API.
 */
@Service
public class RequestPhotoUpload {

    private final MemberDirectory members;
    private final ArticleRepository articles;
    private final PhotoStorage storage;

    public RequestPhotoUpload(MemberDirectory members, ArticleRepository articles, PhotoStorage storage) {
        this.members = members;
        this.articles = articles;
        this.storage = storage;
    }

    public record PhotoUpload(UUID photoId, PresignedUpload upload) {
    }

    @Transactional
    public PhotoUpload execute(Member member, UUID articleId, String contentType, long sizeBytes) {
        PhotoPolicy.requireAcceptable(contentType, sizeBytes);
        UUID memberId = members.ensureMember(member);
        Article article = articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article"));
        article.requireOwner(memberId);

        Photo photo = Photo.pending(articleId, contentType, sizeBytes);
        articles.save(article.withPendingPhoto(photo));      // brouillon seulement, 8 photos au plus
        return new PhotoUpload(photo.id(), storage.prepareUpload(photo.storageKey(), contentType));
    }
}
