import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MetodiPagamento from '../routes/MetodiPagamento.svelte';
import type { Me, MetodoPagamento } from './api';
import { sessione } from './sessione.svelte';

/**
 * Specifica (brief lotto 4, decisioni 1-8): la pagina «Metodi di pagamento» come elenco modificabile.
 * Si prova solo attraverso ruoli ed etichette accessibili: maniglia «Sposta «Nome»», interruttore role=switch
 * «Acceso: «Nome»», cestino «Elimina «Nome»» (primo tocco «Elimina?», secondo elimina), riga «Aggiungi un metodo…»,
 * «Nessun metodo.», borsellino senza cestino, sola lettura senza IMPOSTAZIONI. Il server è un `fetch` finto.
 */
type Chiamata = { metodo: string; url: string; corpo: unknown };

let chiamate: Chiamata[];
let metodi: MetodoPagamento[];
/** risposta forzata per una chiamata «METODO url» (es. 'DELETE /api/pagamenti/metodi/m3') */
let forzate: Record<string, { status: number; corpo?: unknown }>;

const metodo = (id: string, nome: string, extra: Partial<MetodoPagamento> = {}): MetodoPagamento => ({
  id, nome, daIncassare: false, contanti: false, riscontro: false, attivo: true, ordine: 0,
  borsellino: false, eliminabile: true, motivoBlocco: null, ...extra,
});

function elenco(): MetodoPagamento[] {
  return [
    metodo('m1', 'Contanti', { contanti: true, ordine: 1 }),
    metodo('m2', 'Carta', { riscontro: true, ordine: 2 }),
    metodo('m3', 'Satispay', { daIncassare: true, ordine: 3, eliminabile: false, motivoBlocco: 'Satispay ha ancora 2 pagamenti da incassare' }),
    metodo('m4', 'Bonifico', { daIncassare: true, ordine: 4 }),
    metodo('m5', 'Borsellino', { borsellino: true, ordine: 5, eliminabile: false, motivoBlocco: 'Il borsellino è richiesto dal sistema' }),
  ];
}

function utente(permessi: Me['permessi']): Me {
  return { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi, admin: false, deveCambiarePassword: false };
}

const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
  chiamate = [];
  forzate = {};
  metodi = elenco();
  sessione.utente = utente(['IMPOSTAZIONI', 'PAGAMENTI']);
  sessione.pronto = true;
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    const f = forzate[`${m} ${url}`];
    if (f) return json(f.status, f.corpo);
    if (m === 'GET' && url === '/api/pagamenti/metodi') return json(200, metodi);
    if (m === 'PUT' && url === '/api/pagamenti/metodi/ordine') {
      const ids = (corpo as { ids: string[] }).ids;
      metodi = ids.map((id, i) => ({ ...metodi.find((x) => x.id === id)!, ordine: i + 1 }));
      return json(204);
    }
    const id = url.match(/^\/api\/pagamenti\/metodi\/([^/]+)$/)?.[1];
    if (m === 'DELETE' && id) {
      metodi = metodi.filter((x) => x.id !== id);
      return json(204);
    }
    if (m === 'PUT' && id) {
      const i = metodi.findIndex((x) => x.id === id);
      metodi[i] = { ...metodi[i], ...(corpo as object) } as MetodoPagamento;
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

/** I nomi delle righe, nell'ordine in cui compaiono (letti dalle maniglie). */
const ordineRighe = () =>
  screen.queryAllByLabelText(/^Sposta «/).map((e) => e.getAttribute('aria-label')!.replace(/^Sposta «|»$/g, ''));
const attese = async () => { await screen.findByLabelText('Sposta «Contanti»'); };
const put = (url: string) => chiamate.filter((c) => c.metodo === 'PUT' && c.url === url);

describe('Metodi di pagamento – chi può modificare', () => {
  it('con IMPOSTAZIONI ogni riga ha maniglia, interruttore e cestino, e c\'è «Aggiungi un metodo…»', async () => {
    render(MetodiPagamento);
    await attese();
    for (const nome of ['Contanti', 'Carta', 'Satispay', 'Bonifico']) {
      expect(screen.getByLabelText(`Sposta «${nome}»`)).toBeTruthy();
      expect(screen.getByRole('switch', { name: `Acceso: «${nome}»` })).toBeTruthy();
      expect(screen.getByRole('button', { name: `Elimina «${nome}»` })).toBeTruthy();
    }
    expect(screen.getByRole('button', { name: /Aggiungi un metodo/ })).toBeTruthy();
  });

  it('l\'ordine delle righe è quello del server', async () => {
    render(MetodiPagamento);
    await attese();
    expect(ordineRighe().slice(0, 4)).toEqual(['Contanti', 'Carta', 'Satispay', 'Bonifico']);
  });

  it('senza IMPOSTAZIONI (sola lettura): i metodi si leggono ma niente maniglia, interruttore, cestino né «Aggiungi»', async () => {
    sessione.utente = utente(['PAGAMENTI']);
    render(MetodiPagamento);
    await screen.findByText('Carta');
    expect(screen.getByText('Contanti')).toBeTruthy();
    expect(screen.queryByLabelText(/^Sposta «/)).toBeNull();
    expect(screen.queryByRole('switch')).toBeNull();
    expect(screen.queryByRole('button', { name: /^Elimina «/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Aggiungi un metodo/ })).toBeNull();
  });
});

describe('Metodi di pagamento – borsellino', () => {
  it('la riga del borsellino non ha il cestino', async () => {
    render(MetodiPagamento);
    await attese();
    expect(screen.getByText('Borsellino')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Elimina «Borsellino»' })).toBeNull();
  });
});

describe('Metodi di pagamento – elenco vuoto', () => {
  it('mostra «Nessun metodo.» senza pulsante dentro e, sotto, la riga «Aggiungi un metodo…»', async () => {
    metodi = [];
    render(MetodiPagamento);
    const vuoto = await screen.findByText('Nessun metodo.');
    expect(vuoto).toBeTruthy();
    // lo stato vuoto non ha un pulsante proprio: l'unico modo di aggiungere è la riga tratteggiata
    expect(within(vuoto.parentElement!).queryByRole('button')).toBeNull();
    expect(screen.getAllByRole('button', { name: /Aggiungi un metodo/ })).toHaveLength(1);
    expect(screen.queryByRole('switch')).toBeNull();
  });

  it('in sola lettura e vuoto: «Nessun metodo.» e nessun «Aggiungi»', async () => {
    metodi = [];
    sessione.utente = utente(['PAGAMENTI']);
    render(MetodiPagamento);
    await screen.findByText('Nessun metodo.');
    expect(screen.queryByRole('button', { name: /Aggiungi un metodo/ })).toBeNull();
  });
});

describe('Metodi di pagamento – interruttore acceso/spento', () => {
  it('è un role=switch con aria-checked vero per un metodo attivo', async () => {
    metodi[1] = { ...metodi[1], attivo: false };
    render(MetodiPagamento);
    await attese();
    expect(screen.getByRole('switch', { name: 'Acceso: «Contanti»' }).getAttribute('aria-checked')).toBe('true');
    expect(screen.getByRole('switch', { name: 'Acceso: «Carta»' }).getAttribute('aria-checked')).toBe('false');
  });

  it('un tocco spegne subito il metodo: chiamata PUT con attivo=false e interruttore aggiornato', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Carta»' }));
    await waitFor(() => expect(put('/api/pagamenti/metodi/m2')).toHaveLength(1));
    expect((put('/api/pagamenti/metodi/m2')[0].corpo as { attivo: boolean }).attivo).toBe(false);
    await waitFor(() =>
      expect(screen.getByRole('switch', { name: 'Acceso: «Carta»' }).getAttribute('aria-checked')).toBe('false'));
  });

  it('una riga spenta ha ancora tutti i controlli attivi (variante 4B)', async () => {
    metodi[1] = { ...metodi[1], attivo: false };
    render(MetodiPagamento);
    await attese();
    const interruttore = screen.getByRole('switch', { name: 'Acceso: «Carta»' }) as HTMLButtonElement;
    expect(interruttore.disabled).toBe(false);
    expect((screen.getByRole('button', { name: 'Elimina «Carta»' }) as HTMLButtonElement).disabled).toBe(false);
    expect(screen.getByLabelText('Sposta «Carta»').getAttribute('aria-disabled')).not.toBe('true');
    // i valori restano leggibili
    expect(screen.getByText('Carta')).toBeVisible();
  });

  it('da spenta si riaccende con un tocco (PUT con attivo=true)', async () => {
    metodi[1] = { ...metodi[1], attivo: false };
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Carta»' }));
    await waitFor(() => expect(put('/api/pagamenti/metodi/m2')).toHaveLength(1));
    expect((put('/api/pagamenti/metodi/m2')[0].corpo as { attivo: boolean }).attivo).toBe(true);
  });
});

describe('Metodi di pagamento – cestino a due tocchi', () => {
  it('il primo tocco mostra «Elimina?» e non cancella nulla', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    await user.click(screen.getByRole('button', { name: 'Elimina «Bonifico»' }));
    expect(screen.getByRole('button', { name: /Elimina\?/ })).toBeTruthy();
    expect(chiamate.filter((c) => c.metodo === 'DELETE')).toHaveLength(0);
    expect(screen.getByLabelText('Sposta «Bonifico»')).toBeTruthy();
  });

  it('il secondo tocco chiama DELETE /api/pagamenti/metodi/{id} e la riga sparisce', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    await user.click(screen.getByRole('button', { name: 'Elimina «Bonifico»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamate.filter((c) => c.metodo === 'DELETE' && c.url === '/api/pagamenti/metodi/m4')).toHaveLength(1));
    await waitFor(() => expect(screen.queryByLabelText('Sposta «Bonifico»')).toBeNull());
    expect(screen.getByLabelText('Sposta «Carta»')).toBeTruthy();
  });

  it('se il server risponde 409 l\'eliminazione si ferma e compare il messaggio del backend', async () => {
    forzate['DELETE /api/pagamenti/metodi/m3'] = { status: 409, corpo: { detail: 'Satispay ha ancora 2 pagamenti da incassare' } };
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    await user.click(screen.getByRole('button', { name: 'Elimina «Satispay»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    expect(await screen.findByText(/Satispay ha ancora 2 pagamenti da incassare/)).toBeTruthy();
    expect(screen.getByLabelText('Sposta «Satispay»')).toBeTruthy();
  });

  it('il messaggio di blocco è mostrato anche se la riga è tra quelle che non si possono eliminare ora (nessun crash, riga intatta)', async () => {
    const user = userEvent.setup();
    forzate['DELETE /api/pagamenti/metodi/m3'] = { status: 409, corpo: { detail: 'Satispay ha ancora 2 pagamenti da incassare' } };
    render(MetodiPagamento);
    await attese();
    await user.click(screen.getByRole('button', { name: 'Elimina «Satispay»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await screen.findByText(/ancora 2 pagamenti/);
    expect(ordineRighe()).toContain('Satispay');
  });
});

describe('Metodi di pagamento – riordino', () => {
  it('freccia giù sulla maniglia sposta di un posto e chiama PUT /ordine con tutti gli ids nel nuovo ordine', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    screen.getByLabelText('Sposta «Contanti»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(put('/api/pagamenti/metodi/ordine')).toHaveLength(1));
    expect(put('/api/pagamenti/metodi/ordine')[0].corpo).toEqual({ ids: ['m2', 'm1', 'm3', 'm4', 'm5'] });
    await waitFor(() => expect(ordineRighe().slice(0, 2)).toEqual(['Carta', 'Contanti']));
  });

  it('freccia su sposta un posto prima', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    screen.getByLabelText('Sposta «Bonifico»').focus();
    await user.keyboard('{ArrowUp}');
    await waitFor(() => expect(put('/api/pagamenti/metodi/ordine')).toHaveLength(1));
    expect(put('/api/pagamenti/metodi/ordine')[0].corpo).toEqual({ ids: ['m1', 'm2', 'm4', 'm3', 'm5'] });
  });

  it('ai bordi (primo in su) non cambia nulla e non chiama il server', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    screen.getByLabelText('Sposta «Contanti»').focus();
    await user.keyboard('{ArrowUp}');
    expect(put('/api/pagamenti/metodi/ordine')).toHaveLength(0);
    expect(ordineRighe().slice(0, 2)).toEqual(['Contanti', 'Carta']);
  });

  it('annuncia lo spostamento in una regione role=status («… spostato al posto 2 di 5»)', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    screen.getByLabelText('Sposta «Contanti»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => {
      const testi = screen.getAllByRole('status').map((s) => s.textContent ?? '');
      expect(testi.some((t) => /Contanti spostato al posto 2 di 5/.test(t))).toBe(true);
    });
  });

  it('se la chiamata fallisce l\'elenco torna all\'ordine di prima', async () => {
    forzate['PUT /api/pagamenti/metodi/ordine'] = { status: 500 };
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    const prima = ordineRighe();
    screen.getByLabelText('Sposta «Contanti»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(put('/api/pagamenti/metodi/ordine')).toHaveLength(1));
    await waitFor(() => expect(ordineRighe()).toEqual(prima));
  });
});

describe('Metodi di pagamento – aggiunta e foglio', () => {
  it('«Aggiungi un metodo…» apre il foglio «Nuovo metodo» con la scelta a tre e il riquadro acceso', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    await user.click(screen.getByRole('button', { name: /Aggiungi un metodo/ }));
    const foglio = await screen.findByRole('dialog');
    expect(within(foglio).getByText(/Nuovo metodo/)).toBeTruthy();
    for (const scelta of ['Incassato subito', 'Nel cassetto', 'Da incassare']) {
      expect(within(foglio).getByText(scelta)).toBeTruthy();
    }
    // il riquadro «acceso» è un interruttore dentro il foglio: un metodo nuovo può nascere spento
    expect(within(foglio).getAllByRole('switch').length).toBeGreaterThanOrEqual(1);
  });

  it('un tocco sul nome di un metodo apre il foglio per modificarlo, senza chiamare il server', async () => {
    const user = userEvent.setup();
    render(MetodiPagamento);
    await attese();
    const prima = chiamate.length;
    await user.click(screen.getByText('Bonifico'));
    const foglio = await screen.findByRole('dialog');
    expect(within(foglio).getByDisplayValue('Bonifico')).toBeTruthy();
    expect(chiamate.slice(prima).filter((c) => c.metodo !== 'GET')).toHaveLength(0);
  });
});
