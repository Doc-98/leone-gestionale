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

export interface PersonaBreve {
  id: string;
  nome: string;
}

export interface Turno {
  id: string;
  data: string; // YYYY-MM-DD
  inizio: string; // HH:mm[:ss]
  fine: string;
  ore: number;
  impiantoId: string;
  postazioneId: string;
  mansione: string;
  postazione: string;
  stato: 'PIANIFICATO' | 'ANNULLATO';
  note: string | null;
  /** Negli elenchi con più persone; null = turno scoperto. Nei «miei turni» è sempre null. */
  persona: PersonaBreve | null;
}

export interface TurnoInput {
  postazioneId: string;
  data: string;
  inizio: string;
  fine: string;
  personaId: string | null;
  note?: string | null;
}

export interface Postazione {
  id: string;
  nome: string;
  mansione: string;
  impiantoId: string;
}

export interface RigaModello {
  id: string;
  postazioneId: string;
  postazione: string;
  giornoSettimana: number;
  inizio: string;
  fine: string;
  persona: PersonaBreve | null;
}

export interface Modello {
  id: string;
  impiantoId: string;
  nome: string;
  validoDal: string;
  validoAl: string | null;
  righe: RigaModello[];
}

export interface EsitoGenerazione {
  creati: number;
  scoperti: number;
  giaPresenti: number;
  avvisi: string[];
}

export type TipoRichiesta = 'CAMBIO_ORARIO' | 'SOSTITUZIONE' | 'SCAMBIO';
export type StatoRichiesta = 'IN_ATTESA_COLLEGA' | 'IN_ATTESA_APPROVAZIONE' | 'APPROVATA' | 'RESPINTA' | 'ANNULLATA';

export interface Richiesta {
  id: string;
  tipo: TipoRichiesta;
  stato: StatoRichiesta;
  turno: Turno;
  turnoCollega: Turno | null;
  richiedente: PersonaBreve;
  collega: PersonaBreve | null;
  nuovoInizio: string | null;
  nuovoFine: string | null;
  messaggio: string | null;
  creataIl: string;
  decisaIl: string | null;
  notaDecisione: string | null;
}

export interface NuovaRichiesta {
  tipo: TipoRichiesta;
  turnoId: string;
  collegaId?: string | null;
  turnoCollegaId?: string | null;
  nuovoInizio?: string | null;
  nuovoFine?: string | null;
  messaggio?: string | null;
}

export interface VoceRiepilogo {
  mansione: string;
  euroOra: number | null;
  ore: number;
  compenso: number;
}

export interface RigaRiepilogo {
  persona: PersonaBreve;
  turni: number;
  ore: number;
  compenso: number;
  tariffaMancante: boolean;
  voci: VoceRiepilogo[];
}

export interface Riepilogo {
  mese: string;
  righe: RigaRiepilogo[];
  totaleOre: number;
  totaleCompenso: number;
  turniScoperti: number;
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
  turniStaff: (da: string, a: string) => richiesta<Turno[]>('GET', `/api/turni/staff?da=${da}&a=${a}`),
  creaTurno: (t: TurnoInput) => richiesta<Turno>('POST', '/api/turni', t),
  modificaTurno: (id: string, t: TurnoInput) => richiesta<Turno>('PUT', `/api/turni/${id}`, t),
  annullaTurno: (id: string) => richiesta<Turno>('POST', `/api/turni/${id}/annulla`),
  postazioni: () => richiesta<Postazione[]>('GET', '/api/staff/postazioni'),
  personale: () => richiesta<PersonaBreve[]>('GET', '/api/staff/persone'),
  colleghi: () => richiesta<PersonaBreve[]>('GET', '/api/staff/colleghi'),
  modelli: () => richiesta<Modello[]>('GET', '/api/modelli'),
  generaMese: (modelloId: string, mese: string) => richiesta<EsitoGenerazione>('POST', `/api/modelli/${modelloId}/genera`, { mese }),
  creaRichiesta: (r: NuovaRichiesta) => richiesta<Richiesta>('POST', '/api/richieste', r),
  richiesteMie: () => richiesta<Richiesta[]>('GET', '/api/richieste/mie'),
  richiesteAperte: () => richiesta<Richiesta[]>('GET', '/api/richieste/aperte'),
  richiesteDaApprovare: () => richiesta<Richiesta[]>('GET', '/api/richieste'),
  accetta: (id: string) => richiesta<Richiesta>('POST', `/api/richieste/${id}/accetta`),
  rifiuta: (id: string) => richiesta<Richiesta>('POST', `/api/richieste/${id}/rifiuta`),
  annullaRichiesta: (id: string) => richiesta<Richiesta>('POST', `/api/richieste/${id}/annulla`),
  approva: (id: string, nota?: string) => richiesta<Richiesta>('POST', `/api/richieste/${id}/approva`, { nota: nota ?? null }),
  respingi: (id: string, nota?: string) => richiesta<Richiesta>('POST', `/api/richieste/${id}/respingi`, { nota: nota ?? null }),
  riepilogoMio: (mese: string) => richiesta<Riepilogo>('GET', `/api/riepilogo/mio?mese=${mese}`),
  riepilogo: (mese: string) => richiesta<Riepilogo>('GET', `/api/riepilogo?mese=${mese}`),
  urlCsv: (mese: string) => `/api/riepilogo/csv?mese=${mese}`,
};

/** Messaggio leggibile per un errore qualsiasi delle API. */
export function messaggioErrore(e: unknown, predefinito = 'Operazione non riuscita.'): string {
  return e instanceof ErroreApi ? e.message : predefinito;
}

export const TIPI_RICHIESTA: Record<TipoRichiesta, string> = {
  CAMBIO_ORARIO: 'Cambio orario',
  SOSTITUZIONE: 'Sostituzione',
  SCAMBIO: 'Scambio',
};

export const STATI_RICHIESTA: Record<StatoRichiesta, string> = {
  IN_ATTESA_COLLEGA: 'Attende un collega',
  IN_ATTESA_APPROVAZIONE: 'Da approvare',
  APPROVATA: 'Approvata',
  RESPINTA: 'Respinta',
  ANNULLATA: 'Annullata',
};
