# Architecture de Collector.shop (POC)

Ce document décrit l'architecture **réellement codée** : trois services, un bus, un fournisseur d'identité, un
stockage objet, un point d'entrée. Les décisions sont justifiées dans les ADR (`docs/adr/`). Contrats :
[`docs/api/openapi.yaml`](api/openapi.yaml) et [`docs/events.md`](events.md).

Périmètre : trois user stories liées — **US-014** (mise en vente avec contrôle automatisé), **US-033** (revue
des articles par l'administrateur), **US-029** (suivre un article et être prévenu des variations de prix) — plus
US-021 en exemple guidé (ADR 0015).

## 1. Vue d'ensemble

```
 Navigateur (Vue 3, PKCE)                                              PUT photo (URL pré-signée)
    │ HTTPS · JWT RS256 (5 min)                                                   │
    ▼                                                                            ▼
 ┌────────────────────────── Kubernetes (namespace collector) ──────────────────────────────┐
 │ Traefik (TLS cert-manager · HSTS · rate limiting)                                          │
 │  api.collector.local ─► catalogue-service ───JWKS──► Keycloak ◄── auth.collector.local     │
 │                   └───► notification-service ─JWKS─►    ▲                                  │
 │                                │       │ HEAD / pré-signature ──► Stockage objet S3 (Garage)│
 │                   JDBC         │       │                              ▲ s3.collector.local  │
 │                catalogue_app   ▼       │ outbox → relais (confirms)                          │
 │                          ┌──────────────┴───────────┐                                       │
 │                          │       PostgreSQL 16      │◄── JDBC notification_app (tables propres)
 │                          │  (un historique Flyway)  │◄── SELECT controle_app (vue de stats)
 │                          └──────────────▲───────────┘                                       │
 │                                         │ SELECT category_price_stats                       │
 │   catalogue ──article.submitted──► RabbitMQ (quorum, DLX) ──► controle-service              │
 │   catalogue ◄─article.checked───── collector.events ◄─────── (article.checked, fraud.alert)  │
 │   notification ◄─ price.changed · follow.changed · interests.updated · article.reviewed      │
 │                                                                                              │
 │ Prometheus ◄─ /actuator/prometheus (8081) · rabbitmq:15692 · postgres-exporter ─► Grafana    │
 └──────────────────────────────────────────────────────────────────────────────────────────────┘
```

| Brique | Rôle | ADR |
|---|---|---|
| Traefik | TLS, limitation de débit, en-têtes de sécurité ; route `api`, `auth`, `s3` | 0012 |
| Keycloak | Identité, OIDC Authorization Code + PKCE, rôles `acheteur`, `vendeur`, `admin` | 0007 |
| catalogue-service | Articles, photos, prix, revue admin, suivi, centres d'intérêt | 0004 |
| controle-service | Règle des 3 écarts-types, sans API publique | 0004, 0006 |
| notification-service | Abonnements, notifications de variation de prix | 0004, 0015 |
| RabbitMQ | Bus d'événements, files quorum, lettres mortes | 0001 |
| PostgreSQL | Une base, un rôle par service, un historique Flyway | 0010 |
| Garage (S3) | Photos, URL pré-signées | 0008 |
| Prometheus, Grafana | Métriques, SLO, alertes | — |

## 2. Contextes métier et services

| Contexte | Responsabilité | Service | Dans le POC |
|---|---|---|---|
| Catalogue | Articles, catégories, photos, prix | `catalogue-service` | Oui |
| Conformité et fraude | Contrôle automatique, score d'anomalie, alertes | `controle-service` (+ outil externe éventuel) | Oui |
| Notification | Abonnements, notifications de l'espace | `notification-service` | Oui |
| Identité | Comptes, rôles, authentification | Keycloak | Oui |
| Paiement, échanges (chat), recommandation, back-office complet | — | — | Hors POC (V2) |

Principes : communication **asynchrone par événements** entre services, **REST** uniquement du client vers les
services ; chaque service est déployable et scalable indépendamment ; bibliothèque partagée **limitée aux
contrats** (`collector-messaging`), aucun code métier partagé ; un rôle PostgreSQL par service.

## 3. Flux pas à pas

### 3.1 US-014 — mise en ligne avec contrôle automatisé

1. `POST /articles` (vendeur) : crée un **brouillon** (`BROUILLON`) ; le membre est créé au premier passage
   à partir du jeton. Coordonnées refusées (422 `contact_info_forbidden`).
2. `POST /articles/{id}/photos` : réserve un emplacement, renvoie une URL `PUT` **pré-signée** (5 min). Le
   navigateur envoie la photo **directement** au stockage : le binaire ne transite jamais par l'API.
3. `POST /articles/{id}/submission` : `HEAD` de chaque photo (existence, type, taille) ; aucune photo valide =
   422 `photo_required` (CA-6) ; sinon l'article passe `EN_CONTROLE` et `article.submitted` est écrit dans
   l'outbox, **dans la même transaction** (CA-1).
4. Le relais publie (publication confirmée) vers `controle.article-submitted`.
5. **controle-service** lit les statistiques de la catégorie (vue `category_price_stats`), calcule
   `score = |prix − médiane| / écart-type` : `score > 3` donne `EN_REVUE` (CA-3), sinon `PUBLIE` (CA-2) ;
   échantillon inférieur à 30 ou écart-type nul : `PUBLIE` avec `insufficient_sample`.
6. Il publie `fraud.alert` (si anomalie) **puis** `article.checked`.
7. Le catalogue consomme `article.checked` et applique le verdict de façon **idempotente**
   (`UPDATE … WHERE status = 'EN_CONTROLE'`), en moins de 2 s (CA-2). Le délai de contrôle est mesuré
   (`collector_article_check_duration_seconds`).
8. `PATCH /articles/{id}/price` (CA-4) : écrit `price.changed` (avec `aggregate_version`) consommé par
   l'anti-fraude **et** par la notification. 401 sans jeton, 403 sur l'article d'un autre (CA-5).

### 3.2 US-033 — revue d'un article par l'administrateur

1. Un article en anomalie est `EN_REVUE` (étape 7 ci-dessus) ; le motif (`check_reason`) et le score sont
   gardés.
2. `GET /admin/reviews` (rôle `admin`) liste les articles `EN_REVUE`, les plus anciens d'abord, avec score et
   motif (CA-1). Le score n'est jamais exposé hors de cette vue.
3. `POST /admin/reviews/{articleId}` avec `VALIDER` : `PUBLIE` (CA-2) ; avec `REJETER` et un motif : `REJETE`
   (CA-3, le vendeur voit le motif dans `/me/articles`). Un article qui n'est pas `EN_REVUE` : 409 (CA-4).
4. La décision et `article.reviewed` partent dans la même transaction (outbox). Le notification-service vide
   la file ; notifier le vendeur est prévu en V2.

### 3.3 US-029 — suivre un article, être prévenu d'un changement de prix

1. `PUT /articles/{id}/follow` (acheteur, article `PUBLIE` sinon 404) : le catalogue **ne garde aucun état** ;
   il publie `follow.changed` (identité = `sub` Keycloak, CA-1). `DELETE` publie `following: false` (CA-2).
2. Le notification-service tient sa **propre copie** des abonnements : le dernier `changed_at` gagne, quel que
   soit l'ordre d'arrivée.
3. Le vendeur change le prix (US-014 CA-4) : `price.changed` arrive dans `notification.price-changed`.
4. Le service **avance atomiquement** la dernière version vue de l'article
   (`INSERT … ON CONFLICT … WHERE last_version < EXCLUDED.last_version`) ; si l'événement est périmé ou rejoué,
   il est ignoré (CA-4). Sinon il crée une notification par abonné, avec une contrainte d'unicité
   (membre, article, version) comme filet d'idempotence (CA-3).
5. `GET /me/notifications` renvoie les notifications **du jeton seulement** ; `POST …/{id}/read` marque lue
   (404 pour celle d'un autre) (CA-5). 401 sans jeton, 403 sans le rôle `acheteur` (CA-6).

## 4. Architecture interne : hexagonale

Un hexagone **par fonctionnalité**, vérifié par ArchUnit (`ArchitectureTest` dans chaque service) : le domaine
est du Java pur, l'application n'importe aucun framework technique, pas de cycle entre fonctionnalités, les
entités JPA restent dans les adaptateurs (ADR 0003).

```
catalogue-service  (com.collector.catalogue)
├─ article/        US-014, US-033, US-029 (côté catalogue)
│  ├─ domain/      Article (agrégat), ArticleStatus, Photo, PhotoPolicy, ContactInfoPolicy, ReviewDecision,
│  │               événements ArticleSubmitted, PriceChanged, ArticleReviewed, FollowChanged, exceptions métier
│  ├─ application/ CreateDraft, RequestPhotoUpload, SubmitArticle, ChangePrice, ApplyVerdict, GetArticle,
│  │               ListArticles, ListMyArticles, ListPendingReviews, ReviewArticle, FollowArticle, ArticleViews
│  │  └─ port/     ArticleRepository, CategoryCatalog
│  └─ adapter/
│     ├─ in/web/        ArticleController, ReviewController, FollowController + DTO
│     ├─ in/messaging/  ArticleCheckedListener
│     └─ out/persistence/ ArticleEntity, PhotoEntity, ArticlePersistenceAdapter, CategoryCatalogJdbcAdapter
├─ category/       (exemple de référence : liste publique)
├─ interest/       US-021 (exemple guidé)
├─ ping/           démonstration de l'outbox
└─ shared/
   ├─ domain/      DomainEvent, DomainException, Member, NotFoundException, InvalidRequestException
   ├─ application/port/ DomainEventPublisher, PhotoStorage, MemberDirectory
   ├─ adapter/out/outbox/   OutboxEvent, OutboxDomainEventPublisher, OutboxStore, OutboxRelay, OutboxMetrics
   ├─ adapter/out/storage/  S3PhotoStorage, StorageConfig
   ├─ adapter/out/persistence/ MemberJdbcAdapter
   ├─ adapter/in/web/       ApiExceptionHandler, Members (Jwt vers Member)
   └─ config/      SecurityConfig, KeycloakRealmRoleConverter, ClockConfig

controle-service  (com.collector.controle)
└─ pricecheck/
   ├─ domain/      PriceCheckPolicy (règle des 3 σ), PriceCheck, PriceStats, ArticleSubmitted
   ├─ application/ CheckSubmittedArticle ; port/ PriceStatsProvider, VerdictPublisher
   └─ adapter/     in/messaging/ArticleSubmittedListener ; out/persistence/JdbcPriceStatsProvider ;
                   out/messaging/RabbitVerdictPublisher

notification-service  (com.collector.notification)
├─ notification/
│  ├─ domain/      Notification, PriceChange, FollowChange
│  ├─ application/ NotifyPriceChange, RecordFollowChange, RecordInterests, ListNotifications,
│  │               MarkNotificationRead ; port/ FollowRepository, ArticleVersionRepository,
│  │               NotificationRepository, InterestRepository
│  └─ adapter/     in/web/NotificationController ; in/messaging/NotificationListeners, EventDecoder ;
│                  out/persistence/NotificationJdbcAdapter
└─ shared/         domain/DomainException, NotFoundException ; adapter/in/web/ApiExceptionHandler ;
                   config/SecurityConfig, ClockConfig
```

Bibliothèque `libs/collector-messaging` : `EventEnvelope`, `EventTypes`, `Topology`,
`CollectorMessagingAutoConfiguration` (topologie), `ConfirmedPublisher`, `OutgoingMessage`, `EventSchemas` et
les schémas JSON.

## 5. Sécurité

| Sujet | Mesure |
|---|---|
| Authentification | Keycloak, OIDC Authorization Code + PKCE, JWT RS256 de 5 minutes ; `issuer-uri` public, JWKS interne (ADR 0007) |
| Autorisation | Rôles `acheteur`, `vendeur`, `admin` (`@PreAuthorize`), **propriété** vérifiée dans le cas d'usage ; un article non publié d'un autre répond 404 |
| Audiences | `catalogue-api` et `notification-api` : un jeton n'est accepté que par l'API pour laquelle il est émis |
| API | Sans état, CSRF désactivé (aucun cookie), CORS limité au front, CSP `default-src 'none'` sur `/api/**`, `nosniff`, `Referrer-Policy` |
| Erreurs | ProblemDetail avec code stable, aucun détail technique vers le client (ADR 0011) |
| Données | Un rôle PostgreSQL par service, sans DDL ni `DELETE` d'article ; `controle_app` lit une vue et rien d'autre |
| Secrets | Aucun dans le dépôt ni dans une image, aucun secret avec valeur par défaut ; `.env` ignoré par git |
| Photos | Bucket privé, clé dédiée, URL pré-signées courtes, contrôle type et taille côté serveur (ADR 0008) |
| Exécution | Images distroless non-root, système de fichiers en lecture seule, NetworkPolicies, Pod Security, management (8081) non routé |
| Chaîne | gitleaks, CodeQL, Semgrep, SpotBugs/FindSecBugs, Trivy (0 critique), images signées cosign et vérifiées avant déploiement (ADR 0013) |

## 6. Données

- **Une base PostgreSQL 16**, un **seul historique Flyway** appliqué par un **job séparé** avec les
  identifiants du propriétaire ; les services tournent avec Flyway désactivé (ADR 0010).
  - Catalogue : `V1__schema.sql` (comptes, catégories, articles, photos, historique de prix, vue
    `category_price_stats`, achats et notation déjà modélisés mais hors POC, alertes de fraude),
    `V2__outbox.sql`, `V3__ping.sql`, `V4__grants.sql`, `V5__review.sql`.
  - Notification : `V100__notification.sql` (`notification_follow`, `notification_article_version`,
    `notification`, `notification_interest`).
  - Jeu de données de développement : `R__seed_dev.sql`, répétable et idempotent (35 articles publiés par
    catégorie : sneakers, médiane 250 €, écart-type ≈ 48 €, donc 260 € est publié et 1 000 € part en revue).
- **Rôles PostgreSQL**, créés par `infra/postgres/init/00_roles.sh` à partir de l'environnement :

| Rôle | Utilisé par | Droits |
|---|---|---|
| `collector` | job Flyway, rafraîchissement des statistiques, sauvegarde | propriétaire |
| `catalogue_app` | catalogue-service | `SELECT`/`INSERT`/`UPDATE` ciblés, pas de DDL, pas de `DELETE` d'article |
| `controle_app` | controle-service | `SELECT` sur `category_price_stats` seulement |
| `notification_app` | notification-service | ses quatre tables seulement |
| `monitoring` | postgres-exporter | `pg_monitor` |

- **Vue matérialisée** `category_price_stats` rafraîchie par son propriétaire (`stats-refresher` en compose,
  CronJob de 5 minutes en Kubernetes, `CONCURRENTLY`). Compromis assumé du POC : le contrôle la lit
  (ADR 0006) ; en V2 il aura son propre modèle de lecture, par un simple changement d'adaptateur.
- **Outbox** : table `outbox_event` avec bail de réservation ; `collector_outbox_pending` mesure le retard
  (ADR 0005).

## 7. Observabilité et exploitation

- Actuator et Micrometer sur le port de management 8081 (`health`, `info`, `prometheus`), histogrammes HTTP,
  journaux JSON ECS corrélés par identifiant de trace.
- Métriques métier : `collector_articles_submitted_total`, `collector_articles_verdict_total{verdict}`,
  `collector_fraud_alerts_total`, `collector_outbox_pending`, `collector_article_check_duration_seconds`,
  `collector_notifications_created_total`.
- SLO : disponibilité ≥ 99,5 %, p95 < 300 ms, délai de contrôle p95 < 2 s, aucune lettre morte. Alertes dans
  `infra/prometheus/rules.yml`. Détails : `docs/exploitation.md`.

## 8. Déploiement

Docker compose au quotidien, Minikube allégé pour la démo, kind éphémère en CI pour la configuration
complète (ADR 0009) ; Traefik comme point d'entrée (ADR 0012) ; images construites une fois, signées et
promues par digest (ADR 0013).

## 9. Index des décisions

| ADR | Sujet |
|---|---|
| [0001](adr/0001-bus-rabbitmq.md) | Bus RabbitMQ |
| [0002](adr/0002-java-21-spring-boot-4.md) | Java 21 et Spring Boot 4 |
| [0003](adr/0003-architecture-hexagonale.md) | Architecture hexagonale et ArchUnit |
| [0004](adr/0004-trois-services.md) | Trois services |
| [0005](adr/0005-outbox-transactionnelle.md) | Outbox transactionnelle, bail, aucun ordre garanti |
| [0006](adr/0006-lecture-category-price-stats-par-controle.md) | Le contrôle lit la vue de statistiques |
| [0007](adr/0007-keycloak-oidc.md) | Keycloak et OIDC |
| [0008](adr/0008-photos-stockage-objet-garage.md) | Photos, stockage objet, Garage |
| [0009](adr/0009-kubernetes-minikube-kind.md) | Kubernetes : Minikube et kind |
| [0010](adr/0010-migrations-flyway-job-separe.md) | Migrations Flyway par job séparé |
| [0011](adr/0011-erreurs-problemdetail.md) | Erreurs ProblemDetail |
| [0012](adr/0012-traefik-ingress.md) | Traefik |
| [0013](adr/0013-construire-une-fois-promouvoir-le-digest-signe.md) | Construire une fois, promouvoir le digest signé |
| [0014](adr/0014-prestataire-de-paiement.md) | Prestataire de paiement (hors POC) |
| [0015](adr/0015-perimetre-poc-trois-us-et-notification-service.md) | Périmètre réduit à trois US et notification-service |
