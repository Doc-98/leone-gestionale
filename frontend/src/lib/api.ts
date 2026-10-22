/**
 * Client delle API del backend. Sessione con cookie HttpOnly (gestito dal browser);
 * per le richieste che modificano dati si rimanda il token CSRF letto dal cookie XSRF-TOKEN.
 */

/** Codice di un ruolo: i quattro di sistema o uno creato dagli admin. */
export type Ruolo = string;

/** Cosa può fare un ruolo (ADR 0010). L'ambito per impianto lo controlla il server. */
export type Permesso =
  | 'TURNI' | 'PIANIFICAZIONE' | 'APPROVAZIONI' | 'ORE_STAFF' | 'ANAGRAFICHE_STAFF'
  | 'PIANO_MODIFICA' | 'ISCRIZIONI' | 'PRESENZE'
  | 'CLIENTI' | 'BRACCIALETTI' | 'GOLDEN_PASS'
  | 'REGOLE_TORNELLO' | 'RUOLI' | 'ACCOUNT';

export interface Me {
  accountId: string;
  personaId: string;
  email: string;
  nome: string;
  cognome: string;
  ruoli: { ruolo: Ruolo; impiantoId: string | null }[];
  permessi: Permesso[];
  admin: boolean;
  /** password temporanea: prima di tutto va cambiata */
  deveCambiarePassword: boolean;
}

export interface Account {
  id: string;
  personaId: string;
  nome: string;
  cognome: string;
  email: string;
  attivo: boolean;
  deveCambiarePassword: boolean;
  ultimoAccesso: string | null;
  creatoIl: string;
  ruoli: { ruolo: string; impiantoId: string | null }[];
}

/** La password temporanea si vede una volta sola. */
export interface PasswordTemporanea {
  accountId: string;
  email: string;
  password: string;
}

export type TipoScadenza = 'CERTIFICATO' | 'CERTIFICATO_MANCANTE' | 'ABBONAMENTO' | 'CARNET' | 'TESSERAMENTO' | 'TESSERAMENTO_MANCANTE';

export interface VoceScadenza {
  tipo: TipoScadenza;
  personaId: string;
  nome: string;
  cognome: string;
  eta: number | null;
  descrizione: string;
  corso: string | null;
  stagione: string | null;
  scadenza: string | null;
  /** giorni alla scadenza: negativo = già scaduto; null = senza data */
  giorni: number | null;
  riferimento: string;
  email: string | null;
  ultimoPromemoria: string | null;
}

export interface Scadenze {
  oggi: string;
  entro: number;
  certificati: VoceScadenza[];
  abbonamenti: VoceScadenza[];
  tesseramento: VoceScadenza[];
  postaConfigurata: boolean;
}

export type StatoEmail = 'INVIATA' | 'NON_INVIATA' | 'SENZA_EMAIL' | 'ERRORE';

export interface EmailBreve {
  id: string;
  personaId: string | null;
  destinatario: string | null;
  oggetto: string;
  testo: string;
  stato: StatoEmail;
  errore: string | null;
  creataIl: string;
}

export type TipoPromemoria = 'CERTIFICATO' | 'ABBONAMENTO' | 'TESSERAMENTO' | 'TESSERAMENTO_MANCANTE';

export interface RegolaPromemoria {
  id: string;
  tipo: TipoPromemoria;
  giorni: number;
  maxInvii: number | null;
  attivo: boolean;
  oggetto: string;
  testo: string;
}

export interface PaginaPromemoria {
  regole: RegolaPromemoria[];
  ultimeEmail: EmailBreve[];
  postaConfigurata: boolean;
  orario: string;
}

export interface PaginaRuoli {
  permessi: { codice: Permesso; gruppo: string; nome: string; descrizione: string; potere: boolean }[];
  ruoli: {
    codice: string;
    nome: string;
    descrizione: string | null;
    diSistema: boolean;
    modificabile: boolean;
    permessi: Permesso[];
    persone: { assegnazioneId: string; accountId: string; personaId: string; nome: string; cognome: string; email: string; impiantoId: string | null; dal: string }[];
  }[];
  account: { accountId: string; personaId: string; nome: string; cognome: string; email: string; attivo: boolean }[];
  impianti: { id: string; nome: string }[];
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
  /** per le corsie: in quante corsie è divisa la vasca in questo assetto (5 o 6) */
  assetto: number | null;
}

export interface Occorrenza {
  chiave: string;
  tipo: 'LEZIONE' | 'EVENTO';
  categoria: TipoCorso | TipoEvento;
  /** colore del corso «#RRGGBB», se ce l'ha */
  colore: string | null;
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
  /** turni dei corsi: iscritti attivi in quella data e posti (null = senza limite) */
  iscritti: number | null;
  posti: number | null;
}

/** Una fascia della giornata in una vasca, con l'assetto delle corsie (5 o 6). */
export interface FasciaOraria {
  id: string;
  vascaId: string;
  giornoSettimana: number;
  inizio: string;
  fine: string;
  assetto: number | null;
  validaDal: string;
  validaAl: string | null;
}

export interface Iscritto {
  iscrizioneId: string;
  personaId: string;
  nome: string;
  cognome: string;
  dataNascita: string | null;
  eta: number | null;
  dal: string;
  al: string | null;
  presente: boolean | null;
}

/** Una persona in lista d'attesa per un turno pieno; i contatti sono gli indirizzi a cui va l'avviso. */
export interface VoceAttesa {
  id: string;
  personaId: string;
  nome: string;
  cognome: string;
  dataNascita: string | null;
  eta: number | null;
  aggiuntaIl: string;
  note: string | null;
  avvisataIl: string | null;
  posizione: number;
  contatti: string[];
}
/** Un turno del corso con i posti: liberi negativi = soprannumero; liberoDal = primo giorno con un posto (null = mai). */
export interface TurnoPosti {
  lezioneId: string;
  corsoId: string;
  corso: string;
  etaDa: number | null;
  etaA: number | null;
  giornoSettimana: number;
  inizio: string;
  fine: string;
  posti: number | null;
  occupati: number;
  liberi: number | null;
  liberoDal: string | null;
  inAttesa: number;
  attesa: VoceAttesa[];
}
export interface PaginaAttesa {
  oggi: string;
  societa: string;
  turni: TurnoPosti[];
}
export interface InAttesa {
  attesaId: string;
  lezioneId: string;
  corsoId: string;
  corso: string;
  giornoSettimana: number;
  inizio: string;
  fine: string;
  aggiuntaIl: string;
  posizione: number;
  liberi: number | null;
}
export interface CambioFatto {
  iscrizione: Iscritto;
  corsoPrima: string;
  corsoDopo: string;
  corsoCambiato: boolean;
}
type Chi = { personaId?: string; nuovaPersona?: { nome: string; cognome: string; dataNascita: string | null } };

/** Abbinamento del telefono come lettore NFC del PC (ADR 0017). */
export interface AbbinamentoLettore {
  id: string;
  token: string;
  codice: string;
  scadeIl: string;
}
export interface LettureLettore {
  letture: { numero: number; codice: string; letta: string }[];
  telefonoVistoIl: string | null;
}

export interface ElencoIscritti {
  lezioneId: string;
  corsoId: string;
  corso: string;
  giornoSettimana: number;
  inizio: string;
  fine: string;
  data: string;
  siTiene: boolean;
  posti: number | null;
  iscritti: Iscritto[];
  puoSegnarePresenze: boolean;
  puoIscrivere: boolean;
}

export interface PersonaScheda {
  id: string;
  nome: string;
  cognome: string;
  dataNascita: string | null;
}

export interface Piano {
  da: string;
  a: string;
  spazi: SpazioPiano[];
  fasce: FasciaOraria[];
  occorrenze: Occorrenza[];
  impiantiGestiti: string[];
  /** dove si gestiscono le iscrizioni (segreteria, coordinatori) */
  impiantiIscrizioni: string[];
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
  posti: number | null;
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
  colore: string | null;
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
  colore: string | null;
}

export interface LezioneInput {
  giornoSettimana: number;
  inizio: string;
  fine: string;
  validaDal: string;
  validaAl: string | null;
  spazi: string[];
  istruttoreId: string | null;
  posti: number | null;
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
  fasce: () => richiesta<FasciaOraria[]>('GET', '/api/fasce'),
  creaFasce: (f: { vascaId: string; giorni: number[]; inizio: string; fine: string; assetto: number | null; validaDal: string; validaAl: string | null }) =>
    richiesta<FasciaOraria[]>('POST', '/api/fasce', f),
  modificaFascia: (id: string, f: { inizio: string; fine: string; validaDal: string; validaAl: string | null }) =>
    richiesta<{ fascia: FasciaOraria; turniAggiornati: number }>('PUT', `/api/fasce/${id}`, f),
  cambiaAssetto: (id: string, assetto: number) => richiesta<{ fascia: FasciaOraria; turniAggiornati: number }>('POST', `/api/fasce/${id}/assetto`, { assetto }),
  eliminaFascia: (id: string) => richiesta<void>('DELETE', `/api/fasce/${id}`),
  iscritti: (lezioneId: string, data: string) => richiesta<ElencoIscritti>('GET', `/api/lezioni/${lezioneId}/iscritti?data=${data}`),
  iscrivi: (lezioneId: string, i: { personaId?: string; nuovaPersona?: { nome: string; cognome: string; dataNascita: string | null }; dal: string; forza?: boolean }) =>
    richiesta<Iscritto>('POST', `/api/lezioni/${lezioneId}/iscritti`, i),
  terminaIscrizione: (id: string, al: string) => richiesta<void>('POST', `/api/iscrizioni/${id}/termina`, { al }),
  abbinaLettore: () => richiesta<AbbinamentoLettore>('POST', '/api/lettori', {}),
  lettureLettore: (id: string, dopo: number) => richiesta<LettureLettore>('GET', `/api/lettori/${id}/letture?dopo=${dopo}`),
  scollegaLettore: (id: string) => richiesta<void>('DELETE', `/api/lettori/${id}`),
  tokenLettore: (codice: string) => richiesta<{ token: string }>('GET', `/api/lettori/codice/${encodeURIComponent(codice)}`),
  telefonoPresente: (token: string) => richiesta<void>('POST', `/api/lettori/telefono/${token}/presente`, {}),
  inviaLettura: (token: string, codice: string) => richiesta<void>('POST', `/api/lettori/telefono/${token}/letture`, { codice }),
  cambiaTurno: (iscrizioneId: string, c: { lezioneId: string; dal: string; forza?: boolean }) =>
    richiesta<CambioFatto>('POST', `/api/iscrizioni/${iscrizioneId}/cambia`, c),
  paginaAttesa: () => richiesta<PaginaAttesa>('GET', '/api/attesa'),
  turniDelCorso: (corsoId: string) => richiesta<TurnoPosti[]>('GET', `/api/corsi/${corsoId}/turni`),
  attesaDelTurno: (lezioneId: string) => richiesta<VoceAttesa[]>('GET', `/api/lezioni/${lezioneId}/attesa`),
  aggiungiAttesa: (lezioneId: string, a: Chi & { note?: string | null }) => richiesta<VoceAttesa>('POST', `/api/lezioni/${lezioneId}/attesa`, a),
  iscriviDaAttesa: (attesaId: string, i: { dal: string; forza?: boolean }) => richiesta<Iscritto>('POST', `/api/attesa/${attesaId}/iscrivi`, i),
  rinunciaAttesa: (attesaId: string) => richiesta<void>('POST', `/api/attesa/${attesaId}/rinuncia`, {}),
  avvisaAttesa: (attesaId: string, a: { oggetto: string; testo: string }) =>
    richiesta<{ emailId: string; stato: StatoEmail; errore: string | null }>('POST', `/api/attesa/${attesaId}/avvisa`, a),
  segnaPresenze: (lezioneId: string, data: string, presenze: { personaId: string; presente: boolean }[]) =>
    richiesta<ElencoIscritti>('PUT', `/api/lezioni/${lezioneId}/presenze`, { data, presenze }),
  cercaPersone: (q: string) => richiesta<PersonaScheda[]>('GET', `/api/persone?q=${encodeURIComponent(q)}`),
  account: () => richiesta<Account[]>('GET', '/api/account'),
  creaAccount: (a: { personaId?: string; nome?: string; cognome?: string; email: string; ruolo: string | null; impiantoId: string | null }) =>
    richiesta<PasswordTemporanea>('POST', '/api/account', a),
  reimpostaPassword: (id: string) => richiesta<PasswordTemporanea>('POST', `/api/account/${id}/password`, {}),
  disattivaAccount: (id: string) => richiesta<void>('POST', `/api/account/${id}/disattiva`, {}),
  riattivaAccount: (id: string) => richiesta<void>('POST', `/api/account/${id}/riattiva`, {}),
  emailAccount: (id: string, email: string) => richiesta<void>('PUT', `/api/account/${id}/email`, { email }),
  cambiaPassword: (attuale: string, nuova: string) => richiesta<void>('PUT', '/api/me/password', { attuale, nuova }),
  scadenze: (entro: number) => richiesta<Scadenze>('GET', `/api/scadenze?entro=${entro}`),
  inviaPromemoria: (v: { personaId: string; tipo: TipoScadenza; riferimento: string }) =>
    richiesta<{ emailId: string; stato: StatoEmail; errore: string | null }>('POST', '/api/scadenze/promemoria', v),
  promemoria: () => richiesta<PaginaPromemoria>('GET', '/api/promemoria'),
  creaPromemoria: (tipo: TipoPromemoria) => richiesta<RegolaPromemoria>('POST', '/api/promemoria', { tipo }),
  modificaPromemoria: (id: string, r: { giorni: number; maxInvii: number | null; attivo: boolean; oggetto: string; testo: string }) =>
    richiesta<RegolaPromemoria>('PUT', `/api/promemoria/${id}`, r),
  eliminaPromemoria: (id: string) => richiesta<void>('DELETE', `/api/promemoria/${id}`),
  eseguiPromemoria: () => richiesta<{ email: number; inviate: number; nonInviate: number; senzaEmail: number; errori: number }>('POST', '/api/promemoria/esegui', {}),
  contatti: (personaId: string, c: { email: string | null; telefono: string | null }) => richiesta<{ email: string | null; telefono: string | null }>('PUT', `/api/clienti/${personaId}/contatti`, c),
  ruoli: () => richiesta<PaginaRuoli>('GET', '/api/ruoli'),
  creaRuolo: (r: { nome: string; descrizione: string | null; copiaDa: string | null }) => richiesta<{ codice: string }>('POST', '/api/ruoli', r),
  modificaRuolo: (codice: string, r: { nome: string; descrizione: string | null; permessi: Permesso[] }) => richiesta<void>('PUT', `/api/ruoli/${codice}`, r),
  eliminaRuolo: (codice: string) => richiesta<void>('DELETE', `/api/ruoli/${codice}`),
  assegnaRuolo: (codice: string, accountId: string, impiantoId: string | null) =>
    richiesta<{ id: string }>('POST', `/api/ruoli/${codice}/assegnazioni`, { accountId, impiantoId }),
  togliRuolo: (assegnazioneId: string) => richiesta<void>('DELETE', `/api/assegnazioni/${assegnazioneId}`),
  passa: (codice: string, verso: Verso = 'ENTRATA') => richiesta<EsitoTornello>('POST', '/api/accessi/passaggi', { codice, verso }),
  segnalazioni: (dopo: string) => richiesta<Passaggio[]>('GET', `/api/accessi/segnalazioni?dopo=${encodeURIComponent(dopo)}`),
  autorizza: (personaId: string, a: { tipo: Autorizzazione['tipo']; firmataDa: string | null; firmataIl: string | null; dal: string | null; al: string | null; note: string | null }) =>
    richiesta<{ id: string }>('POST', `/api/accessi/persone/${personaId}/autorizzazioni`, a),
  revocaAutorizzazione: (id: string) => richiesta<void>('POST', `/api/accessi/autorizzazioni/${id}/revoca`, {}),
  sospendi: (abbonamentoId: string, s: { dal: string; al: string; motivo: string | null }) => richiesta<{ id: string }>('POST', `/api/clienti/abbonamenti/${abbonamentoId}/sospensioni`, s),
  annullaSospensione: (id: string) => richiesta<void>('POST', `/api/clienti/sospensioni/${id}/annulla`, {}),
  passaggi: (quanti = 20) => richiesta<Passaggio[]>('GET', `/api/accessi/passaggi?quanti=${quanti}`),
  schedaAccessi: (personaId: string) => richiesta<SchedaAccessi>('GET', `/api/accessi/persone/${personaId}`),
  assegnaBraccialetto: (personaId: string, codice: string, note?: string) =>
    richiesta<Braccialetto>('POST', `/api/accessi/persone/${personaId}/braccialetti`, { codice, note: note ?? null }),
  statoBraccialetto: (id: string, stato: StatoBraccialetto) => richiesta<void>('PUT', `/api/accessi/braccialetti/${id}/stato`, { stato }),
  banna: (b: { tipo?: 'BAN' | 'SORVEGLIANZA'; personaId?: string; braccialettoId?: string; motivo: string; al: string | null }) => richiesta<{ id: string }>('POST', '/api/accessi/ban', b),
  revocaBan: (id: string) => richiesta<void>('POST', `/api/accessi/ban/${id}/revoca`, {}),
  goldenPass: (personaId: string, motivo: string, al: string | null) =>
    richiesta<{ id: string }>('POST', `/api/accessi/persone/${personaId}/golden-pass`, { motivo, al }),
  revocaGoldenPass: (id: string) => richiesta<void>('POST', `/api/accessi/golden-pass/${id}/revoca`, {}),
  regole: () => richiesta<RegolaAccesso[]>('GET', '/api/accessi/regole'),
  modificaRegola: (codice: string, attiva: boolean, parametri: Record<string, number | null>) =>
    richiesta<void>('PUT', `/api/accessi/regole/${codice}`, { attiva, parametri }),
  schedaCliente: (personaId: string) => richiesta<SchedaCliente>('GET', `/api/clienti/${personaId}`),
  nuovoCliente: (c: { nome: string; cognome: string; dataNascita: string | null }) => richiesta<SchedaCliente['persona']>('POST', '/api/clienti', c),
  aggiornaAnagrafica: (personaId: string, a: ModificaAnagrafica) => richiesta<Anagrafica>('PUT', `/api/clienti/${personaId}/anagrafica`, a),
  aggiungiTutore: (personaId: string, t: NuovoTutore) => richiesta<{ id: string }>('POST', `/api/clienti/${personaId}/tutori`, t),
  modificaLegame: (legameId: string, l: { relazione: Relazione; comunicazioni: boolean; delegatoRitiro: boolean }) =>
    richiesta<void>('PUT', `/api/clienti/legami/${legameId}`, l),
  chiudiLegame: (legameId: string) => richiesta<void>('POST', `/api/clienti/legami/${legameId}/chiudi`, {}),
  nuovoConsenso: (personaId: string, c: NuovoConsenso) => richiesta<{ id: string }>('POST', `/api/clienti/${personaId}/consensi`, c),
  revocaConsenso: (id: string) => richiesta<void>('POST', `/api/clienti/consensi/${id}/revoca`, {}),
  nuovoCertificato: (personaId: string, c: { tipo: string; rilasciatoIl: string | null; scadenza: string; note?: string | null; firmatoDa?: string | null }) =>
    richiesta<{ id: string }>('POST', `/api/clienti/${personaId}/certificati`, c),
  nuovoTesseramento: (personaId: string, t: { stagione?: string; dal?: string; al?: string; importo?: number | null; pagatoIl?: string | null }) =>
    richiesta<{ id: string }>('POST', `/api/clienti/${personaId}/tesseramenti`, t),
  nuovoAbbonamento: (personaId: string, a: AbbonamentoInput) => richiesta<{ id: string }>('POST', `/api/clienti/${personaId}/abbonamenti`, a),
  terminaAbbonamento: (id: string, al: string) => richiesta<void>('POST', `/api/clienti/abbonamenti/${id}/termina`, { al }),
  annullaVoce: (cosa: 'certificati' | 'tesseramenti' | 'abbonamenti', id: string) => richiesta<void>('POST', `/api/clienti/${cosa}/${id}/annulla`, {}),
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

// ── Clienti e tornello ─────────────────────────────────────────────────────

/** Perché il tornello dà rosso: codice stabile, messaggio per il tornellista, cosa fare. */
export interface Motivo {
  codice: string;
  regola: string;
  messaggio: string;
  rimedio: string;
  blocca: boolean;
}

export interface PersonaEsito {
  id: string;
  nome: string;
  cognome: string;
  eta: number | null;
  braccialettiAttivi: number;
}

export interface EsitoTornello {
  /** null quando è una simulazione (stato «adesso» nella scheda) */
  passaggioId: string | null;
  istante: string;
  codice: string | null;
  verso: Verso;
  verde: boolean;
  persona: PersonaEsito | null;
  statoBraccialetto: StatoBraccialetto | null;
  goldenPass: boolean;
  motivi: Motivo[];
  carnet: { abbonamentoId: string; ingressi: number; usati: number; scalato: boolean; rientro: boolean } | null;
}

export type Verso = 'ENTRATA' | 'USCITA';

export interface Passaggio {
  id: string;
  istante: string;
  codice: string;
  verso: Verso;
  verde: boolean;
  goldenPass: boolean;
  /** persona sotto controllo: la segreteria riceve un avviso */
  segnalato: boolean;
  persona: PersonaEsito | null;
  motivi: Motivo[];
  carnetScalato: boolean;
}

export type StatoBraccialetto = 'ATTIVO' | 'SMARRITO' | 'DISATTIVATO';

export interface Braccialetto {
  id: string;
  codice: string;
  /** come nel gestionale attuale (decimale a 10 cifre), per i seriali di 4 byte */
  decimale: string | null;
  stato: StatoBraccialetto;
  statoDal: string;
  assegnatoIl: string;
  note: string | null;
  bannato: boolean;
}

export interface Ban {
  id: string;
  /** BAN blocca; SORVEGLIANZA («sotto controllo») fa passare e avvisa la segreteria */
  tipo: 'BAN' | 'SORVEGLIANZA';
  personaId: string | null;
  braccialettoId: string | null;
  codice: string | null;
  motivo: string;
  dal: string;
  al: string | null;
  revocatoIl: string | null;
  attivo: boolean;
}

export interface GoldenPass {
  id: string;
  motivo: string | null;
  dal: string;
  al: string | null;
  revocatoIl: string | null;
  attivo: boolean;
}

export interface Autorizzazione {
  id: string;
  tipo: 'USCITA_AUTONOMA' | 'ACCOMPAGNATORE';
  firmataDa: string | null;
  firmataIl: string | null;
  dal: string;
  al: string | null;
  note: string | null;
  revocataIl: string | null;
  attiva: boolean;
}

export interface SchedaAccessi {
  braccialetti: Braccialetto[];
  blocchi: Ban[];
  goldenPass: GoldenPass[];
  autorizzazioni: Autorizzazione[];
  passaggi: Passaggio[];
  adesso: EsitoTornello;
}

export interface Certificato {
  id: string;
  tipo: 'NON_AGONISTICO' | 'AGONISTICO' | 'PROVVISORIO';
  rilasciatoIl: string | null;
  scadenza: string;
  note: string | null;
  firmatoDa: string | null;
  valido: boolean;
}

export interface Tesseramento {
  id: string;
  stagione: string;
  dal: string;
  al: string;
  importo: number | null;
  pagatoIl: string | null;
  valido: boolean;
}

export interface Abbonamento {
  id: string;
  tipo: 'CORSO' | 'CARNET';
  corsoId: string | null;
  corso: string | null;
  volteASettimana: number | null;
  ingressi: number | null;
  usati: number;
  residui: number | null;
  ultimoIngresso: string | null;
  dal: string;
  al: string | null;
  /** la fine allungata dei giorni di sospensione */
  alEffettivo: string | null;
  sospensioni: { id: string; dal: string; al: string; motivo: string | null }[];
  sospesoOggi: boolean;
  importo: number | null;
  note: string | null;
  valido: boolean;
}

export interface TurnoFrequentato {
  iscrizioneId: string;
  lezioneId: string;
  corsoId: string;
  corso: string;
  giornoSettimana: number;
  inizio: string;
  fine: string;
  dal: string;
  al: string | null;
}

export interface SchedaCliente {
  persona: { id: string; nome: string; cognome: string; dataNascita: string | null; eta: number | null };
  anagrafica: Anagrafica;
  contatti: { email: string | null; telefono: string | null };
  famiglia: { tutori: Familiare[]; assistiti: Familiare[]; fratelli: Familiare[]; destinatari: string[] };
  consensi: Consenso[];
  email: EmailBreve[];
  certificati: Certificato[];
  tesseramenti: Tesseramento[];
  abbonamenti: Abbonamento[];
  turni: TurnoFrequentato[];
  attesa: InAttesa[];
  stagioneProposta: { stagione: string; dal: string; al: string; importo: number };
}

export type Sesso = 'M' | 'F';
export interface ModificaAnagrafica {
  nome: string;
  cognome: string;
  dataNascita: string | null;
  sesso: Sesso | null;
  luogoNascita: string | null;
  codiceFiscale: string | null;
  indirizzo: string | null;
  cap: string | null;
  citta: string | null;
  provincia: string | null;
  nazione: string | null;
  note: string | null;
}
export interface Anagrafica extends ModificaAnagrafica {
  id: string;
  email: string | null;
  telefono: string | null;
}

export type Relazione = 'MADRE' | 'PADRE' | 'TUTORE' | 'NONNO' | 'ALTRO';
/** Un tutore o un assistito o un fratello, con i dati del legame (per i fratelli: quello con il tutore in comune). */
export interface Familiare {
  legameId: string;
  personaId: string;
  nome: string;
  cognome: string;
  dataNascita: string | null;
  relazione: Relazione;
  comunicazioni: boolean;
  delegatoRitiro: boolean;
  email: string | null;
  telefono: string | null;
}
export interface NuovoTutore {
  tutoreId?: string | null;
  nome?: string;
  cognome?: string;
  codiceFiscale?: string | null;
  email?: string | null;
  telefono?: string | null;
  relazione: Relazione;
  comunicazioni: boolean;
  delegatoRitiro: boolean;
}

export type TipoConsenso = 'TRATTAMENTO' | 'MARKETING' | 'IMMAGINI';
export interface Consenso {
  id: string;
  tipo: TipoConsenso;
  firmatoDa: string | null;
  datoIl: string;
  validoFino: string | null;
  email: boolean;
  sms: boolean;
  cartaceo: boolean;
  revocatoIl: string | null;
  valido: boolean;
}
export interface NuovoConsenso {
  tipo: TipoConsenso;
  firmatoDa: string | null;
  datoIl: string | null;
  validoFino: string | null;
  email: boolean;
  sms: boolean;
  cartaceo: boolean;
}

export interface ParametroRegola {
  nome: string;
  etichetta: string;
  unita: string;
  valore: number | null;
  predefinito: number | null;
  facoltativo: boolean;
  seVuoto: string | null;
  max: number;
}

export interface RegolaAccesso {
  codice: string;
  nome: string;
  descrizione: string;
  ordine: number;
  valeConGoldenPass: boolean;
  attiva: boolean;
  parametri: ParametroRegola[];
  modificataIl: string | null;
}

export interface AbbonamentoInput {
  tipo: 'CORSO' | 'CARNET';
  corsoId?: string | null;
  volteASettimana?: number | null;
  ingressi?: number | null;
  dal: string;
  al: string | null;
  importo?: number | null;
  note?: string | null;
}

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
