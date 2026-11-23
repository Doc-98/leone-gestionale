import { describe, expect, it } from 'vitest';
import { rinumeraPrecedenze } from './gruppi';

const g = (id: string, precedenza: number | null) => ({ id, precedenza, nome: id });

describe('rinumeraPrecedenze', () => {
  it('numera 1, 2, 3 nell\'ordine dato tra chi ha la precedenza', () => {
    expect(rinumeraPrecedenze([g('a', 3), g('b', 1), g('c', 2)]).map((x) => x.precedenza)).toEqual([1, 2, 3]);
  });
  it('chi non ce l\'ha resta null e non occupa un numero', () => {
    const r = rinumeraPrecedenze([g('a', null), g('b', 2), g('c', null), g('d', 1)]);
    expect(r.map((x) => x.precedenza)).toEqual([null, 1, null, 2]);
    expect(r.map((x) => x.id)).toEqual(['a', 'b', 'c', 'd']);
  });
  it('non modifica l\'array di partenza e conserva gli altri campi', () => {
    const a = [g('a', 5), g('b', 9)];
    const r = rinumeraPrecedenze(a);
    expect(a.map((x) => x.precedenza)).toEqual([5, 9]);
    expect(r[0].nome).toBe('a');
    expect(rinumeraPrecedenze([])).toEqual([]);
  });
});
