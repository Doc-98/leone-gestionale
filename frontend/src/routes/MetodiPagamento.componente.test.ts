import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Me, MetodoPagamento } from '../lib/api';
import { sessione } from '../lib/sessione.svelte';
import MetodiPagamento from './MetodiPagamento.svelte';

/** La pagina con un `fetch` finto: i PUT di modifica, riga e foglio sempre uguali, 409 e 400, metodo sparito. */
type Chiamata = { metodo: string; url: string; corpo: any };
let chiamate: Chiamata[];
let metodi: MetodoPagamento[];
let forzate: Record<string, { status: number; corpo?: unknown; ritardo?: number }>;

const metodo = (id: string, nome: string, extra: Partial<MetodoPagamento> = {}): MetodoPagamento => ({
  id, nome, daIncassare: false, contanti: false, riscontro: false, attivo: true, ordine: 0, borsellino: false, eliminabile: true, motivoBlocco: null, ...extra,
});
const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
const utente: Me = { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi: ['IMPOSTAZIONI'], admin: false, deveCambiarePassword: false };

beforeEach(() => {
  chiamate = [];
  forzate = {};
  metodi = [metodo('m1', 'Contanti', { contanti: true, ordine: 1 }), metodo('m2', 'Carta', { ordine: 2 }), metodo('m3', 'Satispay', { daIncassare: true, ordine: 3 })];
  sessione.utente = utente;
  sessione.pronto = true;
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    const f = forzate[`${m} ${url}`];
    if (f) {
      if (f.ritardo) await new Promise((r) => setTimeout(r, f.ritardo));
      return json(f.status, f.corpo);
    }
    if (m === 'GET' && url === '/api/pagamenti/metodi') return json(200, metodi);
    if (m === 'GET') return json(200, {});
    const id = url.match(/^\/api\/pagamenti\/metodi\/([^/]+)$/)?.[1];
    if (m === 'PUT' && id) {
      const i = metodi.findIndex((x) => x.id === id);
      metodi[i] = { ...metodi[i], ...corpo };
      return json(200, metodi[i]);
    }
    return json(200, {});
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
});

const attese = () => screen.findByLabelText('Sposta «Contanti»');
/** quello della riga: con il foglio aperto ce n'è un altro con lo stesso nome, ma la riga viene prima nel documento */
const interruttore = (n: string) => screen.getAllByRole('switch', { name: `Acceso: «${n}»` })[0];

describe('Metodi di pagamento – pagina', () => {
  it('le modifiche non rimandano `ordine` (non annullano un riordino in corso)', async () => {
    render(MetodiPagamento);
    await attese();
    await userEvent.click(interruttore('Carta'));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'PUT')).toBe(true));
    const put = chiamate.find((c) => c.metodo === 'PUT')!;
    expect(put.corpo).not.toHaveProperty('ordine');
    expect(put.corpo.attivo).toBe(false);
  });

  it('interruttore della riga e del foglio sono sempre uguali, in tutte e due le direzioni', async () => {
    render(MetodiPagamento);
    await attese();
    await userEvent.click(screen.getByText('Carta'));
    const foglio = await screen.findByRole('dialog');
    const nelFoglio = () => within(foglio).getByRole('switch');
    expect(nelFoglio()).toHaveAttribute('aria-checked', 'true');
    await userEvent.click(nelFoglio());
    await waitFor(() => expect(interruttore('Carta')).toHaveAttribute('aria-checked', 'false'));
    expect(nelFoglio()).toHaveAttribute('aria-checked', 'false');
    expect(within(foglio).getByText('spento')).toBeTruthy();
    // dalla riga (dietro al foglio, ma raggiungibile dal DOM): il foglio segue
    await userEvent.click(interruttore('Carta'));
    await waitFor(() => expect(nelFoglio()).toHaveAttribute('aria-checked', 'true'));
    expect(within(foglio).queryByText('spento')).toBeNull();
  });

  it('due tocchi veloci sull\'interruttore: la riga finisce come il server', async () => {
    render(MetodiPagamento);
    await attese();
    await userEvent.click(interruttore('Carta'));
    await userEvent.click(interruttore('Carta'));
    await waitFor(() => expect(chiamate.filter((c) => c.metodo === 'PUT')).toHaveLength(2));
    await new Promise((r) => setTimeout(r, 30));
    expect(metodi[1].attivo).toBe(true);
    expect(interruttore('Carta')).toHaveAttribute('aria-checked', 'true');
  });

  it('errore dell\'interruttore con il foglio aperto: il messaggio sta nel foglio', async () => {
    forzate['PUT /api/pagamenti/metodi/m2'] = { status: 500, corpo: { detail: 'Non salvato' } };
    render(MetodiPagamento);
    await attese();
    await userEvent.click(screen.getByText('Carta'));
    const foglio = await screen.findByRole('dialog');
    await userEvent.click(within(foglio).getByRole('switch'));
    expect(await within(foglio).findByRole('alert')).toBeTruthy();
  });

  it('409 all\'eliminazione: il messaggio sta sotto la riga e la riga resta', async () => {
    forzate['DELETE /api/pagamenti/metodi/m3'] = { status: 409, corpo: { detail: 'Satispay ha ancora 2 pagamenti da incassare' } };
    render(MetodiPagamento);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Satispay»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    const riga = (await screen.findByText(/Satispay ha ancora 2 pagamenti/)).closest('li')!;
    expect(within(riga).getByLabelText('Sposta «Satispay»')).toBeTruthy();
    expect(screen.getAllByRole('alert')).toHaveLength(1); // niente messaggio doppio sopra la card
  });

  it('400 «L\'elenco è cambiato» al riordino: ricarica e lo dice', async () => {
    forzate['PUT /api/pagamenti/metodi/ordine'] = { status: 400, corpo: { detail: "L'elenco dei metodi è cambiato: ricarica la pagina." } };
    render(MetodiPagamento);
    await attese();
    const prima = chiamate.filter((c) => c.url === '/api/pagamenti/metodi' && c.metodo === 'GET').length;
    screen.getByLabelText('Sposta «Contanti»').focus();
    await userEvent.keyboard('{ArrowDown}');
    expect(await screen.findByText("L'elenco era cambiato: l'ho ricaricato.")).toBeTruthy();
    expect(chiamate.filter((c) => c.url === '/api/pagamenti/metodi' && c.metodo === 'GET').length).toBeGreaterThan(prima);
  });

  it('riordino o interruttore dalla riga non mettono messaggi sopra la card', async () => {
    render(MetodiPagamento);
    await attese();
    await userEvent.click(interruttore('Carta'));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'PUT')).toBe(true));
    expect(screen.queryByText(/acceso|spento\./)).toBeNull();
  });

  it('foglio aperto su un metodo che sparisce: si chiude e dice «Il metodo non c\'è più.»', async () => {
    render(MetodiPagamento);
    await attese();
    await userEvent.click(screen.getByText('Carta'));
    await screen.findByRole('dialog');
    metodi = metodi.filter((m) => m.id !== 'm2');
    // la riga viene eliminata altrove: l'elenco si ricarica (qui con un altro metodo eliminato dalla riga stessa)
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Carta»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.getByText("Il metodo non c'è più.")).toBeTruthy();
    expect(screen.queryByText('Nuovo metodo')).toBeNull();
  });
});
