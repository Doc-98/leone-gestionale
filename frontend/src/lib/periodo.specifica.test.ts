import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import {
  contiene,
  etichettaPeriodo,
  finePeriodo,
  inizioPeriodo,
  spostaPeriodo,
  vistaControllo,
  type Passo,
} from './periodo';

/**
 * Specifica `/tmp/b7/piano.md` §2.5 e design system («Barra di pagina», «Date e ore»).
 * Le date sono «da calendario» (aaaa-mm-gg, mai istanti): il risultato non dipende dal fuso del computer.
 * Settimana = da lunedì a domenica; mese = dal primo all'ultimo giorno; giorno = il giorno stesso.
 */

// Il fuso del computer non deve cambiare niente: si prova con Europe/Rome (quello del gestionale) e con altri.
const FUSI = ['Europe/Rome', 'UTC', 'America/New_York', 'Pacific/Auckland'];
const fusoIniziale = process.env.TZ;
afterAll(() => {
  if (fusoIniziale === undefined) delete process.env.TZ;
  else process.env.TZ = fusoIniziale;
});

const PASSI: Passo[] = ['giorno', 'settimana', 'mese'];

describe.each(FUSI)('periodo – fuso %s', (fuso) => {
  beforeAll(() => {
    process.env.TZ = fuso;
  });

  describe('inizioPeriodo', () => {
    it('giorno: il giorno stesso', () => {
      expect(inizioPeriodo('2026-11-11', 'giorno')).toBe('2026-11-11');
    });
    it('settimana: il lunedì (mercoledì, domenica e lunedì stesso)', () => {
      expect(inizioPeriodo('2026-11-11', 'settimana')).toBe('2026-11-09');
      expect(inizioPeriodo('2026-11-15', 'settimana')).toBe('2026-11-09');
      expect(inizioPeriodo('2026-11-09', 'settimana')).toBe('2026-11-09');
    });
    it('settimana a cavallo di due mesi e di due anni', () => {
      expect(inizioPeriodo('2026-11-01', 'settimana')).toBe('2026-10-26'); // domenica
      expect(inizioPeriodo('2026-01-01', 'settimana')).toBe('2025-12-29'); // giovedì
      expect(inizioPeriodo('2027-01-03', 'settimana')).toBe('2026-12-28');
    });
    it('mese: il primo, anche dall\'ultimo giorno e dal 29 febbraio', () => {
      expect(inizioPeriodo('2026-11-30', 'mese')).toBe('2026-11-01');
      expect(inizioPeriodo('2028-02-29', 'mese')).toBe('2028-02-01');
      expect(inizioPeriodo('2026-12-31', 'mese')).toBe('2026-12-01');
    });
  });

  describe('finePeriodo', () => {
    it('giorno: lo stesso giorno', () => {
      expect(finePeriodo('2026-11-09', 'giorno')).toBe('2026-11-09');
    });
    it('settimana: la domenica, anche oltre il mese e l\'anno', () => {
      expect(finePeriodo('2026-11-09', 'settimana')).toBe('2026-11-15');
      expect(finePeriodo('2026-10-26', 'settimana')).toBe('2026-11-01');
      expect(finePeriodo('2025-12-29', 'settimana')).toBe('2026-01-04');
    });
    it('mese: l\'ultimo giorno (30, 31, febbraio normale, bisestile, secolare non bisestile)', () => {
      expect(finePeriodo('2026-11-01', 'mese')).toBe('2026-11-30');
      expect(finePeriodo('2026-12-01', 'mese')).toBe('2026-12-31');
      expect(finePeriodo('2026-02-01', 'mese')).toBe('2026-02-28');
      expect(finePeriodo('2028-02-01', 'mese')).toBe('2028-02-29');
      expect(finePeriodo('2024-02-01', 'mese')).toBe('2024-02-29');
      expect(finePeriodo('2100-02-01', 'mese')).toBe('2100-02-28');
      expect(finePeriodo('2000-02-01', 'mese')).toBe('2000-02-29');
    });
  });

  describe('spostaPeriodo', () => {
    it('giorno: avanti e indietro di un giorno, tra mesi e anni', () => {
      expect(spostaPeriodo('2026-11-09', 'giorno', 1)).toBe('2026-11-10');
      expect(spostaPeriodo('2026-11-30', 'giorno', 1)).toBe('2026-12-01');
      expect(spostaPeriodo('2026-12-31', 'giorno', 1)).toBe('2027-01-01');
      expect(spostaPeriodo('2027-01-01', 'giorno', -1)).toBe('2026-12-31');
      expect(spostaPeriodo('2028-02-28', 'giorno', 1)).toBe('2028-02-29');
      expect(spostaPeriodo('2028-02-29', 'giorno', 1)).toBe('2028-03-01');
      expect(spostaPeriodo('2026-02-28', 'giorno', 1)).toBe('2026-03-01');
    });
    it('settimana: di sette giorni, a cavallo di mesi e anni', () => {
      expect(spostaPeriodo('2026-11-09', 'settimana', 1)).toBe('2026-11-16');
      expect(spostaPeriodo('2026-11-09', 'settimana', -1)).toBe('2026-11-02');
      expect(spostaPeriodo('2026-10-26', 'settimana', 1)).toBe('2026-11-02');
      expect(spostaPeriodo('2026-12-28', 'settimana', 1)).toBe('2027-01-04');
      expect(spostaPeriodo('2025-12-29', 'settimana', -1)).toBe('2025-12-22');
      expect(spostaPeriodo('2027-01-04', 'settimana', -1)).toBe('2026-12-28');
    });
    it('mese: il primo del mese dopo e prima, tra anni e con mesi di lunghezza diversa', () => {
      expect(spostaPeriodo('2026-11-01', 'mese', 1)).toBe('2026-12-01');
      expect(spostaPeriodo('2026-12-01', 'mese', 1)).toBe('2027-01-01');
      expect(spostaPeriodo('2027-01-01', 'mese', -1)).toBe('2026-12-01');
      expect(spostaPeriodo('2026-01-01', 'mese', 1)).toBe('2026-02-01');
      expect(spostaPeriodo('2026-03-01', 'mese', -1)).toBe('2026-02-01');
      expect(spostaPeriodo('2028-02-01', 'mese', 1)).toBe('2028-03-01');
      expect(spostaPeriodo('2026-07-01', 'mese', 1)).toBe('2026-08-01');
    });
    it('un mese di 31 giorni non salta febbraio (31 gennaio non esiste come inizio, ma il mese dopo gennaio è febbraio)', () => {
      expect(spostaPeriodo('2026-01-01', 'mese', 1).slice(0, 7)).toBe('2026-02');
      expect(spostaPeriodo('2026-03-01', 'mese', 1).slice(0, 7)).toBe('2026-04');
    });
  });

  describe('cambio dell\'ora legale (Europe/Rome: 29 marzo e 25 ottobre 2026)', () => {
    it('il giorno del cambio di marzo dura un giorno di calendario, non 23 ore', () => {
      expect(spostaPeriodo('2026-03-28', 'giorno', 1)).toBe('2026-03-29');
      expect(spostaPeriodo('2026-03-29', 'giorno', 1)).toBe('2026-03-30');
      expect(spostaPeriodo('2026-03-30', 'giorno', -1)).toBe('2026-03-29');
      expect(spostaPeriodo('2026-03-29', 'giorno', -1)).toBe('2026-03-28');
    });
    it('il giorno del cambio di ottobre dura un giorno di calendario, non 25 ore', () => {
      expect(spostaPeriodo('2026-10-24', 'giorno', 1)).toBe('2026-10-25');
      expect(spostaPeriodo('2026-10-25', 'giorno', 1)).toBe('2026-10-26');
      expect(spostaPeriodo('2026-10-26', 'giorno', -1)).toBe('2026-10-25');
    });
    it('la settimana del cambio di marzo (23–29) e quella dopo (30 marzo – 5 aprile)', () => {
      expect(inizioPeriodo('2026-03-29', 'settimana')).toBe('2026-03-23');
      expect(finePeriodo('2026-03-23', 'settimana')).toBe('2026-03-29');
      expect(spostaPeriodo('2026-03-23', 'settimana', 1)).toBe('2026-03-30');
      expect(finePeriodo('2026-03-30', 'settimana')).toBe('2026-04-05');
      expect(spostaPeriodo('2026-03-30', 'settimana', -1)).toBe('2026-03-23');
    });
    it('la settimana del cambio di ottobre (19–25) e quella dopo (26 ottobre – 1 novembre)', () => {
      expect(inizioPeriodo('2026-10-25', 'settimana')).toBe('2026-10-19');
      expect(finePeriodo('2026-10-19', 'settimana')).toBe('2026-10-25');
      expect(spostaPeriodo('2026-10-19', 'settimana', 1)).toBe('2026-10-26');
      expect(spostaPeriodo('2026-10-26', 'settimana', -1)).toBe('2026-10-19');
    });
    it('i mesi di marzo e ottobre hanno 31 giorni interi e si passa da uno all\'altro senza salti', () => {
      expect(finePeriodo('2026-03-01', 'mese')).toBe('2026-03-31');
      expect(finePeriodo('2026-10-01', 'mese')).toBe('2026-10-31');
      expect(spostaPeriodo('2026-03-01', 'mese', 1)).toBe('2026-04-01');
      expect(spostaPeriodo('2026-10-01', 'mese', 1)).toBe('2026-11-01');
      expect(spostaPeriodo('2026-11-01', 'mese', -1)).toBe('2026-10-01');
    });
    it('contiene: la domenica del cambio sta nella sua settimana e nel suo mese', () => {
      expect(contiene('2026-03-23', 'settimana', '2026-03-29')).toBe(true);
      expect(contiene('2026-03-23', 'settimana', '2026-03-30')).toBe(false);
      expect(contiene('2026-10-19', 'settimana', '2026-10-25')).toBe(true);
      expect(contiene('2026-10-01', 'mese', '2026-10-25')).toBe(true);
    });
  });

  describe('contiene', () => {
    it('giorno: solo se è lo stesso giorno', () => {
      expect(contiene('2026-11-09', 'giorno', '2026-11-09')).toBe(true);
      expect(contiene('2026-11-09', 'giorno', '2026-11-10')).toBe(false);
      expect(contiene('2026-11-09', 'giorno', '2026-11-08')).toBe(false);
    });
    it('settimana: lunedì e domenica dentro, il giorno prima e quello dopo fuori', () => {
      expect(contiene('2026-11-09', 'settimana', '2026-11-09')).toBe(true);
      expect(contiene('2026-11-09', 'settimana', '2026-11-15')).toBe(true);
      expect(contiene('2026-11-09', 'settimana', '2026-11-08')).toBe(false);
      expect(contiene('2026-11-09', 'settimana', '2026-11-16')).toBe(false);
    });
    it('settimana a cavallo di due anni: 31 dicembre e 1 gennaio stanno nella stessa', () => {
      expect(contiene('2025-12-29', 'settimana', '2025-12-31')).toBe(true);
      expect(contiene('2025-12-29', 'settimana', '2026-01-01')).toBe(true);
      expect(contiene('2025-12-29', 'settimana', '2026-01-05')).toBe(false);
    });
    it('mese: primo e ultimo giorno dentro, quelli attorno fuori', () => {
      expect(contiene('2026-11-01', 'mese', '2026-11-01')).toBe(true);
      expect(contiene('2026-11-01', 'mese', '2026-11-30')).toBe(true);
      expect(contiene('2026-11-01', 'mese', '2026-10-31')).toBe(false);
      expect(contiene('2026-11-01', 'mese', '2026-12-01')).toBe(false);
    });
    it('mese: 29 febbraio solo nell\'anno bisestile', () => {
      expect(contiene('2028-02-01', 'mese', '2028-02-29')).toBe(true);
      expect(contiene('2028-02-01', 'mese', '2028-03-01')).toBe(false);
      expect(contiene('2026-02-01', 'mese', '2026-02-28')).toBe(true);
      expect(contiene('2026-02-01', 'mese', '2026-03-01')).toBe(false);
    });
  });

  describe('coerenza su cinque anni (2024–2028, con due bisestili)', () => {
    it('per ogni giorno e ogni passo: inizio ≤ giorno ≤ fine, contiene, idempotenza, andata e ritorno, fine + 1 = prossimo inizio', () => {
      const giorni: string[] = [];
      for (let t = Date.UTC(2024, 0, 1); t <= Date.UTC(2028, 11, 31); t += 86_400_000) {
        giorni.push(new Date(t).toISOString().slice(0, 10));
      }
      expect(giorni).toHaveLength(366 + 365 + 365 + 365 + 366);
      const errori: string[] = [];
      for (const g of giorni) {
        for (const passo of PASSI) {
          const ini = inizioPeriodo(g, passo);
          const fine = finePeriodo(ini, passo);
          if (!(ini <= g && g <= fine)) errori.push(`${passo} ${g}: ${ini}..${fine} non contiene il giorno`);
          if (!contiene(ini, passo, g)) errori.push(`${passo} ${g}: contiene() dice no`);
          if (inizioPeriodo(ini, passo) !== ini) errori.push(`${passo} ${g}: inizioPeriodo non idempotente`);
          const dopo = spostaPeriodo(ini, passo, 1);
          if (spostaPeriodo(dopo, passo, -1) !== ini) errori.push(`${passo} ${g}: avanti e indietro non torna`);
          if (contiene(ini, passo, dopo)) errori.push(`${passo} ${g}: il periodo dopo è dentro il periodo`);
          if (inizioPeriodo(dopo, passo) !== dopo) errori.push(`${passo} ${g}: l'inizio del periodo dopo non è un inizio`);
          // la fine e l'inizio del periodo dopo sono giorni consecutivi
          const domani = spostaPeriodo(fine, 'giorno', 1);
          if (domani !== dopo) errori.push(`${passo} ${g}: dopo la fine (${fine}) viene ${domani}, non ${dopo}`);
        }
      }
      expect(errori.slice(0, 5)).toEqual([]);
    });

    it('la settimana inizia sempre di lunedì e finisce di domenica', () => {
      for (let t = Date.UTC(2024, 0, 1); t <= Date.UTC(2028, 11, 31); t += 86_400_000) {
        const g = new Date(t).toISOString().slice(0, 10);
        const ini = inizioPeriodo(g, 'settimana');
        const fine = finePeriodo(ini, 'settimana');
        expect(new Date(ini + 'T12:00:00Z').getUTCDay()).toBe(1);
        expect(new Date(fine + 'T12:00:00Z').getUTCDay()).toBe(0);
      }
    });
  });
});

describe('etichettaPeriodo', () => {
  it('settimana nello stesso mese: «9–15 novembre» (con il trattino lungo)', () => {
    expect(etichettaPeriodo('2026-11-09', 'settimana', false)).toBe('9–15 novembre');
  });
  it('settimana tra due mesi: «26 ottobre – 1 novembre»; corta, a tre lettere: «26 ott – 1 nov»', () => {
    expect(etichettaPeriodo('2026-10-26', 'settimana', false)).toBe('26 ottobre – 1 novembre');
    expect(etichettaPeriodo('2026-10-26', 'settimana', true)).toBe('26 ott – 1 nov');
  });
  it('settimana tra due anni: «29 dicembre – 4 gennaio»; corta «29 dic – 4 gen»', () => {
    expect(etichettaPeriodo('2025-12-29', 'settimana', false)).toBe('29 dicembre – 4 gennaio');
    expect(etichettaPeriodo('2025-12-29', 'settimana', true)).toBe('29 dic – 4 gen');
  });
  it('settimana nello stesso mese, corta: resta «9–15 novembre» (design system: tre lettere solo tra due mesi)', () => {
    expect(etichettaPeriodo('2026-11-09', 'settimana', true)).toBe('9–15 novembre');
  });
  it('settimana di fine febbraio in anno bisestile e non', () => {
    expect(etichettaPeriodo('2028-02-28', 'settimana', false)).toBe('28 febbraio – 5 marzo');
    expect(etichettaPeriodo('2026-02-23', 'settimana', false)).toBe('23 febbraio – 1 marzo');
  });
  it('mese: «Novembre 2026» con la maiuscola', () => {
    expect(etichettaPeriodo('2026-11-01', 'mese', false)).toBe('Novembre 2026');
    expect(etichettaPeriodo('2028-02-01', 'mese', false)).toBe('Febbraio 2028');
    expect(etichettaPeriodo('2026-12-01', 'mese', false)).toBe('Dicembre 2026');
  });
  it('mese corto: più breve (o uguale) del lungo, con l\'anno e l\'iniziale del mese', () => {
    const lunga = etichettaPeriodo('2026-11-01', 'mese', false);
    const corta = etichettaPeriodo('2026-11-01', 'mese', true);
    expect(corta.length).toBeLessThanOrEqual(lunga.length);
    expect(corta).toContain('2026');
    expect(corta.toLowerCase().startsWith('nov')).toBe(true);
  });
  it('mese corto di un mese già breve («maggio») non si inventa niente', () => {
    expect(etichettaPeriodo('2026-05-01', 'mese', true).toLowerCase().startsWith('mag')).toBe(true);
  });
  it('giorno: «Lunedì 9 novembre» (formato lungo dei titoli, con la maiuscola)', () => {
    expect(etichettaPeriodo('2026-11-09', 'giorno', false)).toBe('Lunedì 9 novembre');
    expect(etichettaPeriodo('2026-11-15', 'giorno', false)).toBe('Domenica 15 novembre');
    expect(etichettaPeriodo('2028-02-29', 'giorno', false)).toBe('Martedì 29 febbraio');
  });
  it('giorno corto: contiene il giorno e il mese, non più lungo del lungo', () => {
    const lunga = etichettaPeriodo('2026-11-09', 'giorno', false);
    const corta = etichettaPeriodo('2026-11-09', 'giorno', true);
    expect(corta.length).toBeLessThanOrEqual(lunga.length);
    expect(corta).toMatch(/9/);
    expect(corta.toLowerCase()).toMatch(/nov|\/11/);
  });
  it('non dipende dal fuso del computer', () => {
    const prima = process.env.TZ;
    try {
      for (const fuso of FUSI) {
        process.env.TZ = fuso;
        expect(etichettaPeriodo('2026-03-29', 'giorno', false)).toBe('Domenica 29 marzo');
        expect(etichettaPeriodo('2026-10-25', 'giorno', false)).toBe('Domenica 25 ottobre');
        expect(etichettaPeriodo('2026-03-23', 'settimana', false)).toBe('23–29 marzo');
      }
    } finally {
      if (prima === undefined) delete process.env.TZ;
      else process.env.TZ = prima;
    }
  });
});

describe('vistaControllo', () => {
  it('fino a 3 opzioni un segmentato', () => {
    for (const n of [0, 1, 2, 3]) expect(vistaControllo(n)).toBe('segmentato');
  });
  it('oltre 3 una tendina', () => {
    for (const n of [4, 5, 8, 20]) expect(vistaControllo(n)).toBe('tendina');
  });
});
