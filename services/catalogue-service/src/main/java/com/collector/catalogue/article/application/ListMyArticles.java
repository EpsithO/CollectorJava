package com.collector.catalogue.article.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.article.application.port.ArticleRepository;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;

/** GET /me/articles : tous les articles du vendeur connecté. */
@Service
public class ListMyArticles {

    private final MemberDirectory members;
    private final ArticleRepository articles;
    private final ArticleViews views;

    public ListMyArticles(MemberDirectory members, ArticleRepository articles, ArticleViews views) {
        this.members = members;
        this.articles = articles;
        this.views = views;
    }

    // Un membre qui n'a jamais rien écrit n'a pas de ligne app_user : liste vide,
    // et aucune création sur une lecture.
    @Transactional(readOnly = true)
    public List<ArticleView> execute(Member member, ArticleStatus statusOrNull) {
        return members.findId(member.subject())
                .map(id -> articles.findBySeller(id, statusOrNull).stream().map(views::of).toList())
                .orElse(List.of());
    }
}
