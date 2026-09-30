# Tests d'acceptation

Scénarios Gherkin en français (Cucumber-JVM + REST Assured), exécutés **en boîte noire** contre une pile
déployée : compose (local) ou kind (recette). Ils couvrent US-014, US-033, US-029, US-021 et le socle.
Hors du build normal : le module n'existe que sous le profil Maven `acceptance`.

| Fichier | Contenu |
|---|---|
| `us014_mise_en_ligne.feature` | CA-1 à CA-6, photo réellement envoyée sur l'URL pré-signée |
| `us033_revue_admin.feature` | File de revue, validation, rejet avec motif, 409, 401/403 |
| `us029_suivre_article.feature` | Suivre, notification de variation de prix (notification-service), lecture, isolation |
| `us021_centres_interet.feature` | Exemple guidé (`docs/guide/`) |
| `socle.feature` | Routes publiques, erreurs sans détail technique, ping par l'outbox, topologie RabbitMQ |

## Lancer

```bash
docker compose up -d --build
set -a; . ./.env; set +a          # KC_TEST_USER_PASSWORD, RABBITMQ_PASSWORD
./mvnw -B -Pacceptance -pl acceptance-tests verify -Dcollector.env=local
# une seule user story :
./mvnw -B -Pacceptance -pl acceptance-tests verify -Dcucumber.filter.tags="@US-029"
# vérifier que toutes les étapes sont définies, sans appeler la pile :
./mvnw -B -Pacceptance -pl acceptance-tests verify -Dcucumber.execution.dry-run=true
```

Sous PowerShell : charger le `.env` comme indiqué dans CLAUDE.md §G15, puis `.\mvnw` avec `"-Dcollector.env=local"`.
Rapports : `acceptance-tests/target/cucumber.html` et `cucumber.xml`.

## Environnements

`-Dcollector.env=local` (défaut) : API `localhost:8080`, notification `localhost:8082`, Keycloak `localhost:8081`,
console RabbitMQ `localhost:15672`. `recette` : `https://api.collector.local` (les deux API derrière le même
Ingress), `https://auth.collector.local`. Chaque adresse se surcharge par variable d'environnement
(`COLLECTOR_API_URL`, `COLLECTOR_NOTIFICATION_URL`, `COLLECTOR_KEYCLOAK_URL`, `COLLECTOR_RABBIT_URL`).

HTTPS de la recette (autorité locale de cert-manager) :

```bash
kubectl -n collector get secret collector-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > ca.crt
./mvnw -B -Pacceptance -pl acceptance-tests verify -Dcollector.env=recette -Dcollector.tls.ca=$PWD/ca.crt
```

Pour un cluster kind jetable seulement : `-Dcollector.tls.relaxed=true` désactive la vérification.

## Conception

- **Jetons** : flux mot de passe du client `collector-tests` (dev uniquement), comptes `vendeur1`, `vendeur2`,
  `acheteur1`, `admin`. Mot de passe = `KC_TEST_USER_PASSWORD`, jamais une valeur par défaut.
- **Événements** : étiquette `@events`. Une file `tests.audit`, liée à `collector.events` avec `#`, reçoit une
  **copie** de chaque événement ; elle est lue par l'API de management (`POST /api/queues/%2F/tests.audit/get`)
  et supprimée à la fin. Lire les files des services volerait leurs messages. Un curseur écarte les événements
  d'avant l'action testée (« aucun événement publié » ne doit pas être trompé par une étape précédente).
- **Indépendance** : les articles « publiés » et « en revue » sont créés par les scénarios eux-mêmes, en passant
  par le vrai contrôle automatique, et non tirés des données de démonstration ; un scénario se rejoue donc sans
  dépendre de l'état laissé par le précédent (un même prix redemandé ne produirait aucun événement).
- **Photo** : petit JPEG (`photo.jpg.b64`) envoyé sur l'`upload_url` avec les `upload_headers`. Le `Content-Type`
  est signé : REST Assured ne doit pas y ajouter de `charset`.
- `@wip` exclut un scénario (`cucumber.filter.tags=not @wip`).

## Limites

- Les scénarios US-029 supposent le `notification-service` démarré et consommant ses files ; leurs attentes ont
  des délais de 5 à 10 secondes, à élargir si la machine est lente.
- `US-029 CA-4` (deux changements rapprochés) vérifie le résultat observable, pas l'ordre de livraison : l'inversion
  elle-même est couverte par `NotificationUseCasesTest` et `NotificationFlowIT`.
