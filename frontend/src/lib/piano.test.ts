import { describe, expect, it } from 'vitest';
import type { Occorrenza, SpazioPiano } from './api';
import { colonneDelGiorno, colonneSpazi, definitePerVasca, disponi, etichettaSpazi, fasceDefiniteIl, finestraOraria, inFascia, intervalliColonne, pianta, raggruppaFasce, tratti } from './piano';

const sp = (id: string, nome: string, tipo: string, padreId: string | null = null): SpazioPiano => ({ id, impiantoId: 'i', nome, tipo, padreId, assetto: null });
const SPAZI = [
  sp('vg', 'Vasca grande', 'VASCA'),
  sp('vp', 'Vasca piccola', 'VASCA'),
  sp('sm', 'Spogliatoio maschile', 'SPOGLIATOIO'),
  sp('c1', 'Corsia 1', 'CORSIA', 'vg'),
  sp('c2', 'Corsia 2', 'CORSIA', 'vg'),
  sp('c3', 'Corsia 3', 'CORSIA', 'vg'),
];

const occ = (inizio: string, fine: string, extra: Partial<Occorrenza> = {}): Occorrenza => ({
  chiave: inizio + fine, tipo: 'LEZIONE', categoria: 'SCUOLA_NUOTO', colore: null, titolo: 't', dettaglio: null, data: '2026-10-19', inizio, fine,
  spazi: [], istruttore: null, stato: 'REGOLARE', nota: null, lezioneId: null, corsoId: null, eventoId: null, eccezioneId: null,
  dataOriginale: null, conflitti: [], iscritti: null, posti: null, ...extra,
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

describe('fasce e pianta', () => {
  const SP: SpazioPiano[] = [
    { ...sp('vg', 'Vasca grande', 'VASCA') , assetto: null },
    { ...sp('vp', 'Vasca piccola', 'VASCA'), assetto: null },
    ...[1, 2, 3, 4, 5, 6].map((n) => ({ ...sp(`c${n}`, `Corsia ${n}`, 'CORSIA', 'vg'), assetto: 6 })),
    ...[1, 2, 3, 4, 5].map((n) => ({ ...sp(`q${n}`, `Corsia ${n}`, 'CORSIA', 'vg'), assetto: 5 })),
  ];
  const l = (k: string, inizio: string, fine: string, spazi: string[], extra: Partial<Occorrenza> = {}) => occ(inizio, fine, { chiave: k, titolo: k, spazi, ...extra });

  it('vasca piccola sfalsata nella stessa fascia della grande; le attività lunghe nella fascia in cui iniziano', () => {
    const lista = [
      l('giallo', '17:00', '17:45', ['c3']), l('verde', '17:00', '17:45', ['c4']), l('amb', '16:55', '17:35', ['vp']),
      l('amb2', '17:40', '18:20', ['vp']), l('nero', '17:50', '18:35', ['c6']),
      l('libero', '17:00', '19:00', ['c5', 'c6']),
    ];
    const { fasce, fasciaDi } = raggruppaFasce(lista);
    expect(fasce.map((f) => f.chiave)).toEqual(['17:00–17:45', '17:50–18:35']);
    expect(fasciaDi.get('amb')).toBe('17:00–17:45');
    expect(fasciaDi.get('amb2')).toBe('17:50–18:35');
    expect(fasciaDi.get('libero')).toBe('17:00–17:45');
    // il nuoto libero si vede anche nella fascia dopo
    expect(inFascia(fasce[1], lista, fasciaDi).map((o) => o.chiave).sort()).toEqual(['amb2', 'libero', 'nero']);
  });

  it('un\'attività lunga che inizia fuori dalle fasce ne fa una sua, e si vede anche in quelle che attraversa', () => {
    const lista = [l('libero', '07:00', '10:20', ['c5']), l('baby', '08:50', '09:20', ['vp']), l('prop', '14:30', '16:00', ['q1']), l('stella', '15:20', '16:05', ['q3'])];
    const { fasce, fasciaDi } = raggruppaFasce(lista);
    expect(fasce.map((f) => f.chiave)).toEqual(['07:00–10:20', '08:50–09:20', '14:30–16:00', '15:20–16:05']);
    expect(inFascia(fasce[1], lista, fasciaDi).map((o) => o.chiave).sort()).toEqual(['baby', 'libero']);
    expect(inFascia(fasce[3], lista, fasciaDi).map((o) => o.chiave).sort()).toEqual(['prop', 'stella']);
  });

  it('pianta: assetto in uso, corsie libere, vasca intera, più corsie, assetti misti', () => {
    const sei = pianta(SP, [l('giallo', '17:00', '17:45', ['c3']), l('master', '18:40', '19:25', ['c4', 'c5']), l('amb', '16:55', '17:35', ['vp'])]);
    const vg = sei.vasche.find((v) => v.id === 'vg')!;
    expect(vg.assetto).toBe(6);
    expect(vg.corsie).toHaveLength(6);
    expect(vg.pezzi.map((p) => [p.occorrenza.chiave, p.da, p.quante])).toEqual([['giallo', 2, 1], ['master', 3, 2]]);
    expect(sei.vasche.find((v) => v.id === 'vp')!.pezzi).toHaveLength(1);

    const cinque = pianta(SP, [l('prop', '14:30', '16:00', ['q1', 'q2'])]).vasche.find((v) => v.id === 'vg')!;
    expect([cinque.assetto, cinque.corsie.length, cinque.assettiMisti]).toEqual([5, 5, false]);

    const intera = pianta(SP, [l('libero', '08:00', '09:00', ['vg'])]).vasche.find((v) => v.id === 'vg')!;
    expect(intera.pezzi[0]).toMatchObject({ da: 0, quante: 6 });

    const misti = pianta(SP, [l('a', '17:00', '17:45', ['c2']), l('b', '17:00', '17:45', ['q2'])]).vasche.find((v) => v.id === 'vg')!;
    expect(misti.assettiMisti).toBe(true);
    expect(misti.pezzi.map((p) => p.colonne)).toEqual([2, 2]);
  });

  it('senza spazio: «altrove»', () => {
    expect(pianta(SP, [l('privata', '17:00', '17:45', [])]).altrove.map((o) => o.chiave)).toEqual(['privata']);
  });
});

describe('fasce definite', () => {
  const l = (k: string, inizio: string, fine: string, spazi: string[]) => occ(inizio, fine, { chiave: k, titolo: k, spazi });

  it('danno l\'orario alla fascia e compaiono anche vuote', () => {
    const { fasce, fasciaDi } = raggruppaFasce(
      [l('a', '17:02', '17:44', ['c1']), l('b', '16:55', '17:35', ['vp'])],
      [{ inizio: '17:00:00', fine: '17:45:00' }, { inizio: '18:40:00', fine: '19:25:00' }],
    );
    expect(fasce.map((f) => f.chiave)).toEqual(['17:00–17:45', '18:40–19:25']);
    expect(fasciaDi.get('a')).toBe('17:00–17:45');
    expect(fasciaDi.get('b')).toBe('17:00–17:45');
  });

  it('valgono nel loro giorno e periodo; per vasca si sceglie quella che si sovrappone di più', () => {
    const f = (id: string, vascaId: string, g: number, inizio: string, fine: string, validaAl: string | null = null) =>
      ({ id, vascaId, giornoSettimana: g, inizio, fine, assetto: 6, validaDal: '2026-01-01', validaAl });
    const tutte = [f('lun', 'vg', 1, '17:00', '17:45'), f('mar', 'vg', 2, '17:00', '17:45'), f('vecchia', 'vg', 1, '18:00', '18:45', '2026-03-01'),
      f('p1', 'vp', 1, '16:55', '17:35'), f('p2', 'vp', 1, '17:40', '18:20')];
    const lunedi = fasceDefiniteIl(tutte, '2026-10-19');
    expect(lunedi.map((x) => x.id)).toEqual(['lun', 'p1', 'p2']);
    const per = definitePerVasca(lunedi, { chiave: 'x', inizio: 17 * 60, fine: 17 * 60 + 45 });
    expect(per.get('vg')?.id).toBe('lun');
    expect(per.get('vp')?.id).toBe('p1');
  });
});

describe('griglia oraria compatta', () => {
  const h = (x: number) => x * 60;
  it('chiude i buchi lunghi e lascia quelli brevi', () => {
    const t = tratti([[h(7), h(10) + 20], [h(12) + 20, h(14) + 10], [h(16) + 10, h(21) + 15]], [h(7), h(22)]);
    expect(t).toEqual([
      { da: h(7), a: h(10) + 30, vuoto: false },
      { da: h(10) + 30, a: h(12) + 15, vuoto: true },
      { da: h(12) + 15, a: h(14) + 15, vuoto: false },
      { da: h(14) + 15, a: h(16), vuoto: true },
      { da: h(16), a: h(22), vuoto: false },
    ]);
  });
  it('senza buchi lunghi resta un tratto solo', () => {
    expect(tratti([[h(8), h(9)], [h(10), h(11)]], [h(8), h(11)])).toEqual([{ da: h(8), a: h(11), vuoto: false }]);
  });
  it('chiude anche la fine della giornata vuota', () => {
    expect(tratti([[h(8), h(10)]], [h(8), h(21)])).toEqual([{ da: h(8), a: h(10), vuoto: false }, { da: h(10), a: h(21), vuoto: true }]);
  });
});

describe('colonneDelGiorno', () => {
  const sp = (id: string, padreId: string | null, assetto: number | null = null): SpazioPiano =>
    ({ id, impiantoId: 'i', nome: padreId ? `Corsia ${id.slice(-1)}` : id, tipo: padreId ? 'CORSIA' : 'VASCA', padreId, assetto });
  const spazi = [
    sp('G', null), ...[1, 2, 3, 4, 5, 6].map((n) => sp(`G6-${n}`, 'G', 6)), ...[1, 2, 3, 4, 5].map((n) => sp(`G5-${n}`, 'G', 5)),
    sp('P', null),
  ];
  const ids = (c: { id: string }[]) => c.map((x) => x.id);

  it('senza indicazioni mostra l\'assetto con più corsie', () => {
    expect(ids(colonneDelGiorno(spazi, []))).toEqual(['G6-1', 'G6-2', 'G6-3', 'G6-4', 'G6-5', 'G6-6', 'P']);
  });
  it('segue l\'assetto delle attività del giorno', () => {
    expect(ids(colonneDelGiorno(spazi, [{ spazi: ['G5-2'] }]))).toEqual(['G5-1', 'G5-2', 'G5-3', 'G5-4', 'G5-5', 'P']);
  });
  it('se il giorno usa due assetti li mostra entrambi', () => {
    expect(colonneDelGiorno(spazi, [{ spazi: ['G5-2'] }], [{ vascaId: 'G', assetto: 6 }])).toHaveLength(12);
  });
  it('una vasca intera non decide l\'assetto', () => {
    expect(colonneDelGiorno(spazi, [{ spazi: ['G'] }])).toHaveLength(7);
  });
});
