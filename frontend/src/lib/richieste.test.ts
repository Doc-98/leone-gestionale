import { describe, it, expect } from 'vitest';
import { descrivi } from './richieste';
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
