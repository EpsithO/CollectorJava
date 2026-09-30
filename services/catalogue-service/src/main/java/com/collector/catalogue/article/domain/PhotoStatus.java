package com.collector.catalogue.article.domain;

public enum PhotoStatus {
    /** URL d'envoi délivrée, binaire pas encore vérifié. */
    EN_ATTENTE,
    /** Présent dans le stockage, type et taille conformes. */
    VALIDEE
}
