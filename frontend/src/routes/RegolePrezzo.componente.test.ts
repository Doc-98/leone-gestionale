import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Me, Offerta, PaginaListino, RegolaPrezzo, Stagione } from '../lib/api';
import { sessione } from '../lib/sessione.svelte';
import RegolePrezzo from './RegolePrezzo.svelte';

type Chiamata = { metodo: string; url: string; corpo: any };
let chiamate: Chiamata[];
let offerte: Offerta[];
let regole: RegolaPrezzo[];

const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
const stagione: Stagione = { id: 's1', nome: '2026/27', dal: '2026-09-01', al: '2027-08-31', quota: null, quotaRimborsabile: false };
const abbonamento: Offerta = {
  id: 'o1', stagioneId: 's1', nome: 'Scuola nuoto', tipo: 'CORSO', descrizione: null, ingressi: null, validitaMesi: null, validitaGiorni: null, corsoId: null, corsi: ['c1'],
  riduzioneTardiva: true, attiva: true, ordine: 0, prezzi: [{ volte: 1, gruppoId: null, importo: 40 }], eliminabile: true, motivoBlocco: null,
};
const utente = (permessi: string[]): Me => ({ accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi, admin: false, deveCambiarePassword: false } as Me);

beforeEach(() => {
  chiamate = [];
  offerte = [abbonamento];
  regole = [
    { tipo: 'RIDUZIONE_TARDIVA', attiva: true, parametri: { scaglioni: [{ dal: '2026-10-01', percentuale: 10 }, { dal: '2026-12-01', percentuale: 20 }] } },
    { tipo: 'RECESSO', attiva: true, parametri: { scaglioni: [{ finoAl: '2026-11-30', percentuale: 50 }], rimborso: 'DENARO' } },
    { tipo: 'RATE', attiva: true, parametri: { numero: 3, primaPercentuale: 50, scadenze: ['2026-10-15', '2026-11-15'] } },
    { tipo: 'SOSPENSIONE', attiva: false, parametri: {} },
  ];
  sessione.utente = utente(['IMPOSTAZIONI']);
  sessione.pronto = true;
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    if (m === 'GET' && url === '/api/stagioni') return json(200, [stagione]);
    if (m === 'GET' && url.startsWith('/api/listino')) return json(200, { stagione, offerte, regole } satisfies PaginaListino);
    return json(200, {});
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
});

const pronta = () => screen.findByRole('region', { name: 'Riduzione tardiva' });
const barra = () => document.querySelector('.salva-bar [role="status"]')?.textContent ?? '';
const sezione = (nome: string) => screen.getByRole('region', { name: nome });

describe('Regole di prezzo – pagina', () => {
  it('in cima la scelta della stagione e i fumetti; niente paragrafo di spiegazione in pagina', async () => {
    render(RegolePrezzo);
    await pronta();
    expect(screen.getByRole('combobox', { name: 'Stagione' })).toHaveValue('s1');
    expect(screen.getByRole('button', { name: "Spiega l'esempio" })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Spiega quando valgono le regole' })).toBeTruthy();
    expect(screen.queryByText(/Tocca o trascina sulla linea/)).toBeNull();
    expect(document.querySelector('.mini-btn, .x-btn')).toBeNull();
  });

  it('aggiungere e togliere uno scaglione cambia il conteggio della barra', async () => {
    render(RegolePrezzo);
    await pronta();
    expect(barra()).toBe('');
    await userEvent.click(within(sezione('Riduzione tardiva')).getByRole('button', { name: 'Aggiungi uno scaglione' }));
    await waitFor(() => expect(barra()).toBe('1 modifica da salvare'));
    // la riga nuova è senza data: il cestino la chiama per posizione
    const cestino = within(sezione('Riduzione tardiva')).getByRole('button', { name: 'Elimina lo scaglione 3' });
    await userEvent.click(cestino);
    await userEvent.click(within(sezione('Riduzione tardiva')).getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(barra()).toBe(''));
  });

  it('il cestino di uno scaglione chiede «Elimina?»: al primo tocco la riga resta, al secondo se ne va e la barra lo conta', async () => {
    render(RegolePrezzo);
    await pronta();
    const rid = sezione('Riduzione tardiva');
    expect(within(rid).getAllByLabelText('Dal')).toHaveLength(2);
    await userEvent.click(within(rid).getByRole('button', { name: 'Elimina lo scaglione dal 01/12/2026' }));
    expect(within(rid).getAllByLabelText('Dal')).toHaveLength(2);
    expect(barra()).toBe('');
    await userEvent.click(within(rid).getByRole('button', { name: /Elimina\?/ }));
    expect(within(rid).getAllByLabelText('Dal')).toHaveLength(1);
    await waitFor(() => expect(barra()).toBe('1 modifica da salvare'));
    // le righe restano con i propri valori (la chiave sta alla riga, non alla posizione)
    expect(within(rid).getByLabelText('Dal')).toHaveValue('2026-10-01');
  });

  it('togliere il primo scaglione non scambia i valori delle righe che restano', async () => {
    render(RegolePrezzo);
    await pronta();
    const rid = sezione('Riduzione tardiva');
    await userEvent.click(within(rid).getByRole('button', { name: 'Elimina lo scaglione dal 01/10/2026' }));
    await userEvent.click(within(rid).getByRole('button', { name: /Elimina\?/ }));
    expect(within(rid).getByLabelText('Dal')).toHaveValue('2026-12-01');
    expect(within(rid).getByLabelText('Percentuale')).toHaveValue(20);
  });

  it('le modifiche si contano per riga: togliere la prima di due righe e aggiungerne una vale 1 + 1, non un conto per campo', async () => {
    render(RegolePrezzo);
    await pronta();
    const rid = sezione('Riduzione tardiva');
    await userEvent.click(within(rid).getByRole('button', { name: 'Elimina lo scaglione dal 01/10/2026' }));
    await userEvent.click(within(rid).getByRole('button', { name: /Elimina\?/ }));
    expect(barra()).toBe('1 modifica da salvare');
    await userEvent.click(within(rid).getByRole('button', { name: 'Aggiungi uno scaglione' }));
    await waitFor(() => expect(barra()).toBe('2 modifiche da salvare'));
    await userEvent.type(within(rid).getAllByLabelText('Percentuale')[0], '5');
    await waitFor(() => expect(barra()).toBe('3 modifiche da salvare'));
  });

  it('il recesso ha «Fino al» e la riga «Dopo: niente rimborso»', async () => {
    render(RegolePrezzo);
    await pronta();
    const rec = sezione('Rimborso in caso di recesso');
    expect(within(rec).getByLabelText('Fino al')).toHaveValue('2026-11-30');
    expect(within(rec).getByText('niente rimborso')).toBeTruthy();
    expect(within(rec).getByRole('button', { name: 'Elimina lo scaglione fino al 30/11/2026' })).toBeTruthy();
  });

  it('le rate: cestino solo sopra le 2 rate, «Aggiungi una rata» fino a 12, tutto nella barra', async () => {
    render(RegolePrezzo);
    await pronta();
    const rate = sezione('Rate');
    expect(within(rate).getByRole('button', { name: 'Elimina la 3ª rata' })).toBeTruthy();
    await userEvent.click(within(rate).getByRole('button', { name: 'Elimina la 3ª rata' }));
    await userEvent.click(within(rate).getByRole('button', { name: /Elimina\?/ }));
    // ora sono 2 rate: la 2ª non si può togliere e la colonna resta vuota
    expect(within(rate).queryByRole('button', { name: /Elimina la/ })).toBeNull();
    expect(within(rate).getByLabelText('Scadenza della 2ª rata')).toBeTruthy();
    await userEvent.click(within(rate).getByRole('button', { name: 'Aggiungi una rata' }));
    expect(within(rate).getByLabelText('Scadenza della 3ª rata')).toBeTruthy();
    expect(barra()).toMatch(/modific/);
  });

  it('salva gli scaglioni rimasti, uno alla volta per regola cambiata', async () => {
    render(RegolePrezzo);
    await pronta();
    const rid = sezione('Riduzione tardiva');
    await userEvent.click(within(rid).getByRole('button', { name: 'Elimina lo scaglione dal 01/10/2026' }));
    await userEvent.click(within(rid).getByRole('button', { name: /Elimina\?/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'PUT')).toBe(true));
    const put = chiamate.find((c) => c.metodo === 'PUT')!;
    expect(put.url).toBe('/api/listino/regole/s1/RIDUZIONE_TARDIVA');
    expect(put.corpo.parametri.scaglioni).toEqual([{ dal: '2026-12-01', percentuale: 20 }]);
  });

  it('senza abbonamenti nel listino l\'esempio è uno stato vuoto, con l\'azione per chi può', async () => {
    offerte = [];
    render(RegolePrezzo);
    expect(await screen.findByText('Nessun abbonamento nel listino.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Aggiungi un abbonamento' })).toHaveAttribute('href', '#/listino');
  });

  it('senza IMPOSTAZIONI lo stato vuoto non ha l\'azione e le righe non hanno cestino né «Aggiungi»', async () => {
    sessione.utente = utente(['CLIENTI']);
    offerte = [];
    render(RegolePrezzo);
    expect(await screen.findByText('Nessun abbonamento nel listino.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Aggiungi un abbonamento' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Aggiungi uno scaglione' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Elimina lo scaglione/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Aggiungi una rata' })).toBeNull();
  });
});
