package com.collector.notification.notification.application.port;

import java.util.UUID;

public interface ArticleVersionRepository {

    /**
     * Avance la dernière version vue d'un article, atomiquement. Renvoie vrai seulement si la version
     * est strictement plus récente que la précédente : un événement périmé ou rejoué est ignoré.
     */
    boolean advance(UUID articleId, long version);
}
