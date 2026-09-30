import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Port 5173 : c'est l'origine autorisée par le CORS des API, le realm Keycloak et le stockage objet.
export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, strictPort: true },
})
