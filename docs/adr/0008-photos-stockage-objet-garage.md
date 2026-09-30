# ADR 0008 — Photos en stockage objet, URL pré-signées, Garage

- Statut : accepté (30/09/2026)

## Contexte

Un article doit avoir au moins une photo (CA-1, CA-6), jusqu'à 8, de 5 Mo au plus. Faire transiter le binaire
par l'API l'exposerait à la saturation (mémoire, bande passante) et à des fichiers piégés. Il faut un
stockage compatible avec un cloud européen (Scaleway, OVHcloud) pour le remplacement prévu.

## Décision

- Stockage **S3-compatible**, accédé par le SDK AWS v2 derrière le port `PhotoStorage` (adaptateur
  `S3PhotoStorage`) : aucune dépendance à un produit dans le code.
- Local et Minikube : **Garage v2** (`dxflrs/garage`), maintenu par l'association française Deuxfleurs, léger,
  URL pré-signées et CORS gérés. Cible : stockage objet européen.
- Bucket **privé** `collector-photos`, **clé d'accès dédiée** au catalogue (lecture et écriture sur ce bucket
  seulement ; Garage gère les droits par bucket et non par préfixe). Aucune liste publique.
- **L'API ne manipule jamais le binaire** : elle délivre une URL pré-signée `PUT` (5 min, `Content-Type` signé) ;
  le navigateur envoie directement au stockage. Clé d'objet choisie par le serveur :
  `articles/{article_id}/{photo_id}`.
- À la soumission, `HEAD` de chaque photo : existe, type `image/jpeg|png|webp`, taille ≤ 5 Mo ; sinon refusée
  (`invalid_photo`) et effacée. Lecture par URL pré-signée `GET` de 10 min, pour un article visible seulement.
- **Piège traité** : l'URL signée contient l'hôte. Deux clients S3 : un sur l'adresse **interne** (HEAD,
  suppression), un *presigner* sur l'adresse **publique** (`http://localhost:3900`, `https://s3.collector.local`).
- CORS du stockage limité à l'origine du front. Initialisation idempotente par l'API d'administration de Garage
  (`infra/storage/init.sh`), la même pour compose, les tests et le Job Kubernetes.

## Alternatives écartées

| Option | Raison |
|---|---|
| **MinIO** | Plus d'image communautaire gratuite depuis octobre 2025, dépôt archivé en février 2026, images retirées de Docker Hub en septembre 2026 (règle : pas de logiciel en fin de vie) |
| Binaire en base (`bytea`) | Gonfle la base et les sauvegardes, charge l'API |
| Envoi du binaire par l'API | Saturation, exposition aux fichiers piégés |
| Système de fichiers partagé | Non scalable, pas d'équivalent cloud |

## Conséquences

- Le navigateur parle directement au stockage : un hôte public supplémentaire (`s3.collector.local`), TLS et CORS
  à configurer.
- Pas de traitement d'image côté serveur en V1 : suppression des métadonnées EXIF (position GPS du vendeur) et
  analyse antivirus sont en remédiation.
- Les URL pré-signées expirent : le front redemande une fiche pour rafraîchir les liens.
- Limite connue de Garage : droits par bucket, donc un bucket dédié aux photos.
