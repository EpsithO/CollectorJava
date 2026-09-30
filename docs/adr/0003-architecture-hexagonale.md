# ADR 0003 — Architecture hexagonale (ports et adaptateurs), vérifiée par ArchUnit

- Statut : accepté (30/09/2026)

## Contexte

Le projet vient de changer de langage et de version majeure de framework. Trois remplacements
d'adaptateurs sont déjà prévus : outil anti-fraude interne ou acheté, stockage Garage local vers un stockage
objet cloud, statistiques lues en base partagée vers un modèle de lecture propre. La métrique de
maintenabilité exige un domaine testable vite et une architecture dont le respect se mesure.

## Décision

Chaque service suit l'architecture hexagonale, avec des règles pragmatiques :

1. Un hexagone **par fonctionnalité** (package-by-feature), pas un hexagone global.
2. `domain` : Java pur (records, règles, exceptions métier, événements). Aucun import Spring, JPA, Jackson,
   AMQP ni AWS.
3. `application` : cas d'usage = classes concrètes (le port entrant est la classe elle-même) ; ports sortants
   = interfaces dans `application/port`. Seules annotations Spring tolérées : `@Service`, `@Transactional`.
4. `adapter/in/*` (web, messaging) appellent les cas d'usage ; `adapter/out/*` (persistence, outbox, storage,
   messaging) implémentent les ports. Un adaptateur n'en appelle jamais un autre.
5. L'entité JPA vit dans `adapter/out/persistence`, avec conversions explicites.
6. L'autorisation (rôle, **propriété**) se décide dans le cas d'usage à partir d'une identité passée en
   paramètre (`Member`), pas dans le contrôleur. La conversion du jeton en `Member` vit dans l'adaptateur web
   (`Members.from(jwt)`).

Les règles sont **exécutées** : un `ArchitectureTest` par service (onion architecture, domaine sans
framework, application sans framework technique, pas de cycles entre fonctionnalités) fait échouer le build.

## Alternatives écartées

| Option | Raison |
|---|---|
| Couches classiques avec JPA dans le domaine | Rapide, mais domaine couplé au framework et non testable seul |
| Clean architecture stricte (un port entrant par cas d'usage) | Cérémonie sans gain pour 3 à 5 développeurs |

## Conséquences

- Plus de classes (entité JPA séparée du modèle, DTO, conversions) : risque de sur-ingénierie sur un CRUD
  simple, assumé.
- Tests rapides : règles du domaine et cas d'usage testés sans Spring ni base, avec des doublures écrites à la
  main pour les ports.
- Chaque adaptateur choisit sa technique (JPA pour `category` et `article`, `JdbcClient` pour les tables
  d'association) sans que le domaine le sache.
- Les trois remplacements prévus sont chacun un adaptateur à remplacer.
- Formation : atelier d'une journée « hexagonale et DDD » ; efficacité mesurée par les violations ArchUnit et le
  ratio de dette Sonar.
