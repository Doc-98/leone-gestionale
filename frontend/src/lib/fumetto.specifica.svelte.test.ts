// @vitest-environment jsdom
import { flushSync } from 'svelte';
import { afterEach, describe, expect, it } from 'vitest';
import { fumetti } from './fumetto.svelte';

/**
 * Specifica §2.2: «Uno solo aperto»: stato condiviso `fumetti.aperto = $state<string | null>(null)`;
 * aprirne uno chiude l'altro.
 */
afterEach(() => {
  fumetti.aperto = null;
});

describe('registro dei fumetti aperti', () => {
  it('all\'inizio non ce n\'è nessuno aperto', () => {
    expect(fumetti.aperto).toBeNull();
  });

  it('aprirne uno lo registra, aprirne un altro sostituisce il primo (uno solo aperto)', () => {
    fumetti.aperto = 'a';
    expect(fumetti.aperto).toBe('a');
    fumetti.aperto = 'b';
    expect(fumetti.aperto).toBe('b');
  });

  it('si chiude rimettendo null', () => {
    fumetti.aperto = 'a';
    fumetti.aperto = null;
    expect(fumetti.aperto).toBeNull();
  });

  it('è reattivo: chi lo legge in un effetto viene avvisato a ogni cambio', () => {
    const visti: (string | null)[] = [];
    const fine = $effect.root(() => {
      $effect(() => {
        visti.push(fumetti.aperto);
      });
    });
    flushSync();
    fumetti.aperto = 'a';
    flushSync();
    fumetti.aperto = 'b';
    flushSync();
    fumetti.aperto = null;
    flushSync();
    fine();
    expect(visti).toEqual([null, 'a', 'b', null]);
  });
});
