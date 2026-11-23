import { describe, expect, it } from 'vitest';
import type { Servizio } from './api';
import { riassuntoServizio, usiConRicarica } from './servizi';

/**
 * Specifica (brief coerenza blocco 7, lotto 4, «lib/servizi.ts»): funzioni pure per le righe dei servizi.
 *  - riassuntoServizio: le parti in fila con « · »: crediti («1 credito», «3 crediti», 0 -> «gratis»), poi, se > 0 e i
 *    crediti non sono 0, «N gratis al giorno», poi, se c'è, «N minuti» / «1 minuto».
 *  - usiConRicarica: per i servizi accesi con crediti > 0, nell'ordine dato, al massimo due: usi = floor(crediti / s.crediti).
 */
const servizio = (nome: string, extra: Partial<Servizio> = {}): Servizio => ({
  id: nome, nome, crediti: 1, gratuitiAlGiorno: 0, minuti: null, attivo: true, ordine: 0, ...extra,
});

describe('riassuntoServizio – i crediti', () => {
  it('1 credito, singolare', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 0, minuti: null })).toBe('1 credito');
  });
  it('più crediti, plurale', () => {
    expect(riassuntoServizio({ crediti: 3, gratuitiAlGiorno: 0, minuti: null })).toBe('3 crediti');
    expect(riassuntoServizio({ crediti: 2, gratuitiAlGiorno: 0, minuti: null })).toBe('2 crediti');
  });
  it('0 crediti: «gratis»', () => {
    expect(riassuntoServizio({ crediti: 0, gratuitiAlGiorno: 0, minuti: null })).toBe('gratis');
  });
});

describe('riassuntoServizio – gratis al giorno', () => {
  it('1 gratis al giorno, singolare', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 1, minuti: null })).toBe('1 credito · 1 gratis al giorno');
  });
  it('2 gratis al giorno, plurale', () => {
    expect(riassuntoServizio({ crediti: 2, gratuitiAlGiorno: 2, minuti: null })).toBe('2 crediti · 2 gratis al giorno');
  });
  it('con 0 usi gratuiti la parte non compare', () => {
    expect(riassuntoServizio({ crediti: 2, gratuitiAlGiorno: 0, minuti: null })).not.toContain('gratis al giorno');
  });
  it('con 0 crediti il servizio è già «gratis» e NON dice anche «gratis al giorno»', () => {
    const t = riassuntoServizio({ crediti: 0, gratuitiAlGiorno: 2, minuti: null });
    expect(t).toBe('gratis');
    expect(t).not.toContain('al giorno');
  });
});

describe('riassuntoServizio – minuti', () => {
  it('5 minuti, plurale', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 0, minuti: 5 })).toBe('1 credito · 5 minuti');
  });
  it('1 minuto, singolare', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 0, minuti: 1 })).toBe('1 credito · 1 minuto');
  });
  it('minuti assenti (null): niente parte dei minuti', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 1, minuti: null })).not.toContain('minut');
  });
  it('anche un servizio gratis mostra i minuti, dopo «gratis»', () => {
    expect(riassuntoServizio({ crediti: 0, gratuitiAlGiorno: 0, minuti: 10 })).toBe('gratis · 10 minuti');
  });
});

describe('riassuntoServizio – ordine e separatore', () => {
  it('tutte le parti: crediti, gratis al giorno, minuti, separate da « · »', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 1, minuti: 5 })).toBe('1 credito · 1 gratis al giorno · 5 minuti');
    expect(riassuntoServizio({ crediti: 4, gratuitiAlGiorno: 2, minuti: 1 })).toBe('4 crediti · 2 gratis al giorno · 1 minuto');
  });
  it('il separatore è «spazio punto-medio spazio»', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 1, minuti: 5 }).split(' · ')).toEqual(['1 credito', '1 gratis al giorno', '5 minuti']);
  });
  it('una sola parte: nessun separatore', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 0, minuti: null })).not.toContain('·');
  });
});

describe('usiConRicarica', () => {
  it('usi = crediti della ricarica divisi per i crediti del servizio (per difetto)', () => {
    expect(usiConRicarica(25, [servizio('Phon', { crediti: 1 }), servizio('Docce', { crediti: 2 })])).toEqual([
      { nome: 'Phon', usi: 25 },
      { nome: 'Docce', usi: 12 },
    ]);
  });
  it('arrotonda per difetto, mai per eccesso', () => {
    expect(usiConRicarica(7, [servizio('Docce', { crediti: 2 })])).toEqual([{ nome: 'Docce', usi: 3 }]);
    expect(usiConRicarica(29, [servizio('Sauna', { crediti: 10 })])).toEqual([{ nome: 'Sauna', usi: 2 }]);
  });
  it('salta i servizi spenti', () => {
    expect(usiConRicarica(10, [servizio('Phon', { attivo: false }), servizio('Docce', { crediti: 2 })])).toEqual([{ nome: 'Docce', usi: 5 }]);
  });
  it('salta i servizi gratuiti (crediti 0): niente divisione per zero', () => {
    const r = usiConRicarica(10, [servizio('Fontanella', { crediti: 0 }), servizio('Phon', { crediti: 1 })]);
    expect(r).toEqual([{ nome: 'Phon', usi: 10 }]);
  });
  it('al massimo due servizi, i primi nell\'ordine dato', () => {
    const r = usiConRicarica(12, [servizio('A', { crediti: 1 }), servizio('B', { crediti: 2 }), servizio('C', { crediti: 3 })]);
    expect(r.map((x) => x.nome)).toEqual(['A', 'B']);
  });
  it('il limite di due si applica dopo aver tolto spenti e gratuiti', () => {
    const r = usiConRicarica(12, [
      servizio('Spento', { attivo: false }), servizio('Gratis', { crediti: 0 }),
      servizio('B', { crediti: 2 }), servizio('C', { crediti: 3 }), servizio('D', { crediti: 4 }),
    ]);
    expect(r).toEqual([{ nome: 'B', usi: 6 }, { nome: 'C', usi: 4 }]);
  });
  it('mantiene l\'ordine dato, non quello alfabetico né quello dei crediti', () => {
    expect(usiConRicarica(10, [servizio('Zeta', { crediti: 5 }), servizio('Alfa', { crediti: 1 })]).map((x) => x.nome)).toEqual(['Zeta', 'Alfa']);
  });
  it('lista vuota: nessun uso', () => {
    expect(usiConRicarica(25, [])).toEqual([]);
  });
  it('nessun servizio utile: nessun uso', () => {
    expect(usiConRicarica(25, [servizio('Spento', { attivo: false }), servizio('Gratis', { crediti: 0 })])).toEqual([]);
  });
});
