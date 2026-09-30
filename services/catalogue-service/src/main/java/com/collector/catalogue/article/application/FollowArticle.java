package com.collector.catalogue.article.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.FollowChanged;
import com.collector.catalogue.shared.application.port.DomainEventPublisher;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;

/**
 * PUT / DELETE /articles/{id}/follow (US-029, CA-1 et CA-2). Le catalogue ne garde aucun état
 * de suivi : il vérifie l'article et transmet le changement ; le notification-service tient
 * sa propre copie. Idempotent : répéter l'appel republie un événement sans effet de plus.
 */
@Service
public class FollowArticle {

    private final ArticleRepository articles;
    private final DomainEventPublisher events;
    private final Clock clock;

    public FollowArticle(ArticleRepository articles, DomainEventPublisher events, Clock clock) {
        this.articles = articles;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void follow(Member member, UUID articleId) {
        Article article = articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article"));
        if (!article.isPublished()) {
            throw new NotFoundException("Article");          // un article non publié n'existe pas pour un acheteur
        }
        publish(member, articleId, true);
    }

    @Transactional
    public void unfollow(Member member, UUID articleId) {
        articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article"));
        publish(member, articleId, false);
    }

    private void publish(Member member, UUID articleId, boolean following) {
        // L'identité qui circule entre services est le sub Keycloak.
        events.publish(new FollowChanged(UUID.fromString(member.subject()), articleId, following, clock.instant()));
    }
}
