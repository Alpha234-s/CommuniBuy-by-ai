import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '../api'
import { ApiError, tokenStorage } from '../api/http'
import type { User } from '../types/api'

export const useAuth = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const ready = ref(false)
  const isAdmin = computed(() => user.value?.role === 1)
  let restoring: Promise<void> | null = null
  function clear(forgetIntents = false) {
    if (forgetIntents && user.value) {
      const prefix = `communibuy.intent:${user.value.id}:`
      const keys = Array.from({ length: sessionStorage.length }, (_, index) =>
        sessionStorage.key(index)
      )
      keys.forEach((key) => {
        if (key?.startsWith(prefix)) sessionStorage.removeItem(key)
      })
    }
    tokenStorage.clear()
    user.value = null
    ready.value = true
  }
  async function restore(): Promise<void> {
    if (ready.value) return
    if (restoring) return restoring
    restoring = (async () => {
      if (!tokenStorage.get()) {
        ready.value = true
        return
      }
      try {
        user.value = await api.me()
        ready.value = true
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) clear()
        else throw error
      }
    })().finally(() => {
      restoring = null
    })
    return restoring
  }
  async function login(id: string, password: string) {
    const result = await api.login(id, password)
    tokenStorage.set(result.token)
    try {
      user.value = await api.me()
      ready.value = true
    } catch (e) {
      clear()
      throw e
    }
  }
  return { user, ready, isAdmin, clear, restore, login }
})
