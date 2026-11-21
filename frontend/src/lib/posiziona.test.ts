import { describe, expect, it } from 'vitest';
import { posizionaBolla, posizionaMenu, type Rett } from './posiziona';

const vista = { w: 390, h: 844 };
const bolla = { w: 280, h: 80 };
const i = (x: number, y: number): Rett => ({ x, y, w: 40, h: 40 });

describe('posizionaBolla', () => {
  it('«i» al centro: la bolla è centrata sotto, la punta è al centro della bolla', () => {
    const p = posizionaBolla(i(175, 200), bolla, vista);
    expect(p.sotto).toBe(true);
    expect(p.x).toBe(195 - 140 > 16 ? 55 : 16);
    expect(p.y).toBe(240 + 8);
    expect(p.punta).toBe(195 - p.x);
  });

  it('«i» a 4px dal bordo sinistro: la bolla resta a 16px dal bordo, la punta resta sulla «i»', () => {
    const p = posizionaBolla(i(4, 200), bolla, vista);
    expect(p.x).toBe(16);
    expect(p.punta).toBe(12); // il centro della «i» (24) cadrebbe a 8px dall'angolo: la punta resta a `raggio`
    expect(p.x + p.punta).toBeGreaterThanOrEqual(4);
    expect(p.x + p.punta).toBeLessThanOrEqual(44);
  });

  it('«i» a 4px dal bordo destro: la bolla finisce a 16px dal bordo, la punta resta sulla «i»', () => {
    const a = i(vista.w - 4 - 40, 200);
    const p = posizionaBolla(a, bolla, vista);
    expect(p.x + bolla.w).toBe(vista.w - 16);
    expect(p.x + p.punta).toBeGreaterThanOrEqual(a.x);
    expect(p.x + p.punta).toBeLessThanOrEqual(a.x + a.w);
  });

  it('vista più stretta di 280 + 32: parte dal margine sinistro e la punta resta dentro la bolla', () => {
    const p = posizionaBolla(i(100, 200), { w: 250, h: 60 }, { w: 280, h: 600 });
    expect(p.x).toBe(16);
    expect(p.punta).toBeGreaterThanOrEqual(12);
    expect(p.punta).toBeLessThanOrEqual(250 - 12);
  });

  it('sotto non c\'è posto: va sopra, con la punta in basso', () => {
    const p = posizionaBolla(i(175, 800), bolla, vista);
    expect(p.sotto).toBe(false);
    expect(p.y).toBe(800 - 8 - bolla.h);
  });

  it('sotto ci sta appena: resta sotto', () => {
    // spazio sotto = 844 - 240 - 8 - 16 = 580
    const p = posizionaBolla(i(175, 200), { w: 280, h: 580 }, vista);
    expect(p.sotto).toBe(true);
  });

  it('non c\'è posto né sopra né sotto: sotto (la pagina scorre)', () => {
    const p = posizionaBolla(i(175, 100), { w: 280, h: 500 }, { w: 390, h: 300 });
    expect(p.sotto).toBe(true);
  });

  it('i margini e le distanze si possono cambiare', () => {
    const p = posizionaBolla(i(0, 100), bolla, vista, { margine: 24, distanza: 4, raggio: 20 });
    expect(p.x).toBe(24);
    expect(p.y).toBe(140 + 4);
    expect(p.punta).toBe(20);
  });
});

describe('posizionaMenu', () => {
  const pannello = { w: 220, h: 200 };

  it('sotto il pulsante, allineato al suo bordo destro', () => {
    const m = posizionaMenu(i(330, 100), pannello, vista);
    expect(m.sotto).toBe(true);
    expect(m.x + pannello.w).toBe(370);
    expect(m.y).toBe(148);
  });

  it('se a destra non sta, si allinea al bordo sinistro del pulsante', () => {
    const m = posizionaMenu(i(20, 100), pannello, vista);
    expect(m.x).toBe(20);
  });

  it('mai fuori dai margini', () => {
    const m = posizionaMenu(i(4, 100), pannello, vista);
    expect(m.x).toBe(16);
    const n = posizionaMenu(i(vista.w - 44, 100), { w: 380, h: 100 }, vista);
    expect(n.x).toBeGreaterThanOrEqual(16);
  });

  it('sopra se sotto non c\'è posto', () => {
    const m = posizionaMenu(i(330, 780), pannello, vista);
    expect(m.sotto).toBe(false);
    expect(m.y).toBe(780 - 8 - pannello.h);
  });
});
