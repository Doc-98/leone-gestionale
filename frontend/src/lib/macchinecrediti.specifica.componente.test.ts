import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PhonDocce from '../routes/PhonDocce.svelte';
import type { Dispositivo, Me, RegoleBorsellino, Servizio, StatoCreditiBraccialetto } from './api';
import { sessione } from './sessione.svelte';

/**
 * Specifica (brief coerenza blocco 7, lotto 4 + «Decisioni finali dell'utente»): la pagina «Macchine e crediti»
 * (routes/PhonDocce.svelte). Servizi e macchine sono righe di elenco modificabile con il loro foglio (dialog).
 * Si prova solo attraverso ruoli ed etichette accessibili: maniglia «Sposta «X»», interruttore role=switch
 * «Acceso: «X»», cestino «Elimina «X»» (primo tocco «Elimina?»), «Aggiungi un servizio…», «Aggiungi una macchina…».
 * Il server è un `fetch` finto che segue il contratto:
 *   GET /api/servizi, /api/dispositivi, /api/borsellino/regole, /api/crediti-braccialetto/stato
 *   PUT /api/servizi/ordine {ids} -> 200 elenco; PUT/DELETE /api/servizi/{id}; POST /api/servizi
 *   PUT/DELETE /api/dispositivi/{id}; POST /api/dispositivi -> 201 {dispositivo, chiave}; POST /api/dispositivi/{id}/chiave
 * Decisioni finali: servizi e macchine si eliminano sempre (anche se usati); una macchina senza servizio porta il tag
 * «Senza servizio». Il 409 resta come meccanismo generico (cose «in corso»), provato con un testo inventato.
 *
 * IPOTESI sulle etichette del foglio (dal brief): campi «Nome», «Crediti per ogni uso», «Usi gratuiti al giorno»,
 * «Minuti sulla macchina», «Servizio»; pulsanti «Salva il servizio» / «Aggiungi il servizio», «Salva la macchina» /
 * «Aggiungi la macchina», «Nuova chiave», «L'ho copiata»; titolo del foglio = nome (nuova macchina: «Nuova macchina»).
 */
type Chiamata = { metodo: string; url: string; corpo: any };

let chiamate: Chiamata[];
let servizi: Servizio[];
let dispositivi: Dispositivo[];
let regole: RegoleBorsellino;
let forzate: Record<string, { status: number; corpo?: unknown }>;

const unServizio = (id: string, nome: string, extra: Partial<Servizio> = {}): Servizio & { eliminabile?: boolean | null; motivoBlocco?: string | null } => ({
  id, nome, crediti: 1, gratuitiAlGiorno: 0, minuti: null, attivo: true, ordine: 0, eliminabile: true, motivoBlocco: null, ...extra,
});
const unaMacchina = (id: string, nome: string, extra: Partial<Dispositivo> = {}): Dispositivo & { eliminabile?: boolean | null; motivoBlocco?: string | null } => ({
  id, nome, tipo: 'SERVIZIO', servizioId: null, servizio: null, attivo: true, creatoIl: '2026-09-01T08:00:00Z',
  ultimoContatto: new Date().toISOString(), utilizziOggi: 4, eliminabile: true, motivoBlocco: null, ...extra,
});

function utente(permessi: Me['permessi']): Me {
  return { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi, admin: false, deveCambiarePassword: false };
}
const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });

const statoChip: StatoCreditiBraccialetto = { sulBraccialetto: false, formato: 'PROVA', nomeFormato: 'Prova', pronto: true, nota: 'Formato di prova.', accesso: null };

beforeEach(() => {
  chiamate = [];
  forzate = {};
  servizi = [
    unServizio('sv1', 'Phon', { crediti: 1, gratuitiAlGiorno: 1, minuti: 5, ordine: 1 }),
    unServizio('sv2', 'Docce', { crediti: 2, ordine: 2 }),
    unServizio('sv3', 'Sauna', { crediti: 3, attivo: false, ordine: 3 }),
  ];
  dispositivi = [
    unaMacchina('d1', 'Phon spogliatoio maschile', { servizioId: 'sv1', servizio: 'Phon' }),
    unaMacchina('d2', 'Docce spogliatoio femminile', { servizioId: 'sv2', servizio: 'Docce' }),
    unaMacchina('d3', 'Phon atrio', { servizioId: null, servizio: null }),
    unaMacchina('d4', 'Lettore ingresso', { tipo: 'SALDO' }),
  ];
  regole = {
    valoreCredito: 0.2, ricaricaMinima: 5, validitaEuroMesi: 12, trasferibile: true, restituibile: false, cauzione: 5,
    cauzioneRiconsegna: 'DENARO', creditiRiconsegna: true, creditiSmarrimento: 'RESTANO', creditiSu: 'SERVER',
  };
  sessione.utente = utente(['IMPOSTAZIONI', 'BORSELLINO']);
  sessione.pronto = true;

  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    const f = forzate[`${m} ${url}`];
    if (f) return json(f.status, f.corpo);

    if (m === 'GET' && url === '/api/servizi') return json(200, servizi);
    if (m === 'GET' && url === '/api/dispositivi') return json(200, dispositivi);
    if (m === 'GET' && url === '/api/borsellino/regole') return json(200, regole);
    if (m === 'GET' && url === '/api/crediti-braccialetto/stato') return json(200, statoChip);
    if (m === 'GET') return json(200, {});

    if (m === 'PUT' && url === '/api/borsellino/regole') { regole = { ...regole, ...corpo }; return json(200, regole); }
    if (m === 'PUT' && url === '/api/servizi/ordine') {
      servizi = (corpo.ids as string[]).map((id, i) => ({ ...servizi.find((s) => s.id === id)!, ordine: i + 1 }));
      return json(200, servizi);
    }
    if (m === 'POST' && url === '/api/servizi') {
      const nuovo = unServizio('sv-nuovo', corpo.nome, { ...corpo, ordine: servizi.length + 1 });
      servizi = [...servizi, nuovo];
      return json(201, nuovo);
    }
    const idServizio = url.match(/^\/api\/servizi\/([^/]+)$/)?.[1];
    if (idServizio && m === 'PUT') {
      const i = servizi.findIndex((s) => s.id === idServizio);
      servizi[i] = { ...servizi[i], ...corpo };
      return json(200, servizi[i]);
    }
    if (idServizio && m === 'DELETE') { servizi = servizi.filter((s) => s.id !== idServizio); return json(204); }

    if (m === 'POST' && url === '/api/dispositivi') {
      const nuovo = unaMacchina('d-nuova', corpo.nome, { tipo: corpo.tipo ?? 'SERVIZIO', servizioId: corpo.servizioId ?? null, attivo: corpo.attivo ?? true, ultimoContatto: null, utilizziOggi: 0 });
      dispositivi = [...dispositivi, nuovo];
      return json(201, { dispositivo: nuovo, chiave: 'chiave-nuova-0001' });
    }
    const chiave = url.match(/^\/api\/dispositivi\/([^/]+)\/chiave$/)?.[1];
    if (chiave && m === 'POST') return json(200, { dispositivo: dispositivi.find((d) => d.id === chiave), chiave: 'chiave-rigenerata-9999' });
    const idDisp = url.match(/^\/api\/dispositivi\/([^/]+)$/)?.[1];
    if (idDisp && m === 'PUT') {
      const i = dispositivi.findIndex((d) => d.id === idDisp);
      dispositivi[i] = { ...dispositivi[i], ...corpo };
      return json(200, dispositivi[i]);
    }
    if (idDisp && m === 'DELETE') { dispositivi = dispositivi.filter((d) => d.id !== idDisp); return json(204); }
    return json(200, {});
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
  location.hash = '';
});

const chiamateA = (metodo: string, url: string) => chiamate.filter((c) => c.metodo === metodo && c.url === url);
const scritture = () => chiamate.filter((c) => c.metodo !== 'GET');
const attendi = async () => { await screen.findByLabelText('Sposta «Phon»'); await screen.findByRole('switch', { name: 'Acceso: «Phon spogliatoio maschile»' }); };
const maniglie = () => screen.queryAllByLabelText(/^Sposta «/).map((e) => e.getAttribute('aria-label')!.replace(/^Sposta «|»$/g, ''));

function èGrassetto(el: HTMLElement): boolean {
  if (el.closest('strong, b')) return true;
  const peso = getComputedStyle(el).fontWeight;
  return peso === 'bold' || peso === 'bolder' || Number(peso) >= 600;
}

describe('Servizi – righe dell\'elenco', () => {
  it('ogni servizio ha maniglia, interruttore e cestino, nell\'ordine del server; «Aggiungi un servizio…»', async () => {
    render(PhonDocce);
    await attendi();
    expect(maniglie()).toEqual(['Phon', 'Docce', 'Sauna']);
    for (const nome of ['Phon', 'Docce', 'Sauna']) {
      expect(screen.getByLabelText(`Sposta «${nome}»`)).toBeTruthy();
      expect(screen.getByRole('switch', { name: `Acceso: «${nome}»` })).toBeTruthy();
      expect(screen.getByRole('button', { name: `Elimina «${nome}»` })).toBeTruthy();
    }
    expect(screen.getByRole('button', { name: /Aggiungi un servizio/ })).toBeTruthy();
  });

  it('sotto il nome, il riassunto del servizio', async () => {
    render(PhonDocce);
    await attendi();
    expect(screen.getByText('1 credito · 1 gratis al giorno · 5 minuti')).toBeTruthy();
    expect(screen.getByText('2 crediti')).toBeTruthy();
  });

  it('l\'interruttore riflette «attivo»', async () => {
    render(PhonDocce);
    await attendi();
    expect(screen.getByRole('switch', { name: 'Acceso: «Phon»' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('switch', { name: 'Acceso: «Sauna»' })).toHaveAttribute('aria-checked', 'false');
  });

  it('non c\'è più la tabella dei servizi con i campi in pagina', async () => {
    render(PhonDocce);
    await attendi();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByLabelText(/^Crediti per uso, /)).toBeNull();
  });
});

describe('Servizi – riordino', () => {
  it('freccia giù sulla maniglia: UNA sola PUT /api/servizi/ordine con tutti gli id nel nuovo ordine', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    screen.getByLabelText('Sposta «Phon»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(chiamateA('PUT', '/api/servizi/ordine')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/servizi/ordine')[0].corpo).toEqual({ ids: ['sv2', 'sv1', 'sv3'] });
    await waitFor(() => expect(maniglie()).toEqual(['Docce', 'Phon', 'Sauna']));
    expect(scritture().filter((c) => c.url !== '/api/servizi/ordine')).toHaveLength(0);
  });

  it('se il server risponde con un errore l\'elenco torna com\'era', async () => {
    forzate['PUT /api/servizi/ordine'] = { status: 500 };
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    screen.getByLabelText('Sposta «Phon»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(chiamateA('PUT', '/api/servizi/ordine')).toHaveLength(1));
    await waitFor(() => expect(maniglie()).toEqual(['Phon', 'Docce', 'Sauna']));
  });

  it('ai bordi (il primo in su) non chiama il server', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    screen.getByLabelText('Sposta «Phon»').focus();
    await user.keyboard('{ArrowUp}');
    expect(chiamateA('PUT', '/api/servizi/ordine')).toHaveLength(0);
  });
});

describe('Servizi – interruttore, subito e senza barra', () => {
  it('spegne il servizio: PUT /api/servizi/{id} con attivo=false e il resto dei dati salvati', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Phon»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/servizi/sv1')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/servizi/sv1')[0].corpo).toMatchObject({ nome: 'Phon', crediti: 1, gratuitiAlGiorno: 1, minuti: 5, attivo: false });
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Phon»' })).toHaveAttribute('aria-checked', 'false'));
  });

  it('riaccende un servizio spento (attivo=true)', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Sauna»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/servizi/sv3')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/servizi/sv3')[0].corpo).toMatchObject({ nome: 'Sauna', crediti: 3, attivo: true });
  });

  it('cambiare un servizio non fa comparire la barra di salvataggio', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Docce»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/servizi/sv2')).toHaveLength(1));
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Docce»' })).toHaveAttribute('aria-checked', 'false'));
    expect(screen.queryByText(/da salvare/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Salva' })).toBeNull();
  });
});

describe('Servizi – cestino a due tocchi (si elimina sempre)', () => {
  it('il primo tocco mostra «Elimina?» e non cancella nulla', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: 'Elimina «Docce»' }));
    expect(screen.getByRole('button', { name: /Elimina\?/ })).toBeTruthy();
    expect(chiamate.filter((c) => c.metodo === 'DELETE')).toHaveLength(0);
  });

  it('il secondo tocco chiama DELETE /api/servizi/{id} e la riga sparisce', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: 'Elimina «Sauna»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamate.filter((c) => c.metodo === 'DELETE').map((c) => c.url)).toEqual(['/api/servizi/sv3']));
    await waitFor(() => expect(screen.queryByLabelText('Sposta «Sauna»')).toBeNull());
    expect(maniglie()).toEqual(['Phon', 'Docce']);
  });

  it('un servizio che ha macchine collegate si elimina lo stesso: il cestino c\'è e il tocco chiama il server', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    // «Phon» ha la macchina «Phon spogliatoio maschile»: nessun blocco preventivo in pagina
    await user.click(screen.getByRole('button', { name: 'Elimina «Phon»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamate.filter((c) => c.metodo === 'DELETE').map((c) => c.url)).toEqual(['/api/servizi/sv1']));
  });

  it('un 409 del server (qualcosa «in corso») si legge a due righe sotto la riga, con l\'attacco in grassetto; la riga resta', async () => {
    forzate['DELETE /api/servizi/sv2'] = {
      status: 409,
      corpo: { detail: 'Non si può eliminare: «Docce» ha una ricarica ancora aperta.\nConcludila: poi potrai eliminare il servizio.' },
    };
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: 'Elimina «Docce»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await screen.findByText('Non si può eliminare:');
    const riga = screen.getByLabelText('Sposta «Docce»').closest('li')!;
    const attacco = within(riga).getByText('Non si può eliminare:');
    expect(èGrassetto(attacco)).toBe(true);
    const prima = attacco.parentElement!;
    const seconda = within(riga).getByText('Concludila:').parentElement!;
    expect(prima).not.toBe(seconda);
    expect(prima.contains(seconda)).toBe(false);
    expect(seconda.contains(prima)).toBe(false);
    expect(prima.textContent).not.toContain('Concludila');
    expect(screen.getAllByText('Non si può eliminare:')).toHaveLength(1);
    expect(screen.getByLabelText('Sposta «Docce»')).toBeTruthy();
  });

  it('eliminabile=false: il secondo tocco mostra motivoBlocco senza chiamare DELETE', async () => {
    servizi = servizi.map((s) =>
      s.id === 'sv2' ? { ...s, eliminabile: false, motivoBlocco: 'Non si può eliminare: «Docce» ha una ricarica ancora aperta.\nConcludila: poi potrai eliminare il servizio.' } as Servizio : s);
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: 'Elimina «Docce»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await screen.findByText('Non si può eliminare:');
    expect(screen.getByText('Concludila:')).toBeTruthy();
    expect(chiamate.filter((c) => c.metodo === 'DELETE')).toHaveLength(0);
  });
});

describe('Servizi – il foglio', () => {
  it('«Aggiungi un servizio…» apre il foglio con «Aggiungi il servizio» e non chiama il server', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: /Aggiungi un servizio/ }));
    const foglio = await screen.findByRole('dialog');
    expect(within(foglio).getByRole('button', { name: 'Aggiungi il servizio' })).toBeTruthy();
    expect(within(foglio).getByRole('button', { name: 'Annulla' })).toBeTruthy();
    expect(within(foglio).getByLabelText(/^Nome/)).toBeTruthy();
    expect(within(foglio).getByLabelText(/^Crediti per ogni uso/)).toBeTruthy();
    expect(within(foglio).getByLabelText(/^Usi gratuiti al giorno/)).toBeTruthy();
    expect(within(foglio).getByLabelText(/^Minuti sulla macchina/)).toBeTruthy();
    expect(scritture()).toHaveLength(0);
  });

  it('«Aggiungi il servizio» è disabilitato finché manca il nome; poi crea (POST senza «ordine») e non compare la barra', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: /Aggiungi un servizio/ }));
    const foglio = await screen.findByRole('dialog');
    const aggiungi = within(foglio).getByRole('button', { name: 'Aggiungi il servizio' });
    expect(aggiungi).toBeDisabled();
    await user.type(within(foglio).getByLabelText(/^Nome/), 'Idromassaggio');
    const crediti = within(foglio).getByLabelText(/^Crediti per ogni uso/);
    await user.clear(crediti);
    await user.type(crediti, '4');
    await waitFor(() => expect(aggiungi).toBeEnabled());
    await user.click(aggiungi);
    await waitFor(() => expect(chiamateA('POST', '/api/servizi')).toHaveLength(1));
    const c = chiamateA('POST', '/api/servizi')[0].corpo;
    expect(c).toMatchObject({ nome: 'Idromassaggio', crediti: 4 });
    expect(c).not.toHaveProperty('ordine');
    await waitFor(() => expect(screen.getByLabelText('Sposta «Idromassaggio»')).toBeTruthy());
    expect(screen.queryByText(/da salvare/)).toBeNull();
  });

  it('il foglio di un servizio salvato: «Salva il servizio» disabilitato finché non cambia nulla; poi PUT', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Docce'));
    const foglio = await screen.findByRole('dialog');
    const salva = within(foglio).getByRole('button', { name: 'Salva il servizio' });
    expect(salva).toBeDisabled();
    const nome = within(foglio).getByLabelText(/^Nome/);
    expect((nome as HTMLInputElement).value).toBe('Docce');
    await user.clear(nome);
    await user.type(nome, 'Docce calde');
    await waitFor(() => expect(salva).toBeEnabled());
    await user.click(salva);
    await waitFor(() => expect(chiamateA('PUT', '/api/servizi/sv2')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/servizi/sv2')[0].corpo).toMatchObject({ nome: 'Docce calde', crediti: 2 });
    // le impostazioni di pagina non sono state toccate
    expect(chiamateA('PUT', '/api/borsellino/regole')).toHaveLength(0);
  });

  it('una bozza non salvata nel foglio non fa comparire la barra', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Docce'));
    const foglio = await screen.findByRole('dialog');
    await user.type(within(foglio).getByLabelText(/^Nome/), ' nuove');
    expect(screen.queryByText(/da salvare/)).toBeNull();
  });

  it('crediti negativi: campo invalido (aria-invalid) e salvataggio disabilitato', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Docce'));
    const foglio = await screen.findByRole('dialog');
    const crediti = within(foglio).getByLabelText(/^Crediti per ogni uso/);
    await user.clear(crediti);
    await user.type(crediti, '-1');
    expect(crediti).toHaveAttribute('aria-invalid', 'true');
    expect(within(foglio).getByRole('button', { name: 'Salva il servizio' })).toBeDisabled();
  });

  it('minuti = 0: invalido (vuoto oppure almeno 1); vuoto va bene', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Phon'));
    const foglio = await screen.findByRole('dialog');
    const minuti = within(foglio).getByLabelText(/^Minuti sulla macchina/);
    await user.clear(minuti);
    await user.type(minuti, '0');
    expect(minuti).toHaveAttribute('aria-invalid', 'true');
    expect(within(foglio).getByRole('button', { name: 'Salva il servizio' })).toBeDisabled();
    await user.clear(minuti);
    expect(minuti).not.toHaveAttribute('aria-invalid', 'true');
    await waitFor(() => expect(within(foglio).getByRole('button', { name: 'Salva il servizio' })).toBeEnabled());
  });

  it('«Annulla» chiude il foglio senza scrivere', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Docce'));
    const foglio = await screen.findByRole('dialog');
    await user.click(within(foglio).getByRole('button', { name: 'Annulla' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(scritture()).toHaveLength(0);
  });
});

describe('Impostazioni di pagina – la barra non conta i servizi', () => {
  it('cambiare «Un credito vale» mostra «1 modifica da salvare»; «Salva» scrive solo le regole del borsellino', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    expect(screen.queryByText(/da salvare/)).toBeNull();
    const campo = screen.getByLabelText('Un credito vale');
    await user.clear(campo);
    await user.type(campo, '0,25');
    expect(await screen.findByText('1 modifica da salvare')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Salva' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/borsellino/regole')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/borsellino/regole')[0].corpo.valoreCredito).toBe(0.25);
    expect(chiamate.filter((c) => c.metodo !== 'GET' && c.url.startsWith('/api/servizi'))).toHaveLength(0);
  });

  it('un servizio spento e una modifica di pagina: la barra conta 1, non 2', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Docce»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/servizi/sv2')).toHaveLength(1));
    const campo = screen.getByLabelText('Ricarica minima');
    await user.clear(campo);
    await user.type(campo, '10');
    expect(await screen.findByText('1 modifica da salvare')).toBeTruthy();
  });

  it('«Annulla» della barra rimette il valore', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    const campo = screen.getByLabelText('Un credito vale') as HTMLInputElement;
    const prima = campo.value;
    await user.clear(campo);
    await user.type(campo, '0,50');
    await user.click(await screen.findByRole('button', { name: 'Annulla' }));
    await waitFor(() => expect((screen.getByLabelText('Un credito vale') as HTMLInputElement).value).toBe(prima));
    expect(screen.queryByText(/da salvare/)).toBeNull();
  });
});

describe('Macchine – righe dell\'elenco', () => {
  it('nessuna maniglia (l\'ordine è per tipo e nome); interruttore e cestino per ogni macchina', async () => {
    render(PhonDocce);
    await attendi();
    for (const nome of ['Phon spogliatoio maschile', 'Docce spogliatoio femminile', 'Phon atrio', 'Lettore ingresso']) {
      expect(screen.queryByLabelText(`Sposta «${nome}»`)).toBeNull();
      expect(screen.getByRole('switch', { name: `Acceso: «${nome}»` })).toBeTruthy();
      expect(screen.getByRole('button', { name: `Elimina «${nome}»` })).toBeTruthy();
    }
    expect(screen.getByRole('button', { name: /Aggiungi una macchina/ })).toBeTruthy();
  });

  it('non c\'è più il pulsante «Spegni» (l\'interruttore ne prende il posto)', async () => {
    render(PhonDocce);
    await attendi();
    expect(screen.queryByRole('button', { name: /spegni/i })).toBeNull();
    expect(screen.queryByTitle(/spegni/i)).toBeNull();
  });

  it('l\'interruttore spegne subito la macchina: PUT /api/dispositivi/{id} con nome e attivo=false', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Phon spogliatoio maschile»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/dispositivi/d1')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/dispositivi/d1')[0].corpo).toMatchObject({ nome: 'Phon spogliatoio maschile', attivo: false });
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Phon spogliatoio maschile»' })).toHaveAttribute('aria-checked', 'false'));
    expect(screen.queryByText(/da salvare/)).toBeNull();
  });

  it('il nome di una macchina lunga sta intero nella riga (nessun troncamento nel testo)', async () => {
    render(PhonDocce);
    await attendi();
    expect(screen.getByText('Docce spogliatoio femminile')).toBeTruthy();
  });

  it('una macchina senza servizio porta il tag «Senza servizio»; le altre no', async () => {
    render(PhonDocce);
    await attendi();
    const tag = screen.getAllByText('Senza servizio');
    expect(tag).toHaveLength(1);
    const riga = screen.getByRole('switch', { name: 'Acceso: «Phon atrio»' }).closest('li')!;
    expect(riga.textContent).toContain('Senza servizio');
    const rigaPhon = screen.getByRole('switch', { name: 'Acceso: «Phon spogliatoio maschile»' }).closest('li')!;
    expect(rigaPhon.textContent).not.toContain('Senza servizio');
  });

  it('il lettore del saldo non porta il tag «Senza servizio» (non ne ha bisogno)', async () => {
    render(PhonDocce);
    await attendi();
    const riga = screen.getByRole('switch', { name: 'Acceso: «Lettore ingresso»' }).closest('li')!;
    expect(riga.textContent).not.toContain('Senza servizio');
  });

  it('il cestino della macchina: due tocchi, DELETE /api/dispositivi/{id}, la riga sparisce (anche con utilizzi)', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: 'Elimina «Phon spogliatoio maschile»' }));
    expect(chiamate.filter((c) => c.metodo === 'DELETE')).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamate.filter((c) => c.metodo === 'DELETE').map((c) => c.url)).toEqual(['/api/dispositivi/d1']));
    await waitFor(() => expect(screen.queryByRole('switch', { name: 'Acceso: «Phon spogliatoio maschile»' })).toBeNull());
  });
});

describe('Macchine – il foglio e la chiave', () => {
  it('il tocco sul nome apre il foglio della macchina con «Nuova chiave» e senza scrivere nulla', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Phon spogliatoio maschile'));
    const foglio = await screen.findByRole('dialog');
    expect((within(foglio).getByLabelText(/^Nome/) as HTMLInputElement).value).toBe('Phon spogliatoio maschile');
    expect(within(foglio).getByRole('button', { name: 'Salva la macchina' })).toBeDisabled();
    expect(within(foglio).getByRole('button', { name: /Nuova chiave/ })).toBeTruthy();
    expect(scritture()).toHaveLength(0);
  });

  it('«Nuova chiave» → «Nuova chiave?» → POST …/chiave → riquadro «Chiave di «…»» con la chiave e «L\'ho copiata»', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Phon spogliatoio maschile'));
    const foglio = await screen.findByRole('dialog');
    await user.click(within(foglio).getByRole('button', { name: /^Nuova chiave$/ }));
    expect(within(foglio).getByRole('button', { name: /Nuova chiave\?/ })).toBeTruthy();
    expect(chiamateA('POST', '/api/dispositivi/d1/chiave')).toHaveLength(0);
    await user.click(within(foglio).getByRole('button', { name: /Nuova chiave\?/ }));
    await waitFor(() => expect(chiamateA('POST', '/api/dispositivi/d1/chiave')).toHaveLength(1));
    expect(await within(foglio).findByText(/Chiave di «Phon spogliatoio maschile»/)).toBeTruthy();
    expect(within(foglio).getByText('chiave-rigenerata-9999')).toBeTruthy();
    expect(within(foglio).getAllByRole('status').some((e) => /Chiave di/.test(e.textContent ?? ''))).toBe(true);
    expect(within(foglio).getByText(/d1/)).toBeTruthy(); // «Id: …»
    await user.click(within(foglio).getByRole('button', { name: /L'ho copiata/ }));
    await waitFor(() => expect(within(foglio).queryByText('chiave-rigenerata-9999')).toBeNull());
    expect(within(foglio).queryByText(/Chiave di «/)).toBeNull();
  });

  it('«Nuova chiave» non sta più nella card Macchine fuori dal foglio', async () => {
    render(PhonDocce);
    await attendi();
    expect(screen.queryByRole('button', { name: /Nuova chiave/ })).toBeNull();
  });

  it('«Aggiungi una macchina…» apre il foglio «Nuova macchina»; «Aggiungi la macchina» è disabilitato senza nome', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: /Aggiungi una macchina/ }));
    const foglio = await screen.findByRole('dialog', { name: 'Nuova macchina' });
    expect(within(foglio).getByRole('button', { name: 'Aggiungi la macchina' })).toBeDisabled();
    expect(within(foglio).queryByRole('button', { name: /Nuova chiave/ })).toBeNull();
    expect(scritture()).toHaveLength(0);
  });

  it('«Aggiungi la macchina» crea (POST) e il foglio resta aperto, ora della macchina creata, con la chiave in cima', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: /Aggiungi una macchina/ }));
    const foglio = await screen.findByRole('dialog', { name: 'Nuova macchina' });
    await user.type(within(foglio).getByLabelText(/^Nome/), 'Docce atrio');
    await user.click(within(foglio).getByRole('button', { name: 'Aggiungi la macchina' }));
    await waitFor(() => expect(chiamateA('POST', '/api/dispositivi')).toHaveLength(1));
    expect(chiamateA('POST', '/api/dispositivi')[0].corpo).toMatchObject({ nome: 'Docce atrio', tipo: 'SERVIZIO' });
    const dopo = await screen.findByRole('dialog', { name: 'Docce atrio' });
    expect(within(dopo).getByText(/Chiave di «Docce atrio»/)).toBeTruthy();
    expect(within(dopo).getByText('chiave-nuova-0001')).toBeTruthy();
    expect(within(dopo).getByRole('button', { name: 'Salva la macchina' })).toBeTruthy();
    expect(within(dopo).queryByRole('button', { name: 'Aggiungi la macchina' })).toBeNull();
    expect(within(dopo).getByRole('button', { name: /Nuova chiave/ })).toBeTruthy();
    // e la macchina è nell'elenco
    expect(screen.getByRole('switch', { name: 'Acceso: «Docce atrio»' })).toBeTruthy();
    await user.click(within(dopo).getByRole('button', { name: /L'ho copiata/ }));
    await waitFor(() => expect(within(dopo).queryByText('chiave-nuova-0001')).toBeNull());
    expect(screen.getByRole('dialog', { name: 'Docce atrio' })).toBeTruthy();
  });

  it('nuova macchina: il tipo «Lettore del saldo» toglie la scelta del servizio e crea con tipo SALDO', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: /Aggiungi una macchina/ }));
    const foglio = await screen.findByRole('dialog', { name: 'Nuova macchina' });
    expect(within(foglio).getByLabelText(/^Servizio/)).toBeTruthy();
    const macchina = within(foglio).getByRole('button', { name: 'Macchina' });
    const lettore = within(foglio).getByRole('button', { name: 'Lettore del saldo' });
    expect(macchina).toHaveAttribute('aria-pressed', 'true');
    await user.click(lettore);
    expect(lettore).toHaveAttribute('aria-pressed', 'true');
    expect(within(foglio).queryByLabelText(/^Servizio/)).toBeNull();
    await user.type(within(foglio).getByLabelText(/^Nome/), 'Lettore palestra');
    await user.click(within(foglio).getByRole('button', { name: 'Aggiungi la macchina' }));
    await waitFor(() => expect(chiamateA('POST', '/api/dispositivi')).toHaveLength(1));
    expect(chiamateA('POST', '/api/dispositivi')[0].corpo).toMatchObject({ nome: 'Lettore palestra', tipo: 'SALDO' });
  });

  it('nuova macchina spenta dal riquadro «Acceso» del foglio: il POST porta attivo=false', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByRole('button', { name: /Aggiungi una macchina/ }));
    const foglio = await screen.findByRole('dialog', { name: 'Nuova macchina' });
    const acceso = within(foglio).getByRole('switch', { name: /^Acceso/ });
    expect(acceso).toHaveAttribute('aria-checked', 'true');
    await user.click(acceso);
    // la macchina nuova non esiste ancora: nessuna chiamata, solo la bozza cambia
    expect(scritture()).toHaveLength(0);
    await user.type(within(foglio).getByLabelText(/^Nome/), 'Phon spento');
    await user.click(within(foglio).getByRole('button', { name: 'Aggiungi la macchina' }));
    await waitFor(() => expect(chiamateA('POST', '/api/dispositivi')).toHaveLength(1));
    expect(chiamateA('POST', '/api/dispositivi')[0].corpo.attivo).toBe(false);
  });

  it('il foglio di una macchina salvata: l\'interruttore del riquadro cambia subito lo stato (PUT)', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Docce spogliatoio femminile'));
    const foglio = await screen.findByRole('dialog');
    await user.click(within(foglio).getByRole('switch', { name: /^Acceso/ }));
    await waitFor(() => expect(chiamateA('PUT', '/api/dispositivi/d2')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/dispositivi/d2')[0].corpo).toMatchObject({ nome: 'Docce spogliatoio femminile', attivo: false });
  });

  it('il tipo di una macchina salvata è solo testo (non si cambia): niente segmentato nel foglio', async () => {
    const user = userEvent.setup();
    render(PhonDocce);
    await attendi();
    await user.click(screen.getByText('Phon spogliatoio maschile'));
    const foglio = await screen.findByRole('dialog');
    expect(within(foglio).queryByRole('button', { name: 'Lettore del saldo' })).toBeNull();
  });
});

describe('Pulizia dei vecchi elementi locali', () => {
  it('nel DOM non resta nessun .mini-btn, .x-btn né .seg-s', async () => {
    const user = userEvent.setup();
    const { container } = render(PhonDocce);
    await attendi();
    const vecchi = '.mini-btn, .x-btn, .seg-s';
    expect(container.querySelectorAll(vecchi)).toHaveLength(0);
    await user.click(screen.getByText('Phon spogliatoio maschile'));
    await screen.findByRole('dialog');
    expect(document.body.querySelectorAll(vecchi)).toHaveLength(0);
  });

  it('nel DOM non resta la tabella dei servizi né le classi locali delle macchine', async () => {
    const { container } = render(PhonDocce);
    await attendi();
    expect(container.querySelectorAll('table, .tabella, .serv, .macc, .ico-m')).toHaveLength(0);
  });
});

describe('Senza il permesso IMPOSTAZIONI', () => {
  it('niente maniglie, cestini né «Aggiungi»', async () => {
    sessione.utente = utente(['BORSELLINO']);
    render(PhonDocce);
    await screen.findByText('Phon spogliatoio maschile');
    expect(screen.queryByLabelText(/^Sposta «/)).toBeNull();
    expect(screen.queryByRole('button', { name: /^Elimina «/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Aggiungi/ })).toBeNull();
  });
});
