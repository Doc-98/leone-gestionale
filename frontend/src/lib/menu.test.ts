import { describe, expect, it } from 'vitest';
import { MAX_VOCI_MENU, ordinaVoci, voceDopoTasto } from './menu';

const v = (testo: string, extra: { elimina?: string; rossa?: boolean } = {}) => ({ testo, ...extra });

describe('ordinaVoci', () => {
  it('lascia l\'ordine dato se nessuna voce chiude il menu', () => {
    expect(ordinaVoci([v('a'), v('b'), v('c')]).map((x) => x.testo)).toEqual(['a', 'b', 'c']);
  });
  it('porta in fondo la voce che elimina, dopo la voce rossa che apre un modulo', () => {
    const r = ordinaVoci([v('elimina', { elimina: 'Elimina?' }), v('banna', { rossa: true }), v('a'), v('b')]);
    expect(r.map((x) => x.testo)).toEqual(['a', 'b', 'banna', 'elimina']);
  });
  it('non cambia la lista data', () => {
    const dato = [v('x', { elimina: 'Rimuovi?' }), v('y')];
    ordinaVoci(dato);
    expect(dato.map((x) => x.testo)).toEqual(['x', 'y']);
  });
  it('accetta sette voci e rifiuta otto', () => {
    const sette = Array.from({ length: MAX_VOCI_MENU }, (_, i) => v(`v${i}`));
    expect(ordinaVoci(sette)).toHaveLength(7);
    expect(() => ordinaVoci([...sette, v('troppo')])).toThrow(/al massimo 7/);
  });
  it('rifiuta più di una voce che elimina', () => {
    expect(() => ordinaVoci([v('a', { elimina: 'Elimina?' }), v('b', { elimina: 'Rimuovi?' })])).toThrow(/una voce che elimina/);
  });
  it('un menu vuoto resta vuoto', () => {
    expect(ordinaVoci([])).toEqual([]);
  });
});

describe('voceDopoTasto', () => {
  it('giù e su vanno a giro', () => {
    expect(voceDopoTasto('ArrowDown', 0, 3)).toBe(1);
    expect(voceDopoTasto('ArrowDown', 2, 3)).toBe(0);
    expect(voceDopoTasto('ArrowUp', 1, 3)).toBe(0);
    expect(voceDopoTasto('ArrowUp', 0, 3)).toBe(2);
  });
  it('Inizio e Fine', () => {
    expect(voceDopoTasto('Home', 2, 4)).toBe(0);
    expect(voceDopoTasto('End', 0, 4)).toBe(3);
  });
  it('senza focus (-1): giù va alla prima, su all\'ultima', () => {
    expect(voceDopoTasto('ArrowDown', -1, 4)).toBe(0);
    expect(voceDopoTasto('ArrowUp', -1, 4)).toBe(3);
  });
  it('salta le voci spente, anche a giro, anche Inizio e Fine', () => {
    const spente = new Set([1, 3]);
    expect(voceDopoTasto('ArrowDown', 0, 5, spente)).toBe(2);
    expect(voceDopoTasto('ArrowDown', 4, 5, spente)).toBe(0);
    expect(voceDopoTasto('ArrowUp', 2, 5, spente)).toBe(0);
    expect(voceDopoTasto('ArrowUp', 0, 5, spente)).toBe(4);
    expect(voceDopoTasto('Home', 4, 5, new Set([0]))).toBe(1);
    expect(voceDopoTasto('End', 0, 5, new Set([4]))).toBe(3);
  });
  it('tasti estranei e menu senza voci accese: -1', () => {
    expect(voceDopoTasto('a', 0, 3)).toBe(-1);
    expect(voceDopoTasto('Tab', 0, 3)).toBe(-1);
    expect(voceDopoTasto('ArrowDown', 0, 0)).toBe(-1);
    expect(voceDopoTasto('ArrowDown', 0, 2, new Set([0, 1]))).toBe(-1);
  });
  it('con una sola voce accesa resta lì', () => {
    expect(voceDopoTasto('ArrowDown', 1, 3, new Set([0, 2]))).toBe(1);
    expect(voceDopoTasto('ArrowUp', 1, 3, new Set([0, 2]))).toBe(1);
  });
});
