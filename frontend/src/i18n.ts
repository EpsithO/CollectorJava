import { createI18n } from 'vue-i18n'

// US-036 (choisir sa langue) : français par défaut, anglais en option. Les codes d'erreur des API
// sont stables (RFC 9457, propriété code) : on les traduit ici, on n'affiche jamais un détail technique.
const messages = {
  fr: {
    app: { tagline: "Vendez et trouvez des objets de collection entre particuliers" },
    nav: { catalogue: 'Catalogue', sell: 'Vendre', mine: 'Mes articles', notifications: 'Notifications', review: 'Revue admin' },
    auth: { login: 'Se connecter', logout: 'Se déconnecter' },
    catalogue: { all: 'Toutes les catégories', empty: 'Aucun article publié.', follow: 'Suivre', unfollow: 'Ne plus suivre', followed: 'Vous suivez cet article', next: 'Suivant', prev: 'Précédent' },
    sell: { title: 'Titre', description: 'Description', category: 'Catégorie', price: 'Prix (€)', shipping: 'Frais de port (€)', photos: 'Photos (jpeg, png, webp, 5 Mo max, 8 au plus)', submit: 'Mettre en ligne', done: 'Article soumis : contrôle automatique en cours.', needLogin: 'Connectez-vous avec un compte vendeur pour mettre un article en vente.' },
    mine: { empty: "Vous n'avez pas encore d'article.", changePrice: 'Modifier le prix', newPrice: 'Nouveau prix (€)', reason: 'Motif du rejet' },
    status: { BROUILLON: 'Brouillon', EN_CONTROLE: 'En contrôle', PUBLIE: 'Publié', EN_REVUE: 'En revue', REJETE: 'Rejeté', VENDU: 'Vendu', RETIRE: 'Retiré' },
    review: { empty: 'Aucun article en revue.', score: "Score d'anomalie", approve: 'Valider', reject: 'Rejeter', reasonRequired: 'Un motif est obligatoire pour rejeter.' },
    notif: { empty: 'Aucune notification.', drop: 'Baisse de prix', rise: 'Hausse de prix', markRead: 'Marquer comme lue', unread: 'Non lues seulement' },
    errors: {
      unauthorized: 'Connexion requise.', forbidden: "Vous n'avez pas le droit de faire cela.", not_found: 'Introuvable.',
      invalid_request: 'Données invalides : vérifiez le formulaire.', invalid_status: "Cette action n'est plus possible dans l'état actuel.",
      too_many_photos: '8 photos au plus.', contact_info_forbidden: 'Les coordonnées (e-mail, téléphone) sont interdites dans une annonce.',
      photo_required: 'Ajoutez au moins une photo valide.', invalid_photo: 'Photo refusée (type ou taille).',
      unknown_category: 'Catégorie inconnue.', internal_error: 'Une erreur est survenue, réessayez plus tard.',
    },
  },
  en: {
    app: { tagline: 'Sell and find collectibles between individuals' },
    nav: { catalogue: 'Catalogue', sell: 'Sell', mine: 'My items', notifications: 'Notifications', review: 'Admin review' },
    auth: { login: 'Sign in', logout: 'Sign out' },
    catalogue: { all: 'All categories', empty: 'No published item.', follow: 'Follow', unfollow: 'Unfollow', followed: 'You follow this item', next: 'Next', prev: 'Previous' },
    sell: { title: 'Title', description: 'Description', category: 'Category', price: 'Price (€)', shipping: 'Shipping (€)', photos: 'Photos (jpeg, png, webp, 5 MB max, up to 8)', submit: 'Publish', done: 'Item submitted: automatic check in progress.', needLogin: 'Sign in with a seller account to sell an item.' },
    mine: { empty: 'You have no item yet.', changePrice: 'Change price', newPrice: 'New price (€)', reason: 'Rejection reason' },
    status: { BROUILLON: 'Draft', EN_CONTROLE: 'Under check', PUBLIE: 'Published', EN_REVUE: 'In review', REJETE: 'Rejected', VENDU: 'Sold', RETIRE: 'Withdrawn' },
    review: { empty: 'No item in review.', score: 'Anomaly score', approve: 'Approve', reject: 'Reject', reasonRequired: 'A reason is required to reject.' },
    notif: { empty: 'No notification.', drop: 'Price drop', rise: 'Price rise', markRead: 'Mark as read', unread: 'Unread only' },
    errors: {
      unauthorized: 'Sign-in required.', forbidden: 'You are not allowed to do this.', not_found: 'Not found.',
      invalid_request: 'Invalid data: check the form.', invalid_status: 'This action is no longer possible in the current state.',
      too_many_photos: 'Up to 8 photos.', contact_info_forbidden: 'Contact details (e-mail, phone) are forbidden in a listing.',
      photo_required: 'Add at least one valid photo.', invalid_photo: 'Photo refused (type or size).',
      unknown_category: 'Unknown category.', internal_error: 'Something went wrong, please retry later.',
    },
  },
}

const saved = window.localStorage.getItem('locale')

export const i18n = createI18n({
  legacy: false,
  locale: saved === 'en' ? 'en' : 'fr',
  fallbackLocale: 'fr',
  messages,
})

export function setLocale(locale: 'fr' | 'en') {
  i18n.global.locale.value = locale
  window.localStorage.setItem('locale', locale)
  document.documentElement.lang = locale
}
