import { afterEach, describe, expect, it, vi } from 'vitest';
import { applicaScrittura, depositoLocale, LettoreSimulato, lettoreChip, memoriaVuota, type Deposito } from './chip.svelte';
import type { StatoCreditiBraccialetto } from './api';

const accesso = { chip: 'MIFARE_CLASSIC_1K', chiavi: [], blocchi: [4] };
const deposito = (): Deposito => {
  const m = new Map<string, Record<number, string>>();
  return { leggi: (u) => m.get(u) ?? null, scrivi: (u, b) => void m.set(u, b) };
};
const stato = (s: Partial<StatoCreditiBraccialetto>): StatoCreditiBraccialetto => ({
  sulBraccialetto: true, formato: 'PROVA', nomeFormato: '', pronto: true, nota: 'formato sconosciuto', accesso, ...s,
});

describe('lettore simulato', () => {
  it('un braccialetto mai scritto ha i blocchi a zero, poi tiene quello che si scrive', async () => {
    const l = new LettoreSimulato(deposito());
    await expect(l.leggi(accesso)).rejects.toThrow('nessun braccialetto');
    l.appoggia('7b:7b:7b:01');
    expect(await l.leggi(accesso)).toEqual(memoriaVuota('7B7B7B01', [4]));
    await l.scrivi('7B7B7B01', { 4: 'A'.repeat(32) }, accesso);
    expect((await l.leggi(accesso)).blocchi[4]).toBe('A'.repeat(32));
  });

  it('non scrive su un braccialetto diverso da quello letto', async () => {
    const l = new LettoreSimulato(deposito());
    l.appoggia('11223344');
    await expect(l.scrivi('7B7B7B01', { 4: 'A'.repeat(32) }, accesso)).rejects.toThrow('non c\'è più lo stesso');
  });

  it('applica una scrittura alla memoria', () => {
    expect(applicaScrittura(memoriaVuota('AA', [4, 5]), { 5: 'B'.repeat(32) }).blocchi).toEqual({ 4: '0'.repeat(32), 5: 'B'.repeat(32) });
  });
});

describe('quale lettore', () => {
  it('simulato con il formato di prova, nessuno con un formato sconosciuto o con i crediti nel gestionale', () => {
    expect(lettoreChip(stato({})).lettore?.tipo).toBe('simulato');
    expect(lettoreChip(stato({ pronto: false })).perche).toBe('formato sconosciuto');
    expect(lettoreChip(stato({ sulBraccialetto: false })).lettore).toBeNull();
    expect(lettoreChip(stato({ formato: 'ROMANO' })).perche).toContain('programma per Windows');
  });
});

describe('dove il lettore simulato tiene le memorie', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('nel browser, separate per braccialetto', () => {
    const memoria = new Map<string, string>();
    vi.stubGlobal('localStorage', { getItem: (k: string) => memoria.get(k) ?? null, setItem: (k: string, v: string) => memoria.set(k, v) });
    depositoLocale.scrivi('AA000001', { 4: 'ff'.repeat(16) });
    expect(JSON.parse(memoria.get('leone:chip-simulato:AA000001')!)).toEqual({ 4: 'ff'.repeat(16) });
    expect(depositoLocale.leggi('AA000001')).toEqual({ 4: 'ff'.repeat(16) });
    expect(depositoLocale.leggi('AA000002')).toBeNull();
  });

  it('senza memoria del browser, finché la pagina è aperta', () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('bloccato'); }, setItem: () => { throw new Error('bloccato'); } });
    depositoLocale.scrivi('BB000001', { 4: '01'.repeat(16) });
    expect(depositoLocale.leggi('BB000001')).toEqual({ 4: '01'.repeat(16) });
  });
});
