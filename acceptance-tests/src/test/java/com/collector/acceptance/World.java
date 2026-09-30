package com.collector.acceptance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.restassured.response.Response;

/**
 * État partagé entre les étapes d'un même scénario. cucumber-picocontainer en crée une instance
 * neuve par scénario : les scénarios sont indépendants les uns des autres.
 */
public class World {

    /** Utilisateur courant et son jeton (null = non authentifié). */
    public String user;
    public String token;

    /** Dernière réponse reçue. */
    public Response response;

    /** Article travaillé par le scénario et son vendeur (pour le relire avec son jeton). */
    public UUID articleId;
    public String articleOwner;

    /** Notification travaillée par le scénario (US-029). */
    public UUID notificationId;

    /** Les événements arrivés avant ce curseur ne comptent pas (voir EventAudit). */
    public int eventCursor;

    /** Jetons déjà obtenus par utilisateur (un appel Keycloak par compte et par scénario). */
    private final Map<String, String> tokens = new HashMap<>();

    public void authenticateAs(String username) {
        this.user = username;
        this.token = tokenOf(username);
    }

    public void anonymous() {
        this.user = null;
        this.token = null;
    }

    public String tokenOf(String username) {
        return tokens.computeIfAbsent(username, Http::token);
    }

    /** À appeler juste avant l'action dont on observe les événements. */
    public void markEvents() {
        if (EventAudit.isActive()) {
            eventCursor = EventAudit.cursor();
        }
    }
}
