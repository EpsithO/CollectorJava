package com.collector.catalogue.article.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.collector.catalogue.article.domain.Photo;
import com.collector.catalogue.article.domain.PhotoStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "article_photo")
class PhotoEntity {

    @Id
    private UUID id;

    @Column(name = "article_id", nullable = false, updatable = false)
    private UUID articleId;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoStatus status;

    // Attribuée par le trigger assign_photo_position à l'insertion (à la suite).
    @Column(insertable = false, updatable = false)
    private Short position;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PhotoEntity() {
        // requis par JPA
    }

    static PhotoEntity fromDomain(UUID articleId, Photo photo) {
        PhotoEntity e = new PhotoEntity();
        e.id = photo.id();
        e.articleId = articleId;
        e.apply(photo);
        return e;
    }

    void apply(Photo photo) {
        this.storageKey = photo.storageKey();
        this.contentType = photo.contentType();
        this.sizeBytes = photo.sizeBytes();
        this.status = photo.status();
    }

    Photo toDomain() {
        return new Photo(id, storageKey, contentType, sizeBytes, status);
    }

    UUID getId() {
        return id;
    }

    UUID getArticleId() {
        return articleId;
    }

    Short getPosition() {
        return position;
    }
}
