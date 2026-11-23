import { describe, expect, it } from 'vitest';
import { contaModifiche, testoModifiche } from './salvataggio';

/**
 * Specifica (brief coerenza blocco 7, lotto 1, «Barra di salvataggio unica»):
 * - `contaModifiche(originale, bozza)` dice quante cose sono cambiate fra quello che è salvato e la bozza in pagina;
 * - nessuna differenza = 0; se si torna al valore originale il conto torna a 0 (la barra sparisce);
 * - il testo della barra è «1 modifica da salvare» / «N modifiche da salvare» (plurale giusto).
 * IPOTESI sul nome: il brief fissa solo `contaModifiche`; per il testo si prova `testoModifiche(n)`.
 */
describe('contaModifiche – campi semplici', () => {
  it('nessuna modifica: 0', () => {
    expect(contaModifiche({ nome: 'Società Prova', iva: 22 }, { nome: 'Società Prova', iva: 22 })).toBe(0);
  });

  it('un campo di testo cambiato: 1', () => {
    expect(contaModifiche({ nome: 'Società Prova', iva: 22 }, { nome: 'Società Nuova', iva: 22 })).toBe(1);
  });

  it('due campi cambiati: 2; tre campi di tipo diverso (testo, numero, booleano): 3', () => {
    expect(contaModifiche({ a: 'x', b: 1 }, { a: 'y', b: 2 })).toBe(2);
    expect(contaModifiche({ a: 'x', b: 1, c: false }, { a: 'y', b: 2, c: true })).toBe(3);
  });

  it('un interruttore acceso/spento conta come una modifica', () => {
    expect(contaModifiche({ attivo: true }, { attivo: false })).toBe(1);
  });

  it('un numero a zero è un valore, non «vuoto»: 5 -> 0 è una modifica', () => {
    expect(contaModifiche({ importo: 5 }, { importo: 0 })).toBe(1);
    expect(contaModifiche({ importo: 0 }, { importo: 0 })).toBe(0);
  });

  it('testo svuotato: è una modifica', () => {
    expect(contaModifiche({ nota: 'ciao' }, { nota: '' })).toBe(1);
  });

  it('oggetti vuoti: 0', () => {
    expect(contaModifiche({}, {})).toBe(0);
  });
});

describe('contaModifiche – ritorno all\'originale', () => {
  it('cambio e poi ritorno al valore di partenza: 0', () => {
    const originale = { nome: 'Società Prova', iva: 22 };
    const bozza = { ...originale, nome: 'Altro' };
    expect(contaModifiche(originale, bozza)).toBe(1);
    bozza.nome = 'Società Prova';
    expect(contaModifiche(originale, bozza)).toBe(0);
  });

  it('due campi cambiati, uno riportato com\'era: resta 1', () => {
    const originale = { a: 'x', b: 1 };
    expect(contaModifiche(originale, { a: 'y', b: 1 })).toBe(1);
  });

  it('una copia profonda identica (altro riferimento, stessi valori): 0', () => {
    const originale = { nome: 'X', sede: { via: 'Via Prova 1', cap: '20100' } };
    expect(contaModifiche(originale, structuredClone(originale))).toBe(0);
  });
});

describe('contaModifiche – oggetti annidati', () => {
  it('annidato uguale: 0', () => {
    expect(contaModifiche({ sede: { via: 'Via Prova 1', cap: '20100' } }, { sede: { via: 'Via Prova 1', cap: '20100' } })).toBe(0);
  });

  it('un campo cambiato dentro un oggetto annidato: 1', () => {
    expect(contaModifiche({ sede: { via: 'Via Prova 1', cap: '20100' } }, { sede: { via: 'Via Prova 2', cap: '20100' } })).toBe(1);
  });

  it('annidato cambiato e poi riportato: 0', () => {
    const originale = { sede: { via: 'Via Prova 1' } };
    const bozza = { sede: { via: 'Via Prova 2' } };
    bozza.sede.via = 'Via Prova 1';
    expect(contaModifiche(originale, bozza)).toBe(0);
  });

  it('un campo semplice e uno annidato cambiati, in punti diversi: 2', () => {
    expect(contaModifiche(
      { nome: 'A', sede: { via: 'Via Prova 1' } },
      { nome: 'B', sede: { via: 'Via Prova 2' } },
    )).toBe(2);
  });
});

describe('contaModifiche – elenchi', () => {
  it('stesso elenco: 0', () => {
    expect(contaModifiche({ voci: ['a', 'b'] }, { voci: ['a', 'b'] })).toBe(0);
  });

  it('elenco con una voce in più: almeno 1', () => {
    expect(contaModifiche({ voci: ['a'] }, { voci: ['a', 'b'] })).toBeGreaterThanOrEqual(1);
  });

  it('elenco con un elemento modificato: almeno 1', () => {
    expect(contaModifiche({ voci: [{ n: 'a' }] }, { voci: [{ n: 'b' }] })).toBeGreaterThanOrEqual(1);
  });
});

describe('contaModifiche – non altera i dati', () => {
  it('non modifica né l\'originale né la bozza', () => {
    const originale = { a: 1, n: { b: 'x' } };
    const bozza = { a: 2, n: { b: 'y' } };
    contaModifiche(originale, bozza);
    expect(originale).toEqual({ a: 1, n: { b: 'x' } });
    expect(bozza).toEqual({ a: 2, n: { b: 'y' } });
  });
});

describe('testoModifiche – testo della barra', () => {
  it('1: «1 modifica da salvare» (singolare)', () => {
    expect(testoModifiche(1)).toBe('1 modifica da salvare');
  });

  it('2: «2 modifiche da salvare»', () => {
    expect(testoModifiche(2)).toBe('2 modifiche da salvare');
  });

  it('plurale anche per numeri grandi', () => {
    expect(testoModifiche(3)).toBe('3 modifiche da salvare');
    expect(testoModifiche(11)).toBe('11 modifiche da salvare');
    expect(testoModifiche(21)).toBe('21 modifiche da salvare');
  });
});
