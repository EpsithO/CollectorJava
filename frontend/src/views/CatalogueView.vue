<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { catalogue, euros, type ArticlePage, type Category } from '../api'
import { hasRole, session } from '../auth'
import { messageOf } from '../errors'

const { t } = useI18n()
const categories = ref<Category[]>([])
const category = ref('')
const page = ref(0)
const result = ref<ArticlePage | null>(null)
const followed = ref(new Set<string>())
const error = ref('')

async function load() {
  error.value = ''
  try {
    result.value = await catalogue.articles(category.value, page.value)
  } catch (e) {
    error.value = messageOf(e)
  }
}

async function toggleFollow(id: string) {
  error.value = ''
  try {
    if (followed.value.has(id)) {
      await catalogue.unfollow(id)
      followed.value.delete(id)
    } else {
      await catalogue.follow(id)
      followed.value.add(id)
    }
    followed.value = new Set(followed.value)
  } catch (e) {
    error.value = messageOf(e)
  }
}

onMounted(async () => {
  categories.value = await catalogue.categories().catch(() => [])
  await load()
})
watch(category, () => { page.value = 0; load() })
watch(page, load)
</script>

<template>
  <section>
    <label>
      <span>{{ t('sell.category') }}</span>
      <select v-model="category">
        <option value="">{{ t('catalogue.all') }}</option>
        <option v-for="c in categories" :key="c.id" :value="c.slug">{{ c.label }}</option>
      </select>
    </label>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="result && result.items.length === 0" class="muted">{{ t('catalogue.empty') }}</p>
    <div class="grid" style="margin-top: 1rem">
      <article v-for="a in result?.items" :key="a.id" class="card">
        <img v-if="a.photos[0]" :src="a.photos[0].url" :alt="a.title" loading="lazy" />
        <h2 style="font-size: 1rem; margin: 0">{{ a.title }}</h2>
        <span class="price">{{ euros(a.price_cents, a.currency) }}</span>
        <span class="muted">{{ a.description.slice(0, 90) }}</span>
        <button v-if="session.user && hasRole('acheteur')" class="btn" :aria-pressed="followed.has(a.id)"
                @click="toggleFollow(a.id)">
          {{ followed.has(a.id) ? t('catalogue.unfollow') : t('catalogue.follow') }}
        </button>
      </article>
    </div>
    <div v-if="result && result.total > result.size" class="row" style="margin-top: 1rem">
      <button class="btn" :disabled="page === 0" @click="page--">{{ t('catalogue.prev') }}</button>
      <span class="muted">{{ page + 1 }} / {{ Math.ceil(result.total / result.size) }}</span>
      <button class="btn" :disabled="(page + 1) * result.size >= result.total" @click="page++">{{ t('catalogue.next') }}</button>
    </div>
  </section>
</template>
