package com.collector.catalogue.article.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.article.domain.PriceChanged;
import com.collector.catalogue.shared.application.port.DomainEventPublisher;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;

/** PATCH /articles/{id}/price (CA-4, CA-5). */
@Service
public class ChangePrice {

    private final MemberDirectory members;
    private final ArticleRepository articles;
    private final DomainEventPublisher events;
    private final ArticleViews views;

    public ChangePrice(MemberDirectory members, ArticleRepository articles, DomainEventPublisher events,
                       ArticleViews views) {
        this.members = members;
        this.articles = articles;
        this.events = events;
        this.views = views;
    }

    @Transactional
    public ArticleView execute(Member member, UUID articleId, long newPriceCents) {
        UUID memberId = members.ensureMember(member);
        Article article = articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article"));
        article.requireOwner(memberId);                      // CA-5 : 403 pour l'article d'un autre

        Article changed = article.changePrice(newPriceCents);    // 409 si ni publié ni en revue
        if (changed.priceCents() == article.priceCents()) {
            return views.of(article);                        // même prix : rien à écrire, rien à annoncer
        }
        Article saved = articles.save(changed);
        // aggregate_version = version après écriture : le consommateur ignore les versions périmées.
        events.publish(new PriceChanged(saved.id(), saved.version(), saved.sellerId(), saved.categoryId(), saved.title(),
                article.priceCents(), saved.priceCents(), saved.currency()));
        return views.of(saved);
    }
}
