import { describe, expect, it } from 'vitest';
import { forzaPassword, requisitiPassword } from './password';

describe('password nuova', () => {
  it('spunta i requisiti mentre si scrive', () => {
    expect(requisitiPassword('Tmp-1', '', '', true).map((r) => r.ok)).toEqual([false, false, false]);
    expect(requisitiPassword('Tmp-1', 'tuffo del martedì', 'tuffo del martedì', true).every((r) => r.ok)).toBe(true);
    expect(requisitiPassword('stessa-password', 'stessa-password', 'stessa-password', false)[1]).toEqual({ testo: 'Diversa da quella attuale', ok: false });
  });
  it('la forza dipende dalla lunghezza', () => {
    expect([forzaPassword('').livello, forzaPassword('corta').livello, forzaPassword('dieci-car!').livello, forzaPassword('una frase davvero lunga').livello]).toEqual([0, 1, 2, 3]);
  });
});
