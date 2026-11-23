import { describe, expect, it } from 'vitest';
import { creaCoda } from './coda';

describe('coda di chiamate', () => {
  it('le chiamate partono nell\'ordine, una dopo l\'altra', async () => {
    const inCoda = creaCoda();
    const eventi: string[] = [];
    const lenta = inCoda(async () => { eventi.push('1 inizia'); await new Promise((r) => setTimeout(r, 20)); eventi.push('1 finisce'); return 1; });
    const veloce = inCoda(async () => { eventi.push('2 inizia'); return 2; });
    expect(await Promise.all([lenta, veloce])).toEqual([1, 2]);
    expect(eventi).toEqual(['1 inizia', '1 finisce', '2 inizia']);
  });

  it('un errore arriva a chi ha chiamato e non ferma le successive', async () => {
    const inCoda = creaCoda();
    const fallita = inCoda(async () => { throw new Error('no'); });
    const dopo = inCoda(async () => 'ok');
    await expect(fallita).rejects.toThrow('no');
    expect(await dopo).toBe('ok');
  });
});
