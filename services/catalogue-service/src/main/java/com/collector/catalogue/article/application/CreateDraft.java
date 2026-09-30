package com.collector.catalogue.article.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.application.port.CategoryCatalog;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.InvalidRequestException;
import com.collector.catalogue.shared.domain.Member;

/** POST /articles : crée un brouillon (les photos et la soumission viennent ensuite). */
@Service
public class CreateDraft {

    private final MemberDirectory members;
    private final CategoryCatalog categories;
    private final ArticleRepository articles;
    private final ArticleViews views;
    private final Clock clock;

    public CreateDraft(MemberDirectory members, CategoryCatalog categories, ArticleRepository articles,
                       ArticleViews views, Clock clock) {
        this.members = members;
        this.categories = categories;
        this.articles = articles;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public ArticleView execute(Member member, DraftCommand command) {
        if (!categories.exists(command.categoryId())) {
            throw new InvalidRequestException("Unknown category");
        }
        UUID sellerId = members.ensureMember(member);       // créé au premier passage, à partir du jeton
        Article draft = Article.draft(UUID.randomUUID(), sellerId, command.categoryId(), command.title(),
                command.description(), command.priceCents(), command.shippingCents(), command.attributes(),
                clock.instant());
        return views.of(articles.save(draft));
    }
}
