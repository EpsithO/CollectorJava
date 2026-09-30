package com.collector.catalogue.category;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.collector.catalogue.AbstractIntegrationTest;
import com.collector.catalogue.category.application.port.CategoryRepository;
import com.collector.catalogue.category.domain.Category;

/** L'adaptateur réel contre PostgreSQL, via le port ; ddl-auto: validate vérifie aussi le mapping. */
class CategoryPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired CategoryRepository repository;

    @Test
    void readsCategoriesOrderedByLabel() {
        assertThat(repository.findAllOrderedByLabel())
                .extracting(Category::label)
                .hasSizeGreaterThanOrEqualTo(5)
                .isSorted();
    }
}
