package com.collector.catalogue.article.adapter.in.web;

import com.collector.catalogue.article.application.ArticleView;

/** Vue admin : l'article et les éléments du contrôle qui l'ont mis en revue (jamais exposés au public). */
record ReviewItemResponse(ArticleResponse article, Double anomalyScore, String checkReason) {

    static ReviewItemResponse from(ArticleView view) {
        return new ReviewItemResponse(ArticleResponse.from(view), view.article().anomalyScore(),
                view.article().checkReason());
    }
}
