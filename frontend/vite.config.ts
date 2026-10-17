/// <reference types="vitest/config" />
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { defineConfig } from 'vite';

// In sviluppo le chiamate /api vanno al backend Spring Boot: stessa origine per il browser,
// quindi cookie di sessione e CSRF funzionano senza CORS.
export default defineConfig({
  plugins: [svelte()],
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: { include: ['src/**/*.test.ts'] },
});
