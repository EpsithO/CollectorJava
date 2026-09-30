# ADR 0005 — Outbox transactionnelle, relais par bail, aucun ordre garanti

- Statut : accepté (30/09/2026)

## Contexte

Écrire une donnée **et** publier un événement ne peut pas être atomique entre PostgreSQL et RabbitMQ
(double écriture). Si le broker est arrêté, l'API doit tout de même répondre : l'événement partira à son retour.
Une première version du relais (verrouiller 100 événements, puis attendre jusqu'à 5 s par confirmation dans
la **même transaction**) aurait gardé une connexion du pool Hikari (10 connexions) et ses verrous pendant
plusieurs minutes dès que le broker ralentit : la contention que la phase 3 doit analyser, créée par notre
propre code.

## Décision

1. **Outbox** : le cas d'usage publie par le port `DomainEventPublisher`, implémenté par l'écriture d'une ligne
   `outbox_event` dans **la même transaction** que la donnée (`Propagation.MANDATORY` : refuse d'écrire hors
   transaction métier). Jamais de `RabbitTemplate` hors des adaptateurs.
2. **Conformité au contrat** : l'enveloppe est validée contre le schéma JSON avant l'écriture ; un événement
   non conforme annule aussi la donnée métier.
3. **Relais en trois temps courts, sans transaction pendant l'envoi** :
   réserver par **bail** (`UPDATE … SET claimed_until = now() + lease … FOR UPDATE SKIP LOCKED … RETURNING`,
   une seule instruction), envoyer avec publication confirmée (un délai global pour tout le lot), marquer
   publié. Le bail (30 s) est supérieur au délai de confirmation (5 s) ; si le pod meurt, le bail expire et un
   autre réplica reprend le lot.
4. **Livraison au moins une fois** : un événement confirmé mais pas encore marqué (pod tué entre les deux) est
   republié. Les consommateurs sont idempotents.
5. **Aucun ordre garanti** : plusieurs réplicas relaient en parallèle, un lot non confirmé est repris plus
   tard, un message peut être redélivré. Chaque événement qui décrit l'état d'un agrégat porte
   `aggregate_version` (colonne `@Version` de l'article, incrémentée aussi par le verdict SQL) ; un
   consommateur qui a besoin du dernier état ignore toute version inférieure ou égale à la dernière vue.
   Exemple : deux `price.changed` du même article reçus inversés, le notificateur n'annonce pas l'ancien prix
   comme le nouveau.

## Alternatives écartées

| Option | Raison |
|---|---|
| Publier directement après le commit | Perte de l'événement si le broker ou le pod tombe entre les deux |
| Transaction distribuée (XA) | Lourde, peu supportée par les brokers modernes |
| Relais qui verrouille le lot pendant l'attente des confirmations | Saturation du pool de connexions quand le broker ralentit (voir contexte) |
| Ordre garanti (un seul relais, file unique par agrégat) | Supprime la mise à l'échelle du relais ; `aggregate_version` donne le même résultat utile à moindre coût |
| Change Data Capture (Debezium) | Un composant de plus, disproportionné au volume |

## Conséquences

- L'API reste disponible broker arrêté ; `collector_outbox_pending` (jauge) alerte si le relais ne suit plus
  (plus de 100 pendant 5 minutes).
- Publication `mandatory` : un événement sans file liée reste dans l'outbox et est renvoyé en boucle ;
  chaque type d'événement doit avoir une file (`docs/events.md`).
- Index partiel `WHERE published_at IS NULL` : petit quelle que soit la taille de l'historique.
- Les consommateurs de l'état (notification) doivent implémenter la comparaison de version ; c'est testé
  (événements inversés, rejoués).
