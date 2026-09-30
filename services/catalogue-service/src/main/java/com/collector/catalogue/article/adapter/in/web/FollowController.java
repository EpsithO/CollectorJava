package com.collector.catalogue.article.adapter.in.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.collector.catalogue.article.application.FollowArticle;
import com.collector.catalogue.shared.adapter.in.web.Members;

/** US-029 : suivre un article (PUT) ou ne plus le suivre (DELETE), idempotents. */
@RestController
@RequestMapping("/api/v1/articles/{id}/follow")
@PreAuthorize("hasRole('acheteur')")
class FollowController {

    private final FollowArticle followArticle;

    FollowController(FollowArticle followArticle) {
        this.followArticle = followArticle;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void follow(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        followArticle.follow(Members.from(jwt), id);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfollow(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        followArticle.unfollow(Members.from(jwt), id);
    }
}
