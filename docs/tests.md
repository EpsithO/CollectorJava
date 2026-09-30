# Processus de test

Ce document formalise *quoi* est testé, *avec quoi*, *quand* et *par qui* (critère 7 de la grille).
Il distingue ce qui est **mesuré** aujourd'hui de ce qui est **prévu** : rien n'est présenté comme
exécuté s'il ne l'a pas été.

Périmètre : trois user stories liées (US-014 mise en vente, US-033 revue admin, US-029 suivre un
article) et trois services (`catalogue-service`, `controle-service`, `notification-service`), plus la
bibliothèque de contrats `collector-messaging`.

## 1. Types de tests

| Type | Outil | Portée | Quand | Qui écrit / valide |
|---|---|---|---|---|
| Unitaire | JUnit 5, AssertJ, doublures **écrites à la main** pour les ports (Mockito seulement pour les classes techniques) | Domaine et cas d'usage : règles de l'article, politique de coordonnées, règle des 3 σ, propriété, statuts, idempotence, ordre des événements | Chaque commit | Développeur / revue |
| Tranche web | `@WebMvcTest`, `spring-security-test` (`jwt()`) | Contrat HTTP, codes 400/401/403/404/409/422, ProblemDetail, en-têtes de sécurité, rôles | Chaque commit | Développeur |
| Architecture | **ArchUnit** | Règles hexagonales (domaine en Java pur, application sans framework technique, adaptateurs indépendants), absence de cycles, entités JPA cantonnées à la persistance | Chaque commit | Lead Dev |
| Contrat d'événements | `json-schema-validator` (schémas dans `libs/collector-messaging/src/main/resources/schemas/`) | Producteur : chaque événement du domaine, sérialisé comme en production, respecte son schéma ; consommateur : décodage et rejet des messages non conformes | Chaque commit | Producteur et consommateur |
| Intégration | Spring Boot Test + **Testcontainers** (PostgreSQL 16, RabbitMQ 4.1) | Adaptateurs réels (JPA, JDBC, S3 simulé), `ddl-auto: validate`, transactions et outbox, relais, consommateurs, lettres mortes | Chaque commit (CI) | Développeur |
| Acceptation | **Cucumber-JVM** (Gherkin FR) + REST Assured | CA de US-014, US-033, US-029 en boîte noire, sur la recette déployée (kind) | Chaque PR (CI) | Rédigés avec le PO (Collector), exécutés par la CI |
| Sécurité | CodeQL, Semgrep, SpotBugs + FindSecBugs, Trivy (fs et image), gitleaks, OWASP ZAP | Code, dépendances, image, secrets, API en marche | Chaque PR ; ZAP complet la nuit | Lead Dev ; audit externe avant ouverture |
| Performance | JMeter + Prometheus/Grafana | Scénario 70/25/5, paliers 10 → 250 utilisateurs, p95 et taux d'erreur | Nocturne, avant livraison, soutenance | SRE |
| Mutation | PIT (rapport non bloquant) | Qualité des assertions : limite « tests sans assertion » d'un seuil de couverture | Nocturne | Lead Dev |

Règle d'écriture : un test d'intégration n'existe que pour ce qu'un test unitaire ne peut pas prouver
(SQL, mapping JPA, broker). Tout le reste se teste sans Spring, sans base, sans broker.

## 2. Critères de sortie (définition de « terminé »)

Tout vert · couverture de branches ≥ 70 % global et ≥ 80 % sur le code nouveau (Sonar) · 0 vulnérabilité
critique, 0 haute non justifiée · quality gate SonarCloud vert · ArchUnit vert · scénarios Gherkin verts ·
contrats (`openapi.yaml`, `events.md`, schémas JSON) à jour.

## 3. Lancer les tests en local

```bash
./mvnw verify                       # tout : unitaires, ArchUnit, intégration (Docker requis), JaCoCo
./mvnw -pl services/catalogue-service test                       # un module, sans les *IT
./mvnw -pl services/catalogue-service -am verify -Djacoco.skip=true   # sans le seuil de couverture
./mvnw -pl acceptance-tests -Pacceptance verify -Dcollector.env=local # acceptation, pile compose démarrée
```

**Sans Docker**, les classes `*IT` sont **ignorées** (et non en échec) : `@Testcontainers(disabledWithoutDocker = true)`.
Conséquence à connaître : les branches que seuls les tests d'intégration couvrent (adaptateurs JDBC/JPA,
listeners, relais de l'outbox) ne comptent pas, et le seuil JaCoCo de 70 % peut échouer en local. La CI, qui
dispose de Docker, exécute les deux familles et fusionne la couverture. En local sans Docker, utiliser
`-Djacoco.skip=true` pour ne vérifier que le comportement.

## 4. État réel des tests

Relevé par Maven sur ce poste (JDK 21, **sans Docker**) ; les totaux « exécutés » sont ceux de
`mvn test` (paramétrés et règles ArchUnit compris).

| Module | Tests exécutés (hors IT) | Tests d'intégration écrits (`*IT`) | Règles ArchUnit | Remarques |
|---|---|---|---|---|
| `libs/collector-messaging` | 10 | 0 | n/a | Schémas, validation, publication confirmée (mock du broker) |
| `services/catalogue-service` | 134 | 13 (ignorés ici) | 6 | Domaine, cas d'usage US-014/033/029/021, web, contrat producteur |
| `services/controle-service` | 28 | 6 (ignorés ici) | 4 | Règle des 3 σ avec bornes exactes, contrat `article.checked` / `fraud.alert` |
| `services/notification-service` | 26 | 3 (ignorés ici) | 4 | Ordre des événements, doublons, propriété des notifications |

Les tests d'intégration (22 écrits au total) **n'ont jamais été exécutés** : Docker était absent sur le
poste de développement. Ils le seront par la CI ; d'ici là, la validation du SQL (contraintes, `ON CONFLICT`,
jointure de `applyVerdict`) et du mapping JPA reste à prouver. De même, `infra/storage/init.sh` (API
d'administration de Garage) n'a pas été exécuté.

Mesures de couverture de branches (locales, **hors tests d'intégration**, donc sous-estimées) : voir
[`rapport-qualite.md`](rapport-qualite.md). Aucune mesure CI n'existe encore.

## 5. Ce que chaque famille prouve sur les CA

| CA | Preuve sans Docker | Preuve avec Docker / recette |
|---|---|---|
| US-014 CA-1, CA-6 | `SubmitArticleTest`, `ArticleTest`, `ArticleControllerTest` | `SubmissionFlowIT`, scénarios Gherkin |
| US-014 CA-2, CA-3 | `PriceCheckPolicyTest`, `CheckSubmittedArticleTest`, `ApplyVerdictTest` | `ArticleSubmittedListenerIT`, `SubmissionFlowIT` |
| US-014 CA-4 | `ChangePriceTest`, `OutboxDomainEventPublisherTest` (schéma de `price.changed`) | `NotificationFlowIT`, acceptation |
| US-014 CA-5 | `ArticleControllerTest` (401/403) | Acceptation |
| US-033 | `ReviewAndFollowUseCasesTest`, `ReviewAndFollowControllerTest` | Acceptation |
| US-029 | `NotificationUseCasesTest`, `EventDecoderTest`, `NotificationControllerTest`, `ReviewAndFollowUseCasesTest` | `NotificationFlowIT`, acceptation |

## 6. Limites assumées

- Un seuil de couverture se contourne (tests sans assertion) : PIT mesure le score de mutation, à titre informatif.
- Les tests de charge mesurent la capacité d'un cluster de recette, pas de la production.
- Pas de test de bascule du broker en CI : il est couvert par l'expérimentation
  ([`experimentation/rabbitmq-kubernetes.md`](experimentation/rabbitmq-kubernetes.md)), à exécuter.
