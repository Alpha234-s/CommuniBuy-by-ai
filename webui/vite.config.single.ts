import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { viteSingleFile } from 'vite-plugin-singlefile'
import path from 'node:path'

export default defineConfig({
  base: './',
  define: { __SINGLE_FILE__: 'true' },
  plugins: [vue(), viteSingleFile()],
  build: {
    target: 'es2022',
    outDir: 'dist-single',
    cssCodeSplit: false,
    modulePreload: false,
    assetsInlineLimit: 10000000,
    rollupOptions: {
      input: { index: path.resolve(import.meta.dirname, 'index.html') },
      output: { inlineDynamicImports: true },
    },
  },
  publicDir: 'public-single',
  test: { include: ['tests/**/*.test.ts'], environment: 'node' },
})
