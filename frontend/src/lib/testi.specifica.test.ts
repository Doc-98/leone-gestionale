import { describe, expect, it } from 'vitest';
import { CARICO } from './testi';

/** Specifica §2.1: il testo del caricamento sta in una costante sola, «Carico…» (con il carattere «…», non tre punti). */
describe('testi comuni', () => {
  it('CARICO è «Carico…»', () => {
    expect(CARICO).toBe('Carico…');
  });

  it('usa il carattere puntini «…» (U+2026), non tre punti', () => {
    expect(CARICO.endsWith('…')).toBe(true);
    expect(CARICO).not.toContain('...');
  });
});
