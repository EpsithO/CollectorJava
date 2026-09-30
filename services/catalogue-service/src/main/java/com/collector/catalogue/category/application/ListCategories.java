package com.collector.catalogue.category.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.category.application.port.CategoryRepository;
import com.collector.catalogue.category.domain.Category;

/** Cas d'usage : la classe est le port entrant. */
@Service
public class ListCategories {

    private final CategoryRepository categories;

    public ListCategories(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<Category> execute() {
        return categories.findAllOrderedByLabel();
    }
}
