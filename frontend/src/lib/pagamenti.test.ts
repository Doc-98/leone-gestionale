import { describe, expect, it } from 'vitest';
import { inOrdineDiScadenza, ripartisci, somma } from './pagamenti';

describe('pagamenti', () => {
  it('somma senza errori di arrotondamento', () => {
    expect(somma([0.1, 0.2])).toBe(0.3);
    expect(somma(['55', 150, null, ''])).toBe(205);
  });
  it('divide l\'importo nell\'ordine scelto, come il server', () => {
    const r = ripartisci(205, [{ id: 'quota', residuo: 35 }, { id: 'sorella', residuo: 5 }, { id: 'corso', residuo: 300 }, { id: 'cuffia', residuo: 8 }]);
    expect(r).toEqual([{ id: 'quota', quota: 35 }, { id: 'sorella', quota: 5 }, { id: 'corso', quota: 165 }, { id: 'cuffia', quota: 0 }]);
  });
  it('mette prima quello che scade prima', () => {
    expect(inOrdineDiScadenza([{ scadenza: '2026-09-14' }, { scadenza: '2026-09-01' }]).map((a) => a.scadenza)).toEqual(['2026-09-01', '2026-09-14']);
  });
});
