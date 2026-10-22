/// <reference types="vitest/config" />
import { networkInterfaces } from 'node:os';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { defineConfig } from 'vite';

/** Gli indirizzi del PC nella rete locale (es. 192.168.1.9): in sviluppo il QR del telefono-lettore usa questi. */
function indirizziRete(): string[] {
  return Object.values(networkInterfaces()).flat()
    .filter((i) => i && i.family === 'IPv4' && !i.internal && /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(i.address))
    .map((i) => i!.address);
}

// In sviluppo le chiamate /api vanno al backend Spring Boot: stessa origine per il browser,
// quindi cookie di sessione e CSRF funzionano senza CORS.
export default defineConfig(({ command }) => ({
  plugins: [svelte()],
  define: { __INDIRIZZI_RETE__: JSON.stringify(command === 'serve' ? indirizziRete() : []) },
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: { include: ['src/**/*.test.ts'] },
}));
