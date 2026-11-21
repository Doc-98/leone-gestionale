import { describe, expect, it } from 'vitest';
import { ordinaVoci, voceDopoTasto } from './menu';

/**
 * Specifica: /tmp/b7/piano.md §2.3 e design system «Menu «⋯»».
 * - `ordinaVoci`: le voci con `elimina` in fondo (al massimo una), le altre nell'ordine dato; errore se più di 7 voci
 *   o più di una voce che elimina (è un errore di chi scrive la pagina).
 * - `voceDopoTasto`: ArrowDown/ArrowUp a giro, Home, End; -1 per gli altri tasti; salta gli indici in `spente`.
 */
type V = { testo: string; elimina?: string };
const v = (testo: string, elimina?: string): V => (elimina ? { testo, elimina } : { testo });
const testi = (voci: V[]) => voci.map((x) => x.testo);

describe('ordinaVoci', () => {
  it('senza voci che eliminano lascia l\'ordine dato', () => {
    const voci = [v('a'), v('b'), v('c')];
    expect(testi(ordinaVoci(voci))).toEqual(['a', 'b', 'c']);
  });

  it('la voce che elimina, data per prima, va in fondo; le altre restano nell\'ordine dato', () => {
    const voci = [v('Elimina il cliente', 'Elimina?'), v('a'), v('b'), v('c')];
    expect(testi(ordinaVoci(voci))).toEqual(['a', 'b', 'c', 'Elimina il cliente']);
  });

  it('la voce che elimina, data in mezzo, va in fondo', () => {
    const voci = [v('a'), v('Rimuovi il tutore', 'Rimuovi?'), v('b')];
    expect(testi(ordinaVoci(voci))).toEqual(['a', 'b', 'Rimuovi il tutore']);
  });

  it('se è già ultima resta ultima', () => {
    const voci = [v('a'), v('b'), v('x', 'Elimina?')];
    expect(testi(ordinaVoci(voci))).toEqual(['a', 'b', 'x']);
  });

  it('elenco vuoto: elenco vuoto; una sola voce: la stessa', () => {
    expect(ordinaVoci([])).toEqual([]);
    expect(testi(ordinaVoci([v('solo')]))).toEqual(['solo']);
    expect(testi(ordinaVoci([v('solo', 'Elimina?')]))).toEqual(['solo']);
  });

  it('non cambia l\'elenco ricevuto e restituisce le stesse voci (non copie modificate)', () => {
    const e = v('x', 'Elimina?');
    const voci = [e, v('a')];
    const prima = [...voci];
    const fuori = ordinaVoci(voci);
    expect(voci).toEqual(prima);
    expect(voci[0]).toBe(e);
    expect(fuori[fuori.length - 1]).toBe(e);
  });

  it('sette voci vanno bene (confine), anche con l\'eliminazione', () => {
    const sette = Array.from({ length: 7 }, (_, i) => v(`v${i}`));
    expect(ordinaVoci(sette)).toHaveLength(7);
    const conElimina = [...sette.slice(0, 6), v('x', 'Elimina?')];
    expect(ordinaVoci(conElimina)).toHaveLength(7);
  });

  it('otto voci è un errore', () => {
    const otto = Array.from({ length: 8 }, (_, i) => v(`v${i}`));
    expect(() => ordinaVoci(otto)).toThrow();
  });

  it('due voci che eliminano è un errore', () => {
    expect(() => ordinaVoci([v('a', 'Elimina?'), v('b', 'Rimuovi?'), v('c')])).toThrow();
    expect(() => ordinaVoci([v('a', 'Elimina?'), v('b', 'Elimina?')])).toThrow();
  });
});

describe('voceDopoTasto', () => {
  it('ArrowDown va alla voce dopo', () => {
    expect(voceDopoTasto('ArrowDown', 0, 4)).toBe(1);
    expect(voceDopoTasto('ArrowDown', 2, 4)).toBe(3);
  });

  it('ArrowDown sull\'ultima torna alla prima (a giro)', () => {
    expect(voceDopoTasto('ArrowDown', 3, 4)).toBe(0);
  });

  it('ArrowUp va alla voce prima; sulla prima salta all\'ultima (a giro)', () => {
    expect(voceDopoTasto('ArrowUp', 3, 4)).toBe(2);
    expect(voceDopoTasto('ArrowUp', 0, 4)).toBe(3);
  });

  it('Home va alla prima, End all\'ultima, da qualunque posizione', () => {
    expect(voceDopoTasto('Home', 2, 5)).toBe(0);
    expect(voceDopoTasto('End', 2, 5)).toBe(4);
    expect(voceDopoTasto('Home', 4, 5)).toBe(0);
    expect(voceDopoTasto('End', 0, 5)).toBe(4);
  });

  it('gli altri tasti danno -1', () => {
    for (const t of ['Tab', 'Enter', ' ', 'Escape', 'a', 'ArrowLeft', 'ArrowRight', 'PageDown', ''])
      expect(voceDopoTasto(t, 1, 4)).toBe(-1);
  });

  it('una sola voce: le frecce restano lì', () => {
    expect(voceDopoTasto('ArrowDown', 0, 1)).toBe(0);
    expect(voceDopoTasto('ArrowUp', 0, 1)).toBe(0);
  });

  it('le voci spente si saltano con le frecce', () => {
    const spente = new Set([1, 2]);
    expect(voceDopoTasto('ArrowDown', 0, 5, spente)).toBe(3);
    expect(voceDopoTasto('ArrowUp', 3, 5, spente)).toBe(0);
  });

  it('le voci spente si saltano anche nel giro', () => {
    expect(voceDopoTasto('ArrowDown', 4, 5, new Set([0]))).toBe(1);
    expect(voceDopoTasto('ArrowUp', 1, 5, new Set([0]))).toBe(4);
    expect(voceDopoTasto('ArrowDown', 3, 5, new Set([4, 0]))).toBe(1);
  });

  it('Home ed End vanno alla prima e all\'ultima voce accese', () => {
    expect(voceDopoTasto('Home', 3, 5, new Set([0, 1]))).toBe(2);
    expect(voceDopoTasto('End', 1, 5, new Set([4]))).toBe(3);
  });

  it('un insieme vuoto di spente è come nessun insieme', () => {
    expect(voceDopoTasto('ArrowDown', 0, 3, new Set())).toBe(1);
  });

  it('se tutte le altre sono spente, la corrente resta (o -1), mai un indice di una voce spenta', () => {
    const r = voceDopoTasto('ArrowDown', 1, 3, new Set([0, 2]));
    expect([1, -1]).toContain(r);
  });

  it('nessuna voce: -1', () => {
    expect(voceDopoTasto('ArrowDown', 0, 0)).toBe(-1);
    expect(voceDopoTasto('Home', 0, 0)).toBe(-1);
  });

  it('tutte spente: mai un indice di una voce spenta', () => {
    const r = voceDopoTasto('ArrowDown', 0, 2, new Set([0, 1]));
    expect(r === -1 || ![0, 1].includes(r)).toBe(true);
  });

  it('il risultato sta sempre tra -1 e n-1 (prova su tutte le combinazioni piccole)', () => {
    for (const t of ['ArrowDown', 'ArrowUp', 'Home', 'End', 'x'])
      for (let n = 1; n <= 7; n++)
        for (let i = 0; i < n; i++) {
          const r = voceDopoTasto(t, i, n);
          expect(r).toBeGreaterThanOrEqual(-1);
          expect(r).toBeLessThan(n);
        }
  });
});
