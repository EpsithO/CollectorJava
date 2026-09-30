# language: fr
@socle
Fonctionnalité: Socle technique
  Les routes publiques, les erreurs sans détail technique, la chaîne outbox vers RabbitMQ
  et la topologie des événements fonctionnent avant toute fonctionnalité métier.

  Scénario: Le catalogue répond et les catégories sont publiques
    Étant donné un utilisateur non authentifié
    Quand il consulte la liste des catégories
    Alors le code de réponse est 200
    Et la liste contient les catégories de démonstration

  @events
  Scénario: Un ping traverse l'outbox et le broker
    Étant donné un utilisateur non authentifié
    Quand il envoie un ping "bonjour"
    Alors le code de réponse est 201
    Et un événement "ping.created" conforme à son schéma est publié sous 5 secondes

  Scénario: Une requête invalide est refusée sans détail technique
    Étant donné un utilisateur non authentifié
    Quand il envoie un ping sans contenu
    Alors le code de réponse est 400
    Et le champ "code" vaut "invalid_request"
    Et la réponse ne révèle aucun détail technique

  Scénario: Un corps illisible est refusé sans détail technique
    Étant donné un utilisateur non authentifié
    Quand il envoie un corps illisible sur les pings
    Alors le code de réponse est 400
    Et la réponse ne révèle aucun détail technique

  Scénario: La topologie du bus est déclarée
    Alors la topologie de l'échange "collector.events" contient les liaisons
      | clé de routage    | file                            |
      | article.submitted | controle.article-submitted      |
      | article.checked   | catalogue.article-checked       |
      | fraud.alert       | fraude.alerts                   |
      | price.changed     | fraude.price-changed            |
      | price.changed     | notification.price-changed      |
      | follow.changed    | notification.follow-changed     |
      | article.reviewed  | notification.article-reviewed   |
      | interests.updated | notification.interests-updated  |
      | ping.created      | catalogue.ping                  |
