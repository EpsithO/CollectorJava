<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { hasRole, login, logout, session } from './auth'
import { setLocale } from './i18n'
import CatalogueView from './views/CatalogueView.vue'
import SellView from './views/SellView.vue'
import MyArticlesView from './views/MyArticlesView.vue'
import NotificationsView from './views/NotificationsView.vue'
import ReviewView from './views/ReviewView.vue'

const { t, locale } = useI18n()
type Tab = 'catalogue' | 'sell' | 'mine' | 'notifications' | 'review'
const tab = ref<Tab>('catalogue')

// Affichage seulement : l'accès réel est décidé par les API (401, 403), jamais par le front.
const tabs = computed<Tab[]>(() => {
  const result: Tab[] = ['catalogue']
  if (session.user) {
    if (hasRole('vendeur')) result.push('sell', 'mine')
    if (hasRole('acheteur')) result.push('notifications')
    if (hasRole('admin')) result.push('review')
  }
  return result
})
const name = computed(() => (session.user?.profile.name as string | undefined) ?? session.user?.profile.preferred_username)
</script>

<template>
  <header>
    <h1>Collector.shop</h1>
    <nav :aria-label="t('app.tagline')">
      <button v-for="item in tabs" :key="item" :aria-current="tab === item ? 'page' : undefined" @click="tab = item">
        {{ t('nav.' + item) }}
      </button>
    </nav>
    <span v-if="session.user" class="muted">{{ name }}</span>
    <button v-if="session.user" class="btn" @click="logout()">{{ t('auth.logout') }}</button>
    <button v-else class="btn primary" @click="login()">{{ t('auth.login') }}</button>
    <label class="lang">
      <span class="muted">FR / EN</span>
      <select :value="locale" @change="setLocale(($event.target as HTMLSelectElement).value as 'fr' | 'en')">
        <option value="fr">Français</option>
        <option value="en">English</option>
      </select>
    </label>
  </header>
  <main>
    <CatalogueView v-if="tab === 'catalogue'" />
    <SellView v-else-if="tab === 'sell'" @done="tab = 'mine'" />
    <MyArticlesView v-else-if="tab === 'mine'" />
    <NotificationsView v-else-if="tab === 'notifications'" />
    <ReviewView v-else-if="tab === 'review'" />
  </main>
</template>
