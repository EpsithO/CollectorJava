# Exemple guidé — US-021 « Paramétrer ses centres d'intérêt »

Ce guide montre **toute la démarche** pour livrer une user story dans ce projet,
du backlog à la pull request, avec le code complet de chaque couche de
l'architecture hexagonale (CLAUDE.md §C3).

Il porte volontairement sur une **autre** story que US-014, pour que US-014 reste
ton code. Recommandation : **implémente US-021 toi-même en suivant ce guide**
(semaine 3), compare avec le code donné ici, puis applique la même démarche à
US-014 (tableau de transposition en fin de document).

Pourquoi cette story : elle vient du sujet (« l'acheteur authentifié peut, depuis
son profil, paramétrer ses centres d'intérêt »), elle est petite, et elle
traverse **tout** : contrat, règle métier pure, cas d'usage, deux adaptateurs,
sécurité, événement par l'outbox, cinq niveaux de tests.

---

## 1. Backlog

> **US-021** — En tant qu'**acheteur authentifié**, je veux **choisir mes centres
> d'intérêt** parmi les catégories, afin de recevoir des **recommandations** et
> des **notifications** pertinentes.

| CA | Critère d'acceptation |
|---|---|
| CA-1 | Un acheteur choisit des catégories existantes → 200, sa liste est **remplacée**, `interests.updated` est publié |
| CA-2 | Un acheteur consulte ses centres d'intérêt → liste triée par libellé (vide s'il n'en a jamais choisi) |
| CA-3 | Une catégorie inconnue dans la liste → 422 `unknown_category`, **rien n'est modifié** |
| CA-4 | Plus de 10 catégories → 422 `too_many_interests` |
| CA-5 | Sans jeton → 401 ; utilisateur sans le rôle `acheteur` → 403 |
| CA-6 | Le même choix envoyé deux fois → 200, **aucun nouvel événement** |

**Règles métier** : au plus **10** centres d'intérêt ; les doublons sont ignorés ;
une liste vide retire tous les centres d'intérêt ; seules les catégories
existantes sont acceptées.

**Pourquoi un événement ?** Le futur service de notification doit savoir qui
s'intéresse à quoi pour prévenir de la mise en ligne d'un article (exigence du
sujet). Avec une base par service, il ne lira pas notre table : il maintiendra
sa copie à partir de `interests.updated`. C'est la même mécanique que US-014.

**Où vit cette fonctionnalité ?** Dans `catalogue-service`, hexagone
`interest`. Dans l'architecture cible, elle rejoindra le service de
recommandation : grâce à l'hexagonale, on déplacera le domaine et les cas
d'usage tels quels, seuls les adaptateurs changeront.

---

## 2. Ordre de travail

| # | Étape | Pourquoi dans cet ordre |
|---|---|---|
| 1 | Contrat : `openapi.yaml`, `events.md`, schéma JSON | Le front, les tests et le code s'alignent sur la même référence |
| 2 | Scénarios Gherkin (ils échouent) | La spécification devient un test avant d'écrire le code |
| 3 | Migration SQL si besoin | Le schéma existe avant l'adaptateur |
| 4 | **Domaine** + tests unitaires | Les règles, sans aucune dépendance : rapide à écrire et à tester |
| 5 | **Cas d'usage** + tests avec doublures | L'orchestration, toujours sans base ni Spring |
| 6 | **Adaptateur de persistance** + test d'intégration | La base réelle (Testcontainers) |
| 7 | **Adaptateur web** + test de tranche web | Le contrat HTTP et la sécurité |
| 8 | Topologie RabbitMQ (si nouvel événement) | Sinon le message est renvoyé (`mandatory`) |
| 9 | Tout faire passer : `mvnw verify`, acceptation | Les scénarios de l'étape 2 passent au vert |
| 10 | Pull request (checklist §9) | Le pipeline décide, la revue complète |

---

## 3. Contrat

### 3.1 API (`docs/api/openapi.yaml`)

```yaml
  /me/interests:
    get:
      tags: [acheteur]
      summary: Mes centres d'intérêt (US-021, CA-2)
      security: [{ bearerAuth: [] }]
      responses:
        "200":
          description: Centres d'intérêt, triés par libellé
          content:
            application/json:
              schema:
                type: array
                items: { $ref: "#/components/schemas/Interest" }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
    put:
      tags: [acheteur]
      summary: Remplacer mes centres d'intérêt (US-021, CA-1, CA-3, CA-4, CA-6)
      description: |
        Remplacement complet (idempotent) : la liste envoyée devient la liste
        enregistrée. Doublons ignorés, 10 catégories au plus, liste vide autorisée.
      security: [{ bearerAuth: [] }]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [category_ids]
              additionalProperties: false
              properties:
                category_ids:
                  type: array
                  maxItems: 100            # garde-fou technique ; la règle métier (10) est dans le domaine
                  items: { type: string, format: uuid }
      responses:
        "200":
          description: Centres d'intérêt enregistrés
          content:
            application/json:
              schema:
                type: array
                items: { $ref: "#/components/schemas/Interest" }
        "400": { $ref: "#/components/responses/InvalidRequest" }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "422":
          description: "`unknown_category` ou `too_many_interests`"
          content:
            application/problem+json:
              schema: { $ref: "#/components/schemas/Problem" }

# components.schemas
    Interest:
      type: object
      required: [category_id, slug, label]
      properties:
        category_id: { type: string, format: uuid }
        slug: { type: string, example: sneakers }
        label: { type: string, example: Baskets en édition limitée }
```

### 3.2 Événement (`docs/events.md` + schéma)

| Événement | Producteur | `data` | Files (consommateur) |
|---|---|---|---|
| `interests.updated` | catalogue (outbox) | `member_id, category_ids` | `notification.interests-updated` (futur service de notification) |

`libs/collector-messaging/src/main/resources/schemas/interests.updated.v1.json` :

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "interests.updated v1",
  "type": "object",
  "required": ["event_id", "type", "version", "occurred_at", "data"],
  "properties": {
    "event_id": { "type": "string", "format": "uuid" },
    "type": { "const": "interests.updated" },
    "version": { "const": 1 },
    "occurred_at": { "type": "string", "format": "date-time" },
    "data": {
      "type": "object",
      "required": ["member_id", "category_ids"],
      "additionalProperties": false,
      "properties": {
        "member_id": { "type": "string", "format": "uuid" },
        "category_ids": {
          "type": "array",
          "maxItems": 10,
          "uniqueItems": true,
          "items": { "type": "string", "format": "uuid" }
        }
      }
    }
  }
}
```

---

## 4. Spécification exécutable (étape 2)

`acceptance-tests/src/test/resources/features/us021_centres_interet.feature` :

```gherkin
# language: fr
Fonctionnalité: US-021 Paramétrer ses centres d'intérêt
  En tant qu'acheteur authentifié,
  je veux choisir mes centres d'intérêt parmi les catégories,
  afin de recevoir des recommandations et des notifications pertinentes.

  Contexte:
    Étant donné l'utilisateur authentifié "acheteur1"
    Et il choisit les centres d'intérêt ""

  Scénario: CA-1 et CA-2 Choisir puis consulter ses centres d'intérêt
    Quand il choisit les centres d'intérêt "sneakers, figurines"
    Alors le code de réponse est 200
    Et un événement "interests.updated" est publié sous 5 secondes
    Et ses centres d'intérêt sont "Baskets en édition limitée, Figurines"

  Scénario: CA-3 Une catégorie inconnue ne modifie rien
    Étant donné il choisit les centres d'intérêt "posters"
    Quand il choisit les centres d'intérêt "sneakers" et une catégorie inexistante
    Alors le code de réponse est 422
    Et le champ "code" vaut "unknown_category"
    Et ses centres d'intérêt sont "Posters dédicacés"

  Scénario: CA-4 Au plus 10 centres d'intérêt
    Quand il choisit 11 centres d'intérêt
    Alors le code de réponse est 422
    Et le champ "code" vaut "too_many_interests"

  Scénario: CA-5 Sans jeton
    Étant donné un utilisateur non authentifié
    Quand il consulte ses centres d'intérêt
    Alors le code de réponse est 401

  Scénario: CA-5 Sans le rôle acheteur
    Étant donné l'utilisateur authentifié "admin"
    Quand il consulte ses centres d'intérêt
    Alors le code de réponse est 403

  Scénario: CA-6 Le même choix ne republie rien
    Étant donné il choisit les centres d'intérêt "bd"
    Quand il choisit les centres d'intérêt "bd"
    Alors le code de réponse est 200
    Et aucun événement "interests.updated" n'est publié sous 3 secondes
```

Le `Contexte` remet la liste à vide avant chaque scénario : les scénarios sont
indépendants les uns des autres et de l'ordre d'exécution. Les slugs viennent du
jeu de données (`db/seed`).

---

## 5. Migration (étape 3)

La table `user_interest` existe déjà (`V1__schema.sql`), mais le rôle
`catalogue_app` n'y a pas accès. **On ne modifie jamais une migration déjà
appliquée** (Flyway refuserait de démarrer : somme de contrôle différente) : on
en ajoute une.

`db/migration/V5__grants_user_interest.sql` :

```sql
-- US-021 : le catalogue remplace les centres d'intérêt d'un membre
-- (DELETE + INSERT dans la même transaction).
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'catalogue_app') THEN
        GRANT SELECT, INSERT, DELETE ON user_interest TO catalogue_app;
    END IF;
END
$$;
```

---

## 6. Code

```
com.collector.catalogue
├─ interest/
│  ├─ domain/          InterestSelection, InterestsUpdated, TooManyInterestsException, UnknownCategoryException
│  ├─ application/     UpdateInterests, GetInterests, InterestView
│  │  └─ port/         InterestRepository, CategoryCatalog
│  └─ adapter/
│     ├─ in/web/       InterestController, InterestsRequest, InterestResponse
│     └─ out/persistence/  InterestJdbcAdapter
└─ shared/
   ├─ domain/          DomainEvent, DomainException, Member
   ├─ application/port/ DomainEventPublisher, MemberDirectory
   ├─ adapter/in/web/  Members (Jwt -> Member), ApiExceptionHandler
   └─ adapter/out/persistence/ MemberJdbcAdapter
```

### 6.1 Éléments partagés (écrits une fois, réutilisés par US-014)

```java
// shared/domain/DomainException.java
// Le domaine exprime la NATURE de l'erreur, pas un code HTTP : c'est l'adaptateur
// web qui traduit (ApiExceptionHandler). Le domaine reste utilisable hors HTTP
// (consommateur RabbitMQ, batch…).
public abstract class DomainException extends RuntimeException {

    public enum Kind { NOT_FOUND, FORBIDDEN, CONFLICT, RULE_VIOLATED }

    private final Kind kind;
    private final String code;

    protected DomainException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() { return kind; }
    public String code() { return code; }
}
```

```java
// shared/domain/Member.java — l'utilisateur connecté, tel que le métier le voit
public record Member(String subject, String displayName, String email) {

    public Member {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
    }
}
```

```java
// shared/application/port/MemberDirectory.java
public interface MemberDirectory {

    /** Identifiant interne du membre, créé à sa première action d'écriture. */
    UUID ensureMember(Member member);

    /** Lecture seule : ne crée rien (une requête GET ne doit pas écrire). */
    Optional<UUID> findId(String subject);
}
```

```java
// shared/adapter/in/web/Members.java — le jeton (technique) devient un Member (métier)
public final class Members {

    private Members() {
    }

    public static Member from(Jwt jwt) {
        String name = Optional.ofNullable(jwt.getClaimAsString("name"))
                .or(() -> Optional.ofNullable(jwt.getClaimAsString("preferred_username")))
                .orElse("Utilisateur");
        // app_user.email est obligatoire et unique : adresse technique si le jeton n'en porte pas.
        String email = Optional.ofNullable(jwt.getClaimAsString("email"))
                .orElse(jwt.getSubject() + "@users.collector.local");
        return new Member(jwt.getSubject(), name, email);
    }
}
```

```java
// shared/adapter/out/persistence/MemberJdbcAdapter.java
@Component
class MemberJdbcAdapter implements MemberDirectory {

    private final JdbcClient jdbc;

    MemberJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Upsert : pas de course entre « chercher » et « créer ». Effet utile : la ligne
    // du membre reste verrouillée jusqu'au commit, ce qui sérialise deux
    // modifications simultanées pour un même membre.
    @Override
    public UUID ensureMember(Member member) {
        return jdbc.sql("""
                    INSERT INTO app_user (keycloak_sub, display_name, email)
                    VALUES (:sub, :name, :email)
                    ON CONFLICT (keycloak_sub) DO UPDATE SET display_name = EXCLUDED.display_name
                    RETURNING id
                    """)
                .param("sub", UUID.fromString(member.subject()))
                .param("name", member.displayName())
                .param("email", member.email())
                .query(UUID.class)
                .single();
    }

    @Override
    public Optional<UUID> findId(String subject) {
        return jdbc.sql("SELECT id FROM app_user WHERE keycloak_sub = :sub")
                .param("sub", UUID.fromString(subject))
                .query(UUID.class)
                .optional();
    }
}
```

Traduction des erreurs du domaine, à ajouter dans `ApiExceptionHandler`
(CLAUDE.md G5) :

```java
private static HttpStatus statusOf(DomainException e) {
    return switch (e.kind()) {
        case NOT_FOUND -> HttpStatus.NOT_FOUND;
        case FORBIDDEN -> HttpStatus.FORBIDDEN;
        case CONFLICT -> HttpStatus.CONFLICT;
        case RULE_VIOLATED -> HttpStatus.UNPROCESSABLE_ENTITY;
    };
}
```

### 6.2 Domaine (étape 4)

```java
// interest/domain/InterestSelection.java
/**
 * Centres d'intérêt d'un membre : un ensemble de catégories, sans doublon, borné.
 * Objet de valeur immuable : s'il existe, il est valide.
 */
public record InterestSelection(Set<UUID> categoryIds) {

    public static final int MAX_INTERESTS = 10;

    public InterestSelection {
        Objects.requireNonNull(categoryIds, "categoryIds");
        if (categoryIds.size() > MAX_INTERESTS) {
            throw new TooManyInterestsException(categoryIds.size());
        }
        categoryIds = Set.copyOf(categoryIds);   // copie immuable : personne ne la modifie après validation
    }

    /** Les doublons disparaissent ici : la règle « doublons ignorés » est portée par le type. */
    public static InterestSelection of(Collection<UUID> categoryIds) {
        return new InterestSelection(new HashSet<>(categoryIds));
    }

    public static InterestSelection empty() {
        return new InterestSelection(Set.of());
    }

    /** Comparaison d'ensembles : l'ordre d'envoi n'a pas d'importance (CA-6). */
    public boolean sameAs(InterestSelection other) {
        return categoryIds.equals(other.categoryIds);
    }
}
```

```java
// interest/domain/TooManyInterestsException.java
public class TooManyInterestsException extends DomainException {

    public TooManyInterestsException(int requested) {
        super(Kind.RULE_VIOLATED, "too_many_interests",
                "At most " + InterestSelection.MAX_INTERESTS + " interests, " + requested + " requested");
    }
}

// interest/domain/UnknownCategoryException.java
public class UnknownCategoryException extends DomainException {

    public UnknownCategoryException(Set<UUID> unknown) {
        super(Kind.RULE_VIOLATED, "unknown_category", "Unknown categories: " + unknown);
    }
}

// interest/domain/InterestsUpdated.java — événement du domaine
public record InterestsUpdated(UUID memberId, List<UUID> categoryIds) implements DomainEvent {

    @Override
    public String type() {
        return "interests.updated";
    }
}
```

```java
// test : interest/domain/InterestSelectionTest.java
class InterestSelectionTest {

    private static final UUID SNEAKERS = UUID.randomUUID();
    private static final UUID POSTERS = UUID.randomUUID();

    @Test
    void ignoresDuplicates() {
        assertThat(InterestSelection.of(List.of(SNEAKERS, SNEAKERS, POSTERS)).categoryIds())
                .containsExactlyInAnyOrder(SNEAKERS, POSTERS);
    }

    @Test
    void acceptsTenInterestsButNotEleven() {
        assertThatCode(() -> InterestSelection.of(randomIds(10))).doesNotThrowAnyException();
        assertThatThrownBy(() -> InterestSelection.of(randomIds(11)))
                .isInstanceOf(TooManyInterestsException.class)
                .extracting("code").isEqualTo("too_many_interests");
    }

    @Test
    void emptySelectionIsAllowed() {
        assertThat(InterestSelection.of(List.of()).categoryIds()).isEmpty();
    }

    @Test
    void comparisonIgnoresOrder() {
        assertThat(InterestSelection.of(List.of(SNEAKERS, POSTERS))
                .sameAs(InterestSelection.of(List.of(POSTERS, SNEAKERS)))).isTrue();
    }

    @Test
    void isImmutable() {
        var ids = new HashSet<>(Set.of(SNEAKERS));
        var selection = new InterestSelection(ids);
        ids.add(POSTERS);                                         // modifier l'original…
        assertThat(selection.categoryIds()).containsExactly(SNEAKERS);   // …ne change pas la sélection
    }

    private static List<UUID> randomIds(int count) {
        return Stream.generate(UUID::randomUUID).limit(count).toList();
    }
}
```

### 6.3 Cas d'usage (étape 5)

```java
// interest/application/port/InterestRepository.java
public interface InterestRepository {
    InterestSelection findFor(UUID memberId);
    void replace(UUID memberId, InterestSelection selection);
    List<InterestView> detailedFor(UUID memberId);        // lecture pour l'affichage, triée par libellé
}

// interest/application/port/CategoryCatalog.java
// Port propre à cette fonctionnalité (et non le port de `category`) : les
// hexagones restent indépendants, ArchUnit interdit les cycles entre eux.
public interface CategoryCatalog {
    /** Parmi les identifiants donnés, ceux qui existent. */
    Set<UUID> existing(Set<UUID> categoryIds);
}

// interest/application/InterestView.java — modèle de lecture : ce que l'écran affiche
public record InterestView(UUID categoryId, String slug, String label) {}
```

```java
// interest/application/UpdateInterests.java
@Service
public class UpdateInterests {

    private final MemberDirectory members;
    private final CategoryCatalog categories;
    private final InterestRepository interests;
    private final DomainEventPublisher events;

    public UpdateInterests(MemberDirectory members, CategoryCatalog categories,
                           InterestRepository interests, DomainEventPublisher events) {
        this.members = members;
        this.categories = categories;
        this.interests = interests;
        this.events = events;
    }

    // Une transaction : remplacement de la liste ET écriture de l'événement dans
    // l'outbox réussissent ensemble ou échouent ensemble.
    @Transactional
    public void execute(Member member, InterestSelection requested) {
        Set<UUID> unknown = new HashSet<>(requested.categoryIds());
        unknown.removeAll(categories.existing(requested.categoryIds()));
        if (!unknown.isEmpty()) {
            throw new UnknownCategoryException(unknown);          // CA-3 : on refuse avant toute écriture
        }

        UUID memberId = members.ensureMember(member);
        if (interests.findFor(memberId).sameAs(requested)) {
            return;                                               // CA-6 : rien ne change, pas d'événement
        }

        interests.replace(memberId, requested);
        events.publish(new InterestsUpdated(memberId, List.copyOf(requested.categoryIds())));
    }
}
```

```java
// interest/application/GetInterests.java
@Service
public class GetInterests {

    private final MemberDirectory members;
    private final InterestRepository interests;

    public GetInterests(MemberDirectory members, InterestRepository interests) {
        this.members = members;
        this.interests = interests;
    }

    // Un membre qui n'a jamais rien écrit n'a pas de ligne app_user : liste vide,
    // et surtout aucune création sur une lecture (CA-2).
    @Transactional(readOnly = true)
    public List<InterestView> execute(Member member) {
        return members.findId(member.subject())
                .map(interests::detailedFor)
                .orElse(List.of());
    }
}
```

Test du cas d'usage **avec des doublures écrites à la main** : pas de Mockito, pas
de Spring, et les doublures documentent ce qu'on attend de chaque port.

```java
// test : interest/application/UpdateInterestsTest.java
class UpdateInterestsTest {

    private static final UUID SNEAKERS = UUID.randomUUID();
    private static final UUID POSTERS = UUID.randomUUID();
    private static final Member BUYER = new Member(UUID.randomUUID().toString(), "Acheteur", "a@test.local");

    private final InMemoryInterests interests = new InMemoryInterests();
    private final List<DomainEvent> published = new ArrayList<>();
    private final UpdateInterests updateInterests = new UpdateInterests(
            new FixedMember(), ids -> Set.of(SNEAKERS, POSTERS), interests, published::add);

    @Test
    void replacesSelectionAndPublishesEvent() {                   // CA-1
        updateInterests.execute(BUYER, InterestSelection.of(List.of(SNEAKERS)));

        assertThat(interests.stored.categoryIds()).containsExactly(SNEAKERS);
        assertThat(published).singleElement()
                .isInstanceOfSatisfying(InterestsUpdated.class,
                        e -> assertThat(e.categoryIds()).containsExactly(SNEAKERS));
    }

    @Test
    void rejectsUnknownCategoryWithoutWriting() {                // CA-3
        var unknown = UUID.randomUUID();

        assertThatThrownBy(() -> updateInterests.execute(BUYER, InterestSelection.of(List.of(SNEAKERS, unknown))))
                .isInstanceOf(UnknownCategoryException.class);
        assertThat(interests.stored.categoryIds()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void sameSelectionPublishesNothing() {                       // CA-6
        updateInterests.execute(BUYER, InterestSelection.of(List.of(SNEAKERS, POSTERS)));
        published.clear();

        updateInterests.execute(BUYER, InterestSelection.of(List.of(POSTERS, SNEAKERS)));

        assertThat(published).isEmpty();
    }

    // --- doublures -------------------------------------------------------

    private static final class InMemoryInterests implements InterestRepository {
        InterestSelection stored = InterestSelection.empty();

        @Override public InterestSelection findFor(UUID memberId) { return stored; }
        @Override public void replace(UUID memberId, InterestSelection selection) { stored = selection; }
        @Override public List<InterestView> detailedFor(UUID memberId) { return List.of(); }
    }

    private static final class FixedMember implements MemberDirectory {
        private final UUID id = UUID.randomUUID();

        @Override public UUID ensureMember(Member member) { return id; }
        @Override public Optional<UUID> findId(String subject) { return Optional.of(id); }
    }
}
```

`CategoryCatalog` et `DomainEventPublisher` ont une seule méthode : une lambda
(`ids -> Set.of(…)`) ou une référence de méthode (`published::add`) suffit.

### 6.4 Adaptateur de persistance (étape 6)

Ici `JdbcClient` plutôt que JPA : une table d'association sans comportement ne
mérite pas d'entité. L'hexagonale le permet — chaque adaptateur choisit sa
technique, le domaine n'en sait rien.

```java
// interest/adapter/out/persistence/InterestJdbcAdapter.java
@Component
class InterestJdbcAdapter implements InterestRepository, CategoryCatalog {

    private final JdbcClient jdbc;

    InterestJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> existing(Set<UUID> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Set.of();                    // « IN () » est une erreur de syntaxe SQL
        }
        return Set.copyOf(jdbc.sql("SELECT id FROM category WHERE id IN (:ids)")
                .param("ids", categoryIds)
                .query(UUID.class)
                .list());
    }

    @Override
    public InterestSelection findFor(UUID memberId) {
        return InterestSelection.of(jdbc.sql("SELECT category_id FROM user_interest WHERE user_id = :id")
                .param("id", memberId)
                .query(UUID.class)
                .list());
    }

    // Remplacement complet dans la transaction du cas d'usage.
    @Override
    public void replace(UUID memberId, InterestSelection selection) {
        jdbc.sql("DELETE FROM user_interest WHERE user_id = :id").param("id", memberId).update();
        for (UUID categoryId : selection.categoryIds()) {
            jdbc.sql("INSERT INTO user_interest (user_id, category_id) VALUES (:member, :category)")
                    .param("member", memberId)
                    .param("category", categoryId)
                    .update();
        }
    }

    @Override
    public List<InterestView> detailedFor(UUID memberId) {
        return jdbc.sql("""
                    SELECT c.id, c.slug, c.label
                    FROM user_interest i JOIN category c ON c.id = i.category_id
                    WHERE i.user_id = :id
                    ORDER BY c.label
                    """)
                .param("id", memberId)
                .query((rs, row) -> new InterestView(rs.getObject("id", UUID.class),
                        rs.getString("slug"), rs.getString("label")))
                .list();
    }
}
```

Test d'intégration **à travers le cas d'usage** : il vérifie en une fois
l'adaptateur, la transaction et l'outbox.

```java
// test : interest/UpdateInterestsIT.java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UpdateInterestsIT {

    @Autowired UpdateInterests updateInterests;
    @Autowired GetInterests getInterests;
    @Autowired JdbcClient jdbc;

    // Identité unique par test : app_user.email est unique, et la base Testcontainers
    // est partagée par toutes les classes de test du contexte.
    private final String subject = UUID.randomUUID().toString();
    private final Member buyer = new Member(subject, "Acheteur IT", subject + "@test.local");

    @Test
    void storesSelectionAndWritesEventInOutbox() {
        List<UUID> twoCategories = jdbc.sql("SELECT id FROM category ORDER BY label LIMIT 2")
                .query(UUID.class).list();

        updateInterests.execute(buyer, InterestSelection.of(twoCategories));

        assertThat(getInterests.execute(buyer))
                .extracting(InterestView::categoryId)
                .containsExactlyElementsOf(twoCategories);            // triés par libellé
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE routing_key = 'interests.updated'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void unknownCategoryRollsBackEverything() {
        assertThatThrownBy(() -> updateInterests.execute(buyer, InterestSelection.of(List.of(UUID.randomUUID()))))
                .isInstanceOf(UnknownCategoryException.class);

        assertThat(getInterests.execute(buyer)).isEmpty();
    }
}
```

### 6.5 Adaptateur web (étape 7)

```java
// interest/adapter/in/web/InterestController.java
@RestController
@RequestMapping("/api/v1/me/interests")
@PreAuthorize("hasRole('acheteur')")                 // CA-5 : 403 sans le rôle
class InterestController {

    private final GetInterests getInterests;
    private final UpdateInterests updateInterests;

    InterestController(GetInterests getInterests, UpdateInterests updateInterests) {
        this.getInterests = getInterests;
        this.updateInterests = updateInterests;
    }

    @GetMapping
    List<InterestResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return InterestResponse.from(getInterests.execute(Members.from(jwt)));
    }

    // PUT et non POST : remplacement complet, idempotent (CA-6).
    @PutMapping
    List<InterestResponse> replace(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InterestsRequest request) {
        Member member = Members.from(jwt);
        updateInterests.execute(member, InterestSelection.of(request.categoryIds()));   // règles : domaine
        return InterestResponse.from(getInterests.execute(member));
    }
}

// Validation TECHNIQUE (forme du JSON) ; la validation MÉTIER (10 au plus) est
// dans InterestSelection. Le @Size protège seulement contre un corps démesuré.
record InterestsRequest(@NotNull @Size(max = 100) List<@NotNull UUID> categoryIds) {}

record InterestResponse(UUID categoryId, String slug, String label) {

    static List<InterestResponse> from(List<InterestView> views) {
        return views.stream()
                .map(v -> new InterestResponse(v.categoryId(), v.slug(), v.label()))
                .toList();
    }
}
```

Le contrôleur ne contient **aucune règle** : il traduit (jeton → `Member`,
JSON → `InterestSelection`, résultat → JSON) et délègue.

```java
// test : interest/adapter/in/web/InterestControllerTest.java — contrat HTTP et sécurité
// ApiExceptionHandler (@RestControllerAdvice) est chargé d'office par la tranche web ;
// SecurityConfig doit être public pour être importé depuis un autre package.
@WebMvcTest(InterestController.class)
@Import(SecurityConfig.class)
class InterestControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean GetInterests getInterests;
    @MockitoBean UpdateInterests updateInterests;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final String BUYER = "33333333-3333-4333-8333-333333333333";

    private static RequestPostProcessor buyer() {
        return jwt().jwt(j -> j.subject(BUYER).claim("name", "Acheteur Un").claim("email", "a1@collector.local"))
                    .authorities(new SimpleGrantedAuthority("ROLE_acheteur"));
    }

    @Test
    void withoutTokenIs401() throws Exception {                             // CA-5
        mvc.perform(get("/api/v1/me/interests")).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutBuyerRoleIs403() throws Exception {                         // CA-5
        mvc.perform(get("/api/v1/me/interests").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void malformedIdIs400() throws Exception {
        mvc.perform(put("/api/v1/me/interests").with(buyer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_ids\": [\"pas-un-uuid\"]}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void tooManyInterestsIs422() throws Exception {                         // CA-4 (règle du domaine, traduite en HTTP)
        String elevenIds = Stream.generate(() -> "\"" + UUID.randomUUID() + "\"").limit(11)
                .collect(Collectors.joining(",", "[", "]"));

        mvc.perform(put("/api/v1/me/interests").with(buyer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_ids\": " + elevenIds + "}"))
           .andExpect(status().isUnprocessableEntity())
           .andExpect(jsonPath("$.code").value("too_many_interests"));
        verifyNoInteractions(updateInterests);                              // refusé avant le cas d'usage
    }
}
```

### 6.6 Topologie (étape 8)

Une ligne dans `CollectorMessagingAutoConfiguration` (CLAUDE.md G3), une
constante dans `Topology` et `EventTypes` :

```java
bind(declarables, events, Topology.NOTIFICATION_INTERESTS_UPDATED, EventTypes.INTERESTS_UPDATED);
```

Sans elle, la publication (`mandatory`) serait renvoyée faute de file : le relais
garderait l'événement dans l'outbox et réessaierait — rien n'est perdu, mais rien
n'avance. C'est exactement la promesse de la slide 17 : **ajouter un
consommateur = ajouter une file**, sans toucher au producteur.

---

## 7. Étape 9 : tout faire passer

```powershell
.\mvnw verify                                   # unitaires, ArchUnit, intégration, JaCoCo
docker compose up -d --build
.\mvnw -pl acceptance-tests -Pacceptance verify "-Dcucumber.filter.tags=@US-021 or not @wip"
```

(ajouter `@US-021` en tête de la feature pour la lancer seule).

---

## 8. Ce que montre cet exemple

| Principe | Où le voir |
|---|---|
| Le domaine porte les règles, sans framework | `InterestSelection` (bornes, doublons, immuabilité) |
| Validation technique ≠ validation métier | `@Size(max = 100)` sur le DTO, `MAX_INTERESTS = 10` dans le domaine |
| Le domaine ne connaît pas HTTP | `DomainException.Kind`, traduit par `ApiExceptionHandler` |
| Une lecture n'écrit jamais | `GetInterests` utilise `findId`, pas `ensureMember` |
| Idempotence | PUT + `sameAs` : pas d'écriture ni d'événement si rien ne change |
| Donnée + événement atomiques | `@Transactional` + `DomainEventPublisher` (outbox) ; test `unknownCategoryRollsBackEverything` |
| Hexagones indépendants | `CategoryCatalog` propre à `interest`, pas de dépendance vers `category` |
| Adaptateur libre de sa technique | JPA pour `category`, `JdbcClient` ici |
| Tests par couche | domaine (pur) → cas d'usage (doublures) → intégration (Testcontainers) → web (tranche) → acceptation (Gherkin) |

---

## 9. Checklist de la pull request

- [ ] Contrat à jour (`openapi.yaml`, `events.md`, schéma JSON)
- [ ] Scénarios Gherkin écrits **avant** le code, verts maintenant
- [ ] Aucune dépendance Spring/JPA dans `domain` (ArchUnit vert)
- [ ] Une migration **nouvelle** (jamais une migration existante modifiée)
- [ ] Erreurs métier = `DomainException` avec un `code` documenté
- [ ] Pas de donnée personnelle dans les logs
- [ ] `mvnw verify` vert en local, pipeline vert
- [ ] Couverture du nouveau code ≥ 80 % (SonarCloud)

---

## 10. Transposition à US-014

Même démarche, même découpage. Ce que tu écriras :

| Étape | US-021 (cet exemple) | US-014 (à toi) |
|---|---|---|
| Contrat | `/me/interests`, `interests.updated` | `/articles`, `/articles/{id}/photos`, `/articles/{id}/submission`, `/articles/{id}/price`, `/me/articles` ; `article.submitted`, `price.changed` (CLAUDE.md §C8-C9) |
| Gherkin | `us021_centres_interet.feature` | `us014_mise_en_ligne.feature` (CLAUDE.md G13, CA-1 à CA-6) |
| Domaine | `InterestSelection` (objet de valeur) | `Article` (**agrégat** avec un cycle de vie) : statuts `BROUILLON → EN_CONTROLE → PUBLIE / EN_REVUE`, transitions autorisées, `ContactInfoPolicy` (motifs §C8), prix en centimes, au moins une photo valide pour soumettre, propriété (`isOwnedBy(member)`) |
| Exceptions | `TooManyInterests`, `UnknownCategory` | `ArticleNotFound` (NOT_FOUND), `NotOwner` (FORBIDDEN), `InvalidStatus` (CONFLICT), `PhotoRequired`, `ContactInfoForbidden`, `TooManyPhotos` (RULE_VIOLATED / CONFLICT) |
| Événements | `InterestsUpdated` | `ArticleSubmitted`, `PriceChanged` |
| Cas d'usage | `UpdateInterests`, `GetInterests` | `CreateDraft`, `RequestPhotoUpload`, `SubmitArticle`, `ChangePrice`, `GetArticle`, `ListArticles`, `ListMyArticles`, `ApplyVerdict` (idempotent, appelé par le consommateur de `article.checked`) |
| Ports | `InterestRepository`, `CategoryCatalog`, `MemberDirectory`, `DomainEventPublisher` | `ArticleRepository`, `PhotoStorage` (déjà implémenté : S3), `MemberDirectory`, `DomainEventPublisher` (déjà implémenté : outbox), `Clock` |
| Persistance | `JdbcClient` | JPA recommandé (`ArticleEntity`, `PhotoEntity`) : l'agrégat a un vrai état à charger et sauver ; `toDomain()` / `fromDomain()` |
| Web | `InterestController` | `ArticleController` ; propriété vérifiée dans le cas d'usage, jamais dans le contrôleur |
| Messagerie entrante | — | `ArticleCheckedListener` → `ApplyVerdict` |

**Conseil d'ordre pour US-014** : (1) `Article` et ses transitions, en tests
unitaires seulement ; (2) `CreateDraft` + `SubmitArticle` avec doublures ;
(3) persistance JPA + test d'intégration ; (4) photos ; (5) web ; (6) prix ;
(7) verdict. Chaque étape se termine par des tests verts.
