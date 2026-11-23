import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DatiSocieta, Me, PaginaPromemoria, RegolaPromemoria } from '../lib/api';
import { sessione } from '../lib/sessione.svelte';
import Promemoria from './Promemoria.svelte';

/** La pagina con un `fetch` finto: l'interruttore del tipo (una chiamata), il giro di oggi che non tocca la bozza, la barra dei giorni. */
type Chiamata = { metodo: string; url: string; corpo: any };
let chiamate: Chiamata[];
let pagina: PaginaPromemoria;

const regola = (id: string, tipo: RegolaPromemoria['tipo'], extra: Partial<RegolaPromemoria> = {}): RegolaPromemoria =>
  ({ id, tipo, giorni: 7, maxInvii: null, attivo: true, oggetto: `Oggetto ${id}`, testo: `Testo ${id}`, ...extra });
const societa = { nome: 'Società di prova', giorniRispostaAttesa: 3 } as unknown as DatiSocieta;
const json = (status: number, corpo?: unknown) => new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
const utente: Me = { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi: ['IMPOSTAZIONI', 'CLIENTI'], admin: false, deveCambiarePassword: false };

beforeEach(() => {
  chiamate = [];
  pagina = { regole: [regola('r1', 'CERTIFICATO'), regola('r2', 'ABBONAMENTO', { attivo: false })], ultimeEmail: [], postaConfigurata: true, orario: 'ogni giorno alle 9:00' };
  sessione.utente = utente;
  sessione.pronto = true;
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    if (m === 'GET' && url === '/api/promemoria') return json(200, pagina);
    if (m === 'GET' && url === '/api/societa') return json(200, societa);
    if (m === 'PUT' && url === '/api/societa') return json(200, { ...societa, ...corpo });
    if (m === 'PUT' && url.startsWith('/api/promemoria/tipi/')) {
      const tipo = url.split('/').pop();
      pagina = { ...pagina, regole: pagina.regole.map((r) => (r.tipo === tipo ? { ...r, attivo: corpo.attivo } : r)) };
      return json(200, pagina.regole);
    }
    if (m === 'POST' && url === '/api/promemoria/esegui') return json(200, { email: 0, inviate: 0, nonInviate: 0, senzaEmail: 0, errori: 0 });
    return json(200, {});
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
});

const attendi = () => screen.findByRole('switch', { name: 'Acceso: «Abbonamento»' });
const editor = () => screen.getByRole('region', { name: "Testo dell'email" });

describe('Promemoria – pagina', () => {
  it('l\'interruttore del tipo fa una sola PUT del tipo e non perde il testo non salvato della bozza', async () => {
    render(Promemoria);
    await attendi();
    await userEvent.type(within(editor()).getByLabelText('Oggetto'), ' modificato');
    await userEvent.click(screen.getByRole('switch', { name: 'Acceso: «Abbonamento»' }));
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Abbonamento»' })).toHaveAttribute('aria-checked', 'true'));
    const put = chiamate.filter((c) => c.metodo === 'PUT');
    expect(put).toHaveLength(1);
    expect(put[0].url).toBe('/api/promemoria/tipi/ABBONAMENTO');
    expect(put[0].corpo).toEqual({ attivo: true });
    expect((within(editor()).getByLabelText('Oggetto') as HTMLInputElement).value).toBe('Oggetto r1 modificato');
  });

  it('un pulsante solo per il giro di oggi, senza conferma, e la bozza resta', async () => {
    render(Promemoria);
    await attendi();
    await userEvent.type(within(editor()).getByLabelText('Oggetto'), '!');
    expect(screen.getAllByRole('button', { name: 'Manda i promemoria di oggi' })).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'Manda i promemoria di oggi' }));
    expect(await screen.findByText("Oggi non c'era nessun promemoria da preparare.")).toBeTruthy();
    expect(chiamate.filter((c) => c.url === '/api/promemoria/esegui')).toHaveLength(1);
    expect((within(editor()).getByLabelText('Oggetto') as HTMLInputElement).value).toBe('Oggetto r1!');
  });

  it('senza email: riga vuota «Nessuna email finora.»', async () => {
    render(Promemoria);
    await attendi();
    expect(screen.getByText('Nessuna email finora.')).toBeTruthy();
  });

  it('i giorni della lista d\'attesa passano dalla barra, non da «Salva i giorni»', async () => {
    render(Promemoria);
    await attendi();
    const campo = await screen.findByLabelText('Giorni per rispondere');
    await userEvent.clear(campo);
    await userEvent.type(campo, '5');
    expect(await screen.findByText('1 modifica da salvare')).toBeTruthy();
    expect(screen.queryByText('Salva i giorni')).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'PUT' && c.url === '/api/societa')).toBe(true));
    expect(chiamate.find((c) => c.url === '/api/societa' && c.metodo === 'PUT')!.corpo.giorniRispostaAttesa).toBe(5);
  });

  it('avviso ambra se il testo o l\'oggetto hanno ancora «…» o «...»; non blocca il salvataggio', async () => {
    render(Promemoria);
    await attendi();
    expect(screen.queryByText(/ha ancora dei «…» da completare/)).toBeNull();
    const testo = within(editor()).getByLabelText('Testo');
    await userEvent.clear(testo);
    await userEvent.type(testo, 'Buongiorno, ... {{societa}');
    expect(screen.getByText(/ha ancora dei «…» da completare/)).toBeTruthy();
    expect(within(editor()).getByRole('button', { name: 'Salva il promemoria' })).toBeEnabled();
    await userEvent.clear(testo);
    await userEvent.type(testo, 'Buongiorno {{nome}');
    expect(screen.queryByText(/ha ancora dei «…» da completare/)).toBeNull();
    await userEvent.type(within(editor()).getByLabelText('Oggetto'), ' …');
    expect(screen.getByText(/ha ancora dei «…» da completare/)).toBeTruthy();
  });
});
