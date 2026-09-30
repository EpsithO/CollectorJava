package com.collector.catalogue.category.application.port;

import java.util.List;

import com.collector.catalogue.category.domain.Category;

/** Port sortant, exprimé par le besoin du cas d'usage. */
public interface CategoryRepository {

    List<Category> findAllOrderedByLabel();
}
