package com.collector.catalogue.category.adapter.out.persistence;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.collector.catalogue.category.domain.Category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** L'entité JPA reste dans l'adaptateur : le schéma peut évoluer sans toucher au domaine. */
@Entity
@Immutable                                  // lecture seule en V1 (créées par les migrations)
@Table(name = "category")
class CategoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String label;

    @Column(name = "parent_id")
    private UUID parentId;

    protected CategoryEntity() {
        // requis par JPA
    }

    Category toDomain() {
        return new Category(id, slug, label, parentId);
    }
}
