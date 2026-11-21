import { describe, expect, it } from 'vitest';
import { frecceVisibili, schedaDopoTasto, scorriPerVedere } from './schede';

/**
 * Specifica: /tmp/b7/piano.md §2.4 e design system «Schede lunghe».
 * - `schedaDopoTasto`: frecce sinistra/destra a giro, Inizio, Fine; -1 se il tasto non serve.
 * - `frecceVisibili`: quando le schede non stanno, una freccia compare solo se c'è qualcosa da quella parte; tolleranza 1px.
 * - `scorriPerVedere`: lo scrollLeft per avere in vista la scheda [inizio, inizio+larghezza], con `margine` per le frecce;
 *   invariato se è già in vista.
 */
describe('schedaDopoTasto', () => {
  it('ArrowRight va alla scheda dopo; sull\'ultima torna alla prima', () => {
    expect(schedaDopoTasto('ArrowRight', 0, 5)).toBe(1);
    expect(schedaDopoTasto('ArrowRight', 3, 5)).toBe(4);
    expect(schedaDopoTasto('ArrowRight', 4, 5)).toBe(0);
  });

  it('ArrowLeft va alla scheda prima; sulla prima salta all\'ultima', () => {
    expect(schedaDopoTasto('ArrowLeft', 4, 5)).toBe(3);
    expect(schedaDopoTasto('ArrowLeft', 1, 5)).toBe(0);
    expect(schedaDopoTasto('ArrowLeft', 0, 5)).toBe(4);
  });

  it('Home va alla prima, End all\'ultima, da una scheda di mezzo', () => {
    expect(schedaDopoTasto('Home', 3, 5)).toBe(0);
    expect(schedaDopoTasto('End', 1, 5)).toBe(4);
  });

  it('Home sull\'ultima e End sulla prima si muovono', () => {
    expect(schedaDopoTasto('Home', 4, 5)).toBe(0);
    expect(schedaDopoTasto('End', 0, 5)).toBe(4);
  });

  it('i tasti verticali e gli altri non servono: -1', () => {
    for (const t of ['ArrowUp', 'ArrowDown', 'Tab', 'Enter', ' ', 'Escape', 'a', 'PageUp'])
      expect(schedaDopoTasto(t, 2, 5)).toBe(-1);
  });

  it('due schede: le frecce si alternano', () => {
    expect(schedaDopoTasto('ArrowRight', 0, 2)).toBe(1);
    expect(schedaDopoTasto('ArrowRight', 1, 2)).toBe(0);
    expect(schedaDopoTasto('ArrowLeft', 0, 2)).toBe(1);
  });

  it('una scheda sola: le frecce restano lì (o -1), mai fuori intervallo', () => {
    expect([0, -1]).toContain(schedaDopoTasto('ArrowRight', 0, 1));
    expect([0, -1]).toContain(schedaDopoTasto('ArrowLeft', 0, 1));
  });

  it('il risultato sta sempre tra -1 e n-1', () => {
    for (const t of ['ArrowRight', 'ArrowLeft', 'Home', 'End', 'x'])
      for (let n = 1; n <= 7; n++)
        for (let i = 0; i < n; i++) {
          const r = schedaDopoTasto(t, i, n);
          expect(r).toBeGreaterThanOrEqual(-1);
          expect(r).toBeLessThan(n);
        }
  });
});

describe('frecceVisibili', () => {
  it('le schede stanno (totale = larghezza): nessuna freccia', () => {
    expect(frecceVisibili(0, 300, 300)).toEqual({ sx: false, dx: false });
  });

  it('più strette della vista: nessuna freccia', () => {
    expect(frecceVisibili(0, 390, 250)).toEqual({ sx: false, dx: false });
  });

  it('all\'inizio di una riga lunga: solo la destra', () => {
    expect(frecceVisibili(0, 300, 700)).toEqual({ sx: false, dx: true });
  });

  it('a metà: tutte e due', () => {
    expect(frecceVisibili(200, 300, 700)).toEqual({ sx: true, dx: true });
  });

  it('in fondo (scrollLeft + larghezza = totale): solo la sinistra', () => {
    expect(frecceVisibili(400, 300, 700)).toEqual({ sx: true, dx: false });
  });

  it('tolleranza di 1px a sinistra: scrollLeft 1 non conta, 2 sì', () => {
    expect(frecceVisibili(1, 300, 700).sx).toBe(false);
    expect(frecceVisibili(2, 300, 700).sx).toBe(true);
  });

  it('tolleranza di 1px a destra: manca 1px non conta, ne mancano 2 sì', () => {
    expect(frecceVisibili(0, 300, 301).dx).toBe(false);
    expect(frecceVisibili(0, 300, 302).dx).toBe(true);
    expect(frecceVisibili(399, 300, 700).dx).toBe(false);
    expect(frecceVisibili(398, 300, 700).dx).toBe(true);
  });

  it('valori frazionari (zoom del browser): 0,4 px di scarto non fanno comparire le frecce', () => {
    expect(frecceVisibili(0.4, 300, 300.4)).toEqual({ sx: false, dx: false });
  });

  it('tutto a zero (nessuna misura ancora): nessuna freccia', () => {
    expect(frecceVisibili(0, 0, 0)).toEqual({ sx: false, dx: false });
  });
});

describe('scorriPerVedere', () => {
  // vista larga 300, margine 0 per i numeri esatti
  it('scheda già in vista: scrollLeft invariato', () => {
    expect(scorriPerVedere(120, 80, 100, 300, 0)).toBe(100);
    expect(scorriPerVedere(100, 80, 100, 300, 0)).toBe(100); // a filo a sinistra
    expect(scorriPerVedere(320, 80, 100, 300, 0)).toBe(100); // a filo a destra: 320+80 = 400 = 100+300
  });

  it('scheda tagliata a sinistra: la porta a filo a sinistra', () => {
    expect(scorriPerVedere(60, 80, 100, 300, 0)).toBe(60);
  });

  it('scheda tagliata a destra: la porta a filo a destra', () => {
    expect(scorriPerVedere(380, 80, 100, 300, 0)).toBe(380 + 80 - 300);
  });

  it('scheda del tutto fuori a destra e a sinistra', () => {
    expect(scorriPerVedere(900, 100, 0, 300, 0)).toBe(900 + 100 - 300);
    expect(scorriPerVedere(50, 100, 600, 300, 0)).toBe(50);
  });

  it('il margine lascia posto alle frecce: a sinistra', () => {
    // la scheda a 100 con scroll 100 sta sotto la freccia sinistra (margine 40): va scostata
    expect(scorriPerVedere(100, 80, 100, 300, 40)).toBe(60);
  });

  it('il margine lascia posto alle frecce: a destra', () => {
    // 320+80 = 400 = fine della vista: sotto la freccia destra: va scostata
    expect(scorriPerVedere(320, 80, 100, 300, 40)).toBe(100 + 40);
  });

  it('con margine, una scheda ben dentro resta ferma', () => {
    expect(scorriPerVedere(200, 80, 100, 300, 40)).toBe(100);
  });

  it('la prima scheda a scrollLeft 0 resta a 0 (nessun valore negativo)', () => {
    expect(scorriPerVedere(0, 80, 0, 300, 0)).toBe(0);
  });

  it('dopo lo scorrimento la scheda sta davvero in vista (prova su molte posizioni)', () => {
    const vista = 300;
    const margine = 40;
    const larghezza = 90;
    for (let inizio = margine; inizio <= 1000; inizio += 37)
      for (let scroll = 0; scroll <= 800; scroll += 53) {
        const r = scorriPerVedere(inizio, larghezza, scroll, vista, margine);
        expect(inizio - r).toBeGreaterThanOrEqual(0);
        expect(inizio + larghezza - r).toBeLessThanOrEqual(vista);
      }
  });

  it('senza margine esplicito la scheda sta comunque in vista (margine predefinito)', () => {
    const r = scorriPerVedere(700, 90, 0, 300);
    expect(700 - r).toBeGreaterThanOrEqual(0);
    expect(700 + 90 - r).toBeLessThanOrEqual(300);
    // e una scheda già bene in vista non si muove
    expect(scorriPerVedere(150, 90, 100, 300)).toBe(100);
  });
});
