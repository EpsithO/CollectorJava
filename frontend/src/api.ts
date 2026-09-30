import { accessToken } from './auth'
import { config } from './config'

// Erreur au format RFC 9457 : on affiche le code stable (traduit), jamais un détail technique.
export class ApiError extends Error {
  constructor(public status: number, public code: string) {
    super(code)
  }
}

export interface Photo { id: string; url: string }
export interface Article {
  id: string
  seller_id: string
  category_id: string
  title: string
  description: string
  price_cents: number
  shipping_cents: number
  currency: string
  status: string
  review_reason: string | null
  published_at: string | null
  created_at: string
  photos: Photo[]
}
export interface Category { id: string; slug: string; label: string }
export interface ArticlePage { items: Article[]; page: number; size: number; total: number }
export interface ReviewItem { article: Article; anomaly_score: number | null; check_reason: string | null }
export interface AppNotification {
  id: string
  article_id: string
  article_title: string
  old_price_cents: number
  new_price_cents: number
  currency: string
  price_drop: boolean
  created_at: string
  read_at: string | null
}
export interface PhotoUpload { photo_id: string; upload_url: string; upload_headers: Record<string, string> }

async function call<T>(base: string, method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const token = accessToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(base + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(response.status, problem.code ?? 'internal_error')
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

export const catalogue = {
  categories: () => call<Category[]>(config.apiUrl, 'GET', '/api/v1/categories'),
  articles: (category: string, page: number) =>
    call<ArticlePage>(config.apiUrl, 'GET',
      `/api/v1/articles?size=12&page=${page}${category ? `&category=${encodeURIComponent(category)}` : ''}`),
  mine: () => call<Article[]>(config.apiUrl, 'GET', '/api/v1/me/articles'),
  createDraft: (draft: { title: string; description: string; category_id: string; price_cents: number; shipping_cents: number }) =>
    call<Article>(config.apiUrl, 'POST', '/api/v1/articles', draft),
  requestPhoto: (id: string, file: File) =>
    call<PhotoUpload>(config.apiUrl, 'POST', `/api/v1/articles/${id}/photos`,
      { content_type: file.type, size_bytes: file.size }),
  submit: (id: string) => call<Article>(config.apiUrl, 'POST', `/api/v1/articles/${id}/submission`),
  changePrice: (id: string, priceCents: number) =>
    call<Article>(config.apiUrl, 'PATCH', `/api/v1/articles/${id}/price`, { price_cents: priceCents }),
  follow: (id: string) => call<void>(config.apiUrl, 'PUT', `/api/v1/articles/${id}/follow`),
  unfollow: (id: string) => call<void>(config.apiUrl, 'DELETE', `/api/v1/articles/${id}/follow`),
  reviews: () => call<ReviewItem[]>(config.apiUrl, 'GET', '/api/v1/admin/reviews'),
  decide: (id: string, decision: 'VALIDER' | 'REJETER', reason?: string) =>
    call<Article>(config.apiUrl, 'POST', `/api/v1/admin/reviews/${id}`, { decision, reason }),
}

export const notifications = {
  list: () => call<AppNotification[]>(config.notificationUrl, 'GET', '/api/v1/me/notifications'),
  markRead: (id: string) => call<void>(config.notificationUrl, 'POST', `/api/v1/me/notifications/${id}/read`),
}

// Envoi direct du binaire vers le stockage objet par l'URL pré-signée : il ne transite jamais par l'API.
// Le Content-Type est signé : il faut renvoyer exactement l'en-tête fourni.
export async function uploadPhoto(upload: PhotoUpload, file: File): Promise<void> {
  const response = await fetch(upload.upload_url, { method: 'PUT', headers: upload.upload_headers, body: file })
  if (!response.ok) throw new ApiError(response.status, 'invalid_photo')
}

export const euros = (cents: number, currency = 'EUR') =>
  new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(cents / 100)
