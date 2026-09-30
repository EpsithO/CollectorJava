# ADR 0009 — Kubernetes : Minikube pour la démo, kind éphémère en CI

- Statut : accepté (30/09/2026)

## Contexte

Les consignes valorisent un environnement d'exécution **managé** avec disponibilité et montée en charge
**démontrées**. Le poste du développeur a moins de 16 Go de RAM, et le dépôt est public (runners GitHub de
4 vCPU et 16 Go). La configuration cible (RabbitMQ à trois nœuds, HPA de 2 à 6 réplicas) ne tient pas sur le
poste.

## Décision

Kubernetes avec kustomize (base et overlays) :

| Profil | Où | Contenu |
|---|---|---|
| Quotidien | docker compose | Toute la pile, une instance de chaque (3 à 4 Go) |
| Démo | Minikube `--memory=6g --cpus=4 --cni=calico`, overlay `minikube` | RabbitMQ 1 nœud, catalogue 2 réplicas (HPA 2 à 3), controle 1, notification 1, Keycloak heap 512 Mo |
| Expérimentation | Minikube profil `sandbox`, overlay `sandbox` | Opérateur et RabbitMQ 3 nœuds, PerfTest, rien d'autre |
| Recette | **kind** en CI, overlay `ci` | Configuration **complète** : RabbitMQ 3 nœuds, 2 réplicas, HPA 2 à 6 |

La configuration cible est ainsi **prouvée par la CI** à chaque exécution, et la démo locale en montre une
réduction assumée. Option pour la soutenance : un cluster managé européen loué pour la journée (overlay `cloud`).
Calico comme CNI pour que les NetworkPolicies aient un effet ; Pod Security `enforce: baseline`, `warn: restricted`.

## Alternatives écartées

| Option | Raison |
|---|---|
| docker compose seul | Pas de HPA, PDB, NetworkPolicies, pas de disponibilité démontrable |
| Minikube complet en local | Dépasse la mémoire du poste |
| Cluster managé permanent | Coût ; réservé à un essai d'une journée |
| k3s / microk8s | Moins courants pour le jury ; kind et Minikube couvrent les deux besoins (CI et démo) |

## Conséquences

- Plusieurs overlays à maintenir ; `kubeconform` les valide tous en CI.
- Démonstration : pendant JMeter l'HPA ajoute des réplicas ; on supprime un pod catalogue en direct (le service
  reste disponible grâce à deux réplicas et au PDB). La perte d'un nœud RabbitMQ se montre par l'expérimentation
  et par la recette CI.
- Sous Windows, le pilote Docker de Minikube impose `minikube tunnel` et des hôtes vers 127.0.0.1.
