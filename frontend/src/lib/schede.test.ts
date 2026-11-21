import { describe, expect, it } from 'vitest';
import { frecceVisibili, schedaDopoTasto, scorriPerVedere } from './schede';

describe('schedaDopoTasto', () => {
  it('destra e sinistra vanno a giro', () => {
    expect(schedaDopoTasto('ArrowRight', 0, 5)).toBe(1);
    expect(schedaDopoTasto('ArrowRight', 4, 5)).toBe(0);
    expect(schedaDopoTasto('ArrowLeft', 2, 5)).toBe(1);
    expect(schedaDopoTasto('ArrowLeft', 0, 5)).toBe(4);
  });
  it('Inizio e Fine', () => {
    expect(schedaDopoTasto('Home', 3, 5)).toBe(0);
    expect(schedaDopoTasto('End', 1, 5)).toBe(4);
  });
  it('gli altri tasti non servono: -1', () => {
    expect(schedaDopoTasto('ArrowDown', 0, 5)).toBe(-1);
    expect(schedaDopoTasto('Enter', 0, 5)).toBe(-1);
    expect(schedaDopoTasto('ArrowRight', 0, 0)).toBe(-1);
  });
  it('con due schede le frecce si scambiano', () => {
    expect(schedaDopoTasto('ArrowRight', 0, 2)).toBe(1);
    expect(schedaDopoTasto('ArrowRight', 1, 2)).toBe(0);
  });
});

describe('frecceVisibili', () => {
  it('tutto in vista: nessuna freccia', () => {
    expect(frecceVisibili(0, 400, 400)).toEqual({ sx: false, dx: false });
  });
  it('all\'inizio solo la destra, in fondo solo la sinistra, in mezzo tutte e due', () => {
    expect(frecceVisibili(0, 300, 600)).toEqual({ sx: false, dx: true });
    expect(frecceVisibili(300, 300, 600)).toEqual({ sx: true, dx: false });
    expect(frecceVisibili(120, 300, 600)).toEqual({ sx: true, dx: true });
  });
  it('tolleranza di 1 px', () => {
    expect(frecceVisibili(1, 300, 301)).toEqual({ sx: false, dx: false });
    expect(frecceVisibili(0.5, 300, 600)).toEqual({ sx: false, dx: true });
    expect(frecceVisibili(299.4, 300, 600)).toEqual({ sx: true, dx: false });
    expect(frecceVisibili(2, 300, 303)).toEqual({ sx: true, dx: false });
  });
});

describe('scorriPerVedere', () => {
  it('già in vista: invariato', () => {
    expect(scorriPerVedere(100, 80, 50, 300)).toBe(50);
  });
  it('a sinistra del bordo: scorre indietro fino al suo inizio', () => {
    expect(scorriPerVedere(40, 80, 100, 300)).toBe(40);
  });
  it('oltre il bordo destro: scorre avanti fino alla sua fine', () => {
    expect(scorriPerVedere(400, 100, 100, 300)).toBe(200);
  });
  it('il margine lascia posto alle frecce da tutti e due i lati', () => {
    expect(scorriPerVedere(110, 80, 100, 300, 44)).toBe(66);
    expect(scorriPerVedere(300, 80, 100, 300, 44)).toBe(124);
    expect(scorriPerVedere(200, 60, 100, 300, 44)).toBe(100);
  });
  it('non scorre sotto zero', () => {
    expect(scorriPerVedere(10, 80, 30, 300, 44)).toBe(0);
  });
  it('una scheda più larga della vista: vince il suo inizio', () => {
    expect(scorriPerVedere(100, 500, 0, 300)).toBe(100);
    expect(scorriPerVedere(100, 500, 400, 300)).toBe(100);
  });
});
