import { afterEach, describe, expect, it } from 'vitest';
import { fumetti } from './fumetto.svelte';

afterEach(() => fumetti.chiudi());

describe('fumetti: quale è aperto', () => {
  it('apri sostituisce quello di prima', () => {
    fumetti.apri('a');
    fumetti.apri('b');
    expect(fumetti.aperto).toBe('b');
  });

  it('chiudi(id) chiude solo il suo; un altro resta aperto', () => {
    fumetti.apri('a');
    fumetti.chiudi('b');
    expect(fumetti.aperto).toBe('a');
    fumetti.chiudi('a');
    expect(fumetti.aperto).toBeNull();
  });

  it('chiudi() senza id chiude qualunque sia', () => {
    fumetti.apri('a');
    fumetti.chiudi();
    expect(fumetti.aperto).toBeNull();
  });

  it('commuta apre e richiude', () => {
    fumetti.commuta('a');
    expect(fumetti.aperto).toBe('a');
    fumetti.commuta('a');
    expect(fumetti.aperto).toBeNull();
  });
});
