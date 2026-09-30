import { createApp } from 'vue'
import App from './App.vue'
import { initAuth } from './auth'
import { i18n } from './i18n'
import './style.css'

// Le jeton éventuel est traité (retour de Keycloak) avant d'afficher l'application.
initAuth().finally(() => createApp(App).use(i18n).mount('#app'))
