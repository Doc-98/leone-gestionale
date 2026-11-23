import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Dispositivo, Me, RegoleBorsellino, Servizio, StatoCreditiBraccialetto } from '../lib/api';
import { sessione } from '../lib/sessione.svelte';
import PhonDocce from './PhonDocce.svelte';

/** La pagina con un `fetch` finto che segue il contratto: eliminazione sempre riuscita, macchine senza servizio, barra. */
type Chiamata = { metodo: string; url: string; corpo: any };
let chiamate: Chiamata[];
let servizi: Servizio[];
let dispositivi: Dispositivo[];
let regole: RegoleBorsellino;

const servizio = (id: string, nome: string, extra: Partial<Servizio> = {}): Servizio => ({ id, nome, crediti: 1, gratuitiAlGiorno: 0, minuti: null, attivo: true, ordine: 0, eliminabile: true, motivoBlocco: null, ...extra });
const macchina = (id: string, nome: string, extra: Partial<Dispositivo> = {}): Dispositivo => ({
  id, nome, tipo: 'SERVIZIO', servizioId: null, servizio: null, attivo: true, creatoIl: '2026-09-01T08:00:00Z', ultimoContatto: new Date().toISOString(), utilizziOggi: 2, eliminabile: true, motivoBlocco: null, ...extra,
});
const json = (status: number, corpo?: unknown) => new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
const utente: Me = { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi: ['IMPOSTAZIONI'], admin: false, deveCambiarePassword: false };
const statoChip: StatoCreditiBraccialetto = { sulBraccialetto: false, formato: 'PROVA', nomeFormato: 'Prova', pronto: true, nota: 'Formato di prova.', accesso: null };

beforeEach(() => {
  chiamate = [];
  servizi = [servizio('s1', 'Phon', { gratuitiAlGiorno: 1, minuti: 5, ordine: 1, utilizzi: 1240, macchine: 2 }), servizio('s2', 'Docce', { crediti: 2, ordine: 2 })];
  dispositivi = [
    macchina('d1', 'Phon spogliatoio maschile', { servizioId: 's1', servizio: 'Phon' }),
    macchina('d2', 'Phon atrio', { servizioId: 's1', servizio: 'Phon', ultimoContatto: null, utilizzi: 3410 }),
    macchina('d3', 'Docce spogliatoio femminile', { servizioId: 's2', servizio: 'Docce' }),
  ];
  regole = { valoreCredito: 0.2, ricaricaMinima: 5, validitaEuroMesi: 12, trasferibile: true, restituibile: false, cauzione: 5,
    cauzioneRiconsegna: 'DENARO', creditiRiconsegna: true, creditiSmarrimento: 'RESTANO', creditiSu: 'SERVER' };
  sessione.utente = utente;
  sessione.pronto = true;
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    if (m === 'GET' && url === '/api/servizi') return json(200, servizi);
    if (m === 'GET' && url === '/api/dispositivi') return json(200, dispositivi);
    if (m === 'GET' && url === '/api/borsellino/regole') return json(200, regole);
    if (m === 'GET' && url === '/api/crediti-braccialetto/stato') return json(200, statoChip);
    if (m === 'GET') return json(200, {});
    const idS = url.match(/^\/api\/servizi\/([^/]+)$/)?.[1];
    if (idS && m === 'DELETE') {
      servizi = servizi.filter((s) => s.id !== idS);
      // le macchine del servizio restano senza servizio
      dispositivi = dispositivi.map((d) => (d.servizioId === idS ? { ...d, servizioId: null, servizio: null } : d));
      return json(200, { utilizzi: 1300, macchine: 2 }); // contati al momento dell'eliminazione: più di quelli dell'elenco
    }
    const idD = url.match(/^\/api\/dispositivi\/([^/]+)$/)?.[1];
    if (idD && m === 'DELETE') { dispositivi = dispositivi.filter((d) => d.id !== idD); return json(200, { utilizzi: 3500 }); }
    if (idD && m === 'PUT') {
      const i = dispositivi.findIndex((d) => d.id === idD);
      dispositivi[i] = { ...dispositivi[i], ...corpo };
      return json(200, dispositivi[i]);
    }
    return json(200, {});
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
});

const attendi = () => screen.findByLabelText('Sposta «Phon»');
const scritture = () => chiamate.filter((c) => c.metodo !== 'GET');

describe('Macchine e crediti – eliminazione e messaggio dopo', () => {
  it('un servizio con macchine si elimina con due tocchi; le macchine restano «Senza servizio» e il messaggio lo dice', async () => {
    render(PhonDocce);
    await attendi();
    const cestino = screen.getByRole('button', { name: 'Elimina «Phon»' });
    await userEvent.click(cestino);
    expect(scritture()).toHaveLength(0); // il primo tocco arma soltanto
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'DELETE' && c.url === '/api/servizi/s1')).toBe(true));
    expect(await screen.findByText(/«Phon» eliminato: i suoi 1\.300 utilizzi restano nello storico\. Le sue 2 macchine restano senza servizio\./)).toBeTruthy();
    await waitFor(() => expect(screen.getAllByText('Senza servizio')).toHaveLength(2));
  });

  it('una macchina si elimina sempre; il messaggio dice che gli utilizzi restano nello storico', async () => {
    render(PhonDocce);
    await attendi();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Phon atrio»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    expect(await screen.findByText('«Phon atrio» eliminata: i suoi 3.500 utilizzi restano nello storico.')).toBeTruthy();
    expect(screen.queryByText('Phon atrio')).toBeNull();
  });

  it('se il server dice 409 (qualcosa in corso) il motivo sta sotto la riga, in due righe', async () => {
    const base = globalThis.fetch;
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
      if ((init?.method ?? 'GET') === 'DELETE' && url === '/api/servizi/s2')
        return json(409, { status: 409, detail: 'Non si può eliminare: «Docce» ha una ricarica aperta.\nChiudila: poi potrai eliminarlo.' });
      return base(url, init);
    });
    render(PhonDocce);
    await attendi();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Docce»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    const blocco = await screen.findByRole('alert');
    expect(within(blocco).getByText('Non si può eliminare:').tagName).toBe('B');
    expect(blocco.textContent).toContain('Chiudila:');
    expect(screen.getByText('Docce')).toBeTruthy();
  });
});

describe('Macchine e crediti – eliminazione con corpo vuoto', () => {
  it('se il server non manda i conteggi si usano quelli dell\'elenco', async () => {
    const base = globalThis.fetch;
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) =>
      (init?.method ?? 'GET') === 'DELETE' ? json(204) : base(url, init));
    render(PhonDocce);
    await attendi();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Phon atrio»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    expect(await screen.findByText('«Phon atrio» eliminata: i suoi 3.410 utilizzi restano nello storico.')).toBeTruthy();
  });
});

describe('Macchine e crediti – riga e barra', () => {
  it('la riga della macchina senza servizio porta il tag ambra; le altre il nome del servizio', async () => {
    dispositivi = [macchina('d9', 'Phon vecchio'), macchina('d1', 'Phon spogliatoio maschile', { servizioId: 's1', servizio: 'Phon' })];
    render(PhonDocce);
    await attendi();
    const riga = screen.getByText('Phon vecchio').closest('li')!;
    expect(riga.querySelector('.tag.t-warn')?.textContent).toBe('Senza servizio');
    expect(screen.getByText('Phon spogliatoio maschile').closest('li')!.textContent).toContain('phon');
  });

  it('i servizi hanno il riassunto sotto il nome; la barra non compare cambiando un servizio, compare cambiando il valore del credito', async () => {
    render(PhonDocce);
    await attendi();
    expect(screen.getByText('1 credito · 1 gratis al giorno · 5 minuti')).toBeTruthy();
    expect(screen.getByText('2 crediti')).toBeTruthy();
    await userEvent.click(screen.getByRole('switch', { name: 'Acceso: «Docce»' }));
    await waitFor(() => expect(chiamate.some((c) => c.metodo === 'PUT' && c.url === '/api/servizi/s2')).toBe(true));
    expect(screen.queryByText(/modific[ah]e? da salvare/)).toBeNull();
    const campo = screen.getByLabelText('Un credito vale');
    await userEvent.clear(campo);
    await userEvent.type(campo, '0,25');
    expect(await screen.findByText('1 modifica da salvare')).toBeTruthy();
  });

  it('niente classi locali dei vecchi controlli', async () => {
    const { container } = render(PhonDocce);
    await attendi();
    expect(container.querySelector('.mini-btn, .x-btn, .seg-s, table')).toBeNull();
  });
});
