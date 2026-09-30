# notification-service

Abonnements aux articles et notifications de variation de prix (**US-029**). Troisième service du
POC : il démontre qu'*ajouter un consommateur = ajouter une file*, sans toucher au producteur.

## Ce qu'il fait

```
catalogue ──outbox──► follow.changed  ─► notification.follow-changed  ─► copie des abonnements
catalogue ──outbox──► price.changed   ─► notification.price-changed   ─► une notification par abonné
catalogue ──outbox──► interests.updated ► notification.interests-updated ► copie des centres d'intérêt
catalogue ──outbox──► article.reviewed ─► notification.article-reviewed ─► (V2 : prévenir le vendeur)
                                                                      │
acheteur ── GET /api/v1/me/notifications ◄── PostgreSQL (tables du service) ◄┘
         ── POST /api/v1/me/notifications/{id}/read
```

Le catalogue ne garde **aucun état de suivi** : il vérifie que l'article est publié et transmet
`follow.changed`. Ce service tient **sa propre copie** (base par service : seul le schéma est partagé
dans le POC, pas les tables).

## Événements reçus dans le désordre, ou en double

La livraison est « au moins une fois » et **sans ordre garanti** (plusieurs réplicas, reprise après
panne : ADR 0005). Deux règles, appliquées en SQL par des instructions atomiques, donc sûres même avec
plusieurs réplicas :

| Règle | Mécanisme | Test |
|---|---|---|
| Le **dernier** changement d'abonnement gagne, quel que soit l'ordre d'arrivée | `INSERT … ON CONFLICT DO UPDATE … WHERE changed_at < EXCLUDED.changed_at` | `theMostRecentFollowChangeWinsWhateverTheArrivalOrder` |
| Un `price.changed` de version inférieure ou égale à la dernière vue est ignoré | table `notification_article_version`, même schéma atomique sur `aggregate_version` | `invertedEventsNotifyOnlyTheLatestPrice` |
| Une redélivrance ne crée jamais une 2ᵉ notification | `UNIQUE (member_id, article_id, aggregate_version)` + `ON CONFLICT DO NOTHING` | `theSameEventDeliveredTwiceCreatesASingleNotification` |

## Sécurité

- Jeton Keycloak, audience `notification-api`, rôle `acheteur` exigé (401 sans jeton, 403 sans le rôle).
- Le membre est **toujours celui du jeton** (le `sub`), jamais un paramètre : un acheteur ne voit pas les
  notifications d'un autre ; marquer lue la notification d'un autre répond 404 (on ne révèle pas son existence).
- Rôle PostgreSQL `notification_app`, limité aux tables de ce service.
- Message illisible ou non conforme au schéma : lettres mortes directes, sans rejeu.

## Tests

`NotificationUseCasesTest` (règles, sans Spring), `EventDecoderTest`, `NotificationControllerTest`
(contrat HTTP et sécurité), `ArchitectureTest`, `NotificationFlowIT` (PostgreSQL et RabbitMQ réels,
Testcontainers : ignoré sans Docker, exécuté en CI).
