package com.collector.acceptance;

import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;

public class Hooks {

    @BeforeAll
    public static void configureHttp() {
        Http.configure();
    }

    /**
     * Les scénarios qui observent un événement portent l'étiquette @events : la file d'écoute est
     * déclarée AVANT l'action testée, sinon l'événement serait publié avant que la copie existe.
     */
    @Before("@events")
    public void startAudit() {
        EventAudit.start();
    }

    @AfterAll
    public static void removeAuditQueue() {
        EventAudit.stop();
    }
}
