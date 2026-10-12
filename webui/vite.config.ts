import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';
import path from 'node:path';
const srcDir = fileURLToPath(new URL('src', import.meta.url));

export default defineConfig({
  base: './',
  plugins: [react()],
  build: {
    target: 'es2020',
    outDir: 'dist',
    assetsDir: '.',
    assetsInlineLimit: 0,
    cssCodeSplit: true,
    cssMinify: false,
    sourcemap: true,
    minify: false,
    rollupOptions: {
      input: {
        index: path.resolve(import.meta.dirname, 'index.html'),
      },
      output: {
        preserveModules: true,
        keepNames: true,
        entryFileNames: (fileInfo) => {
          const id = fileInfo.facadeModuleId;
          if (!id) return 'js/[name].js';
          var result = id.substring(0, id.lastIndexOf('.') || undefined) + '.js';
          if (result.includes('\0')) {
            var result = result.replace(/\0/g, '_');
            return 'js/_virtual/' + result;
          }
          var rel = path.relative(srcDir, result).replace(/\\/g, '/');
          const node_prefix = '../node_modules';
          if (rel.startsWith(node_prefix)) {
            rel = '_node_modules' + rel.slice(node_prefix.length);
          }
          return `js/${rel}`;
        },
        chunkFileNames: (fileInfo) => {
          const id = fileInfo.facadeModuleId;
          if (!id) return 'js/_chunks/[name].js';
          var result = id.substring(0, id.lastIndexOf('.') || undefined) + '.js';
          if (result.includes('\0')) {
            var result = result.replace(/\0/g, '_');
            return 'js/_chunks/_virtual/' + result;
          }
          var rel = path.relative(srcDir, result).replace(/\\/g, '/');
          const node_prefix = '../node_modules';
          if (rel.startsWith(node_prefix)) {
            rel = '_node_modules' + rel.slice(node_prefix.length);
          }
          return `js/_chunks/${rel}`;
        },
        assetFileNames(assetInfo) {
          const name = assetInfo.originalFileNames[0] || '';
          const src_prefix = 'src/';
          if (!name.startsWith(src_prefix)) {
            return 'res/[name][extname]';
          }
          var rel = name.slice(src_prefix.length);
          const style_suffix = '.css';
          if (rel.endsWith(style_suffix)) {
            return 'css/' + rel;
          }
          return 'res/' + rel;
        },
      },
    },
    emptyOutDir: true,
    reportCompressedSize: false,
  },
  publicDir: 'public',
  test: { include: ['tests/**/*.test.ts'], environment: 'node' },
});
