import { describe, expect, it } from 'vitest';
import type { RegolaPromemoria, TipoPromemoria } from './api';
import { pillole, ripetuto, sottoTipo, statoTipo, TIPI } from './promemoria';

/**
 * Specifica (brief coerenza blocco 7, lotto 4, «lib/promemoria.ts»).
 *  - TIPI: cinque tipi con `tipo`, `nome`, `sotto` corto.
 *  - sottoTipo: per i tipi ripetuti con almeno una regola «ogni 7 giorni, 3 volte» («ogni giorno, …», «…, 1 volta»),
 *    dalla prima regola del tipo; altrimenti il `sotto` del tipo.
 *  - statoTipo: 'vuoto' (nessuna regola), 'acceso' (almeno una accesa), 'spento'.
 *  - pillole: giorni rispetto alla scadenza, come oggi (negativi prima, 0 il giorno stesso, positivi dopo).
 */
const regola = (tipo: TipoPromemoria, extra: Partial<RegolaPromemoria> = {}): RegolaPromemoria => ({
  id: `${tipo}-${extra.giorni ?? 0}`, tipo, giorni: 7, maxInvii: null, attivo: true, oggetto: 'Oggetto', testo: 'Testo', ...extra,
});
const sotto = (t: TipoPromemoria) => TIPI.find((x) => x.tipo === t)!.sotto;

describe('TIPI e ripetuto', () => {
  it('i cinque tipi, nell\'ordine della linea', () => {
    expect(TIPI.map((t) => t.tipo)).toEqual(['CERTIFICATO', 'ABBONAMENTO', 'TESSERAMENTO', 'TESSERAMENTO_MANCANTE', 'PAGAMENTO']);
    expect(TIPI.map((t) => t.nome)).toEqual(['Certificato medico', 'Abbonamento', 'Tesseramento in scadenza', 'Tesseramento mancante', 'Pagamento scaduto']);
  });
  it('il «sotto» corto di ogni tipo', () => {
    expect(sotto('CERTIFICATO')).toBe('prima che scada');
    expect(sotto('ABBONAMENTO')).toBe('se non è rinnovato');
    expect(sotto('TESSERAMENTO')).toBe('prima che scada');
    expect(sotto('TESSERAMENTO_MANCANTE')).toBe('dal giorno in cui manca');
    expect(sotto('PAGAMENTO')).toBe('dopo la scadenza');
  });
  it('si ripetono solo il tesseramento mancante e il pagamento scaduto', () => {
    expect(TIPI.filter((t) => ripetuto(t.tipo)).map((t) => t.tipo)).toEqual(['TESSERAMENTO_MANCANTE', 'PAGAMENTO']);
  });
});

describe('sottoTipo', () => {
  it('tipo ripetuto con una regola: «ogni 7 giorni, 3 volte»', () => {
    expect(sottoTipo('PAGAMENTO', [regola('PAGAMENTO', { giorni: 7, maxInvii: 3 })])).toBe('ogni 7 giorni, 3 volte');
    expect(sottoTipo('TESSERAMENTO_MANCANTE', [regola('TESSERAMENTO_MANCANTE', { giorni: 14, maxInvii: 2 })])).toBe('ogni 14 giorni, 2 volte');
  });
  it('ogni 1 giorno si dice «ogni giorno»', () => {
    expect(sottoTipo('PAGAMENTO', [regola('PAGAMENTO', { giorni: 1, maxInvii: 3 })])).toBe('ogni giorno, 3 volte');
  });
  it('1 sola volta, singolare: «…, 1 volta»', () => {
    expect(sottoTipo('PAGAMENTO', [regola('PAGAMENTO', { giorni: 7, maxInvii: 1 })])).toBe('ogni 7 giorni, 1 volta');
    expect(sottoTipo('TESSERAMENTO_MANCANTE', [regola('TESSERAMENTO_MANCANTE', { giorni: 1, maxInvii: 1 })])).toBe('ogni giorno, 1 volta');
  });
  it('usa la prima regola del tipo, ignorando le altre e i tipi diversi', () => {
    const regole = [
      regola('CERTIFICATO', { giorni: 30 }),
      regola('PAGAMENTO', { id: 'p1', giorni: 5, maxInvii: 4 }),
      regola('PAGAMENTO', { id: 'p2', giorni: 9, maxInvii: 2 }),
    ];
    expect(sottoTipo('PAGAMENTO', regole)).toBe('ogni 5 giorni, 4 volte');
  });
  it('tipo ripetuto senza regole: il «sotto» corto del tipo', () => {
    expect(sottoTipo('PAGAMENTO', [])).toBe('dopo la scadenza');
    expect(sottoTipo('TESSERAMENTO_MANCANTE', [regola('CERTIFICATO')])).toBe('dal giorno in cui manca');
  });
  it('tipo non ripetuto: sempre il «sotto» del tipo, con o senza regole', () => {
    expect(sottoTipo('CERTIFICATO', [regola('CERTIFICATO', { giorni: 30 })])).toBe('prima che scada');
    expect(sottoTipo('CERTIFICATO', [])).toBe('prima che scada');
    expect(sottoTipo('ABBONAMENTO', [regola('ABBONAMENTO', { giorni: 7 })])).toBe('se non è rinnovato');
    expect(sottoTipo('TESSERAMENTO', [])).toBe('prima che scada');
  });
});

describe('statoTipo', () => {
  it('nessuna regola del tipo: «vuoto» (l\'interruttore sarà disabilitato)', () => {
    expect(statoTipo('CERTIFICATO', [])).toBe('vuoto');
    expect(statoTipo('CERTIFICATO', [regola('ABBONAMENTO')])).toBe('vuoto');
  });
  it('almeno una regola accesa: «acceso»', () => {
    expect(statoTipo('CERTIFICATO', [regola('CERTIFICATO', { giorni: 30, attivo: true })])).toBe('acceso');
    expect(statoTipo('CERTIFICATO', [regola('CERTIFICATO', { giorni: 30, attivo: false }), regola('CERTIFICATO', { giorni: 7, attivo: true })])).toBe('acceso');
  });
  it('tutte spente: «spento»', () => {
    expect(statoTipo('CERTIFICATO', [regola('CERTIFICATO', { giorni: 30, attivo: false }), regola('CERTIFICATO', { giorni: 7, attivo: false })])).toBe('spento');
  });
  it('conta solo le regole del tipo chiesto', () => {
    const regole = [regola('ABBONAMENTO', { attivo: true }), regola('CERTIFICATO', { attivo: false })];
    expect(statoTipo('CERTIFICATO', regole)).toBe('spento');
    expect(statoTipo('ABBONAMENTO', regole)).toBe('acceso');
  });
});

describe('pillole – tipi non ripetuti: una pillola, N giorni prima', () => {
  it('7 giorni prima: g = -7, «7 g prima»', () => {
    expect(pillole(regola('CERTIFICATO', { giorni: 7 }))).toEqual([{ g: -7, testo: '7 g prima' }]);
  });
  it('30 giorni prima: g = -30', () => {
    const p = pillole(regola('ABBONAMENTO', { giorni: 30 }));
    expect(p).toHaveLength(1);
    expect(p[0].g).toBe(-30);
    expect(p[0].testo).toBe('30 g prima');
  });
  it('0 giorni: il giorno stesso, g = 0, «il giorno»', () => {
    const p = pillole(regola('TESSERAMENTO', { giorni: 0 }));
    expect(p).toHaveLength(1);
    expect(p[0].g + 0).toBe(0); // +0 normalizza un eventuale -0
    expect(p[0].testo).toBe('il giorno');
  });
});

describe('pillole – tipi ripetuti', () => {
  it('pagamento scaduto: a +N, +2N, +3N dopo la scadenza (la prima non è a 0)', () => {
    expect(pillole(regola('PAGAMENTO', { giorni: 7, maxInvii: 3 })).map((p) => p.g)).toEqual([7, 14, 21]);
    expect(pillole(regola('PAGAMENTO', { giorni: 5, maxInvii: 2 })).map((p) => p.g)).toEqual([5, 10]);
  });
  it('tesseramento mancante: parte dal giorno in cui manca (0) e poi ogni N', () => {
    expect(pillole(regola('TESSERAMENTO_MANCANTE', { giorni: 7, maxInvii: 3 })).map((p) => p.g)).toEqual([0, 7, 14]);
    expect(pillole(regola('TESSERAMENTO_MANCANTE', { giorni: 3, maxInvii: 2 })).map((p) => p.g)).toEqual([0, 3]);
  });
  it('una sola volta: una sola pillola', () => {
    expect(pillole(regola('PAGAMENTO', { giorni: 7, maxInvii: 1 })).map((p) => p.g)).toEqual([7]);
    expect(pillole(regola('TESSERAMENTO_MANCANTE', { giorni: 7, maxInvii: 1 })).map((p) => p.g)).toEqual([0]);
  });
  it('un\'etichetta per pillola, tutte diverse (la linea le distingue)', () => {
    const testi = pillole(regola('PAGAMENTO', { giorni: 7, maxInvii: 3 })).map((p) => p.testo);
    expect(testi).toHaveLength(3);
    expect(new Set(testi).size).toBe(3);
  });
});
