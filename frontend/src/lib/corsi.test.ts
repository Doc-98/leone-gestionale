import { describe, expect, it } from 'vitest';
import type { Corso, Lezione, SpazioPiano } from './api';
import { assettoIn, cadeIl, corsieInAssetto, corsieDelCorso, dateDi, doveBreve, giornoDi, nomiBrevi, prossimaData, riempimento, righeOrario, settimanaDelMese, slotStandard, spaziOccupati, turniPerGiorno } from './corsi';

const lez = (p: Partial<Lezione>): Lezione => ({
  id: Math.random().toString(36).slice(2), giornoSettimana: 1, inizio: '17:00:00', fine: '17:45:00', validaDal: '2026-09-14', validaAl: null,
  spazi: [], istruttore: null, istruttori: [], cadenza: 'SETTIMANALE', posti: 8, iscritti: 0, inAttesa: 0, ...p,
});
const sp = (id: string, nome: string, padreId: string | null, assetto: number | null = null, tipo = padreId ? 'CORSIA' : 'VASCA'): SpazioPiano =>
  ({ id, impiantoId: 'i', nome, tipo, padreId, assetto });
const spazi = [
  sp('G', 'Vasca grande', null), ...[1, 2, 3, 4, 5, 6].map((n) => sp(`G6-${n}`, `Corsia ${n}`, 'G', 6)), ...[1, 2, 3, 4, 5].map((n) => sp(`G5-${n}`, `Corsia ${n}`, 'G', 5)),
  sp('P', 'Vasca piccola', null),
];

describe('date', () => {
  it('giorno della settimana e settimana del mese', () => {
    expect(giornoDi('2026-10-26')).toBe(1);
    expect(giornoDi('2026-10-25')).toBe(7);
    expect(settimanaDelMese('2026-10-28')).toBe(4);
    expect(settimanaDelMese('2026-10-07')).toBe(1);
  });
  it('prossima data: mai oggi', () => {
    expect(prossimaData(1, '2026-10-26')).toBe('2026-11-02');
    expect(prossimaData(3, '2026-10-26')).toBe('2026-10-28');
  });
  it('le date del menu rapido', () => {
    expect(dateDi('una', 3, [], '2026-10-26', '2027-06-13')).toEqual(['2026-10-28']);
    expect(dateDi('settimana', 3, [], '2026-10-26', '2026-11-18')).toEqual(['2026-10-28', '2026-11-04', '2026-11-11', '2026-11-18']);
    // il quarto mercoledì: 28 ottobre, 25 novembre, 23 dicembre
    expect(dateDi('mese', 3, [], '2026-10-26', '2026-12-31')).toEqual(['2026-10-28', '2026-11-25', '2026-12-23']);
    expect(dateDi('giorni', 3, [3, 5], '2026-10-26', '2026-11-06')).toEqual(['2026-10-28', '2026-10-30', '2026-11-04', '2026-11-06']);
  });
  it('un turno mensile cade solo nella sua settimana del mese', () => {
    const l = lez({ giornoSettimana: 3, validaDal: '2026-10-28', cadenza: 'MENSILE' });
    expect(cadeIl(l, '2026-11-25')).toBe(true);
    expect(cadeIl(l, '2026-11-18')).toBe(false);
    expect(cadeIl(lez({ giornoSettimana: 3, validaDal: '2026-10-28' }), '2026-11-18')).toBe(true);
  });
});

describe('turni del corso', () => {
  const turni = [
    lez({ giornoSettimana: 1, iscritti: 8, inAttesa: 3 }),
    lez({ giornoSettimana: 1, inizio: '17:50:00', fine: '18:35:00', iscritti: 7 }),
    lez({ giornoSettimana: 3, iscritti: 5 }),
    lez({ giornoSettimana: 6, inizio: '09:45:00', fine: '10:30:00', posti: null, iscritti: 3 }),
  ];
  it('settimana, orari e griglia', () => {
    expect(turniPerGiorno(turni)).toEqual([2, 0, 1, 0, 0, 1, 0]);
    expect(slotStandard(turni).map((s) => s.chiave)).toEqual(['09:45-10:30', '17:00-17:45', '17:50-18:35']);
    const righe = righeOrario(turni);
    expect(righe[1].celle[0]).toHaveLength(1);
    expect(righe[1].celle[2]).toHaveLength(1);
    expect(righe[1].celle[1]).toHaveLength(0);
  });
  it('riempimento: i turni senza limite non contano nei posti', () => {
    expect(riempimento(turni)).toEqual({ iscritti: 23, posti: 24, liberi: 4, turniLiberi: 2, inAttesa: 3, percentuale: 96 });
  });
});

describe('spazi', () => {
  it('corsie del corso: la vasca e l\'assetto abituali', () => {
    expect(corsieDelCorso([lez({ spazi: ['G6-4'] })], spazi).map((s) => s.id)).toEqual(['G6-1', 'G6-2', 'G6-3', 'G6-4', 'G6-5', 'G6-6']);
    expect(corsieDelCorso([lez({ spazi: ['G5-2'] })], spazi)).toHaveLength(5);
    expect(corsieDelCorso([lez({ spazi: ['P'] })], spazi).map((s) => s.id)).toEqual(['P']);
  });
  it('occupati: una vasca intera occupa le sue corsie', () => {
    const corsi = [
      { attivo: true, lezioni: [lez({ giornoSettimana: 3, spazi: ['G6-1'] })] },
      { attivo: true, lezioni: [lez({ giornoSettimana: 3, inizio: '17:30:00', fine: '18:15:00', spazi: ['P'] })] },
      { attivo: false, lezioni: [lez({ giornoSettimana: 3, spazi: ['G6-2'] })] },
    ] as Corso[];
    const occ = spaziOccupati(corsi, spazi, '2026-10-28', '17:00', '17:45');
    expect([...occ].sort()).toEqual(['G', 'G6-1', 'P']);
    expect(spaziOccupati(corsi, spazi, '2026-10-28', '17:45', '18:30').has('G6-1')).toBe(false);
  });
  it('assetto delle fasce: a 5 corsie il mercoledì pomeriggio', () => {
    const fasce = [{ id: 'f', vascaId: 'G', giornoSettimana: 3, inizio: '15:00:00', fine: '17:00:00', assetto: 5, validaDal: '2026-09-01', validaAl: null }];
    expect(assettoIn(fasce, 'G', '2026-10-28', '16:10', '16:55')).toBe(5);
    expect(assettoIn(fasce, 'G', '2026-10-28', '17:00', '17:45')).toBeNull();
    const sei = corsieDelCorso([lez({ spazi: ['G6-4'] })], spazi);
    expect(corsieInAssetto(sei, 5, spazi).map((s) => s.id)).toEqual(['G5-1', 'G5-2', 'G5-3', 'G5-4', 'G5-5']);
    expect(corsieInAssetto(sei, null, spazi)).toBe(sei);
  });
  it('dove breve e nomi brevi', () => {
    expect(doveBreve(['G6-4'], spazi)).toBe('Corsia 4');
    expect(doveBreve(['G6-1', 'G6-2'], spazi)).toBe('Corsie 1–2');
    expect(doveBreve(['P'], spazi)).toBe('Vasca piccola');
    expect(doveBreve(['G6-4', 'G5-4'], spazi)).toBe('Corsia 4');
    expect(nomiBrevi([{ nome: 'Sara Moretti' }, { nome: 'Luca De Rossi' }])).toBe('S. Moretti, L. De Rossi');
  });
});
