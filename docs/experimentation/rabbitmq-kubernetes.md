# Expérimentation : RabbitMQ en cluster sur Kubernetes

Technologie *plateforme* testée : un **broker de messages en cluster de 3 nœuds** sur Kubernetes (RabbitMQ
Cluster Operator, files quorum, publisher confirms).

> **État : protocole écrit, NON exécuté.** Aucune mesure n'existe. Tous les tableaux de résultats et de
> difficultés sont **vides** et marqués « à renseigner après exécution ». Ne rien y écrire qui ne vienne d'un
> essai réel (critère 1 : les résultats doivent valider l'adoption).

**Objectif** : valider que le découplage par bus tient **sans perte de message**, y compris à la perte d'un
nœud, avant d'en faire la colonne vertébrale de US-014 et US-029.

**Hypothèse à confirmer ou infirmer** : avec des files quorum sur 3 nœuds et des confirmations de
publication, la suppression d'un pod n'entraîne ni perte ni interruption prolongée ; des doublons sont
possibles (livraison au moins une fois) et sont absorbés par les consommateurs idempotents.

## 1. Environnement

À relever avant d'exécuter (ne rien présupposer) :

| Élément | Valeur |
|---|---|
| Poste (modèle, CPU, RAM, OS) | à renseigner après exécution |
| Docker Desktop | à renseigner après exécution |
| Minikube (version, pilote) | à renseigner après exécution |
| Kubernetes | à renseigner après exécution |
| RabbitMQ Cluster Operator | à renseigner après exécution |
| RabbitMQ (image) | `rabbitmq:4.1-management` (vérifier la version exacte tirée) |
| PerfTest (`pivotalrabbitmq/perf-test`) | à renseigner après exécution |
| Ressources allouées | profil `sandbox` : `--memory=5g` ; CPU à renseigner |

Contrainte : poste de moins de 16 Go de RAM. Le profil `sandbox` n'héberge que l'opérateur, le cluster
RabbitMQ à 3 nœuds et PerfTest, rien d'autre.

## 2. Technologies et interactions

```
producteur (PerfTest, puis relais de l'outbox)
   │  publisher confirms (correlated), mandatory
   ▼
échange collector.events (topic) ──► files QUORUM répliquées sur 3 nœuds ──► consommateur (PerfTest, puis Spring AMQP)
                                              │  x-delivery-limit: 5
                                              ▼
                                      collector.dlx ──► collector.dead-letter
Prometheus ◄── plugin rabbitmq_prometheus (15692)
```

| Composant | Rôle dans l'essai |
|---|---|
| Minikube (profil `sandbox`) | Cluster Kubernetes mono-machine |
| RabbitMQ Cluster Operator | Déploie et supervise le cluster |
| `RabbitmqCluster` 3 nœuds | Cluster cible (`pause_minority`) |
| Files quorum | Réplication Raft ; survit à la perte d'un nœud sur trois |
| Publisher confirms | Le producteur sait ce que le broker a accepté |
| PerfTest | Génère la charge et compte les messages |
| Prometheus | Observer débit, profondeur des files, nœuds |

## 3. Étapes reproductibles

### 3.1 Cluster Kubernetes

```bash
minikube start -p sandbox --memory=5g --cpus=4 --cni=calico
kubectl config use-context sandbox
kubectl create namespace rabbit
```

### 3.2 Opérateur et cluster 3 nœuds

```bash
kubectl apply -f "https://github.com/rabbitmq/cluster-operator/releases/latest/download/cluster-operator.yml"
kubectl -n rabbitmq-system rollout status deploy/rabbitmq-cluster-operator
```

Manifeste `RabbitmqCluster` (à enregistrer dans `k8s/overlays/sandbox/`, ce fichier sert de référence) :

```yaml
apiVersion: rabbitmq.com/v1beta1
kind: RabbitmqCluster
metadata:
  name: rabbitmq
  namespace: rabbit
spec:
  replicas: 3
  persistence:
    storageClassName: standard
    storage: 2Gi
  resources:
    requests: { cpu: 250m, memory: 512Mi }
    limits: { cpu: "1", memory: 768Mi }
  rabbitmq:
    additionalConfig: |
      cluster_partition_handling = pause_minority
      default_queue_type = quorum
    additionalPlugins: [rabbitmq_prometheus]
```

```bash
kubectl apply -f k8s/overlays/sandbox/rabbitmq-cluster.yaml
kubectl -n rabbit get rabbitmqcluster rabbitmq -w      # attendre ALLREPLICASREADY=True
kubectl -n rabbit exec rabbitmq-server-0 -- rabbitmqctl cluster_status
```

### 3.3 Référence avec PerfTest (sans incident)

```bash
USER=$(kubectl -n rabbit get secret rabbitmq-default-user -o jsonpath='{.data.username}' | base64 -d)
PASS=$(kubectl -n rabbit get secret rabbitmq-default-user -o jsonpath='{.data.password}' | base64 -d)
kubectl -n rabbit run perftest --rm -it --restart=Never --image=pivotalrabbitmq/perf-test:latest -- \
  --uri "amqp://$USER:$PASS@rabbitmq.rabbit.svc:5672" \
  --quorum-queue --queue perf-baseline --confirm 100 --rate 500 --time 120 \
  --producers 2 --consumers 2 --flag persistent
```

Relever à la fin : messages envoyés, reçus, débit moyen, latence (min, médiane, p95, max).

### 3.4 Même mesure avec suppression d'un pod

Dans un terminal, lancer PerfTest avec `--time 180` (mêmes options, file `perf-failover`). Dans un autre, à t = 60 s :

```bash
date -u +%T
kubectl -n rabbit delete pod rabbitmq-server-1
kubectl -n rabbit get pods -w
```

Relever : instant de la suppression, instant où le producteur reprend (confirms), instant où le pod revient
`Ready`. Comparer le total envoyé et le total reçu (perdus = envoyés confirmés − reçus ; dupliqués = reçus
en trop). Noter la durée de bascule.

Variante à tester aussi : supprimer le nœud **leader** de la file (`rabbitmq-queues quorum_status perf-failover`).

### 3.5 Même chose avec l'application (outbox + consommateur Spring AMQP)

1. Déployer `catalogue-service` et `notification-service` contre ce broker (overlay `sandbox` étendu).
2. Créer un flux soutenu d'événements : `POST /api/v1/pings` (profil `dev`) en boucle, ou changements de prix.
3. Supprimer `rabbitmq-server-1` en plein flux.
4. Vérifier : `collector_outbox_pending` remonte puis redescend à 0 ; aucun événement perdu
   (`SELECT count(*) FROM outbox_event WHERE published_at IS NULL` → 0) ; aucun doublon de **notification**
   (contrainte d'unicité membre, article, version) ; file de lettres mortes vide.

## 4. Résultats

**À renseigner après exécution.**

| Mesure | Référence (sans incident) | Avec suppression d'un pod | Avec suppression du leader |
|---|---|---|---|
| Débit moyen (msg/s) | à renseigner | à renseigner | à renseigner |
| Latence p95 bout en bout (ms) | à renseigner | à renseigner | à renseigner |
| Messages envoyés et confirmés | à renseigner | à renseigner | à renseigner |
| Messages reçus | à renseigner | à renseigner | à renseigner |
| **Perdus** | à renseigner | à renseigner | à renseigner |
| **Dupliqués** | à renseigner | à renseigner | à renseigner |
| Durée de bascule (s) | sans objet | à renseigner | à renseigner |

| Essai avec l'application | Résultat |
|---|---|
| Événements restés dans l'outbox après l'incident | à renseigner après exécution |
| Événements perdus | à renseigner après exécution |
| Doublons de notification | à renseigner après exécution |
| Messages en lettres mortes | à renseigner après exécution |
| Temps pour revenir à `collector_outbox_pending` = 0 | à renseigner après exécution |

## 5. Difficultés réelles

**À renseigner après exécution** (classe de stockage, sondes au démarrage à froid, mémoire, réseau du pilote
Docker, versions d'opérateur…). Ne noter que ce qui a réellement été rencontré.

| Difficulté | Contexte | Contournement | Impact |
|---|---|---|---|
| à renseigner après exécution | | | |

## 6. Limites

- Cluster **mono-machine** : la bascule est logique, pas une vraie défaillance matérielle ou réseau.
- **Pas de partition réseau** testée (le comportement `pause_minority` n'est pas éprouvé).
- Volumétrie de test réduite ; ne préjuge pas de la charge de production.
- La démo locale tourne avec **1 nœud** (poste de moins de 16 Go) ; le cluster à 3 nœuds n'est exercé qu'ici et en recette (kind, runner 16 Go).

## 7. Décision

**À confirmer par les résultats.** Décision de principe : adopter RabbitMQ en cluster avec **files quorum et
publisher confirms obligatoires**.

| Alternative | Écartée pour |
|---|---|
| Kafka | Journal rejouable et haut débit inutiles à ce volume, exploitation plus lourde, pas de lettres mortes natives ; RabbitMQ Streams en cas de besoin de rejeu |
| Appels REST synchrones | Couplage temporel (effet domino), chaque consommateur oblige à modifier le producteur |
| Monolithe modulaire avec événements internes | Pas de déploiement indépendant, pas d'intégration d'un outil anti-fraude externe par contrat |
| File en base interrogée par sondage | Latence et charge SQL (l'outbox, elle, garantit la livraison *vers* le bus) |

Si l'essai montre des pertes ou une bascule incompatible avec le SLO « aucune perte », la décision est
**révisée** et les résultats consignés ici.
