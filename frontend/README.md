# Front minimal (Vue 3, TypeScript, Vite)

Front volontairement **minimal** : il montre le parcours de bout en bout, l'essentiel de la valeur est dans les API.

| Écran | Rôle Keycloak | US |
|---|---|---|
| Catalogue (filtre, pagination, suivre un article) | public, suivi : `acheteur` | US-006, US-029 |
| Vendre (brouillon, photos, soumission) | `vendeur` | US-014 |
| Mes articles (statut rafraîchi tant que `EN_CONTROLE`, modifier le prix, motif de rejet) | `vendeur` | US-014 |
| Notifications (non lues, marquer lue) | `acheteur` | US-029 |
| Revue admin (valider, rejeter avec motif) | `admin` | US-033 |

Choix : connexion OIDC **Authorization Code + PKCE** sans secret (`oidc-client-ts`, client public `collector-web`),
français et anglais (`vue-i18n`, US-036), codes d'erreur des API traduits (jamais de détail technique affiché),
thèmes clair et sombre, navigation au clavier. Les onglets affichés dépendent des rôles, mais **seul le serveur décide
des droits** (401 et 403). Le binaire d'une photo est envoyé directement au stockage par l'URL pré-signée.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 (port fixe : c'est l'origine autorisée par CORS, Keycloak et le stockage)
npm run build      # vérification des types puis bundle
```

La pile doit tourner (`docker compose up -d --build`). Adresses surchargeables : `.env.example`.
Comptes de test : `vendeur1`, `vendeur2`, `acheteur1`, `admin` (mot de passe `KC_TEST_USER_PASSWORD`).

**Non vérifié** : `npm run build` passe (types et bundle), mais l'application n'a jamais été ouverte contre une pile réelle.
