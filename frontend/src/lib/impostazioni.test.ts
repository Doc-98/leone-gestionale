import { describe, expect, it } from 'vitest';
import { cercaImpostazioni, dataIndice, giorniStagione, hrefVoce, indiceGiorno, mesiStagione, posizione, rateDel, recessoDel, riduzioneDel, testoRate } from './impostazioni';

const tutte = ['societa', 'calendario', 'listino', 'prezzi', 'metodi', 'regole', 'promemoria', 'servizi', 'postazioni', 'account'];
const euro = (n: number) => `${n} €`;

describe('ricerca delle impostazioni', () => {
  it('trova per parola, senza accenti né maiuscole, solo nelle pagine visibili', () => {
    expect(cercaImpostazioni('CAUZIONE', tutte).map((v) => v.titolo)).toContain('Cauzione del braccialetto');
    expect(cercaImpostazioni('societa', tutte).length).toBeGreaterThan(0);
    expect(cercaImpostazioni('cauzione', ['societa'])).toEqual([]);
    expect(cercaImpostazioni('   ', tutte)).toEqual([]);
  });
  it('tutte le parole devono esserci; prima i titoli che cominciano con la parola', () => {
    expect(cercaImpostazioni('quota tesseramento', tutte)[0].titolo).toBe('Quota associativa');
    expect(cercaImpostazioni('recesso', tutte)[0].titolo).toBe('Recesso');
  });
  it("l'indirizzo porta alla pagina e al riquadro", () => {
    const v = cercaImpostazioni('rate', tutte)[0];
    expect(hrefVoce(v)).toBe('#/prezzi/rate');
  });
});

describe('la linea della stagione', () => {
  const dal = '2026-09-01', al = '2027-08-31';
  it('giorni e indici', () => {
    expect(giorniStagione(dal, al)).toBe(365);
    expect(indiceGiorno(dal, '2026-11-01')).toBe(61);
    expect(dataIndice(dal, 61)).toBe('2026-11-01');
    expect(dataIndice(dal, -30)).toBe('2026-08-02');
  });
  it('la posizione resta tra 0 e 100', () => {
    expect(posizione(dal, al, '2026-11-01')).toBeCloseTo(16.85, 1);
    expect(posizione(dal, al, '2025-01-01')).toBe(0);
    expect(posizione(dal, al, '2030-01-01')).toBe(100);
  });
  it('dodici mesi, da settembre ad agosto, con gennaio segnato', () => {
    const m = mesiStagione(dal, al);
    expect(m.map((x) => x.nome)).toEqual(['set', 'ott', 'nov', 'dic', 'gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago']);
    expect(m.find((x) => x.gennaio)?.mese).toBe('2027-01');
    expect(m.reduce((s, x) => s + x.largo, 0)).toBeCloseTo(100, 5);
  });
});

describe('regole di prezzo', () => {
  const riduzione = [{ data: '2026-12-01', percentuale: 20 }, { data: '2026-11-01', percentuale: 10 }];
  const recesso = [{ data: '2026-10-31', percentuale: 60 }, { data: '2026-09-14', percentuale: 90 }, { data: '2026-12-31', percentuale: 40 }];
  it('riduzione: l\'ultimo scaglione già cominciato', () => {
    expect(riduzioneDel(riduzione, '2026-10-15')).toBe(0);
    expect(riduzioneDel(riduzione, '2026-11-01')).toBe(10);
    expect(riduzioneDel(riduzione, '2027-03-01')).toBe(20);
  });
  it('recesso: il primo scaglione non ancora finito', () => {
    expect(recessoDel(recesso, '2026-09-01')?.percentuale).toBe(90);
    expect(recessoDel(recesso, '2026-11-01')?.percentuale).toBe(40);
    expect(recessoDel(recesso, '2027-01-01')).toBeNull();
  });
  it('rate: prima della scadenza n × quota, dopo una sola', () => {
    const prima = rateDel(520, 2, 50, ['2026-09-14'], '2026-09-01');
    expect(prima).toEqual({ subito: 260, dopo: [{ importo: 260, scadenza: '2026-09-14' }] });
    expect(testoRate(prima, euro)).toBe('2 × 260 €');
    const dopo = rateDel(520, 2, 50, ['2026-09-14'], '2026-09-15');
    expect(dopo).toEqual({ subito: 520, dopo: [] });
    expect(testoRate(dopo, euro)).toBe('1 × 520 €');
  });
  it('rate: con più rate, quelle scadute si pagano subito con la prima', () => {
    const r = rateDel(600, 3, 50, ['2026-10-01', '2027-01-15'], '2026-11-01');
    expect(r).toEqual({ subito: 450, dopo: [{ importo: 150, scadenza: '2027-01-15' }] });
    expect(testoRate(r, euro)).toBe('450 € + 1 × 150 €');
  });
});
