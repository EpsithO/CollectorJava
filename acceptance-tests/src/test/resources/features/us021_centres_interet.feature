# language: fr
@US-021
Fonctionnalité: US-021 Paramétrer ses centres d'intérêt
  En tant qu'acheteur authentifié,
  je veux choisir mes centres d'intérêt parmi les catégories,
  afin de recevoir des recommandations et des notifications pertinentes.

  Contexte:
    Étant donné l'utilisateur authentifié "acheteur1"
    Et il choisit les centres d'intérêt ""

  @events
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

  @events
  Scénario: CA-6 Le même choix ne republie rien
    Étant donné il choisit les centres d'intérêt "bd"
    Quand il choisit les centres d'intérêt "bd"
    Alors le code de réponse est 200
    Et aucun événement "interests.updated" n'est publié sous 3 secondes
