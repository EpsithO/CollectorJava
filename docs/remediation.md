# Plan de remédiation

Plan **priorisé par risque métier** et relié à des faiblesses identifiées dans le code actuel, à partir du
modèle de menaces ([`securite/stride.md`](securite/stride.md)) et de l'OWASP API Top 10.

> **Ce plan n'intègre aucun résultat de scan ni de charge : ils n'existent pas encore.** Les priorités
> ci-dessous sont fondées sur la lecture du code et de l'architecture. Quand les résultats de CodeQL, Semgrep,
> Trivy, ZAP et JMeter seront disponibles, chaque action doit être **confirmée, reclassée ou complétée** par
> une constatation mesurée, et la colonne « Preuve » renseignée.

## Bloquant (avant toute ouverture à de vrais utilisateurs)

| # | Action | Vulnérabilité visée | Justification | Preuve à produire |
|---|---|---|---|---|
| B1 | Rate limiting **par utilisateur** sur les écritures (création, photos, soumission, prix, suivi) | Déni de service ; abus de ressources (OWASP API4) | Traefik ne limite que par IP : un compte authentifié peut inonder le stockage et le bus | Test de charge avec un seul compte |
| B2 | **Antivirus** et contrôle du contenu réel des photos | Envoi d'un fichier malveillant déguisé en image (`Content-Type` déclaré, non vérifié) | Le HEAD contrôle type déclaré et taille, pas le contenu | Test avec un faux JPEG |
| B3 | **Contrôle de propriété centralisé** et test dédié | Accès à l'objet d'un autre (OWASP API1) | Aujourd'hui vérifié article par article (`Article.requireOwner`) ; un nouveau cas d'usage peut l'oublier | Test d'architecture ou de contrat qui échoue si un cas d'usage d'écriture ignore le propriétaire |
| B4 | **Dimensionnement du pool Hikari** (10 connexions) avec threads virtuels, délais et disjoncteur | Saturation : les threads virtuels déplacent la contention vers le pool | Risque de disponibilité propre à cette pile | Mesure pendant la charge : connexions actives, attente, p95 |
| B5 | **Assainir les champs libres** (titre, description, attributs) et échapper côté front | XSS stocké (OWASP A03) | Le JSON est servi tel quel ; `attributes` est un objet libre | ZAP actif, test d'injection |

## Court terme

| # | Action | Vulnérabilité visée | Justification |
|---|---|---|---|
| C1 | **Retirer le client `collector-tests`** (flux mot de passe) du realm de production | Élévation de privilège (flux mot de passe = identifiants saisis hors Keycloak) | Documenté « dev uniquement » mais présent dans le realm livré |
| C2 | **Keycloak en mode production** (base dédiée, TLS, nom d'hôte, sans `start-dev`) et révocation du refresh token | Usurpation ; jeton volé | Le compose lance `start-dev` |
| C3 | **Gestionnaire de secrets** (External Secrets / Vault) et rotation | Fuite de secrets (`.env`, Secrets Kubernetes en clair) | Aucun secret dans le dépôt, mais stockés en clair côté cluster |
| C4 | **Chiffrement au repos** : base, sauvegardes, stockage objet | Divulgation en cas de vol de disque ou de sauvegarde | Non implémenté |
| C5 | **Suppression des métadonnées EXIF** des photos | Divulgation de la position du vendeur (RGPD) | Aucun traitement d'image côté serveur |
| C6 | **Files `fraude.*`** : brancher l'anti-fraude ou les borner (`x-max-length`, TTL) | Déni de service par croissance sans borne du broker | Deux files sans consommateur dans le POC |
| C7 | **Un utilisateur RabbitMQ par service**, avec permissions d'écriture et de lecture limitées | Usurpation de producteur : un faux `article.checked` | Un seul compte partagé aujourd'hui |
| C8 | **Kyverno `verifyImages`** en admission | Déploiement d'une image non signée | La CI signe et vérifie, mais le cluster ne l'impose pas |
| C9 | Suppression de comptes et d'objets orphelins (photos jamais soumises) | Consommation de stockage, RGPD | Aucun nettoyage |

## Structurel

| # | Action | Justification |
|---|---|---|
| S1 | **Test d'intrusion externe** avant ouverture | Audit indépendant des flux financiers à venir |
| S2 | **Veille CVE sur le SBOM** (CycloneDX) en continu | Dépendances transitives nombreuses |
| S3 | **Paiement délégué à un PSP certifié** (hors POC) | Sortir du périmètre PCI-DSS le plus lourd |
| S4 | **WAF** devant Traefik | Défense en profondeur |
| S5 | **mTLS entre services** (maillage de services) | Authentifier les services entre eux et le bus |
| S6 | **Journalisation à valeur probante** | Répudiation : décisions de revue, modifications de prix |
| S7 | **Base par service pour `controle-service`** (modèle de lecture alimenté par les événements) | Supprimer le compromis de lecture de la vue du catalogue (ADR 0006) |
| S8 | Droit à l'effacement et à l'export (RGPD) | Voir [`securite/rgpd.md`](securite/rgpd.md) |

## À relier aux mesures quand elles existeront

| Source | Ce qu'elle doit alimenter |
|---|---|
| Trivy (fs, images), CodeQL, Semgrep, SpotBugs/FindSecBugs | Liste réelle des vulnérabilités critiques et hautes : reclasser B et C |
| OWASP ZAP (API et actif) | Confirmer B5, B3 ; découvrir les en-têtes ou erreurs manquants |
| JMeter (paliers 10 → 250, deux mesures : capacité et protection) | Confirmer B1 et B4 ; relever le palier de rupture et le facteur limitant (CPU, Hikari, files) |
| Expérimentation RabbitMQ (perte d'un nœud) | Confirmer la décision « files quorum + confirms obligatoires » |

## Actions déjà en place (bonnes pratiques du POC)

Image distroless non-root en lecture seule, `drop: [ALL]`, NetworkPolicies, jetons de 5 minutes, CSP stricte sur
l'API, ProblemDetail sans détail technique, un rôle PostgreSQL par service, schémas d'événements validés des
deux côtés, secrets sans valeur par défaut, signature cosign vérifiée avant déploiement.
