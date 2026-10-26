import { ref } from 'vue'
import { api } from '../api'
import type { Category } from '../types/api'
export function useCategories() {
  const categories = ref<Category[]>([])
  const more = ref(true)
  let page = 0
  async function loadMore() {
    const result = await api.categories({ page, size: 100 })
    categories.value = [...categories.value, ...result.content]
    page += 1
    more.value = BigInt(categories.value.length) < BigInt(result.count)
  }
  return { categories, more, loadMore }
}
