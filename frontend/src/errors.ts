import { ApiError } from './api'
import { i18n } from './i18n'

/** Message lisible pour un code d'erreur stable de l'API ; repli sur internal_error pour un code inconnu. */
export function messageOf(error: unknown): string {
  const t = i18n.global.t
  const code = error instanceof ApiError ? (error.status === 401 ? 'unauthorized' : error.code) : 'internal_error'
  const key = `errors.${code}`
  return i18n.global.te(key) ? t(key) : t('errors.internal_error')
}
