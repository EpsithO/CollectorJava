# ADR 0013 — Construire une fois, promouvoir le digest signé

- Statut : accepté (30/09/2026)

## Contexte

Le schéma de CI/CD présenté doit être **celui qui s'exécute réellement**. Une chaîne où l'image est publiée
après les tests, ou reconstruite pour la production, ne prouve pas que ce qui a été testé est ce qui est
déployé ; la chaîne de confiance ne serait exercée que le jour de la mise en production.

## Décision

- L'image est **construite une fois**, scannée (Trivy, 0 critique pour chaque architecture publiée), **signée**
  (cosign sans clé, identité OIDC du workflow) et **poussée** sur GHCR à l'étape 5 du pipeline, étiquetée par le
  **SHA** du commit (immuable ; jamais `latest` dans les manifests).
- Publiée sans signature puis signée seulement après les deux scans (amd64 et arm64) : une image non signée
  n'est jamais déployée.
- La **recette** (kind) exécute `cosign verify` (seule une image signée par ce workflow de ce dépôt est
  acceptée), résout le **digest** et déploie `dépôt@sha256:…`.
- La **production** (workflow de tag `vX.Y.Z`, environnement GitHub `production` avec approbation manuelle)
  vérifie à nouveau la signature puis **promeut le même digest** en étiquette SemVer, sans reconstruire.
- Build multi-architecture (`linux/amd64,linux/arm64`) pour que le Mac Apple Silicon n'émule pas ; l'étape
  Maven tourne une seule fois en natif (`--platform=$BUILDPLATFORM`).
- **Retour arrière** : redéployer le digest signé précédent (`kubectl rollout undo`), rendu possible par les
  migrations rétrocompatibles (ADR 0010).

## Alternatives écartées

| Option | Raison |
|---|---|
| Reconstruire l'image par environnement | Ce qui part en production n'est pas ce qui a été testé |
| Étiquette mutable (`latest`, `main`) | Non reproductible, pas de retour arrière fiable |
| Signature par clé longue durée | Gestion de secret de plus ; la signature sans clé s'appuie sur l'identité du workflow |
| Pas de vérification au déploiement | La signature ne protégerait rien |

## Conséquences

- La chaîne de confiance est testée à chaque exécution, pas seulement en production.
- Une PR venant d'un fork n'a pas le droit de publier : elle s'arrête à l'étape d'analyse.
- Option Kyverno `verifyImages` côté cluster en remédiation court terme (admission).
- Déploiement sur un cluster réel seulement si `KUBE_CONFIG` est configuré ; sinon promotion seule (non
  pénalisant selon le sujet).
