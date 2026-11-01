import { describe, expect, it } from 'vitest';
import type { Evento } from './api';
import { conteggi, eventiDaCreare, festivita, mesi, pasqua, prossima, quando, statoDel, unAnnoDopo, voci } from './calendario';

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
    expect(quando(elenco[0])).toBe('8 dic 2026 · tutto il giorno');
    expect(quando(elenco[1])).toBe('24 dic 2026 · chiude alle 13:00');
    expect(quando(elenco[2])).toBe('dal 25 dic 2026 al 6 gen 2027 · 13 giorni');
    expect(quando(elenco[3])).toBe('10 feb 2027 · apre alle 10:00 e chiude alle 18:00');
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

  it('copia nella stagione dopo e eventi da creare', () => {
    expect(unAnnoDopo('2026-12-08')).toBe('2027-12-08');
    expect(unAnnoDopo('2028-02-29')).toBe('2029-02-28');
    expect(eventiDaCreare({ nome: 'X', dal: '2026-12-08', al: '2026-12-08', tipo: 'chiuso', apre: null, chiude: null }))
      .toEqual([{ titolo: 'X', data: '2026-12-08', dataFine: null, inizio: '00:00', fine: '23:59' }]);
    expect(eventiDaCreare({ nome: 'X', dal: '2026-12-08', al: '2026-12-09', tipo: 'ridotto', apre: '10:00', chiude: '18:00' })).toHaveLength(2);
  });
});
