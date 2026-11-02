import { describe, expect, it } from 'vitest';
import { temaEffettivo } from './tema.svelte';

describe('temaEffettivo', () => {
  it('con «Sistema» segue il dispositivo', () => {
    expect(temaEffettivo('sistema', true)).toBe('scuro');
    expect(temaEffettivo('sistema', false)).toBe('chiaro');
  });
  it('una scelta esplicita vale qualunque sia il dispositivo', () => {
    expect(temaEffettivo('chiaro', true)).toBe('chiaro');
    expect(temaEffettivo('scuro', false)).toBe('scuro');
  });
});
