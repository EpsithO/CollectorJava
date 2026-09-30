-- Jeu de données de démonstration (dev et recette), idempotent (migration répétable).
-- Les keycloak_sub sont les "id" des utilisateurs de infra/keycloak/collector-realm.json.
INSERT INTO app_user (keycloak_sub, display_name, email, is_seller) VALUES
    ('11111111-1111-4111-8111-111111111111', 'Vendeur Un',   'vendeur1@collector.local',  true),
    ('22222222-2222-4222-8222-222222222222', 'Vendeur Deux', 'vendeur2@collector.local',  true),
    ('33333333-3333-4333-8333-333333333333', 'Acheteur Un',  'acheteur1@collector.local', false),
    ('44444444-4444-4444-8444-444444444444', 'Admin',        'admin@collector.local',     false)
ON CONFLICT (keycloak_sub) DO NOTHING;

-- 35 articles publiés par catégorie (seuil de 30 de category_price_stats) : la règle
-- des 3 écarts-types est active dès le démarrage. Sneakers : médiane ≈ 250 €,
-- écart-type ≈ 47 € → 260 € PUBLIE, 1 000 € EN_REVUE. Ajoutés une seule fois.
INSERT INTO article (seller_id, category_id, title, description,
                     price_cents, shipping_cents, status, published_at)
SELECT seller.id, c.id, c.label || ' #' || g,
       'Article de démonstration généré par le jeu de données.',
       base.price_cents + ((g % 11) - 5) * base.step_cents, 500, 'PUBLIE', now() - g * interval '1 day'
FROM generate_series(1, 35) AS g
CROSS JOIN category AS c
JOIN (VALUES ('sneakers', 25000, 1500), ('posters', 8000, 600), ('figurines', 12000, 900),
             ('cassettes', 3000, 250), ('bd', 4500, 350)) AS base (slug, price_cents, step_cents)
     ON base.slug = c.slug
JOIN app_user AS seller ON seller.email = 'vendeur2@collector.local'
WHERE NOT EXISTS (SELECT 1 FROM article a WHERE a.seller_id = seller.id);

REFRESH MATERIALIZED VIEW category_price_stats;
