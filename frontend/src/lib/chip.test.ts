import { describe, expect, it } from 'vitest';
import { applicaScrittura, LettoreSimulato, lettoreChip, memoriaVuota, type Deposito } from './chip.svelte';
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
