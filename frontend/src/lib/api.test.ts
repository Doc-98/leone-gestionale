import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, eAvviso, ErroreApi, messaggioErrore, NonAutenticato } from './api';

/**
 * Come il frontend parla con il server: il token CSRF prima di ogni modifica, e ogni risposta d'errore diventa un
 * messaggio da mostrare (mai un errore tecnico). Qui `fetch` e i cookie sono finti.
 */
type Chiamata = { url: string; init?: RequestInit };

let chiamate: Chiamata[];
let risposte: (Response | Error)[];
const doc = { cookie: '' };

function risposta(status: number, corpo?: unknown) {
  return new Response(corpo === undefined ? null : typeof corpo === 'string' ? corpo : JSON.stringify(corpo), { status });
}

beforeEach(() => {
  chiamate = [];
  risposte = [];
  doc.cookie = '';
  vi.stubGlobal('document', doc);
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    chiamate.push({ url, init });
    // il server, chiesto il token, lo mette nel cookie
    if (url === '/api/auth/csrf') {
      doc.cookie = 'XSRF-TOKEN=abc%3D%3D';
      return risposta(204);
    }
    const r = risposte.shift() ?? risposta(200, {});
    if (r instanceof Error) throw r;
    return r;
  });
});
afterEach(() => vi.unstubAllGlobals());

const intestazioni = (c: Chiamata) => c.init?.headers as Record<string, string>;

describe('richieste al server', () => {
  it('una lettura non chiede il token e restituisce il JSON', async () => {
    risposte.push(risposta(200, { societa: 'Società di prova', demo: true }));
    expect(await api.ambiente()).toEqual({ societa: 'Società di prova', demo: true });
    expect(chiamate.map((c) => c.url)).toEqual(['/api/ambiente']);
    expect(intestazioni(chiamate[0])).toEqual({ Accept: 'application/json' });
  });

  it('una modifica senza cookie chiede prima il token e lo manda decodificato', async () => {
    risposte.push(risposta(200, { accountId: 'a' }));
    await api.login('segreteria@demo.leone', 'demo1234');
    expect(chiamate.map((c) => c.url)).toEqual(['/api/auth/csrf', '/api/auth/login']);
    const login = chiamate[1];
    expect(login.init?.method).toBe('POST');
    expect(intestazioni(login)['X-XSRF-TOKEN']).toBe('abc==');
    expect(intestazioni(login)['Content-Type']).toBe('application/json');
    expect(JSON.parse(login.init?.body as string)).toEqual({ email: 'segreteria@demo.leone', password: 'demo1234' });
  });

  it('con il cookie già presente il token non si richiede', async () => {
    doc.cookie = 'altro=1; XSRF-TOKEN=xyz';
    risposte.push(risposta(204));
    await api.logout();
    expect(chiamate.map((c) => c.url)).toEqual(['/api/auth/logout']);
    expect(intestazioni(chiamate[0])['X-XSRF-TOKEN']).toBe('xyz');
    expect(intestazioni(chiamate[0])['Content-Type']).toBeUndefined();
  });

  it('una risposta vuota (204) vale undefined', async () => {
    doc.cookie = 'XSRF-TOKEN=xyz';
    risposte.push(risposta(204));
    expect(await api.logout()).toBeUndefined();
  });
});

describe('errori', () => {
  async function errore(p: Promise<unknown>): Promise<unknown> {
    try {
      await p;
    } catch (e) {
      return e;
    }
    throw new Error('doveva fallire');
  }

  it('server spento: «non risponde», con stato 0', async () => {
    risposte.push(new TypeError('Failed to fetch'));
    const e = await errore(api.me());
    expect(e).toBeInstanceOf(ErroreApi);
    expect((e as ErroreApi).status).toBe(0);
    expect((e as ErroreApi).message).toContain('non risponde');
  });

  it('server spento già alla richiesta del token: stesso errore', async () => {
    vi.stubGlobal('fetch', async () => {
      throw new TypeError('Failed to fetch');
    });
    const e = await errore(api.login('a@b.it', 'x'));
    expect((e as ErroreApi).status).toBe(0);
  });

  it('401: sessione scaduta, tranne al login dove sono le credenziali sbagliate', async () => {
    risposte.push(risposta(401));
    expect(await errore(api.me())).toBeInstanceOf(NonAutenticato);
    risposte.push(risposta(401, { detail: 'Email o password non corrette.' }));
    const e = await errore(api.login('a@b.it', 'x'));
    expect(e).toBeInstanceOf(ErroreApi);
    expect((e as ErroreApi).status).toBe(401);
  });

  it('il messaggio del server vince; senza, uno comprensibile per lo stato', async () => {
    risposte.push(risposta(400, { detail: 'Scrivi il nome.' }));
    expect(((await errore(api.me())) as ErroreApi).message).toBe('Scrivi il nome.');
    risposte.push(risposta(403));
    expect(((await errore(api.me())) as ErroreApi).message).toBe('Non hai i permessi per questa operazione.');
    risposte.push(risposta(500, {}));
    expect(((await errore(api.me())) as ErroreApi).message).toBe('Errore 500');
  });

  it('una pagina HTML al posto del JSON (proxy) non rompe niente', async () => {
    risposte.push(risposta(502, '<html><body>Bad Gateway</body></html>'));
    const e = (await errore(api.me())) as ErroreApi;
    expect(e).toBeInstanceOf(ErroreApi);
    expect(e.status).toBe(502);
    expect(e.message).toBe('Il server non risponde. Riprova tra poco.');
  });

  it('un avviso si può confermare', async () => {
    risposte.push(risposta(409, { detail: 'Due turni accavallati.', avviso: true }));
    const e = await errore(api.me());
    expect(eAvviso(e)).toBe(true);
    expect(eAvviso(new ErroreApi(409, 'x'))).toBe(false);
    expect(eAvviso(new Error('x'))).toBe(false);
  });

  it('messaggioErrore: il messaggio dell\'API o quello predefinito', () => {
    expect(messaggioErrore(new ErroreApi(400, 'Scrivi il nome.'))).toBe('Scrivi il nome.');
    expect(messaggioErrore(new Error('interno'))).toBe('Operazione non riuscita.');
    expect(messaggioErrore('boh', 'Non salvato.')).toBe('Non salvato.');
  });
});
