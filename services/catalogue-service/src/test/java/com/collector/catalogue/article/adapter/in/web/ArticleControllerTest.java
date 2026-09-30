package com.collector.catalogue.article.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.collector.catalogue.article.application.ArticleView;
import com.collector.catalogue.article.application.ChangePrice;
import com.collector.catalogue.article.application.CreateDraft;
import com.collector.catalogue.article.application.GetArticle;
import com.collector.catalogue.article.application.ListArticles;
import com.collector.catalogue.article.application.ListMyArticles;
import com.collector.catalogue.article.application.RequestPhotoUpload;
import com.collector.catalogue.article.application.SubmitArticle;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.ContactInfoForbiddenException;
import com.collector.catalogue.article.domain.NotOwnerException;
import com.collector.catalogue.article.domain.PhotoRequiredException;
import com.collector.catalogue.article.domain.TooManyPhotosException;
import com.collector.catalogue.shared.application.port.PhotoStorage.PresignedUpload;
import com.collector.catalogue.shared.config.SecurityConfig;
import com.collector.catalogue.shared.domain.NotFoundException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@WebMvcTest(ArticleController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, ArticleControllerTest.Metrics.class})
class ArticleControllerTest {

    private static final String VENDEUR1 = "11111111-1111-4111-8111-111111111111";
    private static final UUID CATEGORY = UUID.randomUUID();
    private static final UUID ARTICLE_ID = UUID.randomUUID();

    @org.springframework.boot.test.context.TestConfiguration
    static class Metrics {
        @org.springframework.context.annotation.Bean
        io.micrometer.core.instrument.MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean CreateDraft createDraft;
    @MockitoBean RequestPhotoUpload requestPhotoUpload;
    @MockitoBean SubmitArticle submitArticle;
    @MockitoBean ChangePrice changePrice;
    @MockitoBean GetArticle getArticle;
    @MockitoBean ListArticles listArticles;
    @MockitoBean ListMyArticles listMyArticles;
    @MockitoBean JwtDecoder jwtDecoder;

    private static RequestPostProcessor seller() {
        return jwt().jwt(j -> j.subject(VENDEUR1).claim("name", "Vendeur Un"))
                .authorities(new SimpleGrantedAuthority("ROLE_vendeur"));
    }

    private static RequestPostProcessor buyer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_acheteur"));
    }

    private static ArticleView view(ArticleStatus status) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Article article = new Article(ARTICLE_ID, UUID.randomUUID(), CATEGORY, "Air Jordan 1", "Paire neuve jamais portée",
                26_000, 500, "EUR", Map.of("taille", 42), status, 3.5, now, now, 2L, List.of());
        return new ArticleView(article, List.of(new ArticleView.PhotoLink(UUID.randomUUID(),
                URI.create("https://storage.test/read/x"))));
    }

    private static final String VALID_BODY = """
            {"title":"Air Jordan 1","description":"Paire neuve jamais portée","category_id":"%s",
             "price_cents":26000,"shipping_cents":500,"attributes":{"taille":42}}""".formatted(CATEGORY);

    // --- création du brouillon ---------------------------------------------------

    @Test
    void createDraftReturns201WithLocationAndHidesTheAnomalyScore() throws Exception {
        when(createDraft.execute(any(), any())).thenReturn(view(ArticleStatus.BROUILLON));

        mvc.perform(post("/api/v1/articles").with(seller()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
           .andExpect(status().isCreated())
           .andExpect(header().string("Location", "/api/v1/articles/" + ARTICLE_ID))
           .andExpect(jsonPath("$.status").value("BROUILLON"))
           .andExpect(jsonPath("$.price_cents").value(26000))
           .andExpect(jsonPath("$.photos[0].url").value("https://storage.test/read/x"))
           .andExpect(jsonPath("$.anomaly_score").doesNotExist());
    }

    @Test
    void createDraftWithoutTokenIs401() throws Exception {                       // CA-5
        mvc.perform(post("/api/v1/articles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
           .andExpect(status().isUnauthorized());
        verifyNoInteractions(createDraft);
    }

    @Test
    void createDraftAsBuyerIs403() throws Exception {                            // CA-5
        mvc.perform(post("/api/v1/articles").with(buyer()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("forbidden"));
        verifyNoInteractions(createDraft);
    }

    @Test
    void createDraftValidatesTheShapeOfTheBody() throws Exception {
        String tooCheap = VALID_BODY.replace("26000", "0");
        String shortTitle = VALID_BODY.replace("Air Jordan 1", "ab");

        mvc.perform(post("/api/v1/articles").with(seller()).contentType(MediaType.APPLICATION_JSON).content(tooCheap))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_request"))
           .andExpect(jsonPath("$.fields[0]").value("priceCents"));
        mvc.perform(post("/api/v1/articles").with(seller()).contentType(MediaType.APPLICATION_JSON).content(shortTitle))
           .andExpect(status().isBadRequest());
        verifyNoInteractions(createDraft);
    }

    @Test
    void createDraftRefusesUnknownFields() throws Exception {
        String extra = VALID_BODY.replace("{\"title\"", "{\"status\":\"PUBLIE\",\"title\"");

        mvc.perform(post("/api/v1/articles").with(seller()).contentType(MediaType.APPLICATION_JSON).content(extra))
           .andExpect(status().isBadRequest());
    }

    @Test
    void contactInfoIs422() throws Exception {
        when(createDraft.execute(any(), any())).thenThrow(new ContactInfoForbiddenException());

        mvc.perform(post("/api/v1/articles").with(seller()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
           .andExpect(status().isUnprocessableContent())
           .andExpect(jsonPath("$.code").value("contact_info_forbidden"));
    }

    // --- photos --------------------------------------------------------------------

    @Test
    void photoUploadReturnsThePresignedUrl() throws Exception {
        var upload = new RequestPhotoUpload.PhotoUpload(UUID.randomUUID(), new PresignedUpload(
                URI.create("https://storage.test/put"), Map.of("Content-Type", "image/jpeg"),
                Instant.parse("2026-01-01T00:05:00Z")));
        when(requestPhotoUpload.execute(any(), eq(ARTICLE_ID), eq("image/jpeg"), eq(2000L))).thenReturn(upload);

        mvc.perform(post("/api/v1/articles/{id}/photos", ARTICLE_ID).with(seller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content_type\":\"image/jpeg\",\"size_bytes\":2000}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.upload_url").value("https://storage.test/put"))
           .andExpect(jsonPath("$.upload_headers['Content-Type']").value("image/jpeg"))
           .andExpect(jsonPath("$.expires_at").value("2026-01-01T00:05:00Z"));
    }

    @Test
    void ninthPhotoIs409() throws Exception {
        when(requestPhotoUpload.execute(any(), any(), any(), anyLong())).thenThrow(new TooManyPhotosException());

        mvc.perform(post("/api/v1/articles/{id}/photos", ARTICLE_ID).with(seller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content_type\":\"image/jpeg\",\"size_bytes\":2000}"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.code").value("too_many_photos"));
    }

    // --- soumission ------------------------------------------------------------------

    @Test
    void submissionReturnsTheArticleEnControle() throws Exception {              // CA-1
        when(submitArticle.execute(any(), eq(ARTICLE_ID))).thenReturn(view(ArticleStatus.EN_CONTROLE));

        mvc.perform(post("/api/v1/articles/{id}/submission", ARTICLE_ID).with(seller()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("EN_CONTROLE"));
    }

    @Test
    void submissionWithoutPhotoIs422() throws Exception {                        // CA-6
        when(submitArticle.execute(any(), any())).thenThrow(new PhotoRequiredException());

        mvc.perform(post("/api/v1/articles/{id}/submission", ARTICLE_ID).with(seller()))
           .andExpect(status().isUnprocessableContent())
           .andExpect(jsonPath("$.code").value("photo_required"));
    }

    @Test
    void submissionOfSomeoneElsesArticleIs403() throws Exception {               // CA-5
        when(submitArticle.execute(any(), any())).thenThrow(new NotOwnerException());

        mvc.perform(post("/api/v1/articles/{id}/submission", ARTICLE_ID).with(seller()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void submissionOfUnknownArticleIs404() throws Exception {
        when(submitArticle.execute(any(), any())).thenThrow(new NotFoundException("Article"));

        mvc.perform(post("/api/v1/articles/{id}/submission", ARTICLE_ID).with(seller()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void submissionWithMalformedIdIs400() throws Exception {
        mvc.perform(post("/api/v1/articles/{id}/submission", "pas-un-uuid").with(seller()))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    // --- prix -----------------------------------------------------------------------------

    @Test
    void priceChangeReturnsTheUpdatedArticle() throws Exception {                // CA-4
        when(changePrice.execute(any(), eq(ARTICLE_ID), eq(24_000L))).thenReturn(view(ArticleStatus.PUBLIE));

        mvc.perform(patch("/api/v1/articles/{id}/price", ARTICLE_ID).with(seller())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price_cents\":24000}"))
           .andExpect(status().isOk());
    }

    @Test
    void priceChangeOnSomeoneElsesArticleIs403() throws Exception {              // CA-5
        when(changePrice.execute(any(), any(), anyLong())).thenThrow(new NotOwnerException());

        mvc.perform(patch("/api/v1/articles/{id}/price", ARTICLE_ID).with(seller())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price_cents\":1000}"))
           .andExpect(status().isForbidden());
    }

    @Test
    void priceChangeWithoutTokenIs401AndInvalidPriceIs400() throws Exception {   // CA-5
        mvc.perform(patch("/api/v1/articles/{id}/price", ARTICLE_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price_cents\":1000}"))
           .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/articles/{id}/price", ARTICLE_ID).with(seller())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price_cents\":-5}"))
           .andExpect(status().isBadRequest());
    }

    // --- lecture -------------------------------------------------------------------------------

    @Test
    void catalogueIsPublic() throws Exception {
        when(listArticles.execute(eq(Optional.of("sneakers")), eq(0), eq(20)))
                .thenReturn(new ListArticles.Page(List.of(view(ArticleStatus.PUBLIE)), 0, 20, 1));

        mvc.perform(get("/api/v1/articles").param("category", "sneakers"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(1))
           .andExpect(jsonPath("$.items[0].title").value("Air Jordan 1"));
    }

    @Test
    void catalogueRefusesAPageSizeAbove100() throws Exception {
        mvc.perform(get("/api/v1/articles").param("size", "101"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_request"));
        mvc.perform(get("/api/v1/articles").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void articleDetailIsPublicWithAnOptionalToken() throws Exception {
        when(getArticle.execute(any(), eq(ARTICLE_ID))).thenReturn(view(ArticleStatus.PUBLIE));

        mvc.perform(get("/api/v1/articles/{id}", ARTICLE_ID)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/articles/{id}", ARTICLE_ID).with(seller())).andExpect(status().isOk());
    }

    @Test
    void unpublishedArticleIs404ForAStranger() throws Exception {
        when(getArticle.execute(any(), any())).thenThrow(new NotFoundException("Article"));

        mvc.perform(get("/api/v1/articles/{id}", ARTICLE_ID)).andExpect(status().isNotFound());
    }

    @Test
    void myArticlesRequiresAuthenticationAndAcceptsAStatusFilter() throws Exception {
        mvc.perform(get("/api/v1/me/articles")).andExpect(status().isUnauthorized());

        when(listMyArticles.execute(any(), eq(ArticleStatus.EN_REVUE))).thenReturn(List.of(view(ArticleStatus.EN_REVUE)));
        mvc.perform(get("/api/v1/me/articles").param("status", "EN_REVUE").with(seller()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].status").value("EN_REVUE"));
        mvc.perform(get("/api/v1/me/articles").param("status", "N_IMPORTE_QUOI").with(seller()))
           .andExpect(status().isBadRequest());
    }
}
