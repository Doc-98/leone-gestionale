import { describe, expect, it } from 'vitest';
import { posizionaBolla, posizionaMenu, type Rett } from './posiziona';

/**
 * Specifica: /tmp/b7/piano.md §2.2 e design system «Fumetto «i»».
 * Coordinate di viewport. Valori predefiniti: margine 16, distanza 8, raggio 12.
 * La bolla sta sotto l'àncora (sopra solo se sotto non c'è posto), spostata di lato per restare a `margine` dai bordi;
 * la punta segue l'àncora ed è tenuta ad almeno `raggio` dagli angoli della bolla.
 */
const TELEFONO = { w: 390, h: 844 };
const BOLLA = { w: 280, h: 60 };
const MARGINE = 16;
const RAGGIO = 12;
const DISTANZA = 8;

/** Una «i» di 24 px con il centro in `cx`. */
const iCentrata = (cx: number, y = 100): Rett => ({ x: cx - 12, y, w: 24, h: 24 });
const clamp = (v: number, a: number, b: number) => Math.min(Math.max(v, a), b);

describe('posizionaBolla – orizzontale', () => {
  it('«i» al centro: la bolla è centrata sulla «i» e la punta sta a metà bolla', () => {
    const p = posizionaBolla(iCentrata(195), BOLLA, TELEFONO);
    expect(p.x).toBe(195 - 140);
    expect(p.punta).toBe(140);
  });

  it('«i» a 4 px dal bordo sinistro: la bolla si ferma a 16 px dal bordo, la punta resta a 12 px dall\'angolo', () => {
    const p = posizionaBolla({ x: 4, y: 100, w: 24, h: 24 }, BOLLA, TELEFONO); // centro a 16
    expect(p.x).toBe(MARGINE);
    expect(p.punta).toBe(RAGGIO);
  });

  it('«i» a 4 px dal bordo destro: la bolla si ferma a 16 px dal bordo, la punta resta a 12 px dall\'angolo', () => {
    // àncora 24 px che finisce a 386: centro a 374 = bordo destro meno margine
    const p = posizionaBolla({ x: 362, y: 100, w: 24, h: 24 }, BOLLA, TELEFONO);
    expect(p.x).toBe(TELEFONO.w - MARGINE - BOLLA.w);
    expect(p.x + p.punta).toBeLessThanOrEqual(p.x + BOLLA.w - RAGGIO);
    expect(p.punta).toBe(BOLLA.w - RAGGIO);
  });

  it('«i» spostata un po\' a sinistra: la bolla si sposta, la punta segue la «i» (non resta a metà)', () => {
    const cx = 100; // centrata sarebbe a x = -40: va spostata a 16
    const p = posizionaBolla(iCentrata(cx), BOLLA, TELEFONO);
    expect(p.x).toBe(MARGINE);
    expect(p.x + p.punta).toBe(cx); // la punta indica ancora il centro della «i»
  });

  it('«i» spostata un po\' a destra: la punta indica il centro della «i»', () => {
    const cx = 300;
    const p = posizionaBolla(iCentrata(cx), BOLLA, TELEFONO);
    expect(p.x).toBe(TELEFONO.w - MARGINE - BOLLA.w);
    expect(p.x + p.punta).toBe(cx);
  });

  it('con una «i» larga come un pulsante da 40 px il centro è comunque quello dell\'àncora', () => {
    const p = posizionaBolla({ x: 200, y: 100, w: 40, h: 40 }, BOLLA, TELEFONO); // centro 220
    expect(p.x + p.punta).toBe(220);
  });

  it('schermo stretto (320 px) con bolla larga min(280, vw − 32) = 288 → 280: sta sempre dentro i margini', () => {
    const vista = { w: 320, h: 640 };
    const bolla = { w: Math.min(280, vista.w - 32), h: 60 };
    for (const cx of [0, 10, 40, 160, 280, 310, 320]) {
      const p = posizionaBolla(iCentrata(cx), bolla, vista);
      expect(p.x).toBeGreaterThanOrEqual(MARGINE);
      expect(p.x + bolla.w).toBeLessThanOrEqual(vista.w - MARGINE);
    }
  });

  it('bolla larga esattamente quanto lo spazio tra i margini (vw − 32): x vale il margine per ogni «i»', () => {
    const vista = { w: 300, h: 600 };
    const bolla = { w: 268, h: 50 };
    for (const cx of [20, 150, 280]) {
      expect(posizionaBolla(iCentrata(cx), bolla, vista).x).toBe(MARGINE);
    }
  });

  it('proprietà: su tutta la larghezza di 390 px la bolla sta nei margini e la punta nell\'intervallo [raggio, w − raggio]', () => {
    for (let cx = 0; cx <= TELEFONO.w; cx += 5) {
      const p = posizionaBolla(iCentrata(cx), BOLLA, TELEFONO);
      expect(p.x).toBeGreaterThanOrEqual(MARGINE);
      expect(p.x + BOLLA.w).toBeLessThanOrEqual(TELEFONO.w - MARGINE);
      expect(p.punta).toBeGreaterThanOrEqual(RAGGIO);
      expect(p.punta).toBeLessThanOrEqual(BOLLA.w - RAGGIO);
    }
  });

  it('proprietà: la punta indica il centro della «i» ovunque sia possibile (centro tra margine + raggio e vw − margine − raggio)', () => {
    for (let cx = MARGINE + RAGGIO; cx <= TELEFONO.w - MARGINE - RAGGIO; cx += 3) {
      const p = posizionaBolla(iCentrata(cx), BOLLA, TELEFONO);
      expect(p.x + p.punta).toBeCloseTo(cx, 6);
    }
  });

  it('proprietà: dove la punta non può seguire la «i» (troppo vicina al bordo) si ferma a `raggio` dall\'angolo, non oltre', () => {
    for (const cx of [0, 5, 20, 27]) {
      const p = posizionaBolla(iCentrata(cx), BOLLA, TELEFONO);
      expect(p.punta).toBe(clamp(cx - p.x, RAGGIO, BOLLA.w - RAGGIO));
    }
  });

  it('simmetria: specchiando la «i» rispetto al centro dello schermo si specchia anche la bolla', () => {
    const a = posizionaBolla(iCentrata(50), BOLLA, TELEFONO);
    const b = posizionaBolla(iCentrata(TELEFONO.w - 50), BOLLA, TELEFONO);
    expect(a.x + (b.x + BOLLA.w)).toBe(TELEFONO.w);
    expect(a.punta + b.punta).toBe(BOLLA.w);
  });
});

describe('posizionaBolla – verticale', () => {
  it('c\'è posto sotto: sta sotto la «i», a `distanza` dal suo bordo basso', () => {
    const p = posizionaBolla(iCentrata(195, 100), BOLLA, TELEFONO);
    expect(p.sotto).toBe(true);
    expect(p.y).toBe(100 + 24 + DISTANZA);
  });

  it('sotto anche quando sopra c\'è più posto (la regola è «sotto, sopra solo se manca spazio»)', () => {
    const p = posizionaBolla(iCentrata(195, 500), BOLLA, TELEFONO); // sotto ce ne sono 300, sopra 500
    expect(p.sotto).toBe(true);
  });

  it('sotto non c\'è posto e sopra sì: va sopra, a `distanza` dal bordo alto della «i»', () => {
    const p = posizionaBolla(iCentrata(195, 800), BOLLA, TELEFONO);
    expect(p.sotto).toBe(false);
    expect(p.y).toBe(800 - DISTANZA - BOLLA.h);
  });

  it('né sopra né sotto c\'è posto: sta sotto (la pagina scorre), mai sopra fuori schermo', () => {
    const vista = { w: 390, h: 200 };
    const p = posizionaBolla(iCentrata(195, 90), { w: 280, h: 150 }, vista);
    expect(p.sotto).toBe(true);
    expect(p.y).toBe(90 + 24 + DISTANZA);
  });

  it('«i» in cima alla pagina: sotto', () => {
    expect(posizionaBolla(iCentrata(195, 0), BOLLA, TELEFONO).sotto).toBe(true);
  });

  it('sopra la bolla non esce dal bordo alto: se va sopra, y non è negativo', () => {
    const vista = { w: 390, h: 300 };
    const p = posizionaBolla(iCentrata(195, 240), { w: 280, h: 100 }, vista); // sotto: 272+100 > 300; sopra: y = 132
    expect(p.sotto).toBe(false);
    expect(p.y).toBeGreaterThanOrEqual(0);
    expect(p.y + 100).toBeLessThanOrEqual(240); // finisce sopra la «i»
  });
});

describe('posizionaBolla – opzioni e purezza', () => {
  it('`distanza` personalizzata sposta la bolla dalla «i»', () => {
    const p = posizionaBolla(iCentrata(195, 100), BOLLA, TELEFONO, { distanza: 20 });
    expect(p.y).toBe(100 + 24 + 20);
  });

  it('`margine` personalizzato (0) lascia la bolla arrivare al bordo', () => {
    const p = posizionaBolla({ x: 0, y: 100, w: 24, h: 24 }, BOLLA, TELEFONO, { margine: 0 });
    expect(p.x).toBe(0);
  });

  it('`margine` personalizzato più grande (24) è rispettato', () => {
    const p = posizionaBolla({ x: 0, y: 100, w: 24, h: 24 }, BOLLA, TELEFONO, { margine: 24 });
    expect(p.x).toBe(24);
  });

  it('`raggio` personalizzato tiene la punta più lontana dall\'angolo', () => {
    const p = posizionaBolla({ x: 4, y: 100, w: 24, h: 24 }, BOLLA, TELEFONO, { raggio: 20 });
    expect(p.punta).toBe(20);
  });

  it('non modifica gli argomenti e dà lo stesso risultato a ogni chiamata', () => {
    const ancora = Object.freeze({ x: 10, y: 20, w: 24, h: 24 });
    const bolla = Object.freeze({ w: 280, h: 60 });
    const vista = Object.freeze({ w: 390, h: 844 });
    expect(posizionaBolla(ancora, bolla, vista)).toEqual(posizionaBolla(ancora, bolla, vista));
  });

  it('i numeri sono finiti (niente NaN) anche con una «i» fuori dallo schermo', () => {
    const p = posizionaBolla({ x: -50, y: -50, w: 24, h: 24 }, BOLLA, TELEFONO);
    for (const n of [p.x, p.y, p.punta]) expect(Number.isFinite(n)).toBe(true);
  });
});

describe('posizionaMenu', () => {
  const OPZ = { margine: 16, distanza: 8 };
  const PANNELLO = { w: 220, h: 200 };

  it('«⋯» in alto a destra: il pannello si allinea al bordo destro del pulsante e sta sotto', () => {
    const p = posizionaMenu({ x: 330, y: 20, w: 44, h: 44 }, PANNELLO, TELEFONO, OPZ);
    expect(p.x + PANNELLO.w).toBe(330 + 44);
    expect(p.sotto).toBe(true);
    expect(p.y).toBe(20 + 44 + 8);
  });

  it('pulsante vicino al bordo sinistro: il bordo destro non sta, si allinea al sinistro', () => {
    const p = posizionaMenu({ x: 20, y: 20, w: 44, h: 44 }, PANNELLO, TELEFONO, OPZ);
    expect(p.x).toBe(20);
  });

  it('non esce mai dai margini, in qualunque posizione del pulsante', () => {
    for (let x = 0; x <= TELEFONO.w - 44; x += 10) {
      const p = posizionaMenu({ x, y: 20, w: 44, h: 44 }, { w: 300, h: 200 }, TELEFONO, OPZ);
      expect(p.x).toBeGreaterThanOrEqual(16);
      expect(p.x + 300).toBeLessThanOrEqual(TELEFONO.w - 16);
    }
  });

  it('sotto non c\'è posto: va sopra il pulsante', () => {
    const p = posizionaMenu({ x: 330, y: 790, w: 44, h: 44 }, PANNELLO, TELEFONO, OPZ);
    expect(p.sotto).toBe(false);
    expect(p.y).toBe(790 - 8 - PANNELLO.h);
  });

  it('né sopra né sotto: sotto', () => {
    const p = posizionaMenu({ x: 330, y: 90, w: 44, h: 44 }, { w: 220, h: 150 }, { w: 390, h: 200 }, OPZ);
    expect(p.sotto).toBe(true);
  });

  it('con le opzioni predefinite il pannello è comunque dentro i margini e sotto se c\'è posto', () => {
    const p = posizionaMenu({ x: 330, y: 20, w: 44, h: 44 }, PANNELLO, TELEFONO);
    expect(p.sotto).toBe(true);
    expect(p.x).toBeGreaterThanOrEqual(0);
    expect(p.x + PANNELLO.w).toBeLessThanOrEqual(TELEFONO.w);
  });
});
