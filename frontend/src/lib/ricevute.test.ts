import { describe, expect, it } from 'vitest';
import type { Ricevuta } from './api';
import { anteprimaNumero, nomeRicevuta, perChi, predefinitiSerie, riepilogoSerie, serieDiBase, serieRichiesta, serveScelta, statoRicevuta } from './ricevute';

const persona = (nome: string, cognome = 'Bianchi') => ({ id: nome, nome, cognome, codiceFiscale: null, indirizzo: null, dataNascita: null, email: null });
const riga = (importo: number) => ({ addebitoId: 'a', personaId: 'p', descrizione: 'Corso', per: 'Sofia Bianchi', importo });
const vuota = { annullataIl: null, motivoAnnullamento: null, sostituitaDaNumero: null, inviataIl: null, inviataA: null, stampataIl: null };

describe('ricevute', () => {
  it('il numero come uscirà', () => {
    expect(anteprimaNumero('{stagione}-{numero}', 4, '2026/27', 2026, 143)).toBe('2026/27-0143');
    expect(anteprimaNumero('F{anno}/{numero}', 3, '2026/27', 2027, 7)).toBe('F2027/007');
    expect(anteprimaNumero('{numero}', 1, 'x', 2026, 12345)).toBe('12345');
  });

  it('si chiede «per famiglia o per figlio» solo con più figli, «chiedi» e intestata a chi paga', () => {
    expect(serveScelta({ famiglia: 'CHIEDI', intestata: 'PAGANTE' }, 2)).toBe(true);
    expect(serveScelta({ famiglia: 'CHIEDI', intestata: 'PAGANTE' }, 1)).toBe(false);
    expect(serveScelta({ famiglia: 'FAMIGLIA', intestata: 'PAGANTE' }, 2)).toBe(false);
    // intestate a chi frequenta sono sempre una per persona: niente da chiedere
    expect(serveScelta({ famiglia: 'CHIEDI', intestata: 'BENEFICIARIO' }, 2)).toBe(false);
  });

  it('per chi e quanto', () => {
    expect(perChi({ beneficiari: [persona('Sofia')], righe: [riga(520)], totale: 520 })).toBe('Sofia Bianchi · 520\u00a0€');
    expect(perChi({ beneficiari: [persona('Sofia'), persona('Luca')], righe: [riga(500), riga(540)], totale: 1040 })).toBe('Sofia e Luca Bianchi · 2 righe · 1.040\u00a0€');
    expect(perChi({ beneficiari: [persona('Sofia'), persona('Pietro', 'Sala')], righe: [riga(1)], totale: 1 })).toBe('Sofia Bianchi, Pietro Sala · 1\u00a0€');
  });

  it('lo stato: annullata (e rifatta), inviata, stampata o da consegnare', () => {
    expect(statoRicevuta(vuota)).toEqual({ testo: 'Da consegnare', tono: 'da' });
    expect(statoRicevuta({ ...vuota, stampataIl: '2026-11-07T16:24:00Z' }).testo).toBe('Stampata il 07/11/2026');
    expect(statoRicevuta({ ...vuota, stampataIl: '2026-11-07T16:24:00Z', inviataIl: '2026-11-08T08:00:00Z', inviataA: 'marco@esempio.it' }))
      .toEqual({ testo: 'Inviata a marco@esempio.it il 08/11/2026', tono: 'fatto' });
    expect(statoRicevuta({ ...vuota, inviataIl: '2026-11-08T08:00:00Z', annullataIl: '2026-11-09T09:00:00Z', motivoAnnullamento: 'Intestata al figlio', sostituitaDaNumero: '2026/27-0144' }))
      .toEqual({ testo: 'Annullata il 09/11/2026: Intestata al figlio · rifatta come 2026/27-0144', tono: 'annullata' });
    // dopo le 23 a Roma è già il giorno dopo
    expect(statoRicevuta({ ...vuota, stampataIl: '2026-11-07T23:30:00Z' }).testo).toBe('Stampata il 08/11/2026');
  });

  it('il nome con il numero', () => {
    expect(nomeRicevuta({ tipo: 'RICEVUTA', numero: '2026/27-0142' } as Ricevuta)).toBe('Ricevuta 2026/27-0142');
    expect(nomeRicevuta({ tipo: 'FATTURA', numero: 'F2026/0001' } as Ricevuta)).toBe('Fattura F2026/0001');
  });
});

describe('serie delle ricevute', () => {
  const s = (id: string, tipo: 'RICEVUTA' | 'FATTURA', attiva: boolean) => ({ id, tipo, attiva });

  it('l\'ultima serie accesa di ricevute è richiesta dal sistema', () => {
    expect(serieRichiesta([s('a', 'RICEVUTA', true), s('b', 'RICEVUTA', false)], 'a')).toBe(true);
    expect(serieRichiesta([s('a', 'RICEVUTA', true), s('b', 'RICEVUTA', true)], 'a')).toBe(false);
  });

  it('una serie spenta o di fatture non è richiesta', () => {
    expect(serieRichiesta([s('a', 'RICEVUTA', true), s('b', 'RICEVUTA', false)], 'b')).toBe(false);
    expect(serieRichiesta([s('a', 'RICEVUTA', true), s('f', 'FATTURA', true)], 'f')).toBe(false);
    // le fatture accese non salvano una serie di ricevute
    expect(serieRichiesta([s('a', 'RICEVUTA', true), s('f', 'FATTURA', true)], 'a')).toBe(true);
    expect(serieRichiesta([], 'x')).toBe(false);
  });

  it('i valori di una serie nuova seguono il tipo', () => {
    expect(predefinitiSerie('RICEVUTA')).toEqual({ formato: '{stagione}-{numero}', cifre: 4, riparte: 'STAGIONE' });
    expect(predefinitiSerie('FATTURA')).toEqual({ formato: 'F{anno}/{numero}', cifre: 4, riparte: 'ANNO' });
  });

  it('la riga sotto il nome dice quanto conta il numero, secondo quando riparte', () => {
    expect(riepilogoSerie({ emesse: 412, prossima: '2026/27-0413', riparte: 'STAGIONE' })).toBe('Prossima 2026/27-0413 · 412 nel periodo');
    expect(riepilogoSerie({ emesse: 3, prossima: 'F2026/0004', riparte: 'ANNO' })).toBe('Prossima F2026/0004 · 3 quest\'anno');
    expect(riepilogoSerie({ emesse: 0, prossima: '0001', riparte: 'MAI' })).toBe('Prossima 0001 · 0 in tutto');
  });

  it('la serie di base è la prima serie di ricevute accesa, nell\'ordine dell\'elenco', () => {
    const l = [s('f', 'FATTURA', true), s('a', 'RICEVUTA', false), s('b', 'RICEVUTA', true), s('c', 'RICEVUTA', true)];
    expect(serieDiBase(l)).toBe('b');
    expect(serieDiBase([l[3], l[2]])).toBe('c'); // riordinate: cambia la serie di base
    expect(serieDiBase([l[0], l[1]])).toBeNull();
  });
});
