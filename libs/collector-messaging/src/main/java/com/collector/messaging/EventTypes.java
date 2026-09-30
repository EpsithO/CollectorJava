package com.collector.messaging;

/** Types d'événements = clés de routage de l'échange (docs/events.md). */
public final class EventTypes {

    public static final String ARTICLE_SUBMITTED = "article.submitted";
    public static final String ARTICLE_CHECKED = "article.checked";
    public static final String FRAUD_ALERT = "fraud.alert";
    public static final String PRICE_CHANGED = "price.changed";
    public static final String INTERESTS_UPDATED = "interests.updated";
    public static final String ARTICLE_REVIEWED = "article.reviewed";
    public static final String FOLLOW_CHANGED = "follow.changed";
    public static final String PING_CREATED = "ping.created";

    private EventTypes() {
    }
}
