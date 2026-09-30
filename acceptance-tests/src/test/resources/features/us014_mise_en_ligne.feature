# language: fr
@US-014
Fonctionnalité: US-014 Mise en ligne d'un article avec contrôle automatisé
  En tant que vendeur authentifié,
  je veux mettre en ligne un article avec son prix et ses photos,
  afin qu'il soit proposé à la vente après un contrôle automatique de conformité.

  @events
  Scénario: CA-1 Publication nominale
    Étant donné un vendeur authentifié "vendeur1"
    Et un brouillon dans la catégorie "sneakers" au prix de 26000 centimes avec 1 photo
    Quand il soumet le brouillon
    Alors le code de réponse est 200
    Et le champ "status" vaut "EN_CONTROLE"
    Et un événement "article.submitted" est publié pour cet article sous 5 secondes

  Scénario: CA-2 Contrôle automatique d'un prix cohérent
    Étant donné un vendeur authentifié "vendeur1"
    Et un brouillon dans la catégorie "sneakers" au prix de 26000 centimes avec 1 photo
    Quand il soumet le brouillon
    Alors l'article passe au statut "PUBLIE" en moins de 2 secondes

  @events
  Scénario: CA-3 Anomalie de prix
    Étant donné un vendeur authentifié "vendeur1"
    Et un brouillon dans la catégorie "sneakers" au prix de 100000 centimes avec 1 photo
    Quand il soumet le brouillon
    Alors l'article passe au statut "EN_REVUE" en moins de 2 secondes
    Et un événement "fraud.alert" est publié pour cet article sous 5 secondes

  @events
  Scénario: CA-4 Variation de prix
    Étant donné un article publié appartenant à "vendeur2"
    Quand "vendeur2" modifie le prix de cet article à 24000 centimes
    Alors le code de réponse est 200
    Et un événement "price.changed" est publié pour cet article sous 5 secondes
    Et la clé "price.changed" est routée vers les files "fraude.price-changed" et "notification.price-changed"

  Scénario: CA-5 Création sans jeton
    Étant donné un utilisateur non authentifié
    Quand il crée un brouillon dans la catégorie "sneakers" au prix de 26000 centimes
    Alors le code de réponse est 401

  Scénario: CA-5 Un acheteur ne peut pas mettre en vente
    Étant donné l'utilisateur authentifié "acheteur1"
    Quand il crée un brouillon dans la catégorie "sneakers" au prix de 26000 centimes
    Alors le code de réponse est 403

  Scénario: CA-5 Modification de l'article d'un autre vendeur
    Étant donné un article publié appartenant à "vendeur2"
    Quand "vendeur1" modifie le prix de cet article à 1000 centimes
    Alors le code de réponse est 403
    Et le prix de cet article est 26000 centimes

  Scénario: CA-6 Soumission sans photo
    Étant donné un vendeur authentifié "vendeur1"
    Et un brouillon dans la catégorie "sneakers" au prix de 26000 centimes avec 0 photo
    Quand il soumet le brouillon
    Alors le code de réponse est 422
    Et le champ "code" vaut "photo_required"
    Et l'article a le statut "BROUILLON"
