import { describe, it, expect } from 'vitest';
import { cosaCambia, descrivi, esito, ordinaDaCoprire, passi } from './richieste';
import type { Richiesta, Turno } from './api';

const turno = (data: string): Turno => ({
  id: 't', data, inizio: '15:00:00', fine: '20:00:00', ore: 5, impiantoId: 'i', postazioneId: 'p',
  mansione: 'Assistente spogliatoio', postazione: 'Spogliatoio M · postazione 2', stato: 'PIANIFICATO', note: null, persona: null,
});
const base = (x: Partial<Richiesta>): Richiesta => ({
  id: 'r', tipo: 'SOSTITUZIONE', stato: 'IN_ATTESA_COLLEGA', turno: turno('2026-10-19'), turnoCollega: null,
  richiedente: { id: 'luca', nome: 'Luca Bianchi' }, collega: null, nuovoInizio: null, nuovoFine: null,
  messaggio: null, creataIl: '', decisaIl: null, notaDecisione: null, ...x,
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
    creataIl: '', decisaIl: null, notaDecisione: null, ...o });

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
