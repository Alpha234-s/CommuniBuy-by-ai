import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    define: { __SINGLE_FILE__: 'false' },
    plugins: [vue()],

    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: env.API_PROXY_TARGET
        ? { '/api': { target: env.API_PROXY_TARGET, changeOrigin: true } }
        : undefined,
    },

    test: {
      include: ['tests/unit/**/*.test.ts'],
      environment: 'node',
    },
  }
})
