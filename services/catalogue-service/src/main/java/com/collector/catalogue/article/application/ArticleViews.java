package com.collector.catalogue.article.application;

import org.springframework.stereotype.Service;

import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.Photo;
import com.collector.catalogue.shared.application.port.PhotoStorage;

/** Fabrique de vues : signe les URL de lecture (10 min) des photos validées seulement. */
@Service
public class ArticleViews {

    private final PhotoStorage storage;

    public ArticleViews(PhotoStorage storage) {
        this.storage = storage;
    }

    public ArticleView of(Article article) {
        return new ArticleView(article, article.photos().stream()
                .filter(Photo::isValidated)
                .map(photo -> new ArticleView.PhotoLink(photo.id(), storage.downloadUrl(photo.storageKey())))
                .toList());
    }
}
