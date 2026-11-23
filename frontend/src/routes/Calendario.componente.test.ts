import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Evento, Stagione } from '../lib/api';
import Calendario from './Calendario.svelte';

/** La pagina con un `fetch` finto: stagioni, chiusure per intervallo di date, creazione e annullo di eventi. */
type Chiamata = { metodo: string; url: string; corpo: any };
let chiamate: Chiamata[];
let stagioni: Stagione[];
let chiusurePer: (da: string) => Evento[];
let impianti: string[];

const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
const stagione = (id: string, nome: string, dal: string, al: string): Stagione => ({ id, nome, dal, al, quota: null, quotaRimborsabile: false });
const ev = (id: string, titolo: string, data: string, dataFine: string | null = null): Evento => ({
  id, impiantoId: 'i1', tipo: 'CHIUSURA', titolo, data, dataFine, inizio: '00:00:00', fine: '23:59:00', spazi: [], istruttore: null, note: null, annullato: false,
});

beforeEach(() => {
  chiamate = [];
  impianti = ['i1'];
  stagioni = [stagione('s1', '2025/26', '2025-09-01', '2026-08-31'), stagione('s2', '2026/27', '2026-09-01', '2027-08-31')];
  chiusurePer = (da) => (da === '2026-09-01' ? [ev('e1', 'Immacolata', '2026-12-08'), ev('e2', 'Vacanze di Natale', '2026-12-25', '2027-01-06')] : da === '2025-09-01' ? [ev('v1', 'Immacolata', '2025-12-08'), ev('v2', 'Gita sociale', '2026-05-10')] : []);
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-23T12:00:00'));
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    if (m === 'GET' && url === '/api/stagioni') return json(200, stagioni);
    if (m === 'GET' && url.startsWith('/api/chiusure')) {
      const da = new URL(url, 'http://x').searchParams.get('da')!;
      return json(200, { impiantiGestiti: impianti, chiusure: chiusurePer(da) });
    }
    return json(200, {});
  });
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

const attese = () => screen.findByRole('button', { name: 'Elimina «Immacolata»' });
const voceMenu = async (testo: string) => {
  await userEvent.click(screen.getByRole('button', { name: 'Altre azioni per le chiusure' }));
  return screen.findByRole('menuitem', { name: testo });
};

describe('Calendario – scelta della stagione', () => {
  it('parte dalla stagione in corso e mostra la pillola; legge le chiusure di quella stagione', async () => {
    render(Calendario);
    await attese();
    expect(screen.getByRole('combobox', { name: 'Stagione' })).toHaveValue('s2');
    expect(screen.getByText('in corso')).toHaveClass('pill', 'passa');
    expect(chiamate.some((c) => c.url.startsWith('/api/chiusure?da=2026-09-01&a=2027-08-31'))).toBe(true);
  });

  it('senza stagioni: «Nessuna stagione.» e il link a Dati, stagioni e gruppi dentro la frase', async () => {
    stagioni = [];
    render(Calendario);
    expect(await screen.findByText('Nessuna stagione.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Dati, stagioni e gruppi' })).toHaveAttribute('href', '#/societa');
    expect(screen.queryByRole('combobox')).toBeNull();
  });
});

describe('Calendario – le chiusure', () => {
  it('sono un elenco senza maniglia né interruttore, con il cestino «Elimina?» e la riga «Aggiungi una chiusura…»', async () => {
    render(Calendario);
    await attese();
    expect(screen.queryByLabelText(/^Sposta «/)).toBeNull();
    expect(screen.queryByRole('switch')).toBeNull();
    expect(screen.getByRole('button', { name: 'Aggiungi una chiusura…' })).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Immacolata»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'POST' && c.url === '/api/eventi/e1/annulla')).toBe(true));
  });

  it('chi non può modificare (nessun impianto gestito) non vede cestino, «Aggiungi» né menu', async () => {
    impianti = [];
    render(Calendario);
    await screen.findAllByText('Immacolata');
    expect(screen.queryByRole('button', { name: /^Elimina «/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Aggiungi una chiusura/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Altre azioni per le chiusure' })).toBeNull();
  });

  it('«Aggiungi una chiusura…» apre il foglio «Nuova chiusura» con Dal, Al, il segmentato, il nome', async () => {
    render(Calendario);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Aggiungi una chiusura…' }));
    const foglio = await screen.findByRole('dialog', { name: 'Nuova chiusura' });
    expect(within(foglio).getByLabelText('Dal')).toBeTruthy();
    expect(within(foglio).getByLabelText('Al')).toBeTruthy();
    expect(within(foglio).getByRole('button', { name: 'Tutto il giorno' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(foglio).getByRole('button', { name: 'Orario ridotto' })).toBeTruthy();
    expect(within(foglio).getByLabelText('Nome')).toBeTruthy();
    expect(within(foglio).getByRole('button', { name: 'Aggiungi al calendario' })).toBeTruthy();
  });

  it('toccare un giorno della griglia apre lo stesso foglio con Dal = Al = quel giorno', async () => {
    render(Calendario);
    await attese();
    await userEvent.click(screen.getAllByRole('button', { name: /^15 dicembre/i })[0]);
    const foglio = await screen.findByRole('dialog', { name: 'Nuova chiusura' });
    expect(within(foglio).getByLabelText('Dal')).toHaveValue('2026-12-15');
    expect(within(foglio).getByLabelText('Al')).toHaveValue('2026-12-15');
    await userEvent.type(within(foglio).getByLabelText('Nome'), 'Derattizzazione');
    await userEvent.click(within(foglio).getByRole('button', { name: 'Aggiungi al calendario' }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'POST' && c.url === '/api/eventi')).toBe(true));
    const post = chiamate.find((c) => c.metodo === 'POST' && c.url === '/api/eventi')!;
    expect(post.corpo).toMatchObject({ tipo: 'CHIUSURA', titolo: 'Derattizzazione', data: '2026-12-15' });
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });
});

describe('Calendario – il menu della card', () => {
  it('ha le due voci: festività nazionali e copia dalla stagione', async () => {
    render(Calendario);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Altre azioni per le chiusure' }));
    const voci = (await screen.findAllByRole('menuitem')).map((v) => v.textContent?.trim());
    expect(voci).toEqual(['Aggiungi le festività nazionali', 'Copia dalla stagione…']);
  });

  it('senza un\'altra stagione la voce «Copia dalla stagione…» non c\'è', async () => {
    stagioni = [stagioni[1]];
    render(Calendario);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Altre azioni per le chiusure' }));
    expect((await screen.findAllByRole('menuitem')).map((v) => v.textContent?.trim())).toEqual(['Aggiungi le festività nazionali']);
  });

  it('«Copia dalla stagione…»: foglio con la precedente, «N chiusure da copiare» e «Copia N chiusure»', async () => {
    render(Calendario);
    await attese();
    await userEvent.click(await voceMenu('Copia dalla stagione…'));
    const foglio = await screen.findByRole('dialog', { name: 'Copia le chiusure' });
    expect(within(foglio).getByRole('combobox', { name: 'Copia dalla stagione' })).toHaveValue('s1');
    // Immacolata è già segnata (08/12/2026): resta solo la gita sociale, un anno dopo
    expect(await within(foglio).findByText('1 chiusura da copiare')).toBeTruthy();
    await userEvent.click(within(foglio).getByRole('button', { name: 'Copia 1 chiusura' }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'POST' && c.url === '/api/eventi')).toBe(true));
    expect(chiamate.find((c) => c.metodo === 'POST' && c.url === '/api/eventi')!.corpo).toMatchObject({ titolo: 'Gita sociale', data: '2027-05-10' });
  });

  it('se non c\'è niente di nuovo il pulsante è disabilitato e la riga lo dice', async () => {
    chiusurePer = (da) => (da === '2026-09-01' ? [ev('e1', 'Immacolata', '2026-12-08')] : da === '2025-09-01' ? [ev('v1', 'Immacolata', '2025-12-08')] : []);
    render(Calendario);
    await screen.findAllByText('Immacolata');
    await userEvent.click(await voceMenu('Copia dalla stagione…'));
    const foglio = await screen.findByRole('dialog', { name: 'Copia le chiusure' });
    expect(await within(foglio).findByText('Niente di nuovo da copiare')).toBeTruthy();
    expect(within(foglio).getAllByRole('button').filter((b) => b.textContent?.includes('Copia le chiusure') && (b as HTMLButtonElement).disabled)).toHaveLength(1);
  });
});

describe('Calendario – classi vecchie', () => {
  it('non restano mini-btn né x-btn', async () => {
    const { container } = render(Calendario);
    await attese();
    expect(container.querySelector('.mini-btn, .x-btn')).toBeNull();
  });
});
