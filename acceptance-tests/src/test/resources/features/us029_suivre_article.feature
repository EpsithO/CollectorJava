# language: fr
@US-029
Fonctionnalité: US-029 Suivre un article et être prévenu des variations de prix
  En tant qu'acheteur,
  je veux suivre un article et être prévenu quand son prix change,
  afin de ne pas manquer une bonne affaire.

  Contexte:
    Étant donné un article publié appartenant à "vendeur2"

  @events
  Scénario: CA-1 Suivre un article publié
    Quand "acheteur1" suit cet article
    Alors le code de réponse est 204
    Et un événement "follow.changed" est publié pour cet article sous 5 secondes

  Scénario: CA-1 Un article qui n'existe pas ne se suit pas
    Étant donné l'utilisateur authentifié "acheteur1"
    Quand il suit un article qui n'existe pas
    Alors le code de réponse est 404

  @events
  Scénario: CA-2 Ne plus suivre un article
    Étant donné "acheteur1" suit cet article
    Quand "acheteur1" ne suit plus cet article
    Alors le code de réponse est 204
    Et un événement "follow.changed" est publié pour cet article sous 5 secondes

  @events
  Scénario: CA-3 Le suiveur est prévenu d'un changement de prix
    Étant donné "acheteur1" suit cet article
    Et un événement "follow.changed" est publié pour cet article sous 5 secondes
    Quand "vendeur2" modifie le prix de cet article à 24000 centimes
    Alors "acheteur1" voit une notification pour cet article avec l'ancien prix 26000 et le nouveau prix 24000 sous 10 secondes

  Scénario: CA-3 Celui qui ne suit pas n'est pas prévenu
    Quand "vendeur2" modifie le prix de cet article à 24000 centimes
    Alors "acheteur1" ne voit aucune notification pour cet article pendant 5 secondes

  Scénario: CA-3 Celui qui ne suit plus n'est pas prévenu
    Étant donné "acheteur1" suit cet article
    Et "acheteur1" ne suit plus cet article
    Quand "vendeur2" modifie le prix de cet article à 24000 centimes
    Alors "acheteur1" ne voit aucune notification pour cet article pendant 5 secondes

  Scénario: CA-4 Deux changements rapprochés notifient le dernier prix
    Étant donné "acheteur1" suit cet article
    Quand "vendeur2" modifie le prix de cet article à 24000 centimes
    Et "vendeur2" modifie le prix de cet article à 23000 centimes
    Alors "acheteur1" voit une notification pour cet article au nouveau prix 23000 sous 10 secondes
    Et "acheteur1" a au plus 2 notifications pour cet article

  Scénario: CA-5 Marquer une notification comme lue
    Étant donné "acheteur1" suit cet article
    Et "vendeur2" modifie le prix de cet article à 24000 centimes
    Et "acheteur1" voit une notification pour cet article au nouveau prix 24000 sous 10 secondes
    Quand "acheteur1" marque sa notification pour cet article comme lue
    Alors le code de réponse est 204
    Et cette notification n'apparaît plus dans les notifications non lues de "acheteur1"

  Scénario: CA-5 Un autre membre ne peut ni voir ni marquer la notification
    Étant donné "acheteur1" suit cet article
    Et "vendeur2" modifie le prix de cet article à 24000 centimes
    Et "acheteur1" voit une notification pour cet article au nouveau prix 24000 sous 10 secondes
    Quand "vendeur1" tente de marquer cette notification comme lue
    Alors le code de réponse est 404
    Et l'utilisateur authentifié "vendeur1"
    Et il consulte ses notifications
    Et la liste de notifications ne contient aucune notification de "acheteur1"

  Scénario: CA-6 Suivre sans jeton
    Étant donné un utilisateur non authentifié
    Quand il suit cet article
    Alors le code de réponse est 401

  Scénario: CA-6 Suivre sans le rôle acheteur
    Étant donné l'utilisateur authentifié "admin"
    Quand il suit cet article
    Alors le code de réponse est 403

  Scénario: CA-6 Consulter ses notifications sans jeton
    Étant donné un utilisateur non authentifié
    Quand il consulte ses notifications
    Alors le code de réponse est 401
