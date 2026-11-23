import { describe, expect, it } from 'vitest';
import type { Evento } from './api';
import { chiusureDaCopiare, conteggi, eventiDaCreare, festivita, mesi, pasqua, prossima, quando, spostaDiAnni, statoDel, voci, type Voce } from './calendario';

let n = 0;
const ev = (titolo: string, data: string, dataFine: string | null, inizio = '00:00', fine = '23:59'): Evento => ({
  id: `e${++n}`, impiantoId: 'i', tipo: 'CHIUSURA', titolo, data, dataFine, inizio: `${inizio}:00`, fine: `${fine}:00`, spazi: [],
  istruttore: null, note: null, annullato: false,
});

describe('calendario della stagione', () => {
  const elenco = voci([
    ev('Vacanze di Natale', '2026-12-25', '2027-01-06'),
    ev('Immacolata', '2026-12-08', null),
    ev('Vigilia', '2026-12-24', null, '13:00', '23:59'),
    ev('Assemblea', '2027-02-10', null, '00:00', '10:00'),
    ev('Assemblea', '2027-02-10', null, '18:00', '23:59'),
  ]);

  it('raggruppa gli eventi in voci, con tipo e orari', () => {
    expect(elenco.map((v) => v.nome)).toEqual(['Immacolata', 'Vigilia', 'Vacanze di Natale', 'Assemblea']);
    expect(elenco[1]).toMatchObject({ tipo: 'ridotto', apre: null, chiude: '13:00' });
    expect(elenco[3]).toMatchObject({ tipo: 'ridotto', apre: '10:00', chiude: '18:00' });
    expect(elenco[3].eventi).toHaveLength(2);
  });

  it('descrive quando', () => {
    expect(quando(elenco[0])).toBe('08/12/2026 · tutto il giorno');
    expect(quando(elenco[1])).toBe('24/12/2026 · chiude alle 13:00');
    expect(quando(elenco[2])).toBe('dal 25/12/2026 al 06/01/2027 · 13 giorni');
    expect(quando(elenco[3])).toBe('10/02/2027 · apre alle 10:00 e chiude alle 18:00');
  });

  it('stato del giorno e conteggi', () => {
    expect(statoDel('2027-01-01', elenco)?.nome).toBe('Vacanze di Natale');
    expect(statoDel('2027-01-07', elenco)).toBeUndefined();
    const c = conteggi('2026-09-01', '2027-08-31', elenco);
    expect(c).toEqual({ chiusi: 14, ridotti: 2, aperti: 365 - 14 });
  });

  it('prossima chiusura', () => {
    expect(prossima(elenco, '2026-11-01')).toMatchObject({ voce: { nome: 'Immacolata' }, traGiorni: 37 });
    expect(prossima(elenco, '2026-12-30')).toMatchObject({ voce: { nome: 'Vacanze di Natale' }, traGiorni: 0 });
    expect(prossima(elenco, '2027-03-01')).toBeNull();
  });

  it('mesi della stagione partendo dal lunedì', () => {
    const m = mesi('2026-09-01', '2027-08-31');
    expect(m).toHaveLength(12);
    expect(m[0]).toMatchObject({ chiave: '2026-09', nome: 'Settembre 2026', vuote: 1 });
    expect(m[0].giorni).toHaveLength(30);
    expect(m[11].nome).toBe('Agosto 2027');
  });

  it('festività nazionali, con Pasqua', () => {
    expect(pasqua(2027)).toBe('2027-03-28');
    expect(pasqua(2026)).toBe('2026-04-05');
    const f = festivita('2026-09-01', '2027-08-31');
    expect(f[0]).toEqual(['Ognissanti', '2026-11-01', '2026-11-01']);
    expect(f).toContainEqual(['Pasqua e Pasquetta', '2027-03-28', '2027-03-29']);
    expect(f.at(-1)).toEqual(['Ferragosto', '2027-08-15', '2027-08-15']);
  });

  it('eventi da creare', () => {
    expect(eventiDaCreare({ nome: 'X', dal: '2026-12-08', al: '2026-12-08', tipo: 'chiuso', apre: null, chiude: null }))
      .toEqual([{ titolo: 'X', data: '2026-12-08', dataFine: null, inizio: '00:00', fine: '23:59' }]);
    expect(eventiDaCreare({ nome: 'X', dal: '2026-12-08', al: '2026-12-09', tipo: 'ridotto', apre: '10:00', chiude: '18:00' })).toHaveLength(2);
  });
});

describe('spostaDiAnni e chiusureDaCopiare', () => {
  const v = (nome: string, dal: string, al: string): Voce => ({ nome, dal, al, tipo: 'chiuso', apre: null, chiude: null, eventi: [`e-${nome}`] });
  it('sposta di più anni, anche all\'indietro, e il 29 febbraio diventa il 28', () => {
    expect(spostaDiAnni('2026-12-08', 2)).toBe('2028-12-08');
    expect(spostaDiAnni('2028-02-29', -1)).toBe('2027-02-28');
  });
  it('copia dalla stagione precedente: un anno dopo, solo ciò che cade dentro e non c\'è già', () => {
    const vecchie = [v('Vacanze di Natale', '2025-12-25', '2025-12-26'), v('Ferragosto', '2026-08-15', '2026-08-15'), v('Già segnata', '2025-11-01', '2025-11-01')];
    const nuove = chiusureDaCopiare(vecchie, { dal: '2025-09-01' }, { dal: '2026-09-01', al: '2027-08-31' }, [v('Già segnata', '2026-11-01', '2026-11-01')]);
    expect(nuove.map((x) => [x.nome, x.dal, x.al])).toEqual([['Vacanze di Natale', '2026-12-25', '2026-12-26'], ['Ferragosto', '2027-08-15', '2027-08-15']]);
  });
  it('da una stagione di due anni prima sposta di due anni', () => {
    const nuove = chiusureDaCopiare([v('Natale', '2024-12-25', '2024-12-25')], { dal: '2024-09-01' }, { dal: '2026-09-01', al: '2027-08-31' }, []);
    expect(nuove[0].dal).toBe('2026-12-25');
  });
  it('niente da copiare: elenco vuoto', () => {
    expect(chiusureDaCopiare([], { dal: '2025-09-01' }, { dal: '2026-09-01', al: '2027-08-31' }, [])).toEqual([]);
  });
});

describe('chiusureDaCopiare – festività', () => {
  const v = (nome: string, dal: string, al: string): Voce => ({ nome, dal, al, tipo: 'chiuso', apre: null, chiude: null, eventi: [`e-${nome}`] });
  it('Pasqua e Pasquetta si ricalcolano per la stagione di arrivo, non si spostano di un anno', () => {
    // 2026: Pasqua il 5 aprile; 2027: il 28 marzo (Pasquetta il 29)
    const nuove = chiusureDaCopiare([v('Pasqua e Pasquetta', pasqua(2026), '2026-04-06')], { dal: '2025-09-01' }, { dal: '2026-09-01', al: '2027-08-31' }, []);
    expect(nuove.map((x) => [x.nome, x.dal, x.al])).toEqual([['Pasqua e Pasquetta', pasqua(2027), '2027-03-29']]);
    expect(nuove[0].dal).not.toBe('2027-04-05');
  });
  it('dopo «Aggiungi le festività nazionali» la copia non porta una seconda Pasqua', () => {
    const gia = [v('Pasqua e Pasquetta', pasqua(2027), '2027-03-29'), v('Natale', '2026-12-25', '2026-12-25')];
    const vecchie = [v('Pasqua e Pasquetta', pasqua(2026), '2026-04-06'), v('Natale', '2025-12-25', '2025-12-25'), v('Gita sociale', '2026-05-10', '2026-05-10')];
    const nuove = chiusureDaCopiare(vecchie, { dal: '2025-09-01' }, { dal: '2026-09-01', al: '2027-08-31' }, gia);
    expect(nuove.map((x) => x.nome)).toEqual(['Gita sociale']);
  });
  it('una voce con il nome di una festività fissa prende la data della festività', () => {
    const nuove = chiusureDaCopiare([v('Ferragosto', '2026-08-14', '2026-08-14')], { dal: '2025-09-01' }, { dal: '2026-09-01', al: '2027-08-31' }, []);
    expect(nuove[0].dal).toBe('2027-08-15');
  });
});
