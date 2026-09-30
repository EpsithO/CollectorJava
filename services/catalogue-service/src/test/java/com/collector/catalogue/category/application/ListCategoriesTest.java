package com.collector.catalogue.category.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.category.application.port.CategoryRepository;
import com.collector.catalogue.category.domain.Category;

class ListCategoriesTest {

    @Test
    void returnsCategoriesFromRepository() {
        var sneakers = new Category(UUID.randomUUID(), "sneakers", "Baskets", null);
        CategoryRepository repository = () -> List.of(sneakers);      // port à une méthode : une lambda suffit

        assertThat(new ListCategories(repository).execute()).containsExactly(sneakers);
        assertThat(sneakers.isRoot()).isTrue();
    }
}
