package com.collector.catalogue.article.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.collector.catalogue.shared.domain.InvalidRequestException;

/**
 * Agrégat de la mise en vente. Immuable : chaque transition renvoie un nouvel article,
 * et refuse ce que le cycle de vie interdit. Java pur : aucune dépendance de framework.
 *
 * <p>{@code version} vaut {@code null} tant que l'article n'est pas enregistré ; ensuite
 * c'est la colonne de verrou optimiste, reprise comme {@code aggregate_version} des événements.
 *
 * <p>Cycle de vie : BROUILLON, puis EN_CONTROLE (soumission), puis PUBLIE ou EN_REVUE
 * (verdict du contrôle) ; un article EN_REVUE devient PUBLIE ou REJETE par décision d'un admin.
 */
public record Article(UUID id, UUID sellerId, UUID categoryId, String title, String description,
                      long priceCents, long shippingCents, String currency, Map<String, Object> attributes,
                      ArticleStatus status, Double anomalyScore, Instant publishedAt, Instant createdAt,
                      Long version, List<Photo> photos,
                      String checkReason, String reviewReason, UUID reviewedBy) {

    public static final String DEFAULT_CURRENCY = "EUR";

    public Article {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        photos = photos == null ? List.of() : List.copyOf(photos);
    }

    /** Article sans information de contrôle ni de revue (brouillon, ou relu avant verdict). */
    public Article(UUID id, UUID sellerId, UUID categoryId, String title, String description,
                   long priceCents, long shippingCents, String currency, Map<String, Object> attributes,
                   ArticleStatus status, Double anomalyScore, Instant publishedAt, Instant createdAt,
                   Long version, List<Photo> photos) {
        this(id, sellerId, categoryId, title, description, priceCents, shippingCents, currency, attributes,
                status, anomalyScore, publishedAt, createdAt, version, photos, null, null, null);
    }

    /** Nouveau brouillon. Les coordonnées sont refusées dès la création. */
    public static Article draft(UUID id, UUID sellerId, UUID categoryId, String title, String description,
                                long priceCents, long shippingCents, Map<String, Object> attributes, Instant now) {
        if (priceCents <= 0) {
            throw new InvalidRequestException("Price must be positive");
        }
        if (shippingCents < 0) {
            throw new InvalidRequestException("Shipping cost cannot be negative");
        }
        ContactInfoPolicy.requireNone(title);
        ContactInfoPolicy.requireNone(description);
        return new Article(id, sellerId, categoryId, title, description, priceCents, shippingCents,
                DEFAULT_CURRENCY, attributes, ArticleStatus.BROUILLON, null, null, now, null, List.of());
    }

    public boolean isOwnedBy(UUID memberId) {
        return sellerId.equals(memberId);
    }

    /** CA-5 : lève NotOwnerException (403) si le membre n'est pas le vendeur. */
    public void requireOwner(UUID memberId) {
        if (!isOwnedBy(memberId)) {
            throw new NotOwnerException();
        }
    }

    public boolean isPublished() {
        return status == ArticleStatus.PUBLIE;
    }

    public int validatedPhotoCount() {
        return (int) photos.stream().filter(Photo::isValidated).count();
    }

    /** Réserve un emplacement de photo (brouillon seulement, 8 au plus). */
    public Article withPendingPhoto(Photo photo) {
        requireStatus("add a photo to", ArticleStatus.BROUILLON);
        if (photos.size() >= PhotoPolicy.MAX_PER_ARTICLE) {
            throw new TooManyPhotosException();
        }
        List<Photo> updated = new ArrayList<>(photos);
        updated.add(photo);
        return withPhotos(updated);
    }

    public Article withPhotos(List<Photo> updated) {
        return copy(priceCents, status, publishedAt, updated, reviewReason, reviewedBy);
    }

    /** CA-1 / CA-6 : soumission au contrôle, à condition d'avoir au moins une photo valide. */
    public Article submit() {
        requireStatus("submit", ArticleStatus.BROUILLON);
        if (validatedPhotoCount() == 0) {
            throw new PhotoRequiredException();
        }
        return copy(priceCents, ArticleStatus.EN_CONTROLE, publishedAt, photos, reviewReason, reviewedBy);
    }

    /** CA-4 : le prix se modifie tant que l'article est publié ou en revue. */
    public Article changePrice(long newPriceCents) {
        requireStatus("change the price of", ArticleStatus.PUBLIE, ArticleStatus.EN_REVUE);
        if (newPriceCents <= 0) {
            throw new InvalidRequestException("Price must be positive");
        }
        return copy(newPriceCents, status, publishedAt, photos, reviewReason, reviewedBy);
    }

    /** US-033 CA-2 : un admin valide un article en revue, qui est publié. */
    public Article approve(UUID adminId, Instant now) {
        requireStatus("approve", ArticleStatus.EN_REVUE);
        return copy(priceCents, ArticleStatus.PUBLIE, now, photos, null, adminId);
    }

    /** US-033 CA-3 : un admin rejette un article en revue ; le motif est obligatoire. */
    public Article reject(UUID adminId, String reason) {
        requireStatus("reject", ArticleStatus.EN_REVUE);
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("A reason is required to reject an article");
        }
        return copy(priceCents, ArticleStatus.REJETE, publishedAt, photos, reason.strip(), adminId);
    }

    private Article copy(long newPrice, ArticleStatus newStatus, Instant newPublishedAt, List<Photo> newPhotos,
                         String newReviewReason, UUID newReviewedBy) {
        return new Article(id, sellerId, categoryId, title, description, newPrice, shippingCents, currency,
                attributes, newStatus, anomalyScore, newPublishedAt, createdAt, version, newPhotos,
                checkReason, newReviewReason, newReviewedBy);
    }

    private void requireStatus(String action, ArticleStatus... allowed) {
        for (ArticleStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new InvalidStatusException(status, action);
    }
}
