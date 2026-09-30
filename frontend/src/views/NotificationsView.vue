<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { euros, notifications, type AppNotification } from '../api'
import { messageOf } from '../errors'

const { t } = useI18n()
const items = ref<AppNotification[]>([])
const error = ref('')

async function load() {
  try {
    items.value = await notifications.list()
  } catch (e) {
    error.value = messageOf(e)
  }
}

async function markRead(id: string) {
  try {
    await notifications.markRead(id)
    await load()
  } catch (e) {
    error.value = messageOf(e)
  }
}

onMounted(load)
</script>

<template>
  <section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="items.length === 0" class="muted">{{ t('notif.empty') }}</p>
    <ul class="stack" style="list-style: none; padding: 0; max-width: 40rem">
      <li v-for="n in items" :key="n.id" class="card" :style="n.read_at ? 'opacity: 0.7' : ''">
        <strong>{{ n.article_title }}</strong>
        <span :class="n.price_drop ? 'ok' : 'muted'">
          {{ n.price_drop ? t('notif.drop') : t('notif.rise') }} :
          {{ euros(n.old_price_cents, n.currency) }} → {{ euros(n.new_price_cents, n.currency) }}
        </span>
        <span class="muted">{{ new Date(n.created_at).toLocaleString() }}</span>
        <button v-if="!n.read_at" class="btn" @click="markRead(n.id)">{{ t('notif.markRead') }}</button>
      </li>
    </ul>
  </section>
</template>
