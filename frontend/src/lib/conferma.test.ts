import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { creaConferma, etichettaArmata } from './conferma';

describe('conferma in due tocchi', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  const nuova = () => {
    const stati: boolean[] = [];
    return { stati, c: creaConferma((a) => stati.push(a)) };
  };

  it('il primo tocco arma e non esegue, il secondo esegue e disarma', () => {
    const { c, stati } = nuova();
    expect(c.tocca()).toBe(false);
    expect(stati).toEqual([true]);
    expect(c.tocca()).toBe(true);
    expect(stati).toEqual([true, false]);
  });

  it('dopo l\'esecuzione serve di nuovo il primo tocco', () => {
    const { c } = nuova();
    c.tocca();
    c.tocca();
    expect(c.tocca()).toBe(false);
  });

  it('si disarma da solo dopo 5 secondi, non prima', () => {
    const { c, stati } = nuova();
    c.tocca();
    vi.advanceTimersByTime(4999);
    expect(stati).toEqual([true]);
    vi.advanceTimersByTime(1);
    expect(stati).toEqual([true, false]);
    expect(c.tocca()).toBe(false);
  });

  it('disarmando (uscita dal pulsante) il tocco successivo non esegue e il tempo non scatta due volte', () => {
    const { c, stati } = nuova();
    c.tocca();
    c.disarma();
    expect(stati).toEqual([true, false]);
    vi.advanceTimersByTime(10000);
    expect(stati).toEqual([true, false]);
    expect(c.tocca()).toBe(false);
  });

  it('disarmare a riposo non fa nulla', () => {
    const { c, stati } = nuova();
    c.disarma();
    expect(stati).toEqual([]);
  });

  it('un nuovo arma dopo un disarmo riparte con i suoi 5 secondi', () => {
    const { c, stati } = nuova();
    c.tocca();
    vi.advanceTimersByTime(3000);
    c.disarma();
    c.tocca();
    vi.advanceTimersByTime(3000);
    expect(stati).toEqual([true, false, true]);
    vi.advanceTimersByTime(2000);
    expect(stati).toEqual([true, false, true, false]);
  });

  it('il nome per lo screen reader dice cosa fare', () => {
    expect(etichettaArmata('Elimina?')).toBe('Elimina? (tocca di nuovo per confermare)');
  });
});
