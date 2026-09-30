// Adresses par défaut = pile docker compose locale. Surchargeables par .env.local (voir .env.example).
export const config = {
  apiUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  notificationUrl: import.meta.env.VITE_NOTIFICATION_URL ?? 'http://localhost:8082',
  oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8081/realms/collector',
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'collector-web',
}
