# language: fr
@US-033
Fonctionnalité: US-033 Traiter les articles en revue
  En tant qu'administrateur,
  je veux valider ou rejeter un article que le contrôle a mis en revue,
  afin de ne publier que des annonces saines.

  Scénario: CA-1 La file de revue liste l'article à prix anormal
    Étant donné "vendeur1" met en vente un article à un prix anormal
    Et l'utilisateur authentifié "admin"
    Quand il consulte la file de revue
    Alors le code de réponse est 200
    Et la file de revue contient cet article avec son score d'anomalie

  @events
  Scénario: CA-2 L'administrateur valide l'article
    Étant donné "vendeur1" met en vente un article à un prix anormal
    Et l'utilisateur authentifié "admin"
    Quand il valide cet article
    Alors le code de réponse est 200
    Et le champ "status" vaut "PUBLIE"
    Et un événement "article.reviewed" est publié pour cet article sous 5 secondes
    Et la file de revue ne contient plus cet article

  @events
  Scénario: CA-3 L'administrateur rejette l'article avec un motif
    Étant donné "vendeur1" met en vente un article à un prix anormal
    Et l'utilisateur authentifié "admin"
    Quand il rejette cet article avec le motif "Prix sans rapport avec la catégorie"
    Alors le code de réponse est 200
    Et le champ "status" vaut "REJETE"
    Et un événement "article.reviewed" est publié pour cet article sous 5 secondes
    Et son vendeur voit le motif "Prix sans rapport avec la catégorie" sur l'article

  Scénario: CA-3 Rejeter sans motif est refusé
    Étant donné "vendeur1" met en vente un article à un prix anormal
    Et l'utilisateur authentifié "admin"
    Quand il rejette cet article sans motif
    Alors le code de réponse est 400
    Et le champ "code" vaut "invalid_request"
    Et l'article a le statut "EN_REVUE"

  Scénario: CA-4 Un article déjà traité ne peut pas l'être deux fois
    Étant donné "vendeur1" met en vente un article à un prix anormal
    Et l'utilisateur authentifié "admin"
    Et il valide cet article
    Quand il rejette cet article avec le motif "Trop tard"
    Alors le code de réponse est 409
    Et le champ "code" vaut "invalid_status"
    Et l'article a le statut "PUBLIE"

  Scénario: CA-4 Un article inconnu est introuvable
    Étant donné l'utilisateur authentifié "admin"
    Quand il décide de valider un article inconnu
    Alors le code de réponse est 404

  Scénario: CA-5 Sans jeton la file de revue est refusée
    Étant donné un utilisateur non authentifié
    Quand il consulte la file de revue
    Alors le code de réponse est 401

  Scénario: CA-5 Un vendeur n'accède pas à la file de revue
    Étant donné un vendeur authentifié "vendeur1"
    Quand il consulte la file de revue
    Alors le code de réponse est 403
    Et le champ "code" vaut "forbidden"
