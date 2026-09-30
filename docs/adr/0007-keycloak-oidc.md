# ADR 0007 — Keycloak, OIDC, jetons JWT RS256

- Statut : accepté (30/09/2026)

## Contexte

La sécurité est l'exigence de premier plan (transactions financières). Il faut inscription, connexion, rôles
(acheteur, vendeur cumulables, admin), sans stocker de mot de passe dans nos bases, avec un standard que les
services vérifient sans appeler l'annuaire à chaque requête.

## Décision

**Keycloak 26.x** comme fournisseur d'identité, **OIDC Authorization Code + PKCE** pour le navigateur (client
public `collector-web`), **jetons JWT RS256** de 5 minutes, révocation du refresh token. Chaque service est un
**OAuth2 Resource Server** (Spring Security) :

- `issuer-uri` = émetteur **attendu**, l'URL publique ; `jwk-set-uri` = JWKS **interne** (pas de découverte au
  démarrage, et `iss` reste validé) ; `jws-algorithms: RS256`.
- **Audiences** : `catalogue-api` pour le catalogue, `notification-api` pour le notification-service. Le realm
  ajoute les deux audiences aux jetons des clients (mapper d'audience) : un jeton destiné à une API n'est pas
  accepté par une autre sans être émis pour elle.
- Rôles du realm (`realm_access.roles`) convertis en `ROLE_acheteur`, `ROLE_vendeur`, `ROLE_admin` ;
  `@PreAuthorize` dans les adaptateurs web ; **propriété** vérifiée dans le cas d'usage (`sub` =
  `app_user.keycloak_sub`).
- L'identité qui circule entre services est le **`sub`** Keycloak (`member_id` des événements).
- API sans état, CSRF désactivé (aucun cookie), CORS limité au front, CSP stricte sur `/api/**`.
- Client `collector-tests` (flux mot de passe) : **développement uniquement**, à retirer du realm de production.
- Émetteur fixe : `KC_HOSTNAME` public avec `KC_HOSTNAME_BACKCHANNEL_DYNAMIC`, sinon le `iss` du jeton varie
  selon l'adresse par laquelle on joint Keycloak.

## Alternatives écartées

| Option | Raison |
|---|---|
| Authentification maison (mots de passe en base) | Risque majeur, travail de sécurité à refaire (hachage, réinitialisation, verrouillage) |
| Service SaaS d'identité | Hébergement européen exigé ; dépendance externe critique ; Keycloak s'auto-héberge et se configure par un fichier de realm versionné |
| Sessions serveur avec cookies | Impose CSRF, affinité de session ; contraire à des API sans état scalables |
| Une seule audience pour toutes les API | Un jeton volé pour une API servirait contre les autres |

## Conséquences

- Un composant de plus (Keycloak, heap 512 Mo en démo) ; realm importé au démarrage depuis
  `infra/keycloak/collector-realm.json` (les mots de passe de test viennent de l'environnement).
- JWKS injoignable = 401 en masse : entrée du runbook et alerte sur le pic de 401/403.
- Remédiation à prévoir : Keycloak en mode production, retrait du client de test, révocation des jetons.
