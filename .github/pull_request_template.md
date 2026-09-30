## Ce que fait cette PR

<!-- Une ou deux phrases : quelle user story (US-014, US-033, US-029…), quel critère d'acceptation. -->

## Checklist

- [ ] Contrat à jour (`docs/api/openapi.yaml`, `docs/events.md`, schéma JSON)
- [ ] Scénarios Gherkin écrits **avant** le code, verts maintenant
- [ ] Aucune dépendance Spring/JPA dans `domain` (ArchUnit vert)
- [ ] Une migration **nouvelle** (jamais une migration existante modifiée)
- [ ] Erreurs métier = `DomainException` avec un `code` documenté
- [ ] Pas de donnée personnelle dans les logs
- [ ] `mvnw verify` vert en local, pipeline vert
- [ ] Couverture du nouveau code ≥ 80 % (SonarCloud)

## Si la PR touche la sécurité, l'architecture ou l'exploitation

- [ ] ADR rédigé (`docs/adr/`) pour toute décision structurante
- [ ] Écart avec les slides de soutenance signalé
- [ ] Aucun secret dans le dépôt ni dans une image
