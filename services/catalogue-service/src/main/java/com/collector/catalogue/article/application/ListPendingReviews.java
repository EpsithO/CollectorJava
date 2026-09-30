package com.collector.catalogue.article.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.ArticleStatus;

/** GET /admin/reviews (US-033, CA-1) : articles EN_REVUE, les plus anciens d'abord. */
@Service
public class ListPendingReviews {

    private final ArticleRepository articles;
    private final ArticleViews views;

    public ListPendingReviews(ArticleRepository articles, ArticleViews views) {
        this.articles = articles;
        this.views = views;
    }

    // Le rôle admin est exigé par l'adaptateur web (@PreAuthorize) : aucune donnée du membre n'est utilisée ici.
    @Transactional(readOnly = true)
    public List<ArticleView> execute() {
        return articles.findByStatusOldestFirst(ArticleStatus.EN_REVUE).stream().map(views::of).toList();
    }
}
