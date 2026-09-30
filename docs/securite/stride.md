# Modèle de menaces STRIDE

Un tableau par flux. Pour chaque lettre : la menace, la **mesure réellement en place dans ce code**, le risque
résiduel et l'action de remédiation (reprise dans [`../remediation.md`](../remediation.md)).
Périmètre : US-014, US-033, US-029 ; services `catalogue`, `controle`, `notification`.

Légende : S usurpation · T altération · R répudiation · I divulgation · D déni de service · E élévation de privilège.

## Flux 1 · Navigateur → Traefik → services (API)

| | Menace | Mesure en place | Risque résiduel | Remédiation |
|---|---|---|---|---|
| S | Usurper un vendeur ou un admin | OIDC Authorization Code + PKCE ; JWT RS256 validé par chaque service (`issuer-uri`, `jwk-set-uri`, `audiences`, `jws-algorithms: RS256`) ; jetons de 5 min | Jeton volé utilisable jusqu'à expiration | Révocation du refresh token (`revokeRefreshToken` du realm) ; Keycloak en mode production |
| T | Modifier la requête en transit | TLS (cert-manager, HSTS par Traefik) en recette ; autorité locale en démo | En compose local : HTTP clair (dev uniquement) | Certificat d'une vraie autorité en cible |
| R | Un admin nie avoir rejeté un article | `reviewed_by` et `review_reason` en base ; `article.reviewed` porte le `sub` de l'admin ; logs ECS avec identifiant de trace | Journaux non signés, pas de valeur probante | Journalisation à valeur probante (structurel) |
| I | Fuite de données dans les erreurs | ProblemDetail avec `code` stable, détail technique seulement dans les logs ; `include-stacktrace: never` ; score d'anomalie jamais exposé au public ; e-mail jamais exposé | Les `fields` d'une erreur 400 nomment les champs fautifs (jamais la valeur) | Aucune nécessaire |
| D | Saturer l'API | Rate limiting Traefik par IP ; `size` ≤ 100 ; corps bornés (`@Size`) ; threads virtuels | Pas de limite **par utilisateur** ; pool Hikari de 10 | Rate limiting par utilisateur sur les écritures ; dimensionnement Hikari mesuré |
| E | Un acheteur met en vente ; un vendeur modifie l'article d'un autre ; un non-admin décide d'une revue | `@PreAuthorize` par rôle (`vendeur`, `acheteur`, `admin`) ; **propriété** vérifiée dans le cas d'usage (`Article.requireOwner`) ; 401/403 testés ; CSRF désactivé car sans cookie | Une erreur de rôle dans le realm donne trop de droits | Test de propriété centralisé ; revue du realm avant production |

## Flux 2 · Services → PostgreSQL

| | Menace | Mesure en place | Risque résiduel | Remédiation |
|---|---|---|---|---|
| S | Un service se fait passer pour un autre | Un rôle PostgreSQL par service (`catalogue_app`, `controle_app`, `notification_app`), mots de passe en secrets, aucun par défaut | Secrets dans `.env` en local | Gestionnaire de secrets (External Secrets, Vault) |
| T | Injection SQL, modification de schéma | Requêtes paramétrées (`JdbcClient`, JPA) ; rôles **sans DDL** ; pas de `DELETE` d'article pour le catalogue ; migrations par un job séparé (propriétaire) ; `ddl-auto: validate` | Un service peut modifier ses propres tables | Audit SQL, revue des `GRANT` |
| R | Nier une modification | Historique de prix par trigger (`price_history`), `updated_at`, `version` | Pas d'audit des lectures | Journal d'audit |
| I | Lecture de tables non autorisées | `controle_app` : `SELECT` sur la seule vue `category_price_stats` ; `notification_app` limité à ses tables ; `monitoring` : `pg_monitor` seulement | Le catalogue lit `app_user.email` (conservé pour la notification e-mail, jamais exposé) | Chiffrement au repos ; base par service |
| D | Épuisement du pool, verrous longs | Relais de l'outbox **sans transaction** pendant l'attente du broker ; délais Hikari explicites (3 s) | Contention sous charge (10 connexions, threads virtuels) | Mesurer en phase 3, ajuster |
| E | Élévation via un compte applicatif | Moindre privilège (ci-dessus) ; pas de `CREATE` sur `public` | Mot de passe propriétaire connu du job Flyway seulement | Rotation des secrets |

## Flux 3 · catalogue → RabbitMQ → controle et notification

| | Menace | Mesure en place | Risque résiduel | Remédiation |
|---|---|---|---|---|
| S | Un faux producteur publie `article.checked` | Identifiants RabbitMQ par secret ; broker non exposé hors cluster (seule la console en local) | Un seul utilisateur RabbitMQ partagé par les services | Un utilisateur et des permissions par service |
| T | Message altéré ou non conforme | **Validation contre le schéma JSON** à la publication (`OutboxDomainEventPublisher`, `RabbitVerdictPublisher`) et à la consommation ; `additionalProperties: false` ; rejet sans remise en file | Pas de signature des messages | Signature ou mTLS entre services |
| R | Nier un événement | `event_id` unique, `occurred_at`, outbox conservée | Pas de journal immuable | Journalisation à valeur probante |
| I | Données sensibles dans les événements | Événements sans e-mail ni donnée personnelle ; `member_id` = identifiant pseudonyme (`sub`) | Titre d'article dans `price.changed` (public de toute façon) | Aucune nécessaire |
| D | Message empoisonné, file qui déborde | Limite de livraison 5 explicite, puis lettres mortes ; prefetch 10 ; publisher confirms ; `mandatory` | Files `fraude.*` **sans consommateur** : croissance sans borne | Borner les files ou brancher l'anti-fraude |
| E | Un consommateur écrit au-delà de son rôle | `controle-service` ne modifie jamais un article : il publie un verdict, le catalogue décide (`UPDATE ... WHERE status = 'EN_CONTROLE'`) | Un verdict frauduleux publié passerait (pas d'authentification du producteur) | Permissions d'écriture par utilisateur RabbitMQ |

## Flux 4 · Navigateur → stockage objet (photos)

| | Menace | Mesure en place | Risque résiduel | Remédiation |
|---|---|---|---|---|
| S | Envoyer sur l'objet d'un autre | URL **pré-signée** de 5 min par photo, clé choisie par le serveur (`articles/{article_id}/{photo_id}`), propriété vérifiée avant de délivrer l'URL | URL réutilisable pendant 5 min | Durée plus courte si besoin |
| T | Envoyer un fichier d'un autre type ou trop gros | `Content-Type` signé ; **HEAD à la soumission** : type `image/jpeg\|png\|webp`, taille ≤ 5 Mo ; sinon objet effacé (`invalid_photo`) | Le type déclaré n'est pas le contenu réel (pas d'analyse du binaire) | Analyse antivirus et vérification du contenu |
| R | Nier un envoi | Clé et métadonnées en base (`article_photo`) | Pas d'audit côté stockage | Journal du stockage |
| I | Lire les photos d'un article non publié | Bucket **privé**, aucune liste publique ; URL de lecture pré-signée de 10 min, seulement pour un article visible | **EXIF** (position GPS du vendeur) conservé dans l'image | Suppression des métadonnées EXIF |
| D | Remplir le stockage | 8 photos max par article, 5 Mo max | Pas de quota par vendeur | Quota et nettoyage des objets orphelins (photos jamais soumises) |
| E | Accéder à un autre bucket | Clé d'accès dédiée au catalogue, limitée au bucket `collector-photos` ; CORS limité à l'origine du front | Garage : droits par bucket et non par préfixe | Bucket dédié (déjà le cas) |

## Flux 5 · Services → Keycloak

| | Menace | Mesure en place | Risque résiduel | Remédiation |
|---|---|---|---|---|
| S | Faux serveur d'identité | `jwk-set-uri` interne (réseau du cluster), `issuer-uri` fixe, `audiences` par service (`catalogue-api`, `notification-api`), RS256 seulement | Keycloak en `start-dev` dans le compose | Mode production, base dédiée |
| T | Jeton falsifié | Signature RS256 vérifiée ; rejet d'un jeton pour un autre service (audience) | Clé publique récupérée sans authentification (normal) | Aucune nécessaire |
| R | Nier une connexion | Événements Keycloak, `bruteForceProtected` | Journaux Keycloak non exportés | Export vers l'observabilité |
| I | Fuite de comptes | Mots de passe de test lus dans l'environnement (`KC_TEST_USER_PASSWORD`), jamais dans le dépôt | Comptes de test dans le realm importé | Retirer les comptes et le client de test en production |
| D | JWKS injoignable : 401 en masse | Sondes ; alerte `PicEchecsAuthentification` ; runbook §6.3 | Pas de cache de repli documenté | Mettre en place une haute disponibilité de Keycloak |
| E | Client de test en production | Client `collector-tests` (flux mot de passe) documenté **dev uniquement** | Toujours présent dans le realm livré | **Retirer le client `collector-tests`** |

## Synthèse des risques à traiter en priorité

1. Rate limiting par utilisateur sur les écritures (D, flux 1).
2. Analyse antivirus et suppression des EXIF des photos (T et I, flux 4).
3. Pool Hikari et threads virtuels : mesurer la contention (D, flux 2).
4. Keycloak en mode production et retrait du client de test (S et E, flux 5).
5. Files `fraude.*` sans consommateur (D, flux 3).
6. Chiffrement au repos (base, sauvegardes, stockage) (I, flux 2 et 4).
