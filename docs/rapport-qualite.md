# Rapport qualité

Quatre indicateurs ISO/IEC 25010:2023, chacun avec un seuil bloquant, un outil et un tableau de suivi
(critère 6 de la grille). **Aucune valeur n'est inventée** : une case « à relever (CI) » signifie que la
mesure n'existe pas encore.

## 1. Les quatre indicateurs

| # | Indicateur | Caractéristique ISO 25010 | Outils | Seuil bloquant | Fréquence |
|---|---|---|---|---|---|
| 1 | Couverture de branches | Maintenabilité, capacité fonctionnelle | JUnit 5 + JaCoCo (unitaires et intégration fusionnés), PIT en complément | ≥ 70 % global, ≥ 80 % code nouveau (Sonar) | Chaque commit |
| 2 | Vulnérabilités critiques (code, dépendances, image) | Sécurité | CodeQL, Semgrep, SpotBugs + FindSecBugs, Trivy (fs, image), OWASP ZAP | 0 critique, 0 haute non justifiée | Chaque commit |
| 3 | Latence p95 + taux d'erreur sous charge nominale | Performance, fiabilité | JMeter + Prometheus/Grafana | p95 < 300 ms, erreurs < 1 % | Nocturne + avant livraison |
| 4 | Ratio de dette + complexité cognitive | Maintenabilité, flexibilité | SonarCloud (quality gate), ArchUnit | ratio < 5 %, complexité par méthode < 15 | Chaque commit |

Pourquoi ces quatre : ils couvrent les quatre caractéristiques retenues (sécurité, maintenabilité,
flexibilité, performance et fiabilité) et chacun est **bloquant**, donc la dette ne s'accumule pas en
silence : un commit qui dégrade un indicateur sous son seuil ne fusionne pas.

## 2. Mesures locales (hors tests d'intégration)

Seules mesures réellement disponibles : `mvn verify` sur le poste de développement, **sans Docker**, donc
sans les tests d'intégration. Elles sous-estiment la couverture (les adaptateurs JDBC et JPA, les listeners
et le relais de l'outbox ne sont couverts que par les `*IT`). Ce ne sont **pas** des mesures de la CI.

| Module | Couverture de branches (JaCoCo, locale) | Lecture |
|---|---|---|
| `catalogue-service` | ≈ 0,68 | Sous le seuil de 0,70 sans les `*IT` ; les branches manquantes sont dans `ArticlePersistenceAdapter`, `OutboxRelay`, `InterestJdbcAdapter`, `ArticleEntity`, `ArticleCheckedListener`, `S3PhotoStorage`, `OutboxStore` |
| `controle-service` | seuil de 0,70 passé (valeur exacte non relevée) | La règle est en Java pur, donc couverte sans infrastructure |
| `notification-service` | ≈ 0,41 | Toutes les branches manquantes sont dans `NotificationJdbcAdapter` (14 branches), que seuls les `*IT` exercent ; le reste de l'écart vient de `ApiExceptionHandler` |
| `collector-messaging` | seuil de 0,70 passé (valeur exacte non relevée) | |

Mesure du relevé : valeur du 2026-09-30, poste de développement, code non versionné (aucun commit : pas de
référence de commit à citer).

## 3. Tableau de suivi (à remplir à chaque relevé)

Au moins deux relevés dans le temps sont nécessaires pour montrer une tendance ; la métrique 3 vient du
workflow nocturne.

| Indicateur | Valeur mesurée | Date | Commit | Seuil | Écart | Axe d'amélioration | Élément de backlog |
|---|---|---|---|---|---|---|---|
| 1 · Couverture de branches (global, CI, unitaires + intégration) | à relever (CI) | | | ≥ 70 % | | | |
| 1 · Couverture du code nouveau (Sonar) | à relever (CI) | | | ≥ 80 % | | | |
| 1 · Score de mutation PIT (informatif) | à relever (nocturne) | | | aucun (rapport) | | | |
| 2 · Vulnérabilités critiques (Trivy fs + images) | à relever (CI) | | | 0 | | | |
| 2 · Vulnérabilités hautes non justifiées | à relever (CI) | | | 0 | | | |
| 2 · Alertes CodeQL / Semgrep / SpotBugs | à relever (CI) | | | 0 critique | | | |
| 3 · p95 au palier nominal | à relever (nocturne) | | | < 300 ms | | | |
| 3 · Taux d'erreur au palier nominal | à relever (nocturne) | | | < 1 % | | | |
| 3 · Palier de rupture (utilisateurs) | à relever (nocturne) | | | informatif | | | |
| 4 · Ratio de dette technique (Sonar) | à relever (CI) | | | < 5 % | | | |
| 4 · Complexité cognitive max par méthode | à relever (CI) | | | < 15 | | | |
| 4 · Violations ArchUnit | 0 attendu, à confirmer en CI | | | 0 | | | |

Exemple de ligne remplie (format attendu, **illustratif, pas une mesure**) : « couverture de branches de
`article.application` à 58 % → tester les refus de `SubmitArticle` ; backlog : *Tester le refus de photo
invalide* ».

## 4. Axes d'amélioration déjà identifiés (sans mesure CI)

| Constat (source) | Axe | Backlog proposé |
|---|---|---|
| Couverture locale 0,68 (catalogue) et 0,41 (notification) sans `*IT` | Faire tourner les `*IT` en CI et vérifier la fusion des couvertures | Valider les 22 tests d'intégration sur un runner avec Docker |
| Les tests d'intégration n'ont jamais tourné | Premier passage en CI, correction des écarts SQL / mapping | Exécuter la CI complète et traiter les échecs |
| `init.sh` (Garage) jamais exécuté | Test d'intégration avec un conteneur Garage | Rédiger `S3PhotoStorageIT` (Garage + script d'initialisation) |
| Pas de mesure de charge | Exécuter JMeter en nocturne | Plan `tests/load/collector.jmx` |

## 5. Comment les outils mesurent les indicateurs

- **JaCoCo** (`prepare-agent`, `report`, `check` dans le pom parent) : règle `BRANCH` ≥ 0,70 par module,
  unitaires (surefire) et intégration (failsafe) écrits dans le même `jacoco.exec`.
- **SonarCloud** : analyse avec `sonar.qualitygate.wait=true` dans `ci.yml` ; ratio de dette et complexité.
- **Trivy** : `fs` (dépendances, configuration, secrets) et images amd64 et arm64, `CRITICAL` bloquant.
- **Prometheus/Grafana** : p95 (`http_server_requests_seconds_bucket`), taux de 5xx, délai de contrôle
  (`collector_article_check_duration_seconds`).
