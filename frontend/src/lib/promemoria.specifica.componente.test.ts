import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Promemoria from '../routes/Promemoria.svelte';
import type { DatiSocieta, EmailBreve, Me, RegolaPromemoria } from './api';
import { sessione } from './sessione.svelte';

/**
 * Specifica (brief coerenza blocco 7, lotto 4): la pagina «Promemoria» (routes/Promemoria.svelte).
 *  - l'interruttore di un tipo fa UNA chiamata PUT /api/promemoria/tipi/{tipo} {attivo} e nessuna PUT per promemoria;
 *    con la risposta (array di tutte le regole) aggiorna la linea senza ricaricare la bozza;
 *  - un solo pulsante «Manda i promemoria di oggi», con e senza posta, che chiama POST /api/promemoria/esegui;
 *  - nessuna email: «Nessuna email finora.»;
 *  - i giorni della lista d'attesa passano dalla barra (1 modifica da salvare; Salva → PUT /api/societa);
 *  - «Salva il promemoria» disabilitato senza modifiche; nessun «Salva i giorni».
 * Il server è un `fetch` finto che segue il contratto del brief.
 *
 * IPOTESI: il primo promemoria è quello aperto nell'editor all'arrivo (come oggi); la card dell'editor è la regione
 * «Testo dell'email»; il campo dei giorni ha aria-label «Giorni per rispondere».
 */
type Chiamata = { metodo: string; url: string; corpo: any };

let chiamate: Chiamata[];
let regole: RegolaPromemoria[];
let ultimeEmail: EmailBreve[];
let postaConfigurata: boolean;
let societa: DatiSocieta;
let forzate: Record<string, { status: number; corpo?: unknown }>;

const unaRegola = (id: string, tipo: RegolaPromemoria['tipo'], extra: Partial<RegolaPromemoria> = {}): RegolaPromemoria => ({
  id, tipo, giorni: 7, maxInvii: null, attivo: true, oggetto: `Oggetto ${id}`, testo: `Testo ${id}`, ...extra,
});

function utente(permessi: Me['permessi']): Me {
  return { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi, admin: false, deveCambiarePassword: false };
}
const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
  chiamate = [];
  forzate = {};
  postaConfigurata = true;
  ultimeEmail = [];
  regole = [
    unaRegola('r1', 'CERTIFICATO', { giorni: 30 }),
    unaRegola('r2', 'ABBONAMENTO', { giorni: 7, attivo: false }),
    unaRegola('r4', 'PAGAMENTO', { giorni: 7, maxInvii: 3 }),
    // nessuna regola di «Tesseramento in scadenza» né di «Tesseramento mancante»
  ];
  societa = {
    nome: 'Società di prova', ragioneSociale: null, partitaIva: null, codiceFiscale: null, indirizzo: null, cap: null, citta: null, provincia: null,
    email: null, telefono: null, giorniAnticipo: 30, pagamentiFamiglia: 'A_SCELTA', giorniRispostaAttesa: 3,
  } as DatiSocieta;
  sessione.utente = utente(['CLIENTI', 'IMPOSTAZIONI']);
  sessione.pronto = true;

  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    const f = forzate[`${m} ${url}`];
    if (f) return json(f.status, f.corpo);

    if (m === 'GET' && url === '/api/promemoria') return json(200, { regole, ultimeEmail, postaConfigurata, orario: '9:00' });
    if (m === 'GET' && url === '/api/societa') return json(200, societa);
    if (m === 'GET') return json(200, {});

    const tipo = url.match(/^\/api\/promemoria\/tipi\/([^/]+)$/)?.[1];
    if (tipo && m === 'PUT') {
      regole = regole.map((r) => (r.tipo === tipo ? { ...r, attivo: corpo.attivo } : r));
      return json(200, regole);
    }
    if (m === 'POST' && url === '/api/promemoria/esegui') return json(200, { email: 0, inviate: 0, nonInviate: 0, senzaEmail: 0, errori: 0 });
    const id = url.match(/^\/api\/promemoria\/([^/]+)$/)?.[1];
    if (id && m === 'PUT') {
      const i = regole.findIndex((r) => r.id === id);
      regole[i] = { ...regole[i], ...corpo };
      return json(200, regole[i]);
    }
    if (m === 'PUT' && url === '/api/societa') { societa = { ...societa, ...corpo }; return json(200, societa); }
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
const attendi = async () => { await screen.findByRole('switch', { name: 'Acceso: «Certificato medico»' }); };
const editor = () => screen.getByRole('region', { name: "Testo dell'email" });

describe('Interruttore del tipo – una chiamata sola', () => {
  it('spegne «Certificato medico» con UNA PUT /api/promemoria/tipi/CERTIFICATO {attivo:false} e nessuna PUT per promemoria', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    const interruttore = screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' });
    expect(interruttore).toHaveAttribute('aria-checked', 'true');
    await user.click(interruttore);
    await waitFor(() => expect(chiamateA('PUT', '/api/promemoria/tipi/CERTIFICATO')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/promemoria/tipi/CERTIFICATO')[0].corpo).toEqual({ attivo: false });
    expect(chiamate.filter((c) => c.metodo === 'PUT' && /^\/api\/promemoria\/(?!tipi\/)[^/]+$/.test(c.url))).toHaveLength(0);
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' })).toHaveAttribute('aria-checked', 'false'));
  });

  it('un tipo spento si riaccende con attivo:true', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    const interruttore = screen.getByRole('switch', { name: 'Acceso: «Abbonamento»' });
    expect(interruttore).toHaveAttribute('aria-checked', 'false');
    await user.click(interruttore);
    await waitFor(() => expect(chiamateA('PUT', '/api/promemoria/tipi/ABBONAMENTO')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/promemoria/tipi/ABBONAMENTO')[0].corpo).toEqual({ attivo: true });
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Abbonamento»' })).toHaveAttribute('aria-checked', 'true'));
  });

  it('con più regole nello stesso tipo la chiamata resta una (la transazione è del server)', async () => {
    regole = [...regole, unaRegola('r1b', 'CERTIFICATO', { giorni: 7 })];
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/promemoria/tipi/CERTIFICATO')).toHaveLength(1));
    expect(chiamate.filter((c) => c.metodo === 'PUT')).toHaveLength(1);
  });

  it('è disabilitato per un tipo senza regole; il tocco non chiama il server', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    const vuoto = screen.getByRole('switch', { name: 'Acceso: «Tesseramento in scadenza»' });
    expect(vuoto).toBeDisabled();
    await user.click(vuoto);
    expect(chiamate.filter((c) => c.metodo === 'PUT')).toHaveLength(0);
    expect(screen.getByRole('switch', { name: 'Acceso: «Tesseramento mancante»' })).toBeDisabled();
    expect(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' })).toBeEnabled();
  });

  it('non rilegge la pagina: la linea si aggiorna con la risposta (una sola GET /api/promemoria)', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/promemoria/tipi/CERTIFICATO')).toHaveLength(1));
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' })).toHaveAttribute('aria-checked', 'false'));
    expect(chiamateA('GET', '/api/promemoria')).toHaveLength(1);
  });

  it('dopo il tocco il testo non salvato della bozza aperta resta', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    const oggetto = within(editor()).getByLabelText('Oggetto') as HTMLInputElement;
    expect(oggetto.value).toBe('Oggetto r1');
    await user.type(oggetto, ' (bozza)');
    expect(oggetto.value).toBe('Oggetto r1 (bozza)');
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/promemoria/tipi/CERTIFICATO')).toHaveLength(1));
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' })).toHaveAttribute('aria-checked', 'false'));
    expect((within(editor()).getByLabelText('Oggetto') as HTMLInputElement).value).toBe('Oggetto r1 (bozza)');
    expect(within(editor()).getByRole('button', { name: 'Salva il promemoria' })).toBeEnabled();
  });

  it('un errore del server lascia l\'interruttore com\'era e mostra l\'errore', async () => {
    forzate['PUT /api/promemoria/tipi/CERTIFICATO'] = { status: 500 };
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/promemoria/tipi/CERTIFICATO')).toHaveLength(1));
    await screen.findByRole('alert');
    expect(screen.getByRole('switch', { name: 'Acceso: «Certificato medico»' })).toHaveAttribute('aria-checked', 'true');
  });
});

describe('Editor – «Acceso» subito, sui dati salvati', () => {
  it('l\'interruttore dell\'editor salva subito (PUT /api/promemoria/{id}) con i dati SALVATI, non con la bozza', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await user.type(within(editor()).getByLabelText('Oggetto'), ' (bozza)');
    await user.click(within(editor()).getByRole('switch', { name: /^Acceso/ }));
    await waitFor(() => expect(chiamateA('PUT', '/api/promemoria/r1')).toHaveLength(1));
    const c = chiamateA('PUT', '/api/promemoria/r1')[0].corpo;
    expect(c).toMatchObject({ attivo: false, oggetto: 'Oggetto r1', testo: 'Testo r1', giorni: 30 });
    // la bozza non salvata dell'oggetto resta
    expect((within(editor()).getByLabelText('Oggetto') as HTMLInputElement).value).toBe('Oggetto r1 (bozza)');
  });
});

describe('Salva il promemoria', () => {
  it('disabilitato finché la bozza non cambia; poi salva giorni, volte, oggetto e testo', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    const salva = within(editor()).getByRole('button', { name: 'Salva il promemoria' });
    expect(salva).toBeDisabled();
    const oggetto = within(editor()).getByLabelText('Oggetto');
    await user.clear(oggetto);
    await user.type(oggetto, 'Certificato in scadenza');
    await waitFor(() => expect(salva).toBeEnabled());
    await user.click(salva);
    await waitFor(() => expect(chiamateA('PUT', '/api/promemoria/r1')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/promemoria/r1')[0].corpo).toMatchObject({ giorni: 30, oggetto: 'Certificato in scadenza', testo: 'Testo r1' });
  });
});

describe('«Manda i promemoria di oggi» – un pulsante solo', () => {
  it('con la posta configurata: un solo pulsante con questa etichetta, che chiama POST /api/promemoria/esegui', async () => {
    postaConfigurata = true;
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    expect(screen.getByText('Posta configurata')).toBeTruthy();
    const pulsanti = screen.getAllByRole('button', { name: 'Manda i promemoria di oggi' });
    expect(pulsanti).toHaveLength(1);
    expect(screen.queryByRole('button', { name: /Prepara adesso|Manda adesso/ })).toBeNull();
    await user.click(pulsanti[0]);
    await waitFor(() => expect(chiamateA('POST', '/api/promemoria/esegui')).toHaveLength(1));
  });

  it('senza posta: avviso «Il server di posta non è configurato» e lo stesso, unico pulsante', async () => {
    postaConfigurata = false;
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    expect(screen.getByText('Il server di posta non è configurato')).toBeTruthy();
    expect(screen.queryByText('Posta configurata')).toBeNull();
    const pulsanti = screen.getAllByRole('button', { name: 'Manda i promemoria di oggi' });
    expect(pulsanti).toHaveLength(1);
    expect(screen.queryByRole('button', { name: /Prepara adesso|Manda adesso/ })).toBeNull();
    await user.click(pulsanti[0]);
    await waitFor(() => expect(chiamateA('POST', '/api/promemoria/esegui')).toHaveLength(1));
  });

  it('niente conferma: un solo tocco manda il giro', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await user.click(screen.getByRole('button', { name: 'Manda i promemoria di oggi' }));
    await waitFor(() => expect(chiamateA('POST', '/api/promemoria/esegui')).toHaveLength(1));
    expect(screen.queryByRole('button', { name: /\?$/ })).toBeNull();
  });

  it('il risultato si dice in un messaggio (nessun promemoria da preparare)', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await user.click(screen.getByRole('button', { name: 'Manda i promemoria di oggi' }));
    await screen.findByText(/nessun promemoria/i);
  });
});

describe('Ultime email', () => {
  it('senza email: «Nessuna email finora.»', async () => {
    ultimeEmail = [];
    render(Promemoria);
    await attendi();
    expect(screen.getByText('Nessuna email finora.')).toBeTruthy();
  });

  it('con delle email non compare, e lo stato è una pillola su una riga («non spedita» intero)', async () => {
    ultimeEmail = [{ id: 'e1', personaId: null, destinatario: 'famiglia.prova@example.test', oggetto: 'Certificato in scadenza', testo: 'x', stato: 'NON_INVIATA', errore: null, creataIl: '2026-11-20T09:00:00Z' }];
    const { container } = render(Promemoria);
    await attendi();
    expect(screen.queryByText('Nessuna email finora.')).toBeNull();
    const pillola = screen.getByText('non spedita');
    expect(pillola.textContent).toBe('non spedita');
    expect(pillola.querySelector('br')).toBeNull();
    expect(container.querySelector('.stato br')).toBeNull();
  });
});

describe('Giorni della lista d\'attesa – dalla barra', () => {
  const campo = () => screen.getByLabelText('Giorni per rispondere') as HTMLInputElement;

  it('il campo parte dal valore salvato; nessuna barra e nessun «Salva i giorni»', async () => {
    render(Promemoria);
    await attendi();
    await waitFor(() => expect(campo().value).toBe('3'));
    expect(screen.queryByText(/da salvare/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Salva i giorni' })).toBeNull();
  });

  it('cambiare il valore mostra «1 modifica da salvare»; «Salva» chiama PUT /api/societa con giorniRispostaAttesa e il resto invariato', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await waitFor(() => expect(campo().value).toBe('3'));
    await user.clear(campo());
    await user.type(campo(), '5');
    expect(await screen.findByText('1 modifica da salvare')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Salva i giorni' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Salva' }));
    await waitFor(() => expect(chiamateA('PUT', '/api/societa')).toHaveLength(1));
    expect(chiamateA('PUT', '/api/societa')[0].corpo).toMatchObject({ nome: 'Società di prova', giorniAnticipo: 30, giorniRispostaAttesa: 5 });
    await waitFor(() => expect(screen.queryByText(/da salvare/)).toBeNull());
  });

  it('«Annulla» rimette il valore e toglie la barra senza chiamare il server', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await waitFor(() => expect(campo().value).toBe('3'));
    await user.clear(campo());
    await user.type(campo(), '8');
    await user.click(await screen.findByRole('button', { name: 'Annulla' }));
    await waitFor(() => expect(campo().value).toBe('3'));
    expect(screen.queryByText(/da salvare/)).toBeNull();
    expect(chiamateA('PUT', '/api/societa')).toHaveLength(0);
  });

  it('fuori da 1–30 il campo è invalido e «Salva» è disabilitato', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await waitFor(() => expect(campo().value).toBe('3'));
    await user.clear(campo());
    await user.type(campo(), '31');
    expect(campo()).toHaveAttribute('aria-invalid', 'true');
    expect(await screen.findByRole('button', { name: 'Salva' })).toBeDisabled();
    await user.clear(campo());
    await user.type(campo(), '0');
    expect(campo()).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Salva' })).toBeDisabled();
  });

  it('la bozza del promemoria non conta nella barra', async () => {
    const user = userEvent.setup();
    render(Promemoria);
    await attendi();
    await user.type(within(editor()).getByLabelText('Oggetto'), ' (bozza)');
    expect(screen.queryByText(/da salvare/)).toBeNull();
  });

  it('chi non ha IMPOSTAZIONI vede il valore come testo, non un campo (nemmeno disabilitato)', async () => {
    sessione.utente = utente(['CLIENTI']);
    render(Promemoria);
    await attendi();
    expect(screen.queryByLabelText('Giorni per rispondere')).toBeNull();
    expect(screen.queryByRole('spinbutton', { name: /Giorni per rispondere/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Salva' })).toBeNull();
  });
});

describe('Pulizia dei vecchi elementi locali', () => {
  it('nel DOM non resta nessun .mini-btn e nessun «Salva i giorni»', async () => {
    const { container } = render(Promemoria);
    await attendi();
    expect(container.querySelectorAll('.mini-btn')).toHaveLength(0);
    expect(screen.queryByText('Salva i giorni')).toBeNull();
  });
});
