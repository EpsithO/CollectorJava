<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { catalogue, uploadPhoto, type Category } from '../api'
import { messageOf } from '../errors'

const emit = defineEmits<{ done: [] }>()
const { t } = useI18n()

const categories = ref<Category[]>([])
const form = ref({ title: '', description: '', category_id: '', price: '', shipping: '0' })
const files = ref<File[]>([])
const error = ref('')
const busy = ref(false)

onMounted(async () => { categories.value = await catalogue.categories().catch(() => []) })

function pick(event: Event) {
  files.value = Array.from((event.target as HTMLInputElement).files ?? [])
}

// Flux US-014 : brouillon, une URL d'envoi par photo (le binaire va directement au stockage), puis soumission.
async function publish() {
  error.value = ''
  busy.value = true
  try {
    const draft = await catalogue.createDraft({
      title: form.value.title,
      description: form.value.description,
      category_id: form.value.category_id,
      price_cents: Math.round(Number(form.value.price) * 100),
      shipping_cents: Math.round(Number(form.value.shipping || '0') * 100),
    })
    for (const file of files.value) {
      const upload = await catalogue.requestPhoto(draft.id, file)
      await uploadPhoto(upload, file)
    }
    await catalogue.submit(draft.id)
    emit('done')
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form class="stack" @submit.prevent="publish">
    <label>{{ t('sell.title') }}
      <input v-model="form.title" required minlength="3" maxlength="120" />
    </label>
    <label>{{ t('sell.description') }}
      <textarea v-model="form.description" required minlength="10" maxlength="5000" rows="4" />
    </label>
    <label>{{ t('sell.category') }}
      <select v-model="form.category_id" required>
        <option value="" disabled></option>
        <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.label }}</option>
      </select>
    </label>
    <div class="row">
      <label>{{ t('sell.price') }}
        <input v-model="form.price" type="number" min="0.01" step="0.01" required />
      </label>
      <label>{{ t('sell.shipping') }}
        <input v-model="form.shipping" type="number" min="0" step="0.01" />
      </label>
    </div>
    <label>{{ t('sell.photos') }}
      <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="pick" />
    </label>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button class="btn primary" type="submit" :disabled="busy">{{ t('sell.submit') }}</button>
  </form>
</template>
