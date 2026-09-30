# ADR 0004 — Trois services : catalogue, contrôle, notification

- Statut : accepté (30/09/2026). Remplace la version « deux services » ; voir ADR 0015.

## Contexte

Les contextes métier (DDD) du sujet sont le catalogue, la conformité et la fraude, l'identité, la
notification, le paiement, les échanges (chat), la recommandation et le back-office. Avec trois à cinq
développeurs, on découpe quand un contexte a une raison de vivre seul, pas par principe. Le périmètre du POC
est réduit à trois user stories liées (ADR 0015) : le paiement et le chat n'y figurent pas.

## Décision

Trois services déployables, plus Keycloak :

| Service | Contexte | Raison d'être |
|---|---|---|
| `catalogue-service` | Catalogue | Articles, photos, prix, revue admin, centres d'intérêt ; seul point d'entrée HTTP des vendeurs et de l'admin |
| `controle-service` | Conformité et fraude | Cycle de vie propre (peut être remplacé par un outil acheté), charge différente (pics de CPU aux mises en ligne), doit pouvoir tomber sans empêcher de soumettre (les articles attendent `EN_CONTROLE`). Sans API publique |
| `notification-service` | Notification | Démontre qu'on ajoute un consommateur sans toucher au producteur ; tient sa **propre copie** des abonnements, des versions d'article vues et des centres d'intérêt ; API de lecture des notifications |

Principes : communication asynchrone par événements entre services ; REST uniquement du client vers les
services ; bibliothèque partagée **limitée aux contrats** (`collector-messaging`), aucun code métier partagé
(les petites classes communes, comme `DomainException`, sont dupliquées volontairement) ; chaque service est
déployable, versionné et scalable indépendamment ; un rôle PostgreSQL dédié par service.

## Alternatives écartées

| Option | Raison |
|---|---|
| **Monolithe modulaire** | Plus simple, mais n'offre ni déploiement ni mise à l'échelle indépendants, et ne permet pas d'intégrer par contrat un outil anti-fraude externe |
| Un seul service avec événements internes | Aucun découplage au déploiement (mêmes raisons) |
| Plus de services (paiement, chat) | Hors périmètre du POC (ADR 0015) ; chaque service coûte en exploitation ; conception conservée en V2 (ADR 0014) |
| Passerelle API (Spring Cloud Gateway), Eureka, Spring Cloud Config | Redondants avec Traefik, le DNS Kubernetes et les ConfigMaps ; à reconsidérer pour un BFF |

## Conséquences

- Trois images, trois Deployments, trois jeux de NetworkPolicies, trois scrapes Prometheus.
- Cohérence à terme entre services (`EN_CONTROLE` puis `PUBLIE`), consommateurs idempotents (ADR 0005).
- Un service coûte de la RAM : profil allégé pour Minikube, configuration complète prouvée en CI (kind).
- Le notification-service authentifie ses propres appels (audience `notification-api`, ADR 0007).
