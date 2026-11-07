import { afterEach, describe, expect, it, vi } from 'vitest';
import { CHIAVE_TEMA, temaEffettivo } from './tema.svelte';

describe('temaEffettivo', () => {
  it('con «Sistema» segue il dispositivo', () => {
    expect(temaEffettivo('sistema', true)).toBe('scuro');
    expect(temaEffettivo('sistema', false)).toBe('chiaro');
  });
  it('una scelta esplicita vale qualunque sia il dispositivo', () => {
    expect(temaEffettivo('chiaro', true)).toBe('chiaro');
    expect(temaEffettivo('scuro', false)).toBe('scuro');
  });
});

describe('la scelta del tema', () => {
  afterEach(() => vi.unstubAllGlobals());

  async function nuovoTema(salvato: string | null, sistemaScuro: boolean) {
    const memoria = new Map<string, string>(salvato ? [[CHIAVE_TEMA, salvato]] : []);
    let alCambio: (e: { matches: boolean }) => void = () => {};
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => memoria.get(k) ?? null,
      setItem: (k: string, v: string) => memoria.set(k, v),
      removeItem: (k: string) => memoria.delete(k),
    });
    vi.stubGlobal('window', { matchMedia: () => ({ matches: sistemaScuro, addEventListener: (_: string, f: typeof alCambio) => (alCambio = f) }) });
    const html = { dataset: {} as Record<string, string> };
    vi.stubGlobal('document', { documentElement: html });
    vi.resetModules();
    const { tema } = await import('./tema.svelte');
    return { tema, memoria, html, sistemaCambia: (scuro: boolean) => alCambio({ matches: scuro }) };
  }

  it('parte dalla scelta salvata e la applica alla pagina', async () => {
    const { tema, html } = await nuovoTema('scuro', false);
    expect(tema.scelta).toBe('scuro');
    expect(html.dataset.tema).toBe('scuro');
  });

  it('una scelta non valida vale «Sistema»', async () => {
    const { tema, html } = await nuovoTema('viola', true);
    expect(tema.scelta).toBe('sistema');
    expect(html.dataset.tema).toBe('scuro');
  });

  it('una scelta esplicita si salva; «Sistema» toglie quella salvata e segue il dispositivo', async () => {
    const { tema, memoria, html, sistemaCambia } = await nuovoTema(null, false);
    tema.imposta('scuro');
    expect(memoria.get(CHIAVE_TEMA)).toBe('scuro');
    expect(html.dataset.tema).toBe('scuro');
    sistemaCambia(true); // il dispositivo cambia, ma la scelta esplicita vince
    expect(html.dataset.tema).toBe('scuro');
    tema.imposta('sistema');
    expect(memoria.has(CHIAVE_TEMA)).toBe(false);
    expect(html.dataset.tema).toBe('scuro');
    sistemaCambia(false);
    expect(html.dataset.tema).toBe('chiaro');
  });
});
