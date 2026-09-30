# controle-service

Contrôle automatique des articles soumis (US-014, CA-2 et CA-3). Service **sans API publique** :
il consomme `article.submitted`, décide, puis publie `article.checked` (et `fraud.alert` si le
prix est anormal). Le catalogue applique le verdict ; ce service ne modifie jamais un article.

## La règle

```
score = |prix − médiane de la catégorie| / écart-type de la catégorie
score > 3  →  EN_REVUE  (+ alerte anti-fraude)
sinon      →  PUBLIE
```

Code : `pricecheck/domain/PriceCheckPolicy.java` (Java pur, une seule méthode, aucune dépendance).

| Choix | Pourquoi |
|---|---|
| **Médiane**, pas moyenne | Quelques pièces de collection exceptionnelles (un poster à 5 000 €) ne déplacent pas la référence de la catégorie |
| **Valeur absolue** | Un prix très bas est aussi suspect qu'un prix très haut (appât, contrefaçon) |
| **Strictement supérieur à 3** | Exactement 3 écarts-types est publié ; 3 écarts-types + 1 centime part en revue. Les deux bornes sont testées |
| **Échantillon < 30 → on ne conclut pas** | Avec 5 articles, l'écart-type ne veut rien dire : `PUBLIE` avec le motif `insufficient_sample`. Sinon, une catégorie naissante bloquerait tous ses vendeurs |
| **Écart-type nul ou NULL → on ne conclut pas** | Évite la division par zéro ; la vue SQL renvoie NULL sous 30 articles |
| **Catégorie sans article publié → on ne conclut pas** | Même raison : pas de référence |

Exemple du jeu de données (sneakers, 35 articles) : médiane 250 €, écart-type ≈ 48 €, donc 260 €
est publié, 1 000 € part en revue (score > 15).

**Limites assumées** (à citer plutôt qu'à cacher) : la règle suppose une distribution à peu près
symétrique ; une catégorie à deux populations (éditions courantes et éditions rares) produira des
faux positifs, traités par la revue humaine (US-033). Un vendeur qui publie *progressivement* des
prix de plus en plus hauts déplace la médiane : la détection de vendeur suspect (US-034) est hors POC.

## Flux

```
catalogue ──outbox──► article.submitted ─► file controle.article-submitted ─► ArticleSubmittedListener
                                                                                     │ décode + valide le schéma
                                                                                     ▼
   category_price_stats (lecture seule)  ◄── JdbcPriceStatsProvider ◄── CheckSubmittedArticle
                                                                                     │ PriceCheckPolicy
                                                                                     ▼
   fraude.alerts ◄── fraud.alert ◄──┐                                    RabbitVerdictPublisher
   catalogue.article-checked ◄── article.checked ◄──────────────────────┘ (publication confirmée)
```

| Couche | Classes | Rôle |
|---|---|---|
| `domain` | `PriceCheckPolicy`, `PriceCheck`, `PriceStats`, `ArticleSubmitted` | La règle, sans framework |
| `application` | `CheckSubmittedArticle` + ports `PriceStatsProvider`, `VerdictPublisher` | Orchestration |
| `adapter/in/messaging` | `ArticleSubmittedListener` | Décode, valide contre le schéma JSON, délègue |
| `adapter/out/persistence` | `JdbcPriceStatsProvider` | `SELECT` sur la vue du catalogue |
| `adapter/out/messaging` | `RabbitVerdictPublisher` | Enveloppe, validation du schéma, publication **confirmée** |

ArchUnit (`ArchitectureTest`) fait échouer le build si le domaine ou l'application importent Spring,
JDBC, AMQP ou la bibliothèque de messagerie.

## Garanties et cas d'erreur

| Situation | Comportement |
|---|---|
| Message illisible ou non conforme au schéma | `AmqpRejectAndDontRequeueException` : lettres mortes directes, inutile de le rejouer |
| Base indisponible | Exception simple : le message est remis en file (5 tentatives, puis lettres mortes) |
| Broker ne confirme pas la publication | `IllegalStateException` : même remise en file, le verdict n'est jamais perdu en silence |
| Pod tué entre la publication et l'acquittement | Le message entrant est redélivré, le verdict republié ; le catalogue l'ignore (`UPDATE … WHERE status = 'EN_CONTROLE'`). Livraison « au moins une fois », consommateur idempotent |
| Alerte et verdict | L'alerte part **d'abord** : un verdict sans alerte laisserait passer une fraude sans trace ; l'inverse se voit (article bloqué `EN_CONTROLE`) et se rejoue |

Pas d'outbox ici : le service n'écrit rien en base, donc rien à rendre atomique. La garantie vient
de l'ordre « publier (confirmé) puis acquitter ».

## Sécurité

- Rôle PostgreSQL `controle_app` : `SELECT` sur `category_price_stats` **uniquement** (ADR 0006).
- Aucun Service Kubernetes n'expose le port 8080 ; seul le management (8081) sert aux sondes et à
  Prometheus, et il n'est pas routé par Traefik.
- Image distroless non-root, système de fichiers en lecture seule.

## Tests

| Test | Ce qu'il prouve |
|---|---|
| `PriceCheckPolicyTest` | Prix cohérent, anomalie haute et basse, **bornes exactes** à 3 σ, échantillon de 29 et de 30, écart-type nul / NULL / négatif |
| `CheckSubmittedArticleTest` | Alerte avant verdict, pas d'alerte pour un prix cohérent, rien n'est publié si la lecture échoue, rejeu déterministe |
| `ArticleSubmittedListenerTest` | Décodage, rejet sans remise en file des messages illisibles ou non conformes |
| `RabbitVerdictPublisherTest` | Les messages produits respectent les schémas `article.checked` et `fraud.alert` ; non confirmé = exception |
| `ArchitectureTest` | Règles hexagonales |
| `JdbcPriceStatsProviderIT` (Testcontainers) | Lecture de la vraie vue avec le jeu de données ; la règle décide comme documenté |
| `ArticleSubmittedListenerIT` (Testcontainers) | Bout en bout avec RabbitMQ : verdict conforme au schéma, alerte, lettres mortes |

Les tests d'intégration sont ignorés (et non en échec) sans Docker ; la CI les exécute.

## Mesures

- `collector_fraud_alerts_total` : alertes publiées.
- Le délai de contrôle bout en bout (`collector_article_check_duration_seconds`, SLO < 2 s) est mesuré
  côté catalogue, qui connaît la date de soumission.
