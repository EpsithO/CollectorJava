package com.collector.catalogue.article;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.collector.catalogue.AbstractIntegrationTest;
import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.application.port.CategoryCatalog;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.Photo;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;

/** Adaptateur JPA + JDBC de l'article contre PostgreSQL : schéma, version, photos, verdict idempotent. */
class ArticlePersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired ArticleRepository articles;
    @Autowired CategoryCatalog categories;
    @Autowired MemberDirectory members;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;

    private final String subject = UUID.randomUUID().toString();

    private UUID sneakers() {
        return categories.idBySlug("sneakers").orElseThrow();
    }

    private UUID seller() {
        return tx.execute(status -> members.ensureMember(new Member(subject, "Vendeur IT", subject + "@test.local")));
    }

    private Article newDraft(UUID sellerId) {
        return Article.draft(UUID.randomUUID(), sellerId, sneakers(), "Air Jordan 1", "Paire neuve jamais portée",
                26_000, 500, Map.of("taille", 42, "etat", "neuf"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void savesAndReloadsADraftWithAttributesAndVersion() {
        UUID sellerId = seller();

        Article saved = tx.execute(s -> articles.save(newDraft(sellerId)));

        assertThat(saved.version()).isZero();
        Article reloaded = articles.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ArticleStatus.BROUILLON);
        assertThat(reloaded.currency()).isEqualTo("EUR");
        assertThat(reloaded.attributes()).containsEntry("etat", "neuf").containsEntry("taille", 42);
        assertThat(reloaded.priceCents()).isEqualTo(26_000);
    }

    @Test
    void photosKeepTheirInsertionOrderAndBecomeValidated() {
        UUID sellerId = seller();
        Article draft = tx.execute(s -> articles.save(newDraft(sellerId)));
        Photo first = Photo.pending(draft.id(), "image/jpeg", 1000);
        Photo second = Photo.pending(draft.id(), "image/png", 2000);

        Article withPhotos = tx.execute(s -> articles.save(draft.withPhotos(List.of(first))));
        tx.execute(s -> articles.save(withPhotos.withPhotos(List.of(first, second))));
        Article reloaded = articles.findById(draft.id()).orElseThrow();
        assertThat(reloaded.photos()).extracting(Photo::id).containsExactly(first.id(), second.id());

        tx.execute(s -> articles.save(reloaded.withPhotos(List.of(first.validated(), second))));
        assertThat(articles.findById(draft.id()).orElseThrow().photos())
                .extracting(Photo::isValidated).containsExactly(true, false);
    }

    @Test
    void everyUpdateIncrementsTheVersionUsedAsAggregateVersion() {
        UUID sellerId = seller();
        Article published = tx.execute(s -> {
            Article draft = articles.save(newDraft(sellerId));
            jdbc.sql("UPDATE article SET status = 'PUBLIE', published_at = now() WHERE id = :id")
                    .param("id", draft.id()).update();
            return draft;
        });
        Article current = articles.findById(published.id()).orElseThrow();

        Article changed = tx.execute(s -> articles.save(current.changePrice(24_000)));

        assertThat(changed.priceCents()).isEqualTo(24_000);
        assertThat(changed.version()).isGreaterThan(current.version());
        // Le trigger du schéma a tenu l'historique de prix (CA-4).
        assertThat(jdbc.sql("SELECT count(*) FROM price_history WHERE article_id = :id")
                .param("id", published.id()).query(Long.class).single()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void applyVerdictIsIdempotentAndReportsTheSubmissionDate() {
        UUID sellerId = seller();
        Article draft = tx.execute(s -> articles.save(newDraft(sellerId)));
        tx.execute(s -> jdbc.sql("UPDATE article SET status = 'EN_CONTROLE' WHERE id = :id")
                .param("id", draft.id()).update());
        Instant now = Instant.now();

        var first = tx.execute(s -> articles.applyVerdict(draft.id(), ArticleStatus.PUBLIE, 0.4, null, now));
        var second = tx.execute(s -> articles.applyVerdict(draft.id(), ArticleStatus.EN_REVUE, 9.0, "price_outlier", now));

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        Article reloaded = articles.findById(draft.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ArticleStatus.PUBLIE);
        assertThat(reloaded.anomalyScore()).isEqualTo(0.4);
        assertThat(reloaded.publishedAt()).isNotNull();
        assertThat(reloaded.version()).isEqualTo(draft.version() + 1);
    }

    @Test
    void cataloguePaginatesPublishedArticlesOfTheSeed() {
        var page = articles.findPublishedInCategory(sneakers(), 0, 10);

        assertThat(page.total()).isGreaterThanOrEqualTo(35);
        assertThat(page.items()).hasSize(10).allMatch(Article::isPublished);
        assertThat(page.items()).extracting(Article::publishedAt).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(articles.findPublished(0, 5).items()).hasSize(5);
    }

    @Test
    void listsASellersArticlesFilteredByStatus() {
        UUID sellerId = seller();
        tx.execute(s -> articles.save(newDraft(sellerId)));

        assertThat(articles.findBySeller(sellerId, null)).isNotEmpty();
        assertThat(articles.findBySeller(sellerId, ArticleStatus.BROUILLON)).isNotEmpty();
        assertThat(articles.findBySeller(sellerId, ArticleStatus.RETIRE)).isEmpty();
    }
}
