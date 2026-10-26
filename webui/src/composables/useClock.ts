import { onUnmounted, ref } from 'vue'
export function useClock() {
  const now = ref(Math.floor(Date.now() / 1000))
  const timer = window.setInterval(() => {
    now.value = Math.floor(Date.now() / 1000)
  }, 1000)
  onUnmounted(() => window.clearInterval(timer))
  return now
}
