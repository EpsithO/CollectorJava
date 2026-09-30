package com.collector.catalogue.article.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
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
import com.collector.catalogue.article.application.FollowArticle;
import com.collector.catalogue.article.application.GetArticle;
import com.collector.catalogue.article.application.ListArticles;
import com.collector.catalogue.article.application.ListMyArticles;
import com.collector.catalogue.article.application.ListPendingReviews;
import com.collector.catalogue.article.application.RequestPhotoUpload;
import com.collector.catalogue.article.application.ReviewArticle;
import com.collector.catalogue.article.application.SubmitArticle;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.InvalidStatusException;
import com.collector.catalogue.article.domain.ReviewDecision;
import com.collector.catalogue.shared.config.SecurityConfig;
import com.collector.catalogue.shared.domain.InvalidRequestException;
import com.collector.catalogue.shared.domain.NotFoundException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** US-033 et US-029 : contrat HTTP et sécurité (401, 403) des routes admin et de suivi. */
@WebMvcTest({ReviewController.class, FollowController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, ReviewAndFollowControllerTest.Metrics.class})
class ReviewAndFollowControllerTest {

    private static final UUID ARTICLE_ID = UUID.randomUUID();

    @TestConfiguration
    static class Metrics {
        @Bean
        MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean ListPendingReviews listPendingReviews;
    @MockitoBean ReviewArticle reviewArticle;
    @MockitoBean FollowArticle followArticle;
    // ArticleController est exclu de la tranche, mais ses dépendances sont inoffensives à simuler.
    @MockitoBean CreateDraft createDraft;
    @MockitoBean RequestPhotoUpload requestPhotoUpload;
    @MockitoBean SubmitArticle submitArticle;
    @MockitoBean ChangePrice changePrice;
    @MockitoBean GetArticle getArticle;
    @MockitoBean ListArticles listArticles;
    @MockitoBean ListMyArticles listMyArticles;
    @MockitoBean JwtDecoder jwtDecoder;

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("44444444-4444-4444-8444-444444444444"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }

    private static RequestPostProcessor buyer() {
        return jwt().jwt(j -> j.subject("33333333-3333-4333-8333-333333333333"))
                .authorities(new SimpleGrantedAuthority("ROLE_acheteur"));
    }

    private static RequestPostProcessor seller() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_vendeur"));
    }

    private static ArticleView inReview() {
        var now = java.time.Instant.parse("2026-01-01T00:00:00Z");
        return new ArticleView(new Article(ARTICLE_ID, UUID.randomUUID(), UUID.randomUUID(), "Suspect",
                "Description correcte", 100_000, 0, "EUR", Map.of(), ArticleStatus.EN_REVUE, 5.2, null, now, 1L,
                List.of(), "price_outlier", null, null), List.of());
    }

    // --- US-033 ---------------------------------------------------------------------------

    @Test
    void adminSeesTheReviewQueueWithTheControlDetails() throws Exception {             // CA-1
        when(listPendingReviews.execute()).thenReturn(List.of(inReview()));

        mvc.perform(get("/api/v1/admin/reviews").with(admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].article.title").value("Suspect"))
           .andExpect(jsonPath("$[0].anomaly_score").value(5.2))
           .andExpect(jsonPath("$[0].check_reason").value("price_outlier"));
    }

    @Test
    void reviewRoutesRequireAnAdmin() throws Exception {                                // CA-5
        mvc.perform(get("/api/v1/admin/reviews")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reviews").with(seller()))
           .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        mvc.perform(post("/api/v1/admin/reviews/{id}", ARTICLE_ID).with(buyer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VALIDER\"}"))
           .andExpect(status().isForbidden());
        verifyNoInteractions(listPendingReviews, reviewArticle);
    }

    @Test
    void adminApprovesAnArticle() throws Exception {                                    // CA-2
        when(reviewArticle.execute(any(), eq(ARTICLE_ID), eq(ReviewDecision.VALIDER), any())).thenReturn(inReview());

        mvc.perform(post("/api/v1/admin/reviews/{id}", ARTICLE_ID).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VALIDER\"}"))
           .andExpect(status().isOk());
    }

    @Test
    void adminRejectsWithAReasonAndTheMissingReasonIs400() throws Exception {           // CA-3
        when(reviewArticle.execute(any(), eq(ARTICLE_ID), eq(ReviewDecision.REJETER), eq("Photos floues")))
                .thenReturn(inReview());
        mvc.perform(post("/api/v1/admin/reviews/{id}", ARTICLE_ID).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJETER\",\"reason\":\"Photos floues\"}"))
           .andExpect(status().isOk());

        when(reviewArticle.execute(any(), eq(ARTICLE_ID), eq(ReviewDecision.REJETER), eq(null)))
                .thenThrow(new InvalidRequestException("A reason is required"));
        mvc.perform(post("/api/v1/admin/reviews/{id}", ARTICLE_ID).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJETER\"}"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void decisionOnAnArticleNotInReviewIs409AndUnknownIs404() throws Exception {        // CA-4
        doThrow(new InvalidStatusException(ArticleStatus.PUBLIE, "approve")).when(reviewArticle)
                .execute(any(), eq(ARTICLE_ID), any(), any());
        mvc.perform(post("/api/v1/admin/reviews/{id}", ARTICLE_ID).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VALIDER\"}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_status"));

        UUID unknown = UUID.randomUUID();
        doThrow(new NotFoundException("Article")).when(reviewArticle).execute(any(), eq(unknown), any(), any());
        mvc.perform(post("/api/v1/admin/reviews/{id}", unknown).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VALIDER\"}"))
           .andExpect(status().isNotFound());
    }

    @Test
    void unknownDecisionIs400() throws Exception {
        mvc.perform(post("/api/v1/admin/reviews/{id}", ARTICLE_ID).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"PEUT_ETRE\"}"))
           .andExpect(status().isBadRequest());
        verifyNoInteractions(reviewArticle);
    }

    // --- US-029 ---------------------------------------------------------------------------

    @Test
    void buyerFollowsAndUnfollowsWith204() throws Exception {                           // CA-1, CA-2
        mvc.perform(put("/api/v1/articles/{id}/follow", ARTICLE_ID).with(buyer())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/articles/{id}/follow", ARTICLE_ID).with(buyer())).andExpect(status().isNoContent());

        verify(followArticle).follow(any(), eq(ARTICLE_ID));
        verify(followArticle).unfollow(any(), eq(ARTICLE_ID));
    }

    @Test
    void followingAnUnpublishedArticleIs404() throws Exception {
        doThrow(new NotFoundException("Article")).when(followArticle).follow(any(), any());

        mvc.perform(put("/api/v1/articles/{id}/follow", ARTICLE_ID).with(buyer()))
           .andExpect(status().isNotFound());
    }

    @Test
    void followRoutesRequireTheBuyerRole() throws Exception {                           // CA-6
        mvc.perform(put("/api/v1/articles/{id}/follow", ARTICLE_ID)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/articles/{id}/follow", ARTICLE_ID).with(seller())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/articles/{id}/follow", ARTICLE_ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(followArticle);
    }
}
