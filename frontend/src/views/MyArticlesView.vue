<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { catalogue, euros, type Article } from '../api'
import { messageOf } from '../errors'

const { t } = useI18n()
const articles = ref<Article[]>([])
const newPrice = ref<Record<string, string>>({})
const error = ref('')
let timer: number | undefined

async function load() {
  try {
    articles.value = await catalogue.mine()
  } catch (e) {
    error.value = messageOf(e)
  }
}

async function changePrice(id: string) {
  error.value = ''
  try {
    await catalogue.changePrice(id, Math.round(Number(newPrice.value[id]) * 100))
    newPrice.value[id] = ''
    await load()
  } catch (e) {
    error.value = messageOf(e)
  }
}

// Le statut évolue sans action de l'utilisateur (EN_CONTROLE puis PUBLIE en moins de 2 s) :
// on rafraîchit tant qu'un article attend son verdict.
onMounted(() => {
  load()
  timer = window.setInterval(() => {
    if (articles.value.some((a) => a.status === 'EN_CONTROLE')) load()
  }, 2000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="articles.length === 0" class="muted">{{ t('mine.empty') }}</p>
    <div class="grid">
      <article v-for="a in articles" :key="a.id" class="card">
        <img v-if="a.photos[0]" :src="a.photos[0].url" :alt="a.title" loading="lazy" />
        <h2 style="font-size: 1rem; margin: 0">{{ a.title }}</h2>
        <span class="price">{{ euros(a.price_cents, a.currency) }}</span>
        <span class="badge">{{ t('status.' + a.status) }}</span>
        <span v-if="a.review_reason" class="error">{{ a.review_reason }}</span>
        <form v-if="a.status === 'PUBLIE' || a.status === 'EN_REVUE'" class="row" @submit.prevent="changePrice(a.id)">
          <input v-model="newPrice[a.id]" type="number" min="0.01" step="0.01" required
                 :aria-label="t('mine.newPrice')" :placeholder="t('mine.newPrice')" style="width: 8rem" />
          <button class="btn" type="submit">{{ t('mine.changePrice') }}</button>
        </form>
      </article>
    </div>
  </section>
</template>
