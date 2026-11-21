/// <reference types="vitest/config" />
import { networkInterfaces } from 'node:os';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { svelteTesting } from '@testing-library/svelte/vite';
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
  plugins: [svelte(), svelteTesting()],
  define: { __INDIRIZZI_RETE__: JSON.stringify(command === 'serve' ? indirizziRete() : []) },
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  // «npm run copertura»: quanta parte di src/lib provano i test (rapporto anche in coverage/index.html)
  // Due gruppi: la logica (src/lib, ambiente node, veloce) e i componenti (src/components e src/famiglie, ambiente jsdom,
  // con @testing-library/svelte). I test di componente si chiamano `Nome.test.ts` accanto al componente.
  test: {
    coverage: { include: ['src/lib/**'], reporter: ['text-summary', 'html'] },
    projects: [
      { extends: true, test: { name: 'lib', include: ['src/**/*.test.ts'], exclude: ['src/components/**', 'src/famiglie/**', 'src/**/*.componente.test.ts'], environment: 'node' } },
      { extends: true, test: { name: 'componenti', include: ['src/components/**/*.test.ts', 'src/famiglie/**/*.test.ts', 'src/**/*.componente.test.ts'], environment: 'jsdom', setupFiles: ['./src/prova/jsdom.ts'] } },
    ],
  },
}));
