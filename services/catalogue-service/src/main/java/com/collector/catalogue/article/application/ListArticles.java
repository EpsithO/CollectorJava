package com.collector.catalogue.article.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.application.port.ArticleRepository.ArticlePage;
import com.collector.catalogue.article.application.port.CategoryCatalog;

/** GET /articles : catalogue public, articles PUBLIE, récents d'abord. */
@Service
public class ListArticles {

    public static final int MAX_PAGE_SIZE = 100;

    private final ArticleRepository articles;
    private final CategoryCatalog categories;
    private final ArticleViews views;

    public ListArticles(ArticleRepository articles, CategoryCatalog categories, ArticleViews views) {
        this.articles = articles;
        this.categories = categories;
        this.views = views;
    }

    public record Page(List<ArticleView> items, int page, int size, long total) {
    }

    /** categorySlug facultatif ; un slug inconnu donne une page vide (pas une erreur). */
    @Transactional(readOnly = true)
    public Page execute(Optional<String> categorySlug, int page, int size) {
        ArticlePage found;
        if (categorySlug.isPresent()) {
            Optional<UUID> categoryId = categories.idBySlug(categorySlug.get());
            found = categoryId.isPresent()
                    ? articles.findPublishedInCategory(categoryId.get(), page, size)
                    : new ArticlePage(List.of(), 0);
        } else {
            found = articles.findPublished(page, size);
        }
        return new Page(found.items().stream().map(views::of).toList(), page, size, found.total());
    }
}
