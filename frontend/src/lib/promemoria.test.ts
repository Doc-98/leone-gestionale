import { describe, expect, it } from 'vitest';
import type { RegolaPromemoria } from './api';
import { pillole, ripetuto, sottoTipo, statoTipo, TIPI } from './promemoria';

const r = (id: string, tipo: RegolaPromemoria['tipo'], giorni: number, extra: Partial<RegolaPromemoria> = {}): RegolaPromemoria =>
  ({ id, tipo, giorni, maxInvii: null, attivo: true, oggetto: 'o', testo: 't', ...extra });

describe('promemoria', () => {
  it('i tipi ripetuti sono due', () => {
    expect(TIPI.filter((t) => ripetuto(t.tipo)).map((t) => t.tipo)).toEqual(['TESSERAMENTO_MANCANTE', 'PAGAMENTO']);
  });
  it('sottoTipo: ripetuti con regola, altrimenti il testo corto', () => {
    expect(sottoTipo('PAGAMENTO', [r('1', 'PAGAMENTO', 7, { maxInvii: 3 })])).toBe('ogni 7 giorni, 3 volte');
    expect(sottoTipo('PAGAMENTO', [r('1', 'PAGAMENTO', 1, { maxInvii: 1 })])).toBe('ogni giorno, 1 volta');
    expect(sottoTipo('PAGAMENTO', [])).toBe('dopo la scadenza');
    expect(sottoTipo('CERTIFICATO', [r('1', 'CERTIFICATO', 7)])).toBe('prima che scada');
  });
  it('statoTipo: vuoto, acceso con almeno una accesa, spento', () => {
    expect(statoTipo('CERTIFICATO', [])).toBe('vuoto');
    expect(statoTipo('CERTIFICATO', [r('1', 'CERTIFICATO', 7, { attivo: false }), r('2', 'CERTIFICATO', 14)])).toBe('acceso');
    expect(statoTipo('CERTIFICATO', [r('1', 'CERTIFICATO', 7, { attivo: false }), r('2', 'PAGAMENTO', 14)])).toBe('spento');
  });
  it('pillole', () => {
    expect(pillole(r('1', 'CERTIFICATO', 7))).toEqual([{ g: -7, testo: '7 g prima' }]);
    expect(pillole(r('1', 'CERTIFICATO', 0))).toEqual([{ g: 0, testo: 'il giorno' }]);
    expect(pillole(r('1', 'PAGAMENTO', 7, { maxInvii: 2 })).map((p) => p.g)).toEqual([7, 14]);
    expect(pillole(r('1', 'TESSERAMENTO_MANCANTE', 7, { maxInvii: 2 })).map((p) => p.g)).toEqual([0, 7]);
  });
});
