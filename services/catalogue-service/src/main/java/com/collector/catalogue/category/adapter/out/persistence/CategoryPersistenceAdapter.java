package com.collector.catalogue.category.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.collector.catalogue.category.application.port.CategoryRepository;
import com.collector.catalogue.category.domain.Category;

@Component
class CategoryPersistenceAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;

    CategoryPersistenceAdapter(CategoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Category> findAllOrderedByLabel() {
        return jpa.findAllByOrderByLabelAsc().stream()
                .map(CategoryEntity::toDomain)
                .toList();
    }
}
