package com.collector.catalogue.article.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.collector.catalogue.article.application.ListPendingReviews;
import com.collector.catalogue.article.application.ReviewArticle;
import com.collector.catalogue.shared.adapter.in.web.Members;

import jakarta.validation.Valid;

/** US-033 : file de revue de l'administrateur. Tout le contrôleur exige le rôle admin (CA-5). */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('admin')")
class ReviewController {

    private final ListPendingReviews listPendingReviews;
    private final ReviewArticle reviewArticle;

    ReviewController(ListPendingReviews listPendingReviews, ReviewArticle reviewArticle) {
        this.listPendingReviews = listPendingReviews;
        this.reviewArticle = reviewArticle;
    }

    @GetMapping
    List<ReviewItemResponse> pending() {
        return listPendingReviews.execute().stream().map(ReviewItemResponse::from).toList();
    }

    @PostMapping("/{articleId}")
    ArticleResponse decide(@PathVariable UUID articleId, @Valid @RequestBody ReviewRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        return ArticleResponse.from(reviewArticle.execute(Members.from(jwt), articleId,
                request.decision(), request.reason()));
    }
}
