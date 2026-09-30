package com.collector.catalogue.article.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.Article;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.shared.domain.NotFoundException;

/** GET /articles/{id} : public pour un article publié ; sinon visible de son vendeur seulement. */
@Service
public class GetArticle {

    private final MemberDirectory members;
    private final ArticleRepository articles;
    private final ArticleViews views;

    public GetArticle(MemberDirectory members, ArticleRepository articles, ArticleViews views) {
        this.members = members;
        this.articles = articles;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public ArticleView execute(Optional<Member> viewer, UUID articleId) {
        Article article = articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article"));
        if (article.isPublished() || isOwner(viewer, article)) {
            return views.of(article);
        }
        // 404 et non 403 : on ne révèle pas l'existence d'un article non publié.
        throw new NotFoundException("Article");
    }

    private boolean isOwner(Optional<Member> viewer, Article article) {
        return viewer.flatMap(member -> members.findId(member.subject()))
                .map(article::isOwnedBy)
                .orElse(false);
    }
}
