package com.collector.catalogue.category.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.collector.catalogue.category.application.ListCategories;

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

    private final ListCategories listCategories;

    CategoryController(ListCategories listCategories) {
        this.listCategories = listCategories;
    }

    // Public : le parcours du catalogue ne nécessite pas d'être authentifié (sujet).
    @GetMapping
    List<CategoryResponse> list() {
        return listCategories.execute().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
