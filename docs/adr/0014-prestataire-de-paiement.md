# ADR 0014 — Prestataire de paiement (HORS POC, cadrage V2)

- Statut : **reporté**. Le paiement est sorti du périmètre du POC le 30/09/2026 (ADR 0015) ; ce cadrage est
  conservé pour la V2 et n'est pas implémenté.

## Contexte

Le sujet impose le paiement par carte uniquement, par la plateforme, avec une commission de 5 % et
l'interdiction d'échanger des coordonnées. Le paiement doit être **retenu jusqu'à la réception** (la
« garantie » de Collector). Les transactions financières placent Collector dans le périmètre PCI-DSS, sauf si
aucune donnée de carte ne transite par ses services.

## Décision (cadrage pour la V2)

- **Un PSP certifié PCI-DSS**, en **mode test** pour toute démonstration : cartes de test du prestataire,
  aucune vraie carte, aucun argent réel. **Aucune donnée de carte ne transite par nos services** (champs
  hébergés ou redirection du PSP).
- Critères de choix tirés du besoin : paiement **retenu jusqu'à la réception**, **commission de 5 %**
  prélevée à la source, **vérification d'identité des vendeurs** (KYC). Candidats : **Stripe Connect** ou
  **Mangopay** (spécialiste européen des marketplaces).
- Webhooks du PSP : **signature vérifiée**, idempotence par `provider_event_id` (table `payment_event`, déjà au
  schéma), puis événements par l'outbox (`payment.succeeded`, `payment.refunded`…).
- Statuts de commande déjà modélisés : `EN_ATTENTE_PAIEMENT`, `PAYEE`, `EXPEDIEE`, `RECUE` (versement au
  vendeur, commission déduite), `ANNULEE`, `REMBOURSEE` ; un article n'a qu'un achat actif (index unique).
- Un `payment-service` séparé isolera les secrets du PSP et la seule route exposée à Internet sans jeton
  utilisateur (le webhook).

## Alternatives écartées

| Option | Raison |
|---|---|
| Gérer les cartes nous-mêmes | Périmètre PCI-DSS complet, risque inacceptable |
| Virement ou paiement hors plateforme | Interdit par le sujet (commission, garantie) |
| Choisir le PSP maintenant | Hors périmètre du POC ; le choix dépend de la direction (frais, KYC, pays) |

## Conséquences

- Aucun code de paiement dans le dépôt du POC ; le schéma conserve les tables de commande.
- La commission de 5 % (retenue sur le montant versé au vendeur) reste **à confirmer avec la direction**.
- Le nouvel ADR de choix définitif devra être rédigé avant de coder US-015 à US-019.
