# RGPD

Traitements de données personnelles du POC et mesures en place. Ce document est un **point de départ** à
faire valider par le délégué à la protection des données : il ne remplace ni le registre des traitements
définitif ni une analyse d'impact.

## 1. Données traitées

| Donnée | Où | Finalité | Base légale | Durée |
|---|---|---|---|---|
| Identifiant (`sub` Keycloak), nom d'affichage | Keycloak ; `app_user` (catalogue) | Authentification, affichage du vendeur | Exécution du contrat | Durée du compte |
| **E-mail** | Keycloak ; `app_user.email` | Notifications par e-mail (V2) | Exécution du contrat, consentement pour les notifications | Durée du compte |
| Articles (titre, description, prix, attributs) | `article` | Mise en vente | Exécution du contrat | Durée de l'annonce, puis obligations légales |
| **Photos** | Stockage objet | Illustrer l'article | Exécution du contrat | Durée de l'annonce |
| Abonnements (`notification_follow`), notifications | notification-service | US-029 | Exécution du contrat | À définir (proposition : 12 mois) |
| Centres d'intérêt (`user_interest`, copie `notification_interest`) | catalogue ; notification-service | Recommandations et notifications (hors POC) | **Consentement** | Jusqu'au retrait |
| Adresse IP | Journaux d'accès (Traefik) | Sécurité | Intérêt légitime | 1 an au plus |

Aucune donnée de carte bancaire : le paiement est hors POC et sera délégué à un prestataire certifié.

## 2. Mesures en place (dans le code)

- **Minimisation** : l'e-mail est conservé pour les seules notifications ; il **n'est jamais exposé par l'API**
  (aucune réponse ne le contient) ni **journalisé** ; seul `display_name` est public.
- **Pseudonymisation entre services** : les événements portent `member_id` = `sub` Keycloak, jamais l'e-mail ni le nom.
- **Journaux** : ni jeton, ni e-mail, ni corps de requête ; format ECS structuré ; pas de valeur saisie dans les erreurs 400.
- **Cloisonnement** : un rôle PostgreSQL par service, limité à ses tables ; le notification-service a sa propre
  copie des données dont il a besoin.
- **Contrôle d'accès** : un membre ne voit que ses notifications (`member_id` dans chaque requête SQL) ;
  un article non publié est invisible d'un tiers (404).
- **Stockage des photos** : bucket privé, URL pré-signées à durée courte.
- **Hébergement dans l'UE** (cible : stockage objet européen, cluster européen).

## 3. Points ouverts

| Sujet | État | Action |
|---|---|---|
| **EXIF des photos** (position GPS du vendeur) | Non traité : pas de traitement d'image côté serveur | Supprimer les métadonnées à l'envoi ou à la validation (remédiation court terme) |
| **Droit à l'effacement** | Non implémenté | Anonymiser `app_user` (nom, e-mail), supprimer photos et abonnements ; les transactions restent pour les obligations légales |
| **Droit d'accès et portabilité** | Non implémenté | Export des données d'un membre |
| **Durées de conservation** | Proposées, non appliquées | Purge planifiée (notifications, abonnements, IP) |
| **Registre des traitements** | Ce document en tient lieu de brouillon | Registre formel |
| **Sous-traitants** (hébergeur, stockage, PSP, e-mail) | À contractualiser | Contrat de sous-traitance (DPA) avant ouverture |
| **Consentement aux notifications et aux publicités ciblées** | Non implémenté (hors POC) | Recueil et preuve du consentement |
| **Copie des centres d'intérêt** dans le notification-service | Effacement à propager | Consommer un futur événement d'effacement |
