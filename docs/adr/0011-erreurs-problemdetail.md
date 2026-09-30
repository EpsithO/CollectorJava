# ADR 0011 — Erreurs au format ProblemDetail (RFC 9457) avec un code stable

- Statut : accepté (30/09/2026)

## Contexte

Les clients (front, tests d'acceptation) doivent réagir aux erreurs sans analyser des messages, et aucun détail
technique (SQL, pile, chemin de classe) ne doit fuiter : la sécurité est l'exigence de premier plan.

## Décision

- Toutes les erreurs sont `application/problem+json` (RFC 9457) avec une propriété **`code` stable** : le message
  humain peut changer, le code non.
- Codes : `invalid_request` (400), `unauthorized` (401), `forbidden` (403), `not_found` (404), `invalid_status`
  et `too_many_photos` (409), `contact_info_forbidden`, `photo_required`, `invalid_photo`, `unknown_category`,
  `too_many_interests` (422), `internal_error` (500).
- Le domaine exprime la **nature** de l'erreur (`DomainException.Kind` : entrée invalide, introuvable, interdit,
  conflit, règle violée) et un code ; c'est l'adaptateur web (`ApiExceptionHandler`) qui le traduit en statut
  HTTP. Le domaine reste utilisable hors HTTP (consommateur RabbitMQ, batch).
- Une requête invalide liste les **champs** en erreur, jamais les valeurs saisies. JSON illisible, champ
  inconnu, identifiant mal formé : même `invalid_request`, sans détail de désérialisation.
- `server.error.include-stacktrace: never`, `include-message: never` ; le détail part dans les journaux JSON.
- Un article d'un autre ou non publié : **404** plutôt que 403 quand l'existence ne doit pas être révélée.

## Alternatives écartées

| Option | Raison |
|---|---|
| Format d'erreur maison | Non standard, à réexpliquer à chaque client |
| Messages d'exception Spring par défaut | Fuite de détails techniques |
| Code HTTP seul | Trop pauvre : plusieurs 409 ou 422 distincts |

## Conséquences

- Piège : un handler `Exception` générique avale `AccessDeniedException` (403 deviendrait 500) ; un handler
  dédié est obligatoire (testé).
- Une exception levée dans un filtre passe par `/error` : la chaîne générale autorise le dispatch `ERROR` pour
  éviter un 403 vide.
- Les tests vérifient le code de chaque erreur, et qu'un échec inattendu reste `internal_error` sans détail.
