-- US-033 : revue des articles par l'administrateur. Migration additive (expand) : la version
-- précédente de l'application continue de fonctionner sur ce schéma.
ALTER TABLE article
    ADD COLUMN check_reason  text,                                     -- motif du contrôle (price_outlier)
    ADD COLUMN review_reason text,                                     -- motif de rejet saisi par l'admin
    ADD COLUMN reviewed_by   uuid REFERENCES app_user (id) ON DELETE SET NULL;

-- La revue « plus anciens d'abord » parcourt les articles EN_REVUE : index partiel, petit.
CREATE INDEX idx_article_in_review ON article (created_at) WHERE status = 'EN_REVUE';
