# Déploiement Kubernetes

Trois services (`catalogue`, `controle`, `notification`), PostgreSQL, RabbitMQ (opérateur officiel), Keycloak,
stockage objet Garage, Traefik, cert-manager, Prometheus et Grafana. Tout est décrit en Kustomize : une **base**
(un sous-dossier par composant) et quatre **overlays**.

| Overlay | Où | Contenu | Mémoire |
|---|---|---|---|
| `minikube` | Démo locale (< 16 Go de RAM) | RabbitMQ **1 nœud**, catalogue 2 réplicas (HPA 2 à 3), controle et notification 1 | ≈ 5-6 Go |
| `ci` | Recette sur **kind** dans la CI (runner 16 Go) | Configuration **complète** : RabbitMQ 3 nœuds, 2 réplicas de chaque service, HPA 2 à 6 | — |
| `sandbox` | Minikube, profil `sandbox` | Opérateur, RabbitMQ 3 nœuds et PerfTest, rien d'autre (expérimentation) | ≈ 4 Go |
| `cloud` | Cluster managé (option soutenance, production) | Comme `ci`, profil `prod`, images par digest signé | — |

La configuration cible (3 nœuds, HPA 2 à 6) est **prouvée par la CI** ; la démo locale en montre une réduction
assumée (voir `docs/ARCHITECTURE.md`).

## Déployer sur Minikube (démo)

Prérequis : Docker Desktop, `kubectl`, `minikube`, `helm` (CLAUDE.md A5), un fichier `.env` (copie de `.env.example`).

```bash
minikube start --cpus=4 --memory=6g --cni=calico        # calico : les NetworkPolicies sont appliquées
./k8s/scripts/install-platform.sh minikube              # Traefik, cert-manager, opérateur RabbitMQ, metrics-server
./k8s/scripts/deploy.sh minikube                        # secrets, ConfigMaps, images, application, attente
```

**Ne pas** activer l'addon `ingress` de Minikube : c'est ingress-nginx, qui n'est plus maintenu (ADR 0012).
Limiter la mémoire de Docker si besoin : `%UserProfile%\.wslconfig` (`[wsl2]` `memory=8GB`) sous Windows.

Hôtes à déclarer (fichier `hosts`, éditeur lancé en administrateur sous Windows : `C:\Windows\System32\drivers\etc\hosts` ;
`/etc/hosts` avec `sudo` sous macOS) :

```
127.0.0.1 api.collector.local auth.collector.local s3.collector.local
```

Avec le pilote Docker de Minikube, sur les deux systèmes, laisser tourner dans un terminal : `minikube tunnel`.

PowerShell, pour les commandes qui diffèrent : le `.env` est lu **par le script** (Bash), rien à charger à la main ;
lancer les scripts depuis Git Bash.

## Déployer sur kind (recette, comme la CI)

```bash
kind create cluster --name collector --config k8s/kind/cluster.yaml   # ports 80/443 vers Traefik
./k8s/scripts/install-platform.sh kind
./k8s/scripts/deploy.sh ci                                            # images construites localement si *_IMAGE absentes
echo "127.0.0.1 api.collector.local auth.collector.local s3.collector.local" | sudo tee -a /etc/hosts
```

En CI, `deploy.sh ci` reçoit `CATALOGUE_IMAGE`, `CONTROLE_IMAGE`, `NOTIFICATION_IMAGE` (`dépôt@sha256:…`) : les images
ont été **vérifiées avec cosign** juste avant, et c'est ce digest exact qui est déployé. Les secrets sont générés
aléatoirement et écrits dans `k8s/.generated-secrets.env` (ignoré par git) pour les tests d'acceptation.

## Ce que fait `deploy.sh`

1. Namespace `collector` (Pod Security : `enforce: baseline`, `warn: restricted`).
2. Secret `collector-secrets` : depuis `.env`, sinon le Secret existant est **conservé**, sinon valeurs aléatoires (CI seulement).
   Jamais de valeur par défaut : une variable absente arrête le déploiement.
3. ConfigMaps depuis les fichiers du dépôt (une seule source de vérité) : realm Keycloak, rôles PostgreSQL,
   configuration Garage, règles d'alerte, migrations Flyway des services, jeu de données.
4. Images : construites localement et chargées dans Minikube/kind, ou fournies par digest.
5. Kustomization temporaire qui substitue les références d'images par les digests, puis `kubectl apply -k`.
6. Attente : PostgreSQL, stockage, RabbitMQ (`AllReplicasReady`), Job Flyway, Job d'initialisation du stockage,
   puis les rollouts. Un échec affiche l'état des pods.

Les **Jobs** (Flyway, initialisation du stockage) sont immuables : le script les supprime avant de réappliquer.
Flyway tourne dans un Job séparé avec les identifiants du propriétaire ; les services ne connaissent jamais ce
mot de passe. Chaque service attend la présence de son schéma (init container) avant de démarrer.

## Démonstration de disponibilité et de montée en charge

```bash
kubectl -n collector get pods,hpa                       # 2 réplicas du catalogue, HPA à 70 % CPU
# Pendant JMeter (tests/load/collector.jmx) : l'HPA ajoute des réplicas
kubectl -n collector get hpa catalogue -w
# Suppression d'un pod en direct : le service reste disponible (2 réplicas + PodDisruptionBudget minAvailable: 1)
kubectl -n collector delete pod -l app.kubernetes.io/name=catalogue --field-selector=status.phase=Running --wait=false | head -1
curl -k https://api.collector.local/api/v1/categories   # répond pendant le redémarrage
# Perte d'un nœud RabbitMQ (overlay ci ou sandbox) : le cluster de 3 nœuds et les files quorum continuent
kubectl -n collector delete pod rabbitmq-server-1
kubectl -n collector exec rabbitmq-server-0 -- rabbitmq-diagnostics check_if_node_is_quorum_critical
```

Grafana et Prometheus ne sont pas exposés par l'Ingress :

```bash
kubectl -n collector port-forward svc/grafana 3000:3000
kubectl -n collector port-forward svc/prometheus 9090:9090
```

## Sécurité du déploiement

- Conteneurs non-root, système de fichiers en lecture seule, `capabilities: drop: [ALL]`, `seccompProfile: RuntimeDefault`,
  pas de jeton d'API Kubernetes monté (sauf Prometheus, qui lit la liste des pods).
- Exceptions **justifiées** : Keycloak n'est pas en lecture seule (le mode `start-dev` compile au démarrage) ; Flyway non plus.
- NetworkPolicies : refus par défaut (entrée et sortie), puis flux explicites par labels de capacité
  (`collector.shop/db-client`, `amqp-client`, `oidc-client`, `s3-client`). Le service de contrôle n'a **aucune** entrée.
- TLS par cert-manager avec une **autorité locale** ; les tests exportent `ca.crt` du Secret `collector-ca`.
  HSTS, limite de débit par IP et en-têtes de sécurité par des Middlewares Traefik.
- L'API d'administration de Garage (3903) et le management (8081) ne sont jamais routés.

## Limites et points d'attention

- **Non exécuté par l'auteur de ces fichiers** : ni `kubectl` ni `docker` n'étaient disponibles. La syntaxe YAML a été
  validée, pas le comportement dans un cluster. Le premier déploiement sur Minikube et la première recette CI
  sont le vrai test ; attendre des ajustements (noms de clés Helm de Traefik, sondes, droits de volumes).
- **Versions à figer** : `install-platform.sh` prend les dernières versions de Traefik, cert-manager, de l'opérateur
  RabbitMQ et de metrics-server, et les affiche ; les épingler (variables `*_VERSION`) après le premier succès.
- Keycloak en `start-dev` avec base H2 **non persistante** (le realm est réimporté à chaque démarrage) : acceptable pour
  la démo, à remplacer en production (remédiation court terme).
- Le cluster kind de la CI est à **un seul nœud** : la perte d'un nœud RabbitMQ y est une bascule logique, pas une panne
  de machine, et il n'y a pas de partition réseau (limites de l'expérimentation).
- Le kindnet de kind n'applique les NetworkPolicies que selon sa version ; Calico (Minikube) les applique toujours.
- L'alerte `RedemarragesDePods` suppose kube-state-metrics, non installé ici ; les autres alertes n'en dépendent pas.
- Sauvegardes : le CronJob écrit sur un volume du cluster. En production, viser le stockage objet hors cluster et
  **tester la restauration** (docs/exploitation.md).
