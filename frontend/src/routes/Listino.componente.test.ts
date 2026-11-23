import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { GruppoClienti, Me, Offerta, PaginaListino, Stagione } from '../lib/api';
import { sessione } from '../lib/sessione.svelte';
import Listino from './Listino.svelte';

/** La pagina con un `fetch` finto: un listino con un abbonamento (con un prezzo per un gruppo), uno spento, un carnet. */
type Chiamata = { metodo: string; url: string; corpo: any };
let chiamate: Chiamata[];
let offerte: Offerta[];
let forzate: Record<string, { status: number; corpo?: unknown }>;

const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
const stagione: Stagione = { id: 's1', nome: '2026/27', dal: '2020-01-01', al: '2099-12-31', quota: null, quotaRimborsabile: false };
const gruppo = { id: 'g1', nome: 'Agonisti', attivo: true } as GruppoClienti;
const gruppo2 = { id: 'g2', nome: 'Agonisti master', attivo: true } as GruppoClienti;
const offerta = (id: string, nome: string, extra: Partial<Offerta> = {}): Offerta => ({
  id, stagioneId: 's1', nome, tipo: 'CORSO', descrizione: null, ingressi: null, validitaMesi: null, validitaGiorni: null, corsoId: null, corsi: ['c1'],
  riduzioneTardiva: true, attiva: true, ordine: 0, prezzi: [{ volte: 1, gruppoId: null, importo: 40 }, { volte: 2, gruppoId: null, importo: 60 }],
  eliminabile: true, motivoBlocco: null, ...extra,
});
const utente = (permessi: string[]): Me => ({ accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi, admin: false, deveCambiarePassword: false } as Me);

beforeEach(() => {
  chiamate = [];
  forzate = {};
  offerte = [
    offerta('o1', 'Scuola nuoto', { prezzi: [{ volte: 1, gruppoId: null, importo: 40 }, { volte: 2, gruppoId: null, importo: 60 }, { volte: 1, gruppoId: 'g1', importo: 30 }, { volte: 2, gruppoId: 'g2', importo: 50 }] }),
    offerta('o2', 'Nuoto adulti', { attiva: false }),
    offerta('o3', 'Carnet dieci ingressi', { tipo: 'CARNET', corsi: [], ingressi: 10, validitaMesi: 3, prezzi: [{ volte: null, gruppoId: null, importo: 80 }] }),
  ];
  sessione.utente = utente(['IMPOSTAZIONI']);
  sessione.pronto = true;
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    const f = forzate[`${m} ${url}`];
    if (f) return json(f.status, f.corpo);
    if (m === 'GET' && url === '/api/stagioni') return json(200, [stagione]);
    if (m === 'GET' && url === '/api/corsi') return json(200, [{ id: 'c1', nome: 'Nuoto bambini', attivo: true }]);
    if (m === 'GET' && url === '/api/gruppi') return json(200, [gruppo, gruppo2]);
    if (m === 'GET' && url.startsWith('/api/listino')) return json(200, { stagione, offerte, regole: [] } satisfies PaginaListino);
    const id = url.match(/^\/api\/listino\/offerte\/([^/]+)$/)?.[1];
    if (m === 'PUT' && id) {
      const i = offerte.findIndex((x) => x.id === id);
      offerte[i] = { ...offerte[i], ...corpo };
      return json(200, offerte[i]);
    }
    if (m === 'DELETE' && id) {
      offerte = offerte.filter((x) => x.id !== id);
      return json(204);
    }
    return json(200, {});
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
});

const pronta = () => screen.findByText('Scuola nuoto');
const apriOfferta = async (nome: string) => {
  await userEvent.click(screen.getByText(nome));
  return screen.findByRole('dialog');
};

describe('Listino – pagina', () => {
  it('la stagione si sceglie con SceltaStagione: il nome e la pillola, senza le vecchie pillole scure', async () => {
    render(Listino);
    await pronta();
    expect(screen.getByRole('combobox', { name: 'Stagione' })).toHaveValue('s1');
    expect(screen.getByText('in corso')).toBeTruthy();
    expect(document.querySelector('.stag-c')).toBeNull();
    expect(chiamate.filter((c) => c.url.startsWith('/api/listino'))).toHaveLength(1);
  });

  it('le schede cambiano la tabella e portano il conteggio', async () => {
    render(Listino);
    await pronta();
    const schede = screen.getByRole('tablist', { name: 'Tipi di offerta' });
    expect(within(schede).getByRole('tab', { name: /Abbonamenti ai corsi\s*2/ })).toBeTruthy();
    expect(screen.queryByText('Carnet dieci ingressi')).toBeNull();
    await userEvent.click(within(schede).getByRole('tab', { name: /Carnet/ }));
    expect(await screen.findByText('Carnet dieci ingressi')).toBeTruthy();
    expect(screen.queryByText('Scuola nuoto')).toBeNull();
    await userEvent.click(within(schede).getByRole('tab', { name: /Altri importi/ }));
    expect(await screen.findByText('Nessuna offerta.')).toBeTruthy();
    // la riga sotto c'è anche a scheda vuota; il vecchio pulsante in testa no
    expect(screen.getByRole('button', { name: "Aggiungi un'offerta…" })).toBeTruthy();
    expect(document.querySelector('.mini-btn, .x-btn')).toBeNull();
  });

  it('«Aggiungi un\'offerta…» apre il foglio del tipo della scheda', async () => {
    render(Listino);
    await pronta();
    await userEvent.click(screen.getByRole('button', { name: "Aggiungi un'offerta…" }));
    expect((await screen.findByRole('dialog', { name: 'Nuovo abbonamento' }))).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Annulla' }));
    await userEvent.click(screen.getByRole('tab', { name: /Carnet/ }));
    await userEvent.click(screen.getByRole('button', { name: "Aggiungi un'offerta…" }));
    const foglio = await screen.findByRole('dialog', { name: 'Nuovo carnet' });
    expect(within(foglio).getByRole('button', { name: /Aggiungi al listino/ })).toBeTruthy();
    expect(within(foglio).queryByRole('button', { name: "Duplica l'offerta" })).toBeNull();
  });

  it('l\'interruttore del foglio cambia `attiva` sui dati salvati, non sulla bozza', async () => {
    render(Listino);
    await pronta();
    const foglio = await apriOfferta('Scuola nuoto');
    await userEvent.clear(within(foglio).getByLabelText('Nome'));
    await userEvent.type(within(foglio).getByLabelText('Nome'), 'Nome mezzo scritto');
    await userEvent.click(within(foglio).getByRole('switch', { name: /^Acceso/ }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'PUT')).toBe(true));
    const put = chiamate.find((c) => c.metodo === 'PUT')!;
    expect(put.url).toBe('/api/listino/offerte/o1');
    expect(put.corpo.attiva).toBe(false);
    expect(put.corpo.nome).toBe('Scuola nuoto');
    expect(put.corpo).not.toHaveProperty('eliminabile');
    expect(put.corpo).not.toHaveProperty('id');
    // la bozza resta com'era: il nome scritto a metà è ancora lì, e la pillola dice «spenta»
    expect(within(foglio).getByLabelText('Nome')).toHaveValue('Nome mezzo scritto');
    await waitFor(() => expect(within(foglio).getAllByText('spenta').length).toBeGreaterThan(0));
  });

  it('due gruppi che cominciano con la stessa parola hanno chiavi ed etichette diverse, col nome intero', async () => {
    const errori = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(Listino);
    await pronta();
    const celle = [...document.querySelectorAll('.gn')].map((e) => e.textContent);
    expect(celle).toContain('Agonisti');
    expect(celle).toContain('Agonisti master');
    expect(errori).not.toHaveBeenCalled();
    errori.mockRestore();
  });

  it('riaccendere un\'offerta senza prezzi: l\'errore del server sta nel foglio, a due righe', async () => {
    offerte[1] = { ...offerte[1], prezzi: [] };
    forzate['PUT /api/listino/offerte/o2'] = { status: 400, corpo: { detail: 'Non si può riaccendere: «Nuoto adulti» non ha prezzi.\nAggiungi un prezzo e riprova.' } };
    render(Listino);
    await pronta();
    const foglio = await apriOfferta('Nuoto adulti');
    await userEvent.click(within(foglio).getByRole('switch', { name: /^Acceso/ }));
    const avviso = await within(foglio).findByRole('alert');
    expect(avviso.querySelectorAll('.riga-b')).toHaveLength(2);
    expect(avviso.querySelector('b')?.textContent).toBe('Non si può riaccendere:');
  });

  it('il cestino della colonna di un gruppo chiede «Elimina?» e poi toglie la colonna dalla bozza', async () => {
    render(Listino);
    await pronta();
    const foglio = await apriOfferta('Scuola nuoto');
    const cestino = within(foglio).getByRole('button', { name: 'Elimina i prezzi per «Agonisti»' });
    await userEvent.click(cestino);
    expect(within(foglio).getByRole('button', { name: /Elimina\?/ })).toBeTruthy();
    expect(within(foglio).getByLabelText('Mono, Agonisti')).toBeTruthy();
    await userEvent.click(within(foglio).getByRole('button', { name: /Elimina\?/ }));
    expect(within(foglio).queryByLabelText('Mono, Agonisti')).toBeNull();
    expect(chiamate.some((c) => c.metodo !== 'GET')).toBe(false); // si salva con «Salva l'offerta»
  });

  it('togliere la spunta di una frequenza toglie la riga dalla bozza', async () => {
    render(Listino);
    await pronta();
    const foglio = await apriOfferta('Scuola nuoto');
    expect(within(foglio).getByLabelText('Bi, Tutti')).toBeTruthy();
    await userEvent.click(within(foglio).getByRole('button', { name: 'Bi' }));
    expect(within(foglio).queryByLabelText('Bi, Tutti')).toBeNull();
    await userEvent.click(within(foglio).getByRole('button', { name: "Salva l'offerta" }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'PUT')).toBe(true));
    expect(chiamate.find((c) => c.metodo === 'PUT')!.corpo.prezzi.map((p: any) => p.volte)).toEqual([1, 1]);
  });

  it('409 all\'eliminazione: il blocco a due righe sta nel foglio, con l\'attacco in grassetto', async () => {
    forzate['DELETE /api/listino/offerte/o1'] = {
      status: 409,
      corpo: { detail: "Non si può eliminare: «Scuola nuoto» è stata venduta 34 volte.\nPuoi spegnerla: chi l'ha comprata la tiene com'è, ma non si vende più." },
    };
    render(Listino);
    await pronta();
    const foglio = await apriOfferta('Scuola nuoto');
    await userEvent.click(within(foglio).getByRole('button', { name: "Elimina l'offerta" }));
    await userEvent.click(within(foglio).getByRole('button', { name: /Elimina\?/ }));
    const blocco = await within(foglio).findByRole('alert');
    const righe = blocco.querySelectorAll('.riga-b');
    expect(righe).toHaveLength(2);
    expect(righe[0].querySelector('b')?.textContent).toBe('Non si può eliminare:');
    expect(righe[1].querySelector('b')?.textContent).toBe('Puoi spegnerla:');
    expect(screen.getByRole('dialog')).toBeTruthy(); // il foglio resta aperto
  });

  it('un\'offerta che il listino dice già venduta: il secondo tocco mostra il motivo senza chiamare il server', async () => {
    offerte[0] = { ...offerte[0], eliminabile: false, motivoBlocco: 'Non si può eliminare: «Scuola nuoto» è stata venduta 3 volte.\nPuoi spegnerla: chi l\'ha comprata la tiene com\'è, ma non si vende più.' };
    render(Listino);
    await pronta();
    const foglio = await apriOfferta('Scuola nuoto');
    await userEvent.click(within(foglio).getByRole('button', { name: "Elimina l'offerta" }));
    await userEvent.click(within(foglio).getByRole('button', { name: /Elimina\?/ }));
    expect(await within(foglio).findByText(/venduta 3 volte/)).toBeTruthy();
    expect(chiamate.some((c) => c.metodo === 'DELETE')).toBe(false);
  });

  it('un\'offerta mai venduta si elimina con due tocchi', async () => {
    render(Listino);
    await pronta();
    const foglio = await apriOfferta('Nuoto adulti');
    await userEvent.click(within(foglio).getByRole('button', { name: "Elimina l'offerta" }));
    await userEvent.click(within(foglio).getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'DELETE' && c.url === '/api/listino/offerte/o2')).toBe(true));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.queryByText('Nuoto adulti')).toBeNull();
  });

  it('sola lettura: niente «Aggiungi», niente interruttore né cestino; i valori si leggono', async () => {
    sessione.utente = utente(['CLIENTI']);
    render(Listino);
    await pronta();
    expect(screen.queryByRole('button', { name: "Aggiungi un'offerta…" })).toBeNull();
    const foglio = await apriOfferta('Nuoto adulti');
    expect(within(foglio).queryByRole('switch', { name: /^Acceso/ })).toBeNull();
    expect(within(foglio).queryByRole('button', { name: "Elimina l'offerta" })).toBeNull();
    expect(within(foglio).queryByRole('button', { name: "Duplica l'offerta" })).toBeNull();
    expect(within(foglio).getByLabelText('Nome')).toHaveValue('Nuoto adulti');
    expect(within(foglio).getByLabelText('Nome')).toBeDisabled();
    expect(within(foglio).getAllByText('spenta').length).toBeGreaterThan(0);
  });
});
