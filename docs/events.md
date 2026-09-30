# Contrat des événements

Tous les événements passent par l'échange `collector.events` (type `topic`), avec le **type d'événement
comme clé de routage**. La topologie (échanges, files, liaisons, lettres mortes) est déclarée **uniquement**
par la bibliothèque [`libs/collector-messaging`](../libs/collector-messaging)
(`CollectorMessagingAutoConfiguration`, constantes dans `Topology` et `EventTypes`) : deux déclarations d'une
même file avec des arguments différents sont refusées par RabbitMQ (erreur 406).

Un JSON Schema existe par événement et par version :
`libs/collector-messaging/src/main/resources/schemas/<type>.v<version>.json`.
Producteurs et consommateurs valident leurs messages contre ce schéma (`EventSchemas`).

## Vue d'ensemble

```
                              collector.events (topic)
  catalogue ──(outbox)──►  ├─ article.submitted ─► controle.article-submitted      ─► controle-service
                           ├─ price.changed ─────► fraude.price-changed            ─► anti-fraude (externe, hors POC)
                           │                    └► notification.price-changed      ─► notification-service
                           ├─ article.reviewed ──► notification.article-reviewed    ─► notification-service
                           ├─ follow.changed ────► notification.follow-changed      ─► notification-service
                           ├─ interests.updated ─► notification.interests-updated   ─► notification-service
                           └─ ping.created ──────► catalogue.ping                   (démonstration, lue par les tests)

  controle ───────────────►  ├─ article.checked ──► catalogue.article-checked       ─► catalogue-service
   (publication confirmée)   └─ fraud.alert ──────► fraude.alerts                   ─► anti-fraude (externe, hors POC)

  message refusé 5 fois ou illisible ─► collector.dlx ─► collector.dead-letter
```

## Enveloppe commune

Chaque message est un JSON (champs en `snake_case`, horodatage en UTC) :

```json
{
  "event_id": "8f0c7a1e-0000-4000-8000-000000000001",
  "type": "article.submitted",
  "version": 1,
  "occurred_at": "2026-09-30T10:12:00.123Z",
  "data": { }
}
```

| Champ | Rôle |
|---|---|
| `event_id` | UUID unique : clé d'idempotence et identifiant de message AMQP |
| `type` | Identique à la clé de routage |
| `version` | Version du schéma de `data` (1 aujourd'hui) |
| `occurred_at` | Instant de production |
| `data` | Contenu propre au type (`additionalProperties: false` : un champ inconnu est refusé) |

## Identités

- `member_id` (dans `follow.changed`, `interests.updated`) est le **`sub` Keycloak** de l'utilisateur :
  c'est l'identité commune à tous les services, celle que lit le notification-service dans le jeton.
- `reviewed_by` (dans `article.reviewed`) est aussi le `sub` Keycloak de l'administrateur.
- `seller_id`, `category_id`, `article_id` sont des identifiants du catalogue (`app_user.id`, `category.id`,
  `article.id`). **Attention** : `seller_id` est l'identifiant *interne* du vendeur, pas son `sub`. Les
  consommateurs du POC n'en ont pas besoin ; pour notifier un vendeur (V2), il faudra ajouter son `sub`
  à `article.reviewed` (nouvelle version du schéma).

## Les événements

| Événement | Producteur | `data` | Files (consommateur) |
|---|---|---|---|
| `article.submitted` | catalogue (outbox) | `article_id, seller_id, category_id, price_cents, currency, photo_count` | `controle.article-submitted` (controle) |
| `article.checked` | controle | `article_id, verdict (PUBLIE\|EN_REVUE), anomaly_score, reason` | `catalogue.article-checked` (catalogue) |
| `fraud.alert` | controle | `article_id, seller_id, kind (PRIX_ANORMAL), score, details{price_cents, median_cents, stddev_cents}` | `fraude.alerts` (anti-fraude externe) |
| `price.changed` | catalogue (outbox) | `article_id, aggregate_version, seller_id, category_id, title, old_price_cents, new_price_cents, currency` | `fraude.price-changed` (anti-fraude externe), `notification.price-changed` (notification) |
| `article.reviewed` | catalogue (outbox) | `article_id, seller_id, decision (PUBLIE\|REJETE), reason, reviewed_by` | `notification.article-reviewed` (notification) |
| `follow.changed` | catalogue (outbox) | `member_id, article_id, following, changed_at` | `notification.follow-changed` (notification) |
| `interests.updated` | catalogue (outbox) | `member_id, category_ids` (10 au plus, sans doublon) | `notification.interests-updated` (notification) |
| `ping.created` | catalogue (outbox) | `id, payload, created_at` | `catalogue.ping` (démonstration) |

### Exemples

`article.submitted` — US-014, CA-1 (publié à la soumission, dans la même transaction que le passage à `EN_CONTROLE`) :

```json
{
  "event_id": "5b1e3c0a-4f0d-4f43-9a52-6d2c3c0f1a01", "type": "article.submitted", "version": 1,
  "occurred_at": "2026-09-30T10:12:00.123Z",
  "data": {
    "article_id": "6f9c0f0e-1b7a-4c53-9c59-1f2a0b4f7e11", "seller_id": "a3a4f7c2-0d6b-4d67-8d89-2b5f1c3e9a10",
    "category_id": "c1d2e3f4-0a1b-4c2d-8e3f-4a5b6c7d8e9f", "price_cents": 26000, "currency": "EUR", "photo_count": 1
  }
}
```

`article.checked` — verdict du contrôle (CA-2 : `PUBLIE`, CA-3 : `EN_REVUE`). `reason` vaut `price_outlier`,
`insufficient_sample` ou `null` :

```json
{
  "event_id": "0a9b8c7d-6e5f-4a3b-8c2d-1e0f9a8b7c6d", "type": "article.checked", "version": 1,
  "occurred_at": "2026-09-30T10:12:00.480Z",
  "data": { "article_id": "6f9c0f0e-1b7a-4c53-9c59-1f2a0b4f7e11", "verdict": "EN_REVUE", "anomaly_score": 15.6, "reason": "price_outlier" }
}
```

`fraud.alert` — publié **avant** le verdict pour un prix anormal :

```json
{
  "event_id": "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f", "type": "fraud.alert", "version": 1,
  "occurred_at": "2026-09-30T10:12:00.470Z",
  "data": {
    "article_id": "6f9c0f0e-1b7a-4c53-9c59-1f2a0b4f7e11", "seller_id": "a3a4f7c2-0d6b-4d67-8d89-2b5f1c3e9a10",
    "kind": "PRIX_ANORMAL", "score": 15.6,
    "details": { "price_cents": 100000, "median_cents": 25000.0, "stddev_cents": 4800.0 }
  }
}
```

`price.changed` — CA-4 ; `aggregate_version` est la version de l'article après écriture :

```json
{
  "event_id": "2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f60", "type": "price.changed", "version": 1,
  "occurred_at": "2026-09-30T11:00:00.000Z",
  "data": {
    "article_id": "6f9c0f0e-1b7a-4c53-9c59-1f2a0b4f7e11", "aggregate_version": 3,
    "seller_id": "a3a4f7c2-0d6b-4d67-8d89-2b5f1c3e9a10", "category_id": "c1d2e3f4-0a1b-4c2d-8e3f-4a5b6c7d8e9f",
    "title": "Air Jordan 1 Retro High OG", "old_price_cents": 25000, "new_price_cents": 24000, "currency": "EUR"
  }
}
```

`article.reviewed` — US-033 ; `decision` vaut `PUBLIE` ou `REJETE`, `reason` est `null` pour une validation :

```json
{
  "event_id": "3e4f5a6b-7c8d-4e9f-8a0b-2c3d4e5f6071", "type": "article.reviewed", "version": 1,
  "occurred_at": "2026-09-30T12:00:00.000Z",
  "data": {
    "article_id": "6f9c0f0e-1b7a-4c53-9c59-1f2a0b4f7e11", "seller_id": "a3a4f7c2-0d6b-4d67-8d89-2b5f1c3e9a10",
    "decision": "REJETE", "reason": "Photos floues", "reviewed_by": "44444444-4444-4444-8444-444444444444"
  }
}
```

`follow.changed` — US-029 ; le catalogue ne garde aucun état de suivi, il le transmet :

```json
{
  "event_id": "4f5a6b7c-8d9e-4f0a-9b1c-3d4e5f607182", "type": "follow.changed", "version": 1,
  "occurred_at": "2026-09-30T10:30:00.000Z",
  "data": {
    "member_id": "33333333-3333-4333-8333-333333333333", "article_id": "6f9c0f0e-1b7a-4c53-9c59-1f2a0b4f7e11",
    "following": true, "changed_at": "2026-09-30T10:30:00.000Z"
  }
}
```

`interests.updated` — US-021 :

```json
{
  "event_id": "5a6b7c8d-9e0f-4a1b-8c2d-4e5f60718293", "type": "interests.updated", "version": 1,
  "occurred_at": "2026-09-30T10:40:00.000Z",
  "data": { "member_id": "33333333-3333-4333-8333-333333333333", "category_ids": ["c1d2e3f4-0a1b-4c2d-8e3f-4a5b6c7d8e9f"] }
}
```

`ping.created` — démonstration de la chaîne outbox vers RabbitMQ :

```json
{
  "event_id": "6b7c8d9e-0f1a-4b2c-9d3e-5f60718293a4", "type": "ping.created", "version": 1,
  "occurred_at": "2026-09-30T10:00:00.000Z",
  "data": { "id": 1, "payload": "bonjour", "created_at": "2026-09-30T10:00:00Z" }
}
```

## Règles

| Règle | Détail |
|---|---|
| **Au moins une fois** | Un message peut être livré plusieurs fois (pod tué entre l'envoi et l'acquittement, lot non confirmé repris). Tous les consommateurs sont **idempotents** |
| **Aucun ordre garanti** | Plusieurs réplicas relaient en parallèle, un lot non confirmé est repris plus tard, un message peut être redélivré. Un événement qui décrit l'état d'un agrégat porte `aggregate_version` (colonne `@Version` de l'article) ; un consommateur qui a besoin du dernier état **ignore toute version inférieure ou égale** à la dernière vue (ADR 0005). Exemple : deux `price.changed` reçus inversés, le notificateur n'annonce pas l'ancien prix comme le nouveau. Pour `follow.changed`, le dernier `changed_at` gagne |
| **Idempotence concrète** | catalogue : `UPDATE article SET status = … WHERE id = :id AND status = 'EN_CONTROLE'` (0 ligne = doublon). notification : `INSERT … ON CONFLICT (member_id, article_id, aggregate_version) DO NOTHING`, et avance de version atomique par `INSERT … ON CONFLICT … WHERE last_version < EXCLUDED.last_version` |
| **Message illisible ou non conforme** | `AmqpRejectAndDontRequeueException` : lettres mortes directes, inutile de le rejouer |
| **Erreur transitoire** | Exception simple : remise en file, 5 tentatives (`x-delivery-limit: 5`, explicite car RabbitMQ 4 met 20 par défaut), puis lettres mortes |
| **Lettres mortes** | Toutes les files portent `x-dead-letter-exchange: collector.dlx` ; `collector.dlx` est lié à la file `collector.dead-letter` par `#`. Une file `collector.dead-letter` non vide déclenche une alerte |
| **Prefetch et acquittement** | `prefetch` 10 ; acquittement automatique au retour de la méthode, donc après l'effet (écriture en base, publications confirmées) |
| **Files** | Toutes en **quorum**, durables, répliquées (trois nœuds en recette) |
| **Publication** | catalogue : outbox transactionnelle (l'événement est écrit avec la donnée), relais par bail, publication **confirmée** et `mandatory`. controle : publication confirmée puis acquittement du message entrant |
| **Contrat avant code** | Changement incompatible = nouvelle `version` ; l'ancienne reste consommée pendant la transition |

## Pourquoi chaque événement publié doit avoir une file liée

La publication est `mandatory` : un message qui n'atteint **aucune** file est renvoyé par le broker
(`basic.return`) et le relais ne le considère pas comme confirmé. L'événement reste alors dans l'outbox,
est repris à l'expiration du bail et renvoyé en boucle sans jamais être marqué publié : il occupe une place
du lot à chaque reprise et `collector_outbox_pending` ne redescend pas (alerte à plus de 100 pendant
5 minutes). Rien n'est perdu, mais rien n'avance pour cet événement. Conséquence : **ajouter un type d'événement = ajouter au moins une file liée** dans
`CollectorMessagingAutoConfiguration`, et un consommateur pour la vider. C'est aussi pourquoi le
notification-service consomme `article.reviewed` (il ne fait que compter ces événements en V1 : notifier
le vendeur demande son `sub` dans l'événement) et `interests.updated` (il garde une copie des centres
d'intérêt, base de la future notification d'un nouvel article, US-026).

## Files sans consommateur dans le POC

- **`fraude.alerts` et `fraude.price-changed`** : destinées à l'outil anti-fraude, développé en interne **ou
  acheté** (non tranché par le sujet). Il n'existe pas dans le POC ; l'intégration se fait par ce contrat
  d'événements, sans changer le catalogue. Ces files **s'accumulent** tant qu'aucun consommateur n'est branché :
  l'exploitation surveille leur profondeur (runbook, `docs/exploitation.md`). Leur liaison garantit que
  l'outbox n'est pas bloquée.
- **`catalogue.ping`** : démonstration ; les tests d'intégration la lisent (`rabbit.receive`).

## Tests de contrat

- Chaque producteur valide ses messages : `OutboxDomainEventPublisher` (catalogue) refuse d'écrire un
  événement non conforme (l'exception annule aussi la donnée métier) ; `RabbitVerdictPublisher` (controle)
  valide avant de publier. Un test sérialise chaque événement du domaine et le valide contre son schéma.
- Chaque consommateur valide à la réception (`EventSchemas.validate`) et rejette sans remise en file un
  message non conforme.
- Les scénarios Gherkin observent les événements par une file d'audit `tests.audit` liée à `#`, sans voler
  les messages des consommateurs.
