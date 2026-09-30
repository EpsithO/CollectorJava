# ADR 0001 — Bus d'événements : RabbitMQ

- Statut : accepté (30/09/2026)

## Contexte

Le sujet impose des exigences qui se traduisent en contraintes d'architecture :

| Exigence du sujet | Ce qu'elle impose |
|---|---|
| Une variation de prix est reçue par les acheteurs intéressés **et** par le composant anti-fraude | Un événement, **plusieurs consommateurs** indépendants (CA-4 d'US-014, US-029) |
| Outil anti-fraude « développé en interne ou acheté », non tranché | Intégration par un **contrat d'événement** et un protocole standard (AMQP) |
| Ajout rapide d'enchères, de ventes en direct, d'un bot avant-vente, d'une analyse des ventes | Ajouter un **consommateur** sans modifier le producteur |
| Contrôle automatique sans bloquer le vendeur | Traitement **asynchrone**, avec reprise si le contrôle est indisponible |

La grille valorise la communication interservices et cite, pour l'expérimentation, un broker de messages
dans Kubernetes avec tests de publication et de consommation.

## Décision

**RabbitMQ 4.x**, un échange `topic` unique `collector.events` (clé de routage = type d'événement), une file
**quorum** par consommateur et par type, lettres mortes (`collector.dlx`), `x-delivery-limit` 5, publication
confirmée. Client : Spring AMQP. Topologie déclarée uniquement par `collector-messaging`.

## Alternatives écartées

| Option | Raison |
|---|---|
| Kafka | Journal rejouable et très haut débit inutiles au volume d'une start-up ; exploitation plus lourde ; pas de lettres mortes natives. Si l'analyse des ventes demande de rejouer l'historique : **RabbitMQ Streams**, sans changer de broker |
| Appels REST synchrones | Couplage temporel (effet domino) ; chaque nouveau consommateur oblige à modifier le producteur |
| Monolithe modulaire et événements internes | Aucun découplage au déploiement, pas d'intégration d'un outil externe (ADR 0004) |
| File en base interrogée par sondage | Latence et charge SQL. À ne pas confondre avec l'**outbox** (ADR 0005), qui garantit la livraison *vers* le bus sans le remplacer |

## Conséquences

- Un composant de plus à exploiter (cluster de 3 nœuds en recette, 1 nœud en démo locale).
- Livraison « au moins une fois » : consommateurs **idempotents** obligatoires ; aucun ordre garanti
  (`aggregate_version`, ADR 0005).
- Cohérence à terme assumée : `EN_CONTROLE` avant `PUBLIE`.
- Chaque événement publié doit avoir au moins une file liée (publication `mandatory`).
- Limite de pertinence : avec un seul service et aucun consommateur externe, le bus serait de la
  sur-ingénierie ; ce sont l'anti-fraude et CA-4 qui le justifient.
- Expérimentation de référence : RabbitMQ en cluster sur Minikube (`docs/experimentation/`).
