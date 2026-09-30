# ADR 0010 — Migrations Flyway par un job séparé, un seul historique

- Statut : accepté (30/09/2026)

## Contexte

Les services se connectent avec des rôles à moindre privilège : pas de DDL, pas de `DELETE` sur les articles.
Si l'application exécutait ses propres migrations, elle devrait connaître le mot de passe du propriétaire du
schéma, et plusieurs réplicas se disputeraient l'exécution. Les migrations doivent aussi permettre un retour
arrière de l'application.

## Décision

- **Job Flyway séparé** (compose : service `flyway` ; Kubernetes : Job), avec les identifiants du propriétaire
  (`collector`). Les services tournent avec `spring.flyway.enabled=false` et ne connaissent jamais ce mot de
  passe. En test (Testcontainers), Flyway tourne dans l'application.
- **Un seul historique** pour toute la base, alimenté par plusieurs dossiers :
  - `catalogue-service/src/main/resources/db/migration` : **V1 à V5** (schéma, outbox, ping, droits, revue) ;
  - `notification-service/src/main/resources/db/migration` : **V100** et suivantes (tables propres au service) ;
  - `catalogue-service/src/main/resources/db/seed` : `R__seed_dev.sql`, migration **répétable** et idempotente
    (dev et recette uniquement).
  La numérotation du notification-service commence à 100 pour éviter toute collision de version.
- **On ne modifie jamais une migration déjà appliquée** (Flyway refuserait de démarrer : somme de contrôle
  différente) : on en ajoute une.
- **Migrations rétrocompatibles** (*expand / contract*) : la version N-1 de l'application doit fonctionner sur
  le schéma N, sinon pas de retour arrière possible.
- Les `GRANT` aux rôles applicatifs sont dans des blocs `DO $$ … IF EXISTS (SELECT FROM pg_roles WHERE rolname = '…')`
  : la migration passe aussi sur une base de test sans ces rôles.
- Statuts en `text` + `CHECK` (et non en ENUM PostgreSQL) : `@Enumerated(STRING)` sans friction et
  `ddl-auto: validate` passe.
- Vue matérialisée `category_price_stats` rafraîchie par son propriétaire (ADR 0006).

## Alternatives écartées

| Option | Raison |
|---|---|
| Flyway dans chaque service au démarrage | Mot de passe du propriétaire dans les services, concurrence entre réplicas, DDL exposé en cas de compromission |
| Un historique par service (base ou schéma par service) | Cible à terme, disproportionnée pour le POC : une base PostgreSQL partagée avec rôles dédiés suffit |
| Liquibase | Pas de gain ; Flyway, en SQL pur, se relit facilement |
| `ddl-auto: update` | Non reproductible, pas de retour arrière |

## Conséquences

- Le job s'exécute avant les déploiements ; ordre garanti par `service_completed_successfully` en compose et par
  l'attente du Job en Kubernetes.
- Un dossier de migrations par service, montés ensemble dans le job : ajouter un service = ajouter un dossier.
- Test de restauration documenté (sauvegarde `pg_dump` quotidienne, RPO 24 h, RTO 1 h).
