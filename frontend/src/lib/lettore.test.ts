import { describe, expect, it } from 'vitest';
import { indirizzoPerTelefono } from './lettore.svelte';

describe('indirizzo da aprire sul telefono', () => {
  it('su un server vero resta quello', () => {
    expect(indirizzoPerTelefono('https://gestionale.leone.local', ['192.168.1.9'])).toEqual({ url: 'https://gestionale.leone.local', localhost: false, sviluppo: false });
  });
  it('con localhost usa l\'indirizzo del PC in rete, con la stessa porta', () => {
    expect(indirizzoPerTelefono('http://localhost:5173', ['192.168.1.9'])).toEqual({ url: 'http://192.168.1.9:5173', localhost: false, sviluppo: true });
  });
  it('con localhost e senza rete lo segnala', () => {
    expect(indirizzoPerTelefono('http://localhost:5173', [])).toEqual({ url: 'http://localhost:5173', localhost: true, sviluppo: true });
  });
});
