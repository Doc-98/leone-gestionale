import { describe, expect, it } from 'vitest';
import type { Occorrenza, SpazioPiano } from './api';
import { colonneSpazi, disponi, etichettaSpazi, finestraOraria, intervalliColonne } from './piano';

const sp = (id: string, nome: string, tipo: string, padreId: string | null = null): SpazioPiano => ({ id, impiantoId: 'i', nome, tipo, padreId });
const SPAZI = [
  sp('vg', 'Vasca grande', 'VASCA'),
  sp('vp', 'Vasca piccola', 'VASCA'),
  sp('sm', 'Spogliatoio maschile', 'SPOGLIATOIO'),
  sp('c1', 'Corsia 1', 'CORSIA', 'vg'),
  sp('c2', 'Corsia 2', 'CORSIA', 'vg'),
  sp('c3', 'Corsia 3', 'CORSIA', 'vg'),
];

const occ = (inizio: string, fine: string, extra: Partial<Occorrenza> = {}): Occorrenza => ({
  chiave: inizio + fine, tipo: 'LEZIONE', categoria: 'SCUOLA_NUOTO', titolo: 't', dettaglio: null, data: '2026-10-19', inizio, fine,
  spazi: [], istruttore: null, stato: 'REGOLARE', nota: null, lezioneId: null, corsoId: null, eventoId: null, eccezioneId: null,
  dataOriginale: null, conflitti: [], ...extra,
});

describe('spazi del piano', () => {
  it('le colonne sono le corsie di ogni vasca, o la vasca se non ne ha; niente spogliatoi', () => {
    expect(colonneSpazi(SPAZI).map((c) => c.id)).toEqual(['c1', 'c2', 'c3', 'vp']);
  });

  it('una vasca intera copre le sue corsie; corsie non vicine danno più intervalli', () => {
    const col = colonneSpazi(SPAZI);
    expect(intervalliColonne(['vg'], col)).toEqual([[0, 3]]);
    expect(intervalliColonne(['c1', 'c3'], col)).toEqual([[0, 1], [2, 3]]);
    expect(intervalliColonne(['c3', 'vp'], col)).toEqual([[2, 4]]);
  });

  it('etichette leggibili', () => {
    expect(etichettaSpazi(['c2', 'c1'], SPAZI)).toBe('Corsie 1–2 · Vasca grande');
    expect(etichettaSpazi(['c1', 'c3'], SPAZI)).toBe('Corsie 1, 3 · Vasca grande');
    expect(etichettaSpazi(['vp'], SPAZI)).toBe('Vasca piccola');
    expect(etichettaSpazi([], SPAZI)).toBe("Tutto l'impianto");
  });
});

describe('disposizione', () => {
  it('chi si sovrappone si divide la larghezza; chi no la tiene tutta', () => {
    const r = disponi([
      { occorrenza: occ('17:00', '18:00', { chiave: 'a' }), colonna: 0, ampiezza: 2 },
      { occorrenza: occ('17:30', '18:30', { chiave: 'b' }), colonna: 1, ampiezza: 1 },
      { occorrenza: occ('17:00', '18:00', { chiave: 'c' }), colonna: 2, ampiezza: 1 },
      { occorrenza: occ('19:00', '20:00', { chiave: 'd' }), colonna: 0, ampiezza: 1 },
    ]);
    const per = Object.fromEntries(r.map((x) => [x.occorrenza.chiave, x]));
    expect([per.a.corsia, per.a.corsie]).toEqual([0, 2]);
    expect([per.b.corsia, per.b.corsie]).toEqual([1, 2]);
    expect([per.c.corsia, per.c.corsie]).toEqual([0, 1]);
    expect([per.d.corsia, per.d.corsie]).toEqual([0, 1]);
  });

  it('la finestra oraria si allarga alle ore intere, ignorando le chiusure', () => {
    expect(finestraOraria([occ('07:30', '09:00'), occ('20:00', '21:15'), occ('00:00', '23:59', { categoria: 'CHIUSURA' })])).toEqual([7 * 60, 22 * 60]);
    expect(finestraOraria([])).toEqual([8 * 60, 21 * 60]);
  });
});
