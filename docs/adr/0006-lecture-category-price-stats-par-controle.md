# ADR 0006 — Le contrôle lit la vue `category_price_stats` du catalogue (compromis)

- Statut : accepté pour le POC (30/09/2026), à remplacer en V2

## Contexte

La règle des 3 écarts-types (CA-2, CA-3) a besoin de la médiane et de l'écart-type des prix publiés de la
catégorie. Le contrôle doit répondre en moins de 2 secondes. Le principe cible est « une base par service »,
mais le catalogue est le propriétaire de ces données.

## Décision

`controle-service` **lit** la vue matérialisée `category_price_stats` (`sample_size`, `median_cents`,
`stddev_cents`, NULL sous 30 articles) de la base du catalogue, avec un rôle PostgreSQL `controle_app` qui n'a
**que** `SELECT` sur cette vue. La vue est rafraîchie par son propriétaire (`stats-refresher` en compose,
CronJob de 5 minutes en Kubernetes, `REFRESH … CONCURRENTLY`). Grâce à l'hexagonale, l'accès passe par le port
`PriceStatsProvider` (adaptateur `JdbcPriceStatsProvider`).

## Alternatives écartées

| Option | Raison |
|---|---|
| Appel REST synchrone au catalogue | Couplage temporel : le contrôle tomberait avec le catalogue ; latence ajoutée |
| Modèle de lecture propre au contrôle, alimenté par les événements | C'est la **cible** (V2) mais demande de publier les changements de prix et de statut, de construire et de rattraper l'historique ; disproportionné pour le POC |
| Calcul des statistiques à chaque soumission | Coût SQL sur la table d'articles ; contraire à la contrainte des 2 secondes |
| Événement `article.submitted` portant les statistiques | Le catalogue deviendrait responsable de la règle de contrôle |

## Conséquences

- Le contrôle dépend du schéma de la vue : un changement de la vue est un changement de contrat.
- Les statistiques ont jusqu'à 5 minutes de retard : acceptable pour une détection d'anomalie.
- Moindre privilège respecté : aucun droit d'écriture, aucun accès aux autres tables.
- Passer au modèle de lecture propre = **un adaptateur à remplacer**, sans toucher au domaine ni au cas d'usage.
