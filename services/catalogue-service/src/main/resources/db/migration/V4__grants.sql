-- Droits des rôles applicatifs (créés par infra/postgres/init/00_roles.sh).
-- Chaque bloc est gardé par un test d'existence : la migration passe aussi sur
-- une base de test (Testcontainers) où ces rôles n'existent pas.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'catalogue_app') THEN
        GRANT USAGE ON SCHEMA public TO catalogue_app;
        GRANT SELECT ON category, category_price_stats, app_user, shop, article,
            article_photo, price_history, outbox_event, ping, user_interest TO catalogue_app;
        -- Pas de DELETE sur article : le retrait est un statut (RETIRE).
        GRANT INSERT, UPDATE ON app_user, shop, article, article_photo, outbox_event, ping TO catalogue_app;
        GRANT INSERT, DELETE ON user_interest TO catalogue_app;
        -- Le trigger record_price_change s'exécute avec les droits de l'appelant.
        GRANT INSERT ON price_history TO catalogue_app;
        GRANT DELETE ON article_photo TO catalogue_app;
        GRANT USAGE ON SEQUENCE price_history_id_seq, outbox_event_id_seq, ping_id_seq TO catalogue_app;
    END IF;

    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'controle_app') THEN
        GRANT USAGE ON SCHEMA public TO controle_app;
        GRANT SELECT ON category_price_stats TO controle_app;   -- et rien d'autre
    END IF;
END
$$;
