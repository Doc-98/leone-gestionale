import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { leggiPreferenza, salvaPreferenza } from './preferenze';
import { recenti, ricordaAperto } from './recenti';
import { sessione } from './sessione.svelte';
import type { Me } from './api';

/** Le scelte di vista e i clienti recenti restano su questo dispositivo, separati per account. */
const memoria = new Map<string, string>();

function entraCome(accountId: string) {
  sessione.utente = { accountId } as Me;
}

beforeEach(() => {
  memoria.clear();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => memoria.get(k) ?? null,
    setItem: (k: string, v: string) => memoria.set(k, v),
    removeItem: (k: string) => memoria.delete(k),
  });
  entraCome('acc-1');
});

afterEach(() => {
  vi.unstubAllGlobals();
  sessione.utente = null;
});

describe('preferenze', () => {
  it('ogni account ritrova le sue scelte', () => {
    salvaPreferenza('piano.vista', 'settimana');
    expect(memoria.get('leone:acc-1:piano.vista')).toBe('settimana');
    expect(leggiPreferenza('piano.vista', 'giorno')).toBe('settimana');
    entraCome('acc-2');
    expect(leggiPreferenza('piano.vista', 'giorno')).toBe('giorno');
  });

  it('un valore non più ammesso torna al predefinito', () => {
    salvaPreferenza('piano.vista', 'mese-vecchio');
    expect(leggiPreferenza('piano.vista', 'giorno', ['giorno', 'settimana'] as const)).toBe('giorno');
  });

  it('senza memoria del browser vale il predefinito, e salvare non rompe niente', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => { throw new Error('bloccato'); },
      setItem: () => { throw new Error('bloccato'); },
    });
    expect(() => salvaPreferenza('x', 'y')).not.toThrow();
    expect(leggiPreferenza('x', 'predefinito')).toBe('predefinito');
  });

  it('prima di entrare le scelte sono dell\'«anonimo»', () => {
    sessione.utente = null;
    salvaPreferenza('tema', 'scuro');
    expect(memoria.has('leone:anonimo:tema')).toBe(true);
  });
});

describe('clienti aperti di recente', () => {
  const cliente = (id: string) => ({ id, nome: 'Nome' + id, cognome: 'Cognome', eta: 30 });

  it('il più recente per primo, senza doppioni', () => {
    ricordaAperto(cliente('a'), new Date('2028-01-01T10:00:00Z'));
    ricordaAperto(cliente('b'), new Date('2028-01-01T11:00:00Z'));
    ricordaAperto(cliente('a'), new Date('2028-01-01T12:00:00Z'));
    expect(recenti().map((r) => r.id)).toEqual(['a', 'b']);
    expect(recenti()[0].quando).toBe('2028-01-01T12:00:00.000Z');
  });

  it('al massimo otto', () => {
    for (let i = 0; i < 10; i++) ricordaAperto(cliente(String(i)));
    expect(recenti()).toHaveLength(8);
    expect(recenti()[0].id).toBe('9');
  });

  it('dati rovinati nella memoria non rompono la pagina', () => {
    memoria.set('leone:acc-1:clienti.recenti', '{non è json');
    expect(recenti()).toEqual([]);
    memoria.set('leone:acc-1:clienti.recenti', JSON.stringify([{ id: 'ok', nome: 'A' }, null, { nome: 'senza id' }, 42]));
    expect(recenti().map((r) => r.id)).toEqual(['ok']);
    memoria.set('leone:acc-1:clienti.recenti', JSON.stringify({ non: 'una lista' }));
    expect(recenti()).toEqual([]);
  });
});
