# Tests de charge (JMeter)

Plan : `collector.jmx`. Il produit la **métrique 3** du projet (p95 et taux d'erreur sous charge
nominale, seuils : p95 < 300 ms, erreurs < 1 %) et la **démonstration de montée en charge** de la
soutenance. Le plan est généré puis relu : il n'a **pas encore été exécuté** (JMeter n'est pas
installé sur le poste de développement) ; le XML est valide, la première exécution doit se faire
contre la pile compose avant toute mesure officielle.

## Scénario

| Part | Parcours | Requêtes |
|---|---|---|
| **70 %** | Consultation | `GET /categories`, `GET /articles?category=sneakers`, `GET /articles/{id}` |
| **25 %** | Mise en vente | brouillon (`POST /articles`), URL d'envoi (`POST …/photos`), **envoi réel de la photo** au stockage objet par l'URL pré-signée, soumission (`POST …/submission`) |
| **5 %** | Modification de prix | `PATCH /articles/{id}/price` sur un article de démonstration de `vendeur2` |

- `setUp` : un jeton par utilisateur de test (`vendeur1`, `vendeur2`), flux mot de passe du client
  `collector-tests` (**dev uniquement**), conservé dans les propriétés JMeter.
- Le tirage 1 à 100 (une valeur par itération) rend les trois parcours exclusifs : les parts sont celles
  des **itérations**, pas des requêtes.
- **Arrêt automatique** si plus de 5 % d'erreurs après 200 échantillons. Les `429` du rate limiting ne
  comptent pas comme des erreurs de l'application (c'est la protection qui travaille, voir ci-dessous).

## Paramètres

| Propriété | Défaut | Rôle |
|---|---|---|
| `-Jusers` | 10 | Utilisateurs simultanés : **10, 50, 100, 250** |
| `-Jrampup` | 30 | Montée en charge, en secondes |
| `-Jduration` | 180 | Durée du palier, en secondes (3 min) |
| `-Jprotocol -Jhost -Jport` | http localhost 8080 | API |
| `-Jauth_protocol -Jauth_host -Jauth_port` | http localhost 8081 | Keycloak |
| `-Jkc_password` | variable `KC_TEST_USER_PASSWORD` | Mot de passe des comptes de test (jamais dans le dépôt) |

## Lancer

Pile compose (charger le `.env` avant) :

```bash
set -a; . ./.env; set +a
for users in 10 50 100 250; do
  jmeter -n -t tests/load/collector.jmx -Jusers=$users \
    -l results-$users.jtl -e -o report-$users/
done
```

Recette (Minikube ou kind, hôtes du fichier `hosts`) :

```bash
jmeter -n -t tests/load/collector.jmx -Jusers=100 \
  -Jprotocol=https -Jhost=api.collector.local -Jport=443 \
  -Jauth_protocol=https -Jauth_host=auth.collector.local -Jauth_port=443 \
  -l results.jtl -e -o report/
```

Le certificat de la recette est signé par l'autorité locale : l'importer dans le magasin Java de JMeter
(`keytool -importcert -alias collector-ca -file ca.crt -cacerts`), ca.crt s'exporte du secret `collector-ca`.
Le dossier de rapport (`-o`) doit être vide ou absent.

## Deux mesures, deux objectifs

1. **Capacité de l'application.** Relever temporairement la limite de débit de Traefik
   (Middleware `rate-limit`, `average` et `burst` très élevés) pour mesurer ce que l'application tient,
   pas ce que le rate limiting laisse passer. Résultats : p95 par palier, palier de rupture.
2. **Protection.** Remettre la limite normale (50 req/s par IP, rafale 100) et relancer un palier élevé :
   les `429` montrent que l'excès est absorbé à l'entrée sans dégrader les 200.
   Une seule machine de test partage une seule IP : c'est voulu, cela déclenche la limite.

Les deux mesures alimentent la phase 3 (plan de remédiation) : `docs/remediation.md`.

## À relever

| Quoi | Où |
|---|---|
| **p95 au palier nominal** (seuil 300 ms) et p99 | Rapport HTML (`report/`), tableau « Statistics » |
| **Palier de rupture** : premier palier où p95 dépasse 300 ms ou erreurs > 1 % | Comparaison des quatre rapports |
| **Taux d'erreur** hors 429 | Rapport, colonne « Error % » |
| **Facteur limitant** : CPU des pods, pool Hikari (10 connexions), files RabbitMQ, stockage objet | Grafana, tableau « US-014 » ; `collector_outbox_pending` ; connexions actives PostgreSQL |
| **Réplicas HPA** au cours du palier | `kubectl -n collector get hpa -w` ou panneau Grafana « réplicas » |
| **Délai de contrôle** p95 (SLO < 2 s) | `collector_article_check_duration_seconds` |

Point spécifique à Java à surveiller (remédiation, amélioration 6 du CLAUDE.md) : avec les threads
virtuels et un pool Hikari de 10, la contention se déplace vers le pool. Si p95 monte alors que le CPU
reste bas, regarder `hikaricp_connections_pending`.

## Limites assumées

- Une seule machine génère la charge : à 250 utilisateurs, c'est peut-être JMeter qui sature avant
  l'application (surveiller son CPU).
- Les brouillons créés restent en base : purger ou recréer la pile entre deux séries de mesures.
- Le contrôle automatique (règle des 3 écarts-types) lit une vue rafraîchie toutes les 5 minutes.
- Le jeton dure 5 minutes : un palier de 3 minutes précédé d'un `setUp` reste dans sa validité ;
  ne pas enchaîner plusieurs paliers dans une même exécution JMeter.
