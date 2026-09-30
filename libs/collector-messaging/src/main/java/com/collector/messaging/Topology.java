package com.collector.messaging;

/** Noms de l'échange et des files. La topologie n'est déclarée que par cette bibliothèque. */
public final class Topology {

    public static final String EXCHANGE = "collector.events";
    public static final String DEAD_LETTER_EXCHANGE = "collector.dlx";
    public static final String DEAD_LETTER_QUEUE = "collector.dead-letter";

    public static final String CONTROLE_ARTICLE_SUBMITTED = "controle.article-submitted";
    public static final String CATALOGUE_ARTICLE_CHECKED = "catalogue.article-checked";
    public static final String FRAUDE_ALERTS = "fraude.alerts";
    public static final String FRAUDE_PRICE_CHANGED = "fraude.price-changed";
    public static final String NOTIFICATION_PRICE_CHANGED = "notification.price-changed";
    public static final String NOTIFICATION_INTERESTS_UPDATED = "notification.interests-updated";
    public static final String NOTIFICATION_ARTICLE_REVIEWED = "notification.article-reviewed";
    public static final String NOTIFICATION_FOLLOW_CHANGED = "notification.follow-changed";
    public static final String CATALOGUE_PING = "catalogue.ping";

    /** RabbitMQ 4 met 20 par défaut : explicite pour ne pas rejouer 20 fois un message empoisonné. */
    public static final int DELIVERY_LIMIT = 5;

    private Topology() {
    }
}
