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

export interface Impianto {
  id: string;
  nome: string;
}

export interface Spazio {
  id: string;
  impiantoId: string;
  nome: string;
  tipo: string;
}

export interface PostazioneGestione {
  id: string;
  mansioneId: string;
  nome: string;
  spazioId: string | null;
  ordine: number;
  attiva: boolean;
}

export interface Mansione {
  id: string;
  impiantoId: string;
  codice: string;
  nome: string;
  voceFoglioOre: string | null;
  postazioni: PostazioneGestione[];
}

export interface Tariffa {
  id: string;
  mansioneId: string;
  mansione: string;
  persona: PersonaBreve | null;
  euroOra: number;
  dal: string;
  al: string | null;
}

export interface RigaInput {
  postazioneId: string;
  giornoSettimana: number;
  inizio: string;
  fine: string;
  personaId: string | null;
}

export interface ModelloInput {
  impiantoId: string;
  nome: string;
  validoDal: string;
  validoAl: string | null;
  copiaDa?: string | null;
}

// ── Piano vasca ──────────────────────────────────────────────────────────

export type TipoCorso = 'SCUOLA_NUOTO' | 'ACQUATICITA' | 'AGONISMO' | 'MASTER' | 'ACQUAGYM' | 'CURRICOLARE' | 'NUOTO_LIBERO' | 'ALTRO';
export type TipoEvento = 'GARA' | 'LEZIONE_PRIVATA' | 'LEZIONE_PROVA' | 'CHIUSURA' | 'ALTRO';
export type StatoOccorrenza = 'REGOLARE' | 'SPOSTATA_QUI' | 'SALTATA' | 'SPOSTATA_VIA' | 'CHIUSO' | 'ANNULLATO';

export interface SpazioPiano {
  id: string;
  impiantoId: string;
  nome: string;
  tipo: string;
  padreId: string | null;
}

export interface Occorrenza {
  chiave: string;
  tipo: 'LEZIONE' | 'EVENTO';
  categoria: TipoCorso | TipoEvento;
  titolo: string;
  dettaglio: string | null;
  data: string;
  inizio: string;
  fine: string;
  spazi: string[];
  istruttore: PersonaBreve | null;
  stato: StatoOccorrenza;
  nota: string | null;
  lezioneId: string | null;
  corsoId: string | null;
  eventoId: string | null;
  eccezioneId: string | null;
  dataOriginale: string | null;
  conflitti: string[];
}

export interface Piano {
  da: string;
  a: string;
  spazi: SpazioPiano[];
  occorrenze: Occorrenza[];
  impiantiGestiti: string[];
}

export interface Lezione {
  id: string;
  giornoSettimana: number;
  inizio: string;
  fine: string;
  validaDal: string;
  validaAl: string | null;
  spazi: string[];
  istruttore: PersonaBreve | null;
}

export interface Corso {
  id: string;
  impiantoId: string;
  nome: string;
  tipo: TipoCorso;
  livello: string | null;
  etaDa: number | null;
  etaA: number | null;
  istruttore: PersonaBreve | null;
  note: string | null;
  attivo: boolean;
  lezioni: Lezione[];
}

export interface CorsoInput {
  impiantoId: string;
  nome: string;
  tipo: TipoCorso;
  livello: string | null;
  etaDa: number | null;
  etaA: number | null;
  istruttoreId: string | null;
  note: string | null;
  attivo: boolean;
}

export interface LezioneInput {
  giornoSettimana: number;
  inizio: string;
  fine: string;
  validaDal: string;
  validaAl: string | null;
  spazi: string[];
  istruttoreId: string | null;
}

export interface Evento {
  id: string;
  impiantoId: string;
  tipo: TipoEvento;
  titolo: string;
  data: string;
  dataFine: string | null;
  inizio: string;
  fine: string;
  spazi: string[];
  istruttore: PersonaBreve | null;
  note: string | null;
  annullato: boolean;
}

export interface EventoInput {
  impiantoId: string;
  tipo: TipoEvento;
  titolo: string;
  data: string;
  dataFine: string | null;
  inizio: string;
  fine: string;
  spazi: string[];
  istruttoreId: string | null;
  note: string | null;
}

export interface EccezioneInput {
  data: string;
  tipo: 'SALTATA' | 'SPOSTATA';
  nuovaData?: string | null;
  nuovoInizio?: string | null;
  nuovoFine?: string | null;
  motivo?: string | null;
}

/** Dopo una modifica al piano: le sovrapposizioni che si sono create (avvisi, non errori). */
export interface Esito<T> {
  valore: T;
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
  creaModello: (m: ModelloInput) => richiesta<Modello>('POST', '/api/modelli', m),
  modificaModello: (id: string, m: ModelloInput) => richiesta<Modello>('PUT', `/api/modelli/${id}`, m),
  eliminaModello: (id: string) => richiesta<void>('DELETE', `/api/modelli/${id}`),
  aggiungiRiga: (id: string, r: RigaInput) => richiesta<Modello>('POST', `/api/modelli/${id}/righe`, r),
  modificaRiga: (id: string, rigaId: string, r: RigaInput) => richiesta<Modello>('PUT', `/api/modelli/${id}/righe/${rigaId}`, r),
  togliRiga: (id: string, rigaId: string) => richiesta<Modello>('DELETE', `/api/modelli/${id}/righe/${rigaId}`),
  impianti: () => richiesta<Impianto[]>('GET', '/api/staff/impianti'),
  spazi: () => richiesta<Spazio[]>('GET', '/api/staff/spazi'),
  mansioni: () => richiesta<Mansione[]>('GET', '/api/staff/mansioni'),
  creaMansione: (impiantoId: string, nome: string, voceFoglioOre: string | null) =>
    richiesta<Mansione>('POST', '/api/staff/mansioni', { impiantoId, nome, voceFoglioOre }),
  modificaMansione: (id: string, nome: string, voceFoglioOre: string | null) =>
    richiesta<Mansione>('PUT', `/api/staff/mansioni/${id}`, { nome, voceFoglioOre }),
  creaPostazione: (mansioneId: string, nome: string, spazioId: string | null) =>
    richiesta<PostazioneGestione>('POST', `/api/staff/mansioni/${mansioneId}/postazioni`, { nome, spazioId }),
  modificaPostazione: (id: string, nome: string, spazioId: string | null, attiva: boolean) =>
    richiesta<{ postazione: PostazioneGestione; turniFuturi: number }>('PUT', `/api/staff/postazioni/${id}`, { nome, spazioId, attiva }),
  ordinaPostazioni: (ids: string[]) => richiesta<Mansione[]>('PUT', '/api/staff/postazioni/ordine', { ids }),
  tariffe: () => richiesta<Tariffa[]>('GET', '/api/staff/tariffe'),
  nuovaTariffa: (t: { mansioneId: string; personaId: string | null; euroOra: number; dal: string }) => richiesta<Tariffa>('POST', '/api/staff/tariffe', t),
  correggiTariffa: (id: string, euroOra: number, al: string | null) => richiesta<Tariffa>('PUT', `/api/staff/tariffe/${id}`, { euroOra, al }),
  vociFoglioOre: () => richiesta<string[]>('GET', '/api/foglio-ore/voci'),
  urlFoglioOreMio: (mese: string) => `/api/foglio-ore/mio?mese=${mese}`,
  urlFoglioOrePersona: (personaId: string, mese: string) => `/api/foglio-ore/persone/${personaId}?mese=${mese}`,
  urlFogliOreTutti: (mese: string) => `/api/foglio-ore/tutti?mese=${mese}`,
  piano: (da: string, a: string) => richiesta<Piano>('GET', `/api/piano?da=${da}&a=${a}`),
  corsi: () => richiesta<Corso[]>('GET', '/api/corsi'),
  creaCorso: (c: CorsoInput) => richiesta<Corso>('POST', '/api/corsi', c),
  modificaCorso: (id: string, c: CorsoInput) => richiesta<Corso>('PUT', `/api/corsi/${id}`, c),
  aggiungiLezione: (corsoId: string, l: LezioneInput) => richiesta<Esito<Lezione>>('POST', `/api/corsi/${corsoId}/lezioni`, l),
  modificaLezione: (id: string, l: LezioneInput) => richiesta<Esito<Lezione>>('PUT', `/api/lezioni/${id}`, l),
  togliLezione: (id: string) => richiesta<void>('DELETE', `/api/lezioni/${id}`),
  aggiungiEccezione: (lezioneId: string, e: EccezioneInput) => richiesta<Esito<string>>('POST', `/api/lezioni/${lezioneId}/eccezioni`, e),
  togliEccezione: (id: string) => richiesta<void>('DELETE', `/api/eccezioni/${id}`),
  evento: (id: string) => richiesta<Evento>('GET', `/api/eventi/${id}`),
  creaEvento: (e: EventoInput) => richiesta<Esito<Evento>>('POST', '/api/eventi', e),
  modificaEvento: (id: string, e: EventoInput) => richiesta<Esito<Evento>>('PUT', `/api/eventi/${id}`, e),
  annullaEvento: (id: string) => richiesta<Evento>('POST', `/api/eventi/${id}/annulla`),
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
