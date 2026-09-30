<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { catalogue, euros, type ReviewItem } from '../api'
import { messageOf } from '../errors'

const { t } = useI18n()
const items = ref<ReviewItem[]>([])
const reasons = ref<Record<string, string>>({})
const error = ref('')

async function load() {
  try {
    items.value = await catalogue.reviews()
  } catch (e) {
    error.value = messageOf(e)
  }
}

async function decide(id: string, decision: 'VALIDER' | 'REJETER') {
  error.value = ''
  if (decision === 'REJETER' && !(reasons.value[id] ?? '').trim()) {
    error.value = t('review.reasonRequired')
    return
  }
  try {
    await catalogue.decide(id, decision, reasons.value[id])
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
    <p v-if="items.length === 0" class="muted">{{ t('review.empty') }}</p>
    <div class="grid">
      <article v-for="r in items" :key="r.article.id" class="card">
        <img v-if="r.article.photos[0]" :src="r.article.photos[0].url" :alt="r.article.title" loading="lazy" />
        <h2 style="font-size: 1rem; margin: 0">{{ r.article.title }}</h2>
        <span class="price">{{ euros(r.article.price_cents, r.article.currency) }}</span>
        <span class="muted">{{ t('review.score') }} : {{ r.anomaly_score?.toFixed(1) }} ({{ r.check_reason }})</span>
        <input v-model="reasons[r.article.id]" :aria-label="t('mine.reason')" :placeholder="t('mine.reason')" />
        <div class="row">
          <button class="btn primary" @click="decide(r.article.id, 'VALIDER')">{{ t('review.approve') }}</button>
          <button class="btn danger" @click="decide(r.article.id, 'REJETER')">{{ t('review.reject') }}</button>
        </div>
      </article>
    </div>
  </section>
</template>
