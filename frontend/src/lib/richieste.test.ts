import { describe, it, expect } from 'vitest';
import { cosaCambia, cosaDaFare, descrivi, descriviRichiestaSulTurno, esito, gruppoRichiesta, ordinaDaCoprire, passi } from './richieste';
import type { Richiesta, Turno } from './api';

const turno = (data: string): Turno => ({
  id: 't', data, inizio: '15:00:00', fine: '20:00:00', ore: 5, impiantoId: 'i', postazioneId: 'p',
  mansione: 'Assistente spogliatoio', postazione: 'Spogliatoio M · postazione 2', stato: 'PIANIFICATO', note: null, persona: null,
});
const base = (x: Partial<Richiesta>): Richiesta => ({
  id: 'r', tipo: 'SOSTITUZIONE', stato: 'IN_ATTESA_COLLEGA', turno: turno('2026-10-19'), turnoCollega: null,
  richiedente: { id: 'luca', nome: 'Luca Bianchi' }, collega: null, nuovoInizio: null, nuovoFine: null,
  messaggio: null, creataIl: '', decisaIl: null, notaDecisione: null, propostaDa: null, ...x,
});

describe('descrivi', () => {
  it('sostituzione aperta, vista da altri e da chi la chiede', () => {
    expect(descrivi(base({})).titolo).toBe('Luca Bianchi cerca un sostituto');
    expect(descrivi(base({}), 'luca').titolo).toBe('Cerchi un sostituto');
    expect(descrivi(base({})).dettaglio).toBe('lunedì 19 ottobre, 15:00–20:00 · Spogliatoio M · postazione 2');
  });
  it('sostituzione rivolta a un collega', () => {
    const r = base({ collega: { id: 'marta', nome: 'Marta Ricci' } });
    expect(descrivi(r, 'marta').titolo).toBe('Luca Bianchi ti chiede di sostituirlo');
    expect(descrivi(r, 'luca').titolo).toBe('Marta Ricci ti sostituisce');
    expect(descrivi(r, 'paolo').titolo).toBe('Marta Ricci sostituisce Luca Bianchi');
    const proposta = base({ collega: { id: 'marta', nome: 'Marta Ricci' }, propostaDa: { id: 'c', nome: 'Paolo Neri' } });
    expect(descrivi(proposta, 'marta').titolo).toBe('Paolo Neri ti propone di sostituire Luca Bianchi');
  });
  it('cambio orario', () => {
    const r = base({ tipo: 'CAMBIO_ORARIO', nuovoInizio: '16:00:00', nuovoFine: '19:00:00' });
    expect(descrivi(r).dettaglio).toBe('lunedì 19 ottobre · Spogliatoio M · postazione 2: 15:00–20:00 → 16:00–19:00');
  });
});

describe('area personale', () => {
  const turno = (data: string, inizio: string, fine: string) => ({ id: `${data}${inizio}`, data, inizio, fine, ore: 3, impiantoId: 'i', postazioneId: 'p',
    mansione: 'm', postazione: 'Spogliatoio M', stato: 'PIANIFICATO' as const, note: null, persona: null });
  const r = (o: Partial<Richiesta>): Richiesta => ({ id: 'r', tipo: 'SOSTITUZIONE', stato: 'IN_ATTESA_COLLEGA', turno: turno('2026-11-03', '17:00:00', '20:30:00'),
    turnoCollega: null, richiedente: { id: 'io', nome: 'Luca Ferraris' }, collega: null, nuovoInizio: null, nuovoFine: null, messaggio: null,
    creataIl: '', decisaIl: null, notaDecisione: null, propostaDa: null, ...o });

  it('passi ed esito', () => {
    expect(passi(r({})).map((p) => `${p.nome}:${p.stato}`)).toEqual(['Inviata:fatto', 'Un collega:ora', 'Coordinatore:']);
    expect(passi(r({ tipo: 'CAMBIO_ORARIO', stato: 'IN_ATTESA_APPROVAZIONE' })).map((p) => p.stato)).toEqual(['fatto', 'ora']);
    expect(esito(r({ collega: { id: 'g', nome: 'Giulia Bassi' } }), 'io')).toEqual({ testo: 'aspetta Giulia', tono: 'warn' });
    expect(esito(r({ stato: 'RESPINTA' }), 'io').testo).toBe('non confermata');
    expect(esito(r({ tipo: 'SCAMBIO', stato: 'RESPINTA' }), 'io').testo).toBe('respinto');
  });

  it('cosa cambia', () => {
    expect(cosaCambia(r({ tipo: 'CAMBIO_ORARIO', turno: turno('2026-11-02', '16:00:00', '19:00:00'), nuovoInizio: '16:30:00', nuovoFine: '19:00:00' }), 'io'))
      .toBe('lun 2/11 · 16:00 → 16:30–19:00');
    expect(cosaCambia(r({ stato: 'APPROVATA', collega: { id: 'g', nome: 'Giulia Bassi' } }), 'io')).toBe('mar 3/11 17:00–20:30 · ti ha sostituito Giulia Bassi');
  });

  it('da coprire: i liberi prima, gli occupati in fondo anche se più recenti', () => {
    const a = r({ id: 'a', turno: turno('2026-11-07', '09:30:00', '12:00:00') });
    const b = r({ id: 'b', turno: turno('2026-11-05', '17:00:00', '20:00:00') });
    const c = r({ id: 'c', turno: turno('2026-11-02', '15:00:00', '18:00:00') });
    const o = ordinaDaCoprire([a, b, c], (x) => x.id === 'c' || x.id === 'a');
    expect(o.liberi.map((x) => x.id)).toEqual(['b']);
    expect(o.occupati.map((x) => x.id)).toEqual(['c', 'a']);
  });
});

describe('richieste per il coordinatore', () => {
  it('buchi del cambio orario, asse e quando', async () => {
    const { asse, buchi, quandoInviata } = await import('./richieste');
    expect(buchi('15:30:00', '19:15:00', '16:00', '19:15')).toEqual([['15:30', '16:00']]);
    expect(buchi('15:30', '19:15', '16:00', '18:00')).toEqual([['15:30', '16:00'], ['18:00', '19:15']]);
    expect(buchi('15:30', '19:15', '15:00', '20:00')).toEqual([]);
    const a = asse([['15:30', '19:15'], ['16:00', '20:00']]);
    expect(a.ore).toEqual(['15:00', '16:00', '17:00', '18:00', '19:00', '20:00']);
    expect(a.pos('17:30')).toBe(50);
    expect(quandoInviata('2026-11-01T08:12:00Z', '2026-11-01')).toBe('chiesta oggi alle 9:12');
    expect(quandoInviata('2026-10-31T20:40:00Z', '2026-11-01')).toBe('chiesta ieri alle 21:40');
    expect(quandoInviata('2026-10-28T10:00:00Z', '2026-11-01')).toBe('chiesta il 28/10');
  });
});

describe('le richieste viste dal coordinatore', () => {
  const marta = { id: 'marta', nome: 'Marta Neri' };

  it('sostituzione: da coprire finché nessuno la prende, da decidere quando un collega c\'è', () => {
    const aperta = base({});
    expect(gruppoRichiesta(aperta)).toBe('Da coprire');
    expect(cosaDaFare(aperta)).toBe('Sostituzione da coprire');
    expect(descriviRichiestaSulTurno(aperta)).toBe('Luca Bianchi ha chiesto una sostituzione: proponila a un collega da «Richieste»');
    const proposta = base({ collega: marta });
    expect(gruppoRichiesta(proposta)).toBe('Aspettano il collega');
    const presa = base({ stato: 'IN_ATTESA_APPROVAZIONE', collega: marta });
    expect(gruppoRichiesta(presa)).toBe('Da decidere');
    expect(cosaDaFare(presa)).toBe('Sostituzione da decidere');
    expect(descriviRichiestaSulTurno(presa)).toBe('Marta Neri sostituirebbe Luca Bianchi: da decidere in «Richieste»');
  });

  it('cambio orario e scambio', () => {
    const cambio = base({ tipo: 'CAMBIO_ORARIO', stato: 'IN_ATTESA_APPROVAZIONE', nuovoInizio: '16:00:00', nuovoFine: '19:00:00' });
    expect(cosaDaFare(cambio)).toBe('Cambio orario da decidere');
    expect(gruppoRichiesta(cambio)).toBe('Da decidere');
    expect(descriviRichiestaSulTurno(cambio)).toBe('C\'è un cambio orario da decidere su questo turno (16:00–19:00)');
    const scambio = base({ tipo: 'SCAMBIO', collega: marta });
    expect(cosaDaFare(scambio)).toBe('Scambio in attesa del collega');
    expect(gruppoRichiesta(scambio)).toBe('Aspettano il collega');
    expect(descriviRichiestaSulTurno(scambio)).toBe('C\'è uno scambio con Marta Neri in corso su questo turno');
    expect(descriviRichiestaSulTurno(base({ tipo: 'SCAMBIO' }))).toContain('un collega');
    expect(cosaDaFare(base({ tipo: 'SCAMBIO', stato: 'IN_ATTESA_APPROVAZIONE', collega: marta }))).toBe('Scambio da decidere');
  });
});
