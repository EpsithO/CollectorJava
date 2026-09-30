import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'
import { reactive } from 'vue'
import { config } from './config'

// Authorization Code + PKCE, sans secret : le front est un client public. Le jeton d'accès
// (5 minutes) reste en mémoire de session ; il n'est jamais écrit dans un cookie.
const manager = new UserManager({
  authority: config.oidcAuthority,
  client_id: config.oidcClientId,
  redirect_uri: window.location.origin + '/',
  post_logout_redirect_uri: window.location.origin + '/',
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: true,
})

export const session = reactive<{ user: User | null; roles: string[] }>({ user: null, roles: [] })

function rolesOf(user: User | null): string[] {
  if (!user) return []
  // Les rôles Keycloak sont dans le jeton d'accès (realm_access.roles) ; on lit le payload pour l'affichage
  // seulement : c'est le serveur qui décide des droits, jamais le front.
  try {
    const payload = JSON.parse(atob(user.access_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return payload?.realm_access?.roles ?? []
  } catch {
    return []
  }
}

function setUser(user: User | null) {
  session.user = user
  session.roles = rolesOf(user)
}

export async function initAuth(): Promise<void> {
  const params = new URLSearchParams(window.location.search)
  if (params.has('code') && params.has('state')) {
    setUser(await manager.signinRedirectCallback())
    window.history.replaceState({}, document.title, window.location.pathname)
  } else {
    setUser(await manager.getUser())
  }
  manager.events.addUserLoaded(setUser)
  manager.events.addUserUnloaded(() => setUser(null))
}

export const login = () => manager.signinRedirect()
export const logout = () => manager.signoutRedirect()
export const hasRole = (role: string) => session.roles.includes(role)
export const accessToken = () => session.user?.access_token ?? null
