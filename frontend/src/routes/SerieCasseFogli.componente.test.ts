import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Cassa, Me, SerieRicevute } from '../lib/api';
import { sessione } from '../lib/sessione.svelte';
import ImpostazioniCassa from './ImpostazioniCassa.svelte';
import ImpostazioniRicevute from './ImpostazioniRicevute.svelte';

/** I fogli delle serie e delle casse: cosa salvano e in che ordine (il server è un `fetch` finto che segue il contratto). */
type Chiamata = { metodo: string; url: string; corpo: any };
let chiamate: Chiamata[];
let serie: SerieRicevute[];
let casse: Cassa[];

const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
const unaSerie = (id: string, nome: string, extra: Partial<SerieRicevute> = {}): SerieRicevute => ({
  id, nome, tipo: 'RICEVUTA', attiva: true, formato: '{stagione}-{numero}', cifre: 4, riparte: 'STAGIONE', prossimoNumero: null, periodo: '2026/27', emesse: 3, prossima: '2026/27-0004', ...extra,
});
const utente: Me = { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi: ['IMPOSTAZIONI'], admin: false, deveCambiarePassword: false };

beforeEach(() => {
  chiamate = [];
  serie = [unaSerie('s1', 'Ricevute'), unaSerie('s2', 'Ricevute estate', { attiva: false, emesse: 0 })];
  casse = [{ id: 'c1', nome: 'Segreteria', attiva: true, principale: true, ordine: 1 }, { id: 'c2', nome: 'Piscina', attiva: true, principale: false, ordine: 2 }];
  sessione.utente = utente;
  sessione.pronto = true;
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    if (m === 'GET' && url === '/api/ricevute/impostazioni')
      return json(200, { impostazioni: { intestata: 'PAGANTE', codiciFiscali: true, bollo: false, bolloSoglia: 77.47, bolloImporto: 2, dicitura: null, firma: false, dopo: 'CHIEDI', email: true, famiglia: 'CHIEDI' }, serie, mancaIntestazione: [] });
    if (m === 'GET' && url === '/api/cassa')
      return json(200, { impostazioni: { casse: 'PER_POSTAZIONE', fondo: 'FISSO', importoFondo: 100, chiusura: 'TURNO', cieca: false, tolleranza: 0, controllo: 'NESSUNA', spese: true, banca: false, oreAvviso: 12 },
        casse, stati: casse.map((cassa) => ({ cassa, aperta: false })), daRiscontrare: [], avvisi: {} });
    if (m === 'GET' && url === '/api/pagamenti/metodi') return json(200, []);
    if (m === 'POST' && url === '/api/ricevute/serie') { const n = unaSerie('s9', corpo.nome, { tipo: corpo.tipo, attiva: false, emesse: 0 }); serie = [...serie, n]; return json(200, n); }
    if (m === 'POST' && url === '/api/cassa/casse') { const n: Cassa = { id: 'c9', nome: corpo.nome, attiva: true, principale: false, ordine: 3 }; casse = [...casse, n]; return json(200, n); }
    const idS = url.match(/^\/api\/ricevute\/serie\/([^/]+)$/)?.[1];
    if (idS && m === 'PUT') { const i = serie.findIndex((x) => x.id === idS); serie[i] = { ...serie[i], ...corpo }; return json(200, serie[i]); }
    const idC = url.match(/^\/api\/cassa\/casse\/([^/]+)$/)?.[1];
    if (idC && m === 'PUT') {
      if (corpo.principale) casse = casse.map((c) => ({ ...c, principale: c.id === idC }));
      const i = casse.findIndex((x) => x.id === idC);
      casse[i] = { ...casse[i], ...corpo };
      return json(200, casse[i]);
    }
    return json(200, {});
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
});

const scritture = () => chiamate.filter((c) => c.metodo !== 'GET');

describe('Ricevute – foglio della serie', () => {
  it('«Salva la serie» manda tutti i campi della serie, non quelli della barra', async () => {
    render(ImpostazioniRicevute);
    await userEvent.click(await screen.findByText('Ricevute estate'));
    const foglio = await screen.findByRole('dialog');
    const nome = within(foglio).getByLabelText('Nome');
    await userEvent.clear(nome);
    await userEvent.type(nome, 'Estate 2027');
    await userEvent.click(within(foglio).getByRole('button', { name: 'Salva la serie' }));
    await waitFor(() => expect(scritture()).toHaveLength(1));
    expect(scritture()[0]).toMatchObject({ metodo: 'PUT', url: '/api/ricevute/serie/s2', corpo: { nome: 'Estate 2027', attiva: false, formato: '{stagione}-{numero}', cifre: 4, riparte: 'STAGIONE' } });
    // la barra di pagina non si accende per un cambio fatto nel foglio
    expect(screen.queryByText(/modific[ah]e? da salvare/)).toBeNull();
  });

  it('cifre fuori da 1–8: messaggio nel campo e «Salva la serie» spento', async () => {
    render(ImpostazioniRicevute);
    await userEvent.click(await screen.findByText('Ricevute estate'));
    const foglio = await screen.findByRole('dialog');
    const cifre = within(foglio).getByLabelText('Cifre del numero');
    await userEvent.clear(cifre);
    await userEvent.type(cifre, '9');
    expect(cifre).toHaveAttribute('aria-invalid', 'true');
    expect(within(foglio).getByText('Da 1 a 8 cifre.')).toBeTruthy();
    expect(within(foglio).getByRole('button', { name: 'Salva la serie' })).toBeDisabled();
  });

  it('una serie nuova: prima si crea, poi si applicano i valori del foglio (nasce spenta)', async () => {
    render(ImpostazioniRicevute);
    await userEvent.click(await screen.findByRole('button', { name: /Aggiungi una serie/ }));
    const foglio = await screen.findByRole('dialog');
    await userEvent.type(within(foglio).getByLabelText('Nome'), 'Fatture aziende');
    await userEvent.selectOptions(within(foglio).getByLabelText('Tipo'), 'FATTURA');
    await userEvent.click(within(foglio).getByRole('button', { name: 'Aggiungi la serie' }));
    await waitFor(() => expect(scritture()).toHaveLength(2));
    expect(scritture()[0]).toMatchObject({ metodo: 'POST', url: '/api/ricevute/serie', corpo: { nome: 'Fatture aziende', tipo: 'FATTURA' } });
    expect(scritture()[1]).toMatchObject({ metodo: 'PUT', url: '/api/ricevute/serie/s9', corpo: { attiva: false, formato: 'F{anno}/{numero}', riparte: 'ANNO' } });
  });
});

describe('Cassa – foglio della cassa', () => {
  it('una cassa nuova scelta come principale: si crea e poi diventa principale', async () => {
    render(ImpostazioniCassa);
    await userEvent.click(await screen.findByRole('button', { name: /Aggiungi una cassa/ }));
    const foglio = await screen.findByRole('dialog');
    await userEvent.type(within(foglio).getByLabelText('Nome'), 'Palestra');
    await userEvent.click(within(foglio).getByRole('switch', { name: 'Cassa principale' }));
    await userEvent.click(within(foglio).getByRole('button', { name: 'Aggiungi la cassa' }));
    await waitFor(() => expect(scritture()).toHaveLength(2));
    expect(scritture()[0]).toMatchObject({ metodo: 'POST', url: '/api/cassa/casse', corpo: { nome: 'Palestra' } });
    expect(scritture()[1]).toMatchObject({ metodo: 'PUT', url: '/api/cassa/casse/c9', corpo: { principale: true } });
  });

  it('la principale nel foglio: niente riquadro «Acceso», «Cassa principale» acceso e bloccato', async () => {
    render(ImpostazioniCassa);
    await userEvent.click(await screen.findByText('Segreteria'));
    const foglio = await screen.findByRole('dialog');
    expect(within(foglio).queryByRole('switch', { name: /^Acceso/ })).toBeNull();
    const principale = within(foglio).getByRole('switch', { name: 'Cassa principale' });
    expect(principale).toHaveAttribute('aria-checked', 'true');
    expect(principale).toBeDisabled();
  });
});

describe('Ricevute – barra di salvataggio della pagina', () => {
  it('bollo, dicitura e firma si salvano insieme dalla barra, nel corpo giusto', async () => {
    render(ImpostazioniRicevute);
    await userEvent.click(await screen.findByRole('switch', { name: 'Marca da bollo' }));
    const dicitura = screen.getByLabelText('Dicitura in fondo alla ricevuta');
    await userEvent.type(dicitura, 'Detraibile');
    await userEvent.click(screen.getByRole('switch', { name: 'Spazio per la firma' }));
    expect(screen.getByText('3 modifiche da salvare')).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }));
    await waitFor(() => expect(scritture()).toHaveLength(1));
    expect(scritture()[0]).toMatchObject({
      metodo: 'PUT', url: '/api/ricevute/impostazioni',
      corpo: { bollo: true, bolloSoglia: 77.47, bolloImporto: 2, dicitura: 'Detraibile', firma: true, intestata: 'PAGANTE' },
    });
  });

  it('con la marca scritta male «Salva» resta spento e il campo lo dice', async () => {
    render(ImpostazioniRicevute);
    await userEvent.click(await screen.findByRole('switch', { name: 'Marca da bollo' }));
    const soglia = screen.getByLabelText('Soglia della marca da bollo in euro');
    await userEvent.clear(soglia);
    await userEvent.type(soglia, 'abc');
    expect(soglia).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Salva' })).toBeDisabled();
    expect(scritture()).toHaveLength(0);
  });
});

describe('Ricevute – serie di base', () => {
  it('«Di base» sulla prima serie di ricevute accesa, «Richiesta dal sistema» se è anche l\'ultima', async () => {
    serie = [unaSerie('s1', 'Ricevute'), unaSerie('s2', 'Estate', { emesse: 0 })];
    render(ImpostazioniRicevute);
    const riga1 = (await screen.findByText('Ricevute')).closest('li')!;
    expect(within(riga1).getByText('Di base')).toBeTruthy();
    expect(within(screen.getByText('Estate').closest('li')!).queryByText('Di base')).toBeNull();
  });

  it('con una sola serie accesa vale «Richiesta dal sistema» e non «Di base»', async () => {
    render(ImpostazioniRicevute);
    const riga1 = (await screen.findByText('Ricevute')).closest('li')!;
    expect(within(riga1).getByText('Richiesta dal sistema')).toBeTruthy();
    expect(within(riga1).queryByText('Di base')).toBeNull();
  });

  it('la riga sotto il nome dice «nel periodo» (il blocco conta il totale)', async () => {
    render(ImpostazioniRicevute);
    expect(await screen.findByText(/Prossima 2026\/27-0004 · 3 nel periodo/)).toBeTruthy();
  });
});

describe('Cassa – cassa principale nel foglio', () => {
  it('scegliere «Cassa principale» in una cassa nuova accende e blocca il riquadro «Acceso»', async () => {
    render(ImpostazioniCassa);
    await userEvent.click(await screen.findByRole('button', { name: /Aggiungi una cassa/ }));
    const foglio = await screen.findByRole('dialog');
    const acceso = within(foglio).getByRole('switch', { name: /^Acceso/ });
    await userEvent.click(acceso); // la spengo
    expect(acceso).toHaveAttribute('aria-checked', 'false');
    await userEvent.click(within(foglio).getByRole('switch', { name: 'Cassa principale' }));
    expect(acceso).toHaveAttribute('aria-checked', 'true');
    expect(acceso).toBeDisabled();
  });
});
