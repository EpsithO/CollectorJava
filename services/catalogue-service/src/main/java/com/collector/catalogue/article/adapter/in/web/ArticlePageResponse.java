package com.collector.catalogue.article.adapter.in.web;

import java.util.List;

import com.collector.catalogue.article.application.ListArticles.Page;

record ArticlePageResponse(List<ArticleResponse> items, int page, int size, long total) {

    static ArticlePageResponse from(Page page) {
        return new ArticlePageResponse(page.items().stream().map(ArticleResponse::from).toList(),
                page.page(), page.size(), page.total());
    }
}
