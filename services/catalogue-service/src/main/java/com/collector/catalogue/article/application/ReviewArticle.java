package com.collector.catalogue.article.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.ArticleReviewed;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.article.domain.ReviewDecision;
import com.collector.catalogue.shared.application.port.DomainEventPublisher;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;

/**
 * POST /admin/reviews/{id} (US-033, CA-2 à CA-4) : valide ou rejette un article en revue.
 * La décision et son événement partent dans la même transaction (outbox).
 */
@Service
public class ReviewArticle {

    private final MemberDirectory members;
    private final ArticleRepository articles;
    private final DomainEventPublisher events;
    private final ArticleViews views;
    private final Clock clock;

    public ReviewArticle(MemberDirectory members, ArticleRepository articles, DomainEventPublisher events,
                         ArticleViews views, Clock clock) {
        this.members = members;
        this.articles = articles;
        this.events = events;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public ArticleView execute(Member admin, UUID articleId, ReviewDecision decision, String reason) {
        UUID adminId = members.ensureMember(admin);
        Article article = articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article"));

        Article reviewed = decision == ReviewDecision.VALIDER
                ? article.approve(adminId, clock.instant())       // 409 si l'article n'est pas EN_REVUE
                : article.reject(adminId, reason);                // 400 sans motif
        Article saved = articles.save(reviewed);

        boolean published = saved.status() == ArticleStatus.PUBLIE;
        events.publish(new ArticleReviewed(saved.id(), saved.sellerId(), saved.status().name(),
                published ? null : saved.reviewReason(), UUID.fromString(admin.subject())));
        return views.of(saved);
    }
}
