# ADR 0002 — Java 21 et Spring Boot 4

- Statut : accepté (30/09/2026)

## Contexte

Le projet avait d'abord été conçu en C++ (Drogon). L'équipe cible (un lead et deux développeurs
confirmés, recrutements possibles) et les exigences de sécurité (transactions financières) plaident pour
un écosystème web mûr, à mémoire sûre, facile à recruter et outillé pour la qualité (JaCoCo, PIT,
SpotBugs, ArchUnit, Testcontainers).

## Décision

**Java 21 (Temurin, LTS)** avec les threads virtuels, **Spring Boot 4.x** (4.1.1, dernière stable au
30/09/2026), Maven multi-modules avec wrapper. Pas de Lombok (code lisible par le jury), pas de MapStruct
(conversions explicites). Jackson 3 (`tools.jackson.*`), Spring Security 7, Testcontainers 2.x.

## Alternatives écartées

| Option | Raison |
|---|---|
| **C++ / Drogon** | Écosystème web et sécurité moins fournis (OAuth2 resource server, validation, observabilité à écrire), client AMQP sans reconnexion automatique, recrutement plus difficile, et la sécurité mémoire est un argument pour la JVM ; les outils d'analyse (ASan/UBSan) deviennent sans objet |
| **Node.js / TypeScript** | Bon pour le front, mais typage et outillage qualité (mutation, architecture vérifiable, tests d'intégration) moins solides pour le cœur transactionnel ; équipe déjà orientée JVM |
| Spring Boot 3.5 | N'est plus couvert par le support open source : ne pas revenir en 3.x, ni rétrograder une version pour faire compiler un exemple |

## Conséquences

- Jackson 3, tests et starters modularisés : les exemples de code omettent les imports, la documentation
  de la version fait foi.
- `-Xlint:all -Werror` au compilateur ; versions figées, Maven Enforcer, SBOM CycloneDX.
- Image distroless `java21-debian12:nonroot`, multi-architecture (amd64 et arm64).
- Threads virtuels : la contention se déplace vers le pool de connexions (Hikari, 10 connexions) ; à mesurer
  pendant la charge (phase 3).
