/**
 * Contrasti del sistema «Corsia» del gestionale, letti da app.css in entrambi i temi.
 * Se una coppia scende sotto la soglia il test fallisce, e con lui la CI.
 * Aggiungi qui ogni nuova coppia testo/sfondo o segno/sfondo che introduci.
 */
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const css = readFileSync(new URL('../app.css', import.meta.url), 'utf8');

function blocco(selettore: string): Record<string, string> {
  const i = css.indexOf(selettore);
  if (i < 0) throw new Error(`blocco ${selettore} non trovato in app.css`);
  const corpo = css.slice(css.indexOf('{', i) + 1, css.indexOf('\n}', i));
  const out: Record<string, string> = {};
  for (const m of corpo.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})\b/g)) out[m[1]] = m[2];
  return out;
}

const chiaro = blocco(':root {');
const temi = { chiaro, scuro: { ...chiaro, ...blocco(":root[data-tema='scuro'] {") } };

function luminanza(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
export function contrasto(a: string, b: string): number {
  const [x, y] = [luminanza(a), luminanza(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}

/** Testo: 4,5:1 (WCAG AA) */
const TESTO: [string, string][] = [
  ['ink', 'carta'], ['ink', 'surface'], ['ink', 'cloro-soft'], ['ink', 'surface-sunken'],
  ['muted', 'carta'], ['muted', 'surface'], ['muted', 'warn-bg'],
  ['cloro', 'carta'], ['cloro', 'surface'],
  ['cloro-ink', 'cloro-soft'], ['warn', 'warn-bg'], ['warn', 'surface'],
  ['danger', 'carta'], ['danger', 'surface'], ['seg-fg', 'surface-sunken'],
  ['on-fill', 'fill'], ['on-fill', 'ok'], ['on-cloro', 'cloro'], ['on-cloro', 'cloro-dark'],
  ['on-ink', 'hero'], ['on-ink-soft', 'hero'], ['on-ink-muted', 'hero'], ['on-ink-faint', 'hero'],
  ['area-m-fg', 'area-m-bg'], ['area-f-fg', 'area-f-bg'], ['area-p-fg', 'area-p-bg'],
  ['area-a-fg', 'area-a-bg'], ['area-x-fg', 'area-x-bg'], ['area-v-fg', 'area-v-bg'],
  // pulsanti-icona: grigi a riposo, inchiostro al passaggio, pieni quando premuti, rossi se distruttivi
  ['muted', 'surface-sunken'], ['danger', 'surface-sunken'], ['cloro-ink', 'cloro-soft'],
  // esito del tornello
  ['passa-fg', 'passa-bg'], ['ferma-fg', 'ferma-bg'], ['oro-fg', 'oro-bg'],
];

/** Bordi dei controlli, segni e anello di focus: 3:1 (WCAG 1.4.11) */
const SEGNI: [string, string][] = [
  ['line-strong', 'surface'], ['line-strong', 'carta'], ['switch-off', 'surface'], ['chevron', 'surface'],
  ['cloro', 'carta'], ['cloro', 'surface'], ['ok', 'surface'], ['mint', 'hero'],
  ['knob', 'switch-off'], ['on-cloro', 'cloro'],
  ['area-m', 'surface'], ['area-f', 'surface'], ['area-p', 'surface'], ['area-a', 'surface'], ['area-x', 'surface'],
  // le icone dei pulsanti-icona sono segni: muted, cloro e danger su card e pagina
  ['muted', 'surface'], ['muted', 'carta'], ['danger', 'surface'], ['cloro', 'surface-sunken'],
  ['passa', 'surface'], ['ferma', 'surface'],
  // spunta e croce dell'esito del tornello, disegnate nel colore della superficie
  ['surface', 'passa'], ['surface', 'ferma'],
];

describe.each(Object.entries(temi))('tema %s', (_, t) => {
  it.each(TESTO)('testo %s su %s ≥ 4,5:1', (fg, bg) => {
    expect(t[fg], `--${fg} manca`).toBeDefined();
    expect(t[bg], `--${bg} manca`).toBeDefined();
    expect(contrasto(t[fg], t[bg])).toBeGreaterThanOrEqual(4.5);
  });
  it.each(SEGNI)('segno %s su %s ≥ 3:1', (fg, bg) => {
    expect(t[fg], `--${fg} manca`).toBeDefined();
    expect(t[bg], `--${bg} manca`).toBeDefined();
    expect(contrasto(t[fg], t[bg])).toBeGreaterThanOrEqual(3);
  });
});
