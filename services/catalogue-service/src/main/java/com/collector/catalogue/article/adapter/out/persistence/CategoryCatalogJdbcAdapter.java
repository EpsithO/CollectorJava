package com.collector.catalogue.article.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.collector.catalogue.article.application.port.CategoryCatalog;

@Component
class CategoryCatalogJdbcAdapter implements CategoryCatalog {

    private final JdbcClient jdbc;

    CategoryCatalogJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(UUID categoryId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM category WHERE id = :id)")
                .param("id", categoryId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<UUID> idBySlug(String slug) {
        return jdbc.sql("SELECT id FROM category WHERE slug = :slug")
                .param("slug", slug)
                .query(UUID.class)
                .optional();
    }
}
