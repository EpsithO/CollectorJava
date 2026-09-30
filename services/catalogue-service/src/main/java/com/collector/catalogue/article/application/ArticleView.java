package com.collector.catalogue.article.application;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.collector.catalogue.article.domain.Article;

/** Modèle de lecture : l'article et les URL de lecture pré-signées de ses photos validées. */
public record ArticleView(Article article, List<PhotoLink> photos) {

    public record PhotoLink(UUID id, URI url) {
    }
}
