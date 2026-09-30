# ADR 0015 — Périmètre du POC réduit à trois user stories liées, et notification-service

- Statut : accepté (30/09/2026). Remplace la recommandation initiale d'un `payment-service` et d'un
  `chat-service` (cadrage conservé en V2, ADR 0014).

## Contexte

Le sujet impose d'implémenter **au moins une** fonctionnalité métier. Le deck de soutenance (31 slides)
annonçait un périmètre de **21 user stories sur 38**, dont le paiement en sandbox et le chat protégé. Le
développeur, seul sur ce travail individuel, préfère un périmètre **plus petit, lié, complet et testé** :
**trois user stories qui racontent le même cycle de vie** plutôt que vingt et une superficielles. Le jury lira le
code ; chaque comportement livré doit être testé et défendable.

## Décision

Le POC implémente :

| US | Rôle dans le cycle de vie d'un article | Ce qu'elle démontre |
|---|---|---|
| **US-014** Mettre en ligne un article après contrôle automatique (imposée) | Brouillon, photos, soumission, contrôle, anomalie de prix, variation de prix (CA-1 à CA-6) | Authentification, persistance, stockage objet, outbox, bus, sécurité |
| **US-033** Traiter les articles en revue | Suite de CA-3 : l'admin valide ou rejette un article `EN_REVUE` | Rôle `admin`, machine à états complète, événement `article.reviewed` |
| **US-029** Suivre un article, être prévenu de ses variations de prix | Suite de CA-4 : les abonnés reçoivent la notification | Deuxième consommateur de `price.changed`, `notification-service`, idempotence, `aggregate_version` |

Livrés en plus car ils reposent sur le même socle : consultation publique du catalogue (US-006, US-007),
filtrage des coordonnées dans les annonces (US-023), US-021 en exemple guidé.

**Hors POC** : paiement, commission, livraison, litiges (US-015 à US-019), chat (US-024, US-025),
recommandations, e-mails, alertes, boutiques, publicité.

**Architecture** : trois services déployables (ADR 0004) : `catalogue-service`, `controle-service` et
**`notification-service`** (nouveau). Le notification-service consomme `price.changed`, `follow.changed`,
`interests.updated`, `article.reviewed`, tient sa propre copie des données (abonnements, versions vues,
centres d'intérêt) et sert `GET /me/notifications` ; identité = `sub` Keycloak, audience `notification-api`.

## Alternatives écartées

| Option | Raison |
|---|---|
| Garder les 21 US du deck | Une tentative superficielle sur vingt et une, peu testée : contraire à la règle « chaque comportement livré est testé » |
| US-014 seule | Ne montre ni la machine à états complète ni le deuxième consommateur de l'événement |
| US-014 avec le paiement en sandbox et le chat | Deux services de plus (secrets du PSP, WebSocket) et une conception à finir avant de coder ; sort du temps disponible |
| US-033 et US-006/007 sans US-029 | Zéro changement d'architecture mais un trio moins parlant : le bénéfice du bus (ajouter un consommateur) ne serait pas démontré |

## Conséquences

- **Écart assumé avec le deck** : 3 US au lieu de 21, paiement et chat hors POC. La règle A2-9 impose de le
  signaler : **le deck est à mettre à jour** avant la soutenance (slides de backlog et de critères d'acceptation,
  périmètre, architecture, démonstration).
- Trois services au lieu de quatre prévus : CI (trois images), manifests Kubernetes, NetworkPolicies et
  observabilité suivent ces trois services.
- Nouveau contrat : routes `/admin/reviews`, `/articles/{id}/follow`, `/me/notifications` ; événements
  `article.reviewed` et `follow.changed` (`docs/api/openapi.yaml`, `docs/events.md`).
- Nouvelle migration `V5__review.sql` (colonnes `check_reason`, `review_reason`, `reviewed_by`) et `V100` pour
  le notification-service (ADR 0010).
- Démonstration : parcours vendeur (US-014), file de revue d'un article à 1 000 € (US-033), abonnement puis
  changement de prix et notification (US-029).
- Les sujets du sujet non traités (paiement, chat, recommandations, back-office complet) restent décrits dans le
  README et CLAUDE.md comme feuille de route V2 : l'architecture les accueille sans refonte.
