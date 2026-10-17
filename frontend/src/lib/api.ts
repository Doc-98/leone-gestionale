/**
 * Client delle API del backend. Sessione con cookie HttpOnly (gestito dal browser);
 * per le richieste che modificano dati si rimanda il token CSRF letto dal cookie XSRF-TOKEN.
 */

export type Ruolo = 'ADMIN' | 'COORDINATORE' | 'SEGRETERIA' | 'STAFF';

export interface Me {
  accountId: string;
  personaId: string;
  email: string;
  nome: string;
  cognome: string;
  ruoli: { ruolo: Ruolo; impiantoId: string | null }[];
}

export interface Turno {
  id: string;
  data: string; // YYYY-MM-DD
  inizio: string; // HH:mm[:ss]
  fine: string;
  ore: number;
  mansione: string;
  postazione: string;
  stato: 'PIANIFICATO' | 'ANNULLATO';
  note: string | null;
  persona: { id: string; nome: string } | null;
}

export class NonAutenticato extends Error {}
export class ErroreApi extends Error {
  constructor(
    public status: number,
    messaggio: string,
  ) {
    super(messaggio);
  }
}

function leggiCookie(nome: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(`${nome}=`))
    ?.slice(nome.length + 1);
}

async function richiesta<T>(metodo: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (metodo !== 'GET') {
    if (!leggiCookie('XSRF-TOKEN')) await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    const token = leggiCookie('XSRF-TOKEN');
    if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
  }
  let res: Response;
  try {
    res = await fetch(path, { method: metodo, headers, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ErroreApi(0, 'Il server non risponde. Controlla la connessione.');
  }
  if (res.status === 401 && path !== '/api/auth/login') throw new NonAutenticato('Sessione scaduta.');
  const testo = await res.text();
  const dati = testo ? JSON.parse(testo) : undefined;
  if (!res.ok) throw new ErroreApi(res.status, dati?.detail ?? (res.status === 403 ? 'Non hai i permessi per questa operazione.' : `Errore ${res.status}`));
  return dati as T;
}

export const api = {
  me: () => richiesta<Me>('GET', '/api/me'),
  login: (email: string, password: string) => richiesta<Me>('POST', '/api/auth/login', { email, password }),
  logout: () => richiesta<void>('POST', '/api/auth/logout'),
  turniMiei: (da: string, a: string) => richiesta<Turno[]>('GET', `/api/turni/miei?da=${da}&a=${a}`),
  turniTutti: (da: string, a: string) => richiesta<Turno[]>('GET', `/api/turni?da=${da}&a=${a}`),
};
