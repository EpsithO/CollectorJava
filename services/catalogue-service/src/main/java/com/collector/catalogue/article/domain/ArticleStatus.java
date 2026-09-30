package com.collector.catalogue.article.domain;

/** Cycle de vie d'une annonce : BROUILLON, EN_CONTROLE, puis PUBLIE ou EN_REVUE. */
public enum ArticleStatus {
    BROUILLON,
    EN_CONTROLE,
    PUBLIE,
    EN_REVUE,
    REJETE,
    VENDU,
    RETIRE
}
