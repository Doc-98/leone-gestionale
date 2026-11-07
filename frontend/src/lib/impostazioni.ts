/**
 * Le impostazioni (ADR 0024): l'indice per la ricerca, che porta dritti all'impostazione da qualsiasi pagina,
 * e qualche calcolo condiviso dalle pagine (la linea della stagione, i testi delle regole di prezzo).
 */

/** Una voce della ricerca: dove si trova (pagina e, facoltativo, il riquadro) e le parole che la trovano. */
export interface VoceRicerca {
  titolo: string;
  dove: string;
  sezione: string;
  ancora?: string;
  parole: string;
}

export const VOCI_RICERCA: VoceRicerca[] = [
  // La società
  { titolo: 'Nome e dati della società', dove: 'La società › Dati', sezione: 'societa', ancora: 'dati', parole: 'nome ragione sociale partita iva codice fiscale indirizzo email telefono ricevuta' },
  { titolo: 'Stagioni', dove: 'La società › Stagioni', sezione: 'societa', ancora: 'stagioni', parole: 'stagione date inizio fine anno' },
  { titolo: 'Quota associativa', dove: 'La società › Stagioni', sezione: 'societa', ancora: 'stagioni', parole: 'quota tesseramento associativa rimborsabile' },
  { titolo: 'Tesseramento della stagione nuova', dove: 'La società › Stagioni', sezione: 'societa', ancora: 'stagioni', parole: 'tesseramento rinnovo anticipo giorni prima' },
  { titolo: 'Gruppi di clienti', dove: 'La società › Gruppi', sezione: 'societa', ancora: 'gruppi', parole: 'gruppo convenzione alunni scuola squadra etichetta' },
  { titolo: 'Giorni di chiusura', dove: 'La società › Calendario', sezione: 'calendario', parole: 'chiusura chiuso festivo festa vacanze orario ridotto calendario' },
  // Prezzi e pagamenti
  { titolo: 'Listino', dove: 'Prezzi › Listino', sezione: 'listino', parole: 'listino prezzo offerta abbonamento carnet ingressi costo' },
  { titolo: 'Copiare il listino nella stagione nuova', dove: 'Prezzi › Listino', sezione: 'listino', parole: 'copia listino aumento stagione nuova' },
  { titolo: 'Riduzione per iscrizione a stagione iniziata', dove: 'Prezzi › Regole di prezzo', sezione: 'prezzi', ancora: 'riduzione', parole: 'riduzione sconto tardiva iniziata scaglioni' },
  { titolo: 'Recesso', dove: 'Prezzi › Regole di prezzo', sezione: 'prezzi', ancora: 'recesso', parole: 'recesso rimborso disdetta ritiro restituzione' },
  { titolo: 'Rate', dove: 'Prezzi › Regole di prezzo', sezione: 'prezzi', ancora: 'rate', parole: 'rate rata pagamento dilazione scadenza seconda' },
  { titolo: 'Sospensione per malattia', dove: 'Prezzi › Regole di prezzo', sezione: 'prezzi', ancora: 'sospensione', parole: 'sospensione malattia infortunio certificato stop' },
  { titolo: 'Metodi di pagamento', dove: 'Prezzi › Metodi di pagamento', sezione: 'metodi', parole: 'metodo pagamento contanti carta bonifico satispay incassare' },
  { titolo: 'Ricevute: numerazione e serie', dove: 'Prezzi › Ricevute', sezione: 'ricevute', ancora: 'serie', parole: 'ricevuta ricevute numero numerazione serie fattura fatture formato' },
  { titolo: 'Cosa c\'è scritto sulla ricevuta', dove: 'Prezzi › Ricevute', sezione: 'ricevute', ancora: 'scritto', parole: 'ricevuta intestata codice fiscale marca bollo dicitura detrazione firma' },
  { titolo: 'Come si consegna la ricevuta', dove: 'Prezzi › Ricevute', sezione: 'ricevute', ancora: 'consegna', parole: 'ricevuta stampa email pdf figli famiglia dopo pagamento' },
  { titolo: 'Una famiglia paga per più persone', dove: 'Prezzi › Metodi di pagamento', sezione: 'metodi', ancora: 'famiglia', parole: 'famiglia fratelli pagamento unico insieme' },
  // Clienti e accessi
  { titolo: 'Regole del tornello', dove: 'Clienti e accessi › Tornello', sezione: 'regole', parole: 'tornello regole controlli rosso verde accesso' },
  { titolo: 'Certificato medico provvisorio', dove: 'Clienti e accessi › Tornello', sezione: 'regole', parole: 'certificato medico provvisorio scaduto' },
  { titolo: 'Uscita dei minorenni', dove: 'Clienti e accessi › Tornello', sezione: 'regole', parole: 'minorenni uscita genitore permesso' },
  { titolo: 'Cooldown dopo un passaggio', dove: 'Clienti e accessi › Tornello', sezione: 'regole', parole: 'cooldown passaggio amico minuti' },
  { titolo: 'Promemoria email', dove: 'Clienti e accessi › Promemoria', sezione: 'promemoria', parole: 'promemoria email scadenza avviso testo' },
  { titolo: 'Giorni per rispondere alla lista d\'attesa', dove: 'Clienti e accessi › Promemoria', sezione: 'promemoria', ancora: 'attesa', parole: 'attesa lista posto avviso rispondere' },
  { titolo: 'Valore di un credito', dove: 'Clienti e accessi › Phon e docce', sezione: 'servizi', ancora: 'crediti', parole: 'credito crediti valore ricarica minima phon doccia' },
  { titolo: 'Cauzione del braccialetto', dove: 'Clienti e accessi › Phon e docce', sezione: 'servizi', ancora: 'braccialetto', parole: 'cauzione braccialetto deposito riconsegna' },
  { titolo: 'Se il braccialetto si smarrisce', dove: 'Clienti e accessi › Phon e docce', sezione: 'servizi', ancora: 'braccialetto', parole: 'smarrito perso braccialetto crediti' },
  { titolo: 'Credito in euro del borsellino', dove: 'Clienti e accessi › Phon e docce', sezione: 'servizi', ancora: 'euro', parole: 'borsellino credito euro scadenza trasferire restituire' },
  { titolo: 'Servizi: phon e docce', dove: 'Clienti e accessi › Phon e docce', sezione: 'servizi', ancora: 'servizi', parole: 'servizio phon doccia gratis gratuiti minuti' },
  { titolo: 'Macchine e lettori', dove: 'Clienti e accessi › Phon e docce', sezione: 'servizi', ancora: 'macchine', parole: 'macchina dispositivo lettore chiave saldo' },
  // Staff e sistema
  { titolo: 'Mansioni e postazioni', dove: 'Staff › Mansioni, postazioni e tariffe', sezione: 'postazioni', parole: 'mansione postazione spogliatoio atrio foglio ore' },
  { titolo: 'Dove sta il credito', dove: 'Clienti e accessi › Phon e docce', sezione: 'servizi', ancora: 'dove', parole: 'crediti braccialetto memoria macchine collegate rete formato chip' },
  { titolo: 'Tema chiaro o scuro', dove: 'Sistema › Aspetto', sezione: 'aspetto', parole: 'aspetto tema modalità scuro chiaro notte sistema colori' },
  { titolo: 'Ore da coprire a settimana', dove: 'Staff › Mansioni, postazioni e tariffe', sezione: 'postazioni', parole: 'ore settimanali copertura costo personale stima' },
  { titolo: 'Tariffa oraria', dove: 'Staff › Mansioni, postazioni e tariffe', sezione: 'postazioni', parole: 'tariffa compenso ora paga euro eccezione' },
  { titolo: 'Account', dove: 'Sistema › Account e ruoli', sezione: 'account', parole: 'account accesso email disattiva utente' },
  { titolo: 'Password temporanea', dove: 'Sistema › Account e ruoli', sezione: 'account', parole: 'password temporanea reimposta accesso' },
  { titolo: 'Ruoli e permessi', dove: 'Sistema › Account e ruoli', sezione: 'account', ancora: 'ruoli', parole: 'ruolo permesso admin coordinatore segreteria staff' },
];

const piega = (s: string) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');

/** Le voci che contengono tutte le parole cercate (senza badare ad accenti e maiuscole), solo delle pagine visibili. */
export function cercaImpostazioni(q: string, sezioniVisibili: string[], voci: VoceRicerca[] = VOCI_RICERCA, quante = 8): VoceRicerca[] {
  const parole = piega(q).split(/[^a-z0-9]+/).filter(Boolean);
  if (!parole.length) return [];
  // ogni parola cercata deve essere l'inizio di una parola della voce («rate» trova «Rate», non «fratelli»)
  const parti = (v: VoceRicerca) => piega(`${v.titolo} ${v.dove} ${v.parole}`).split(/[^a-z0-9]+/).filter(Boolean);
  return voci
    .filter((v) => sezioniVisibili.includes(v.sezione))
    .map((v) => ({ v, t: parti(v), titolo: piega(v.titolo) }))
    .filter(({ t }) => parole.every((p) => t.some((w) => w.startsWith(p))))
    // prima quelle col titolo che inizia con la parola cercata
    .sort((a, b) => Number(!b.titolo.startsWith(parole[0])) - Number(!a.titolo.startsWith(parole[0])))
    .slice(0, quante)
    .map(({ v }) => v);
}

/** L'indirizzo di una voce: la pagina e, se c'è, il riquadro a cui scorrere. */
export const hrefVoce = (v: VoceRicerca) => `#/${v.sezione}${v.ancora ? `/${v.ancora}` : ''}`;

// ── La linea della stagione ─────────────────────────────────────────────

const giorno = (d: string) => Date.parse(`${d}T12:00:00Z`) / 86400000;

/** Quanti giorni dura la stagione (estremi compresi). */
export const giorniStagione = (dal: string, al: string) => Math.round(giorno(al) - giorno(dal)) + 1;

/** L'indice del giorno nella stagione (0 = il primo), anche fuori dagli estremi. */
export const indiceGiorno = (dal: string, d: string) => Math.round(giorno(d) - giorno(dal));

/** La data dell'indice (inverso di indiceGiorno). */
export function dataIndice(dal: string, i: number): string {
  return new Date((giorno(dal) + i) * 86400000).toISOString().slice(0, 10);
}

/** La posizione di una data sulla linea, in percentuale (al centro del suo giorno, tra 0 e 100). */
export function posizione(dal: string, al: string, d: string): number {
  const n = giorniStagione(dal, al);
  return Math.min(100, Math.max(0, ((indiceGiorno(dal, d) + 0.5) / n) * 100));
}

const MESI_BREVI = ['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago', 'set', 'ott', 'nov', 'dic'];

/** I mesi della stagione, ognuno con dove comincia e quanto è largo sulla linea (in percentuale). */
export function mesiStagione(dal: string, al: string): { nome: string; mese: string; da: number; largo: number; gennaio: boolean }[] {
  const n = giorniStagione(dal, al);
  const out: { nome: string; mese: string; da: number; largo: number; gennaio: boolean }[] = [];
  let y = Number(dal.slice(0, 4));
  let m = Number(dal.slice(5, 7)) - 1;
  for (let k = 0; k < 24; k++) {
    const inizio = `${y}-${String(m + 1).padStart(2, '0')}-01`;
    if (inizio > al) break;
    const ny = m === 11 ? y + 1 : y;
    const nm = (m + 1) % 12;
    const fine = dataIndice(`${ny}-${String(nm + 1).padStart(2, '0')}-01`, -1);
    const a = Math.max(0, indiceGiorno(dal, inizio));
    const b = Math.min(n - 1, indiceGiorno(dal, fine));
    out.push({ nome: MESI_BREVI[m], mese: inizio.slice(0, 7), da: (a / n) * 100, largo: ((b - a + 1) / n) * 100, gennaio: m === 0 });
    y = ny;
    m = nm;
  }
  return out;
}

// ── Regole di prezzo ────────────────────────────────────────────────────

/** Uno scaglione: da quel giorno (riduzione) o fino a quel giorno (recesso) vale la percentuale. */
export interface Scaglione { data: string; percentuale: number }

/** La riduzione per chi si iscrive quel giorno: la percentuale dell'ultimo scaglione già cominciato. */
export function riduzioneDel(scaglioni: Scaglione[], d: string): number {
  return [...scaglioni].sort((a, b) => a.data.localeCompare(b.data)).filter((s) => s.data <= d).at(-1)?.percentuale ?? 0;
}

/** Il rimborso per chi recede quel giorno: il primo scaglione non ancora finito (null se sono finiti tutti). */
export function recessoDel(scaglioni: Scaglione[], d: string): Scaglione | null {
  return [...scaglioni].sort((a, b) => a.data.localeCompare(b.data)).find((s) => d <= s.data) ?? null;
}

/**
 * Come si paga a rate iscrivendosi quel giorno, con le stesse regole del server: la prima rata all'iscrizione,
 * il resto in parti uguali; le rate già scadute si pagano subito con la prima; se scadono tutte, niente rate.
 */
export function rateDel(totale: number, numero: number, primaPercentuale: number, scadenze: (string | null)[], d: string):
  { subito: number; dopo: { importo: number; scadenza: string | null }[] } {
  if (numero < 2) return { subito: totale, dopo: [] };
  const prima = Math.round(totale * primaPercentuale) / 100;
  const resto = Math.round((totale - prima) * 100) / 100;
  const parte = Math.floor((resto / (numero - 1)) * 100) / 100;
  const rate = Array.from({ length: numero - 1 }, (_, i) => ({
    importo: i === numero - 2 ? Math.round((resto - parte * (numero - 2)) * 100) / 100 : parte,
    scadenza: scadenze[i] || null,
  }));
  const scadute = rate.filter((r) => r.scadenza && r.scadenza < d);
  const dopo = rate.filter((r) => !r.scadenza || r.scadenza >= d);
  if (!dopo.length) return { subito: totale, dopo: [] };
  return { subito: Math.round((prima + scadute.reduce((s, r) => s + r.importo, 0)) * 100) / 100, dopo };
}

/** «2 × 260 €», «1 × 520 €» o «300 € + 1 × 110 €»: come si legge il piano delle rate. */
export function testoRate(r: { subito: number; dopo: { importo: number }[] }, euro: (n: number) => string): string {
  if (!r.dopo.length) return `1 × ${euro(r.subito)}`;
  const uguali = r.dopo.every((x) => Math.abs(x.importo - r.subito) < 0.01);
  return uguali ? `${r.dopo.length + 1} × ${euro(r.subito)}` : `${euro(r.subito)} + ${r.dopo.length} × ${euro(r.dopo[0].importo)}`;
}

// ── Novità dopo un aggiornamento ────────────────────────────────────────

/** Un avviso chiuso non ricompare più per quella persona, su quel dispositivo. */
export function novitaChiusa(chiave: string, utente: string): boolean {
  try {
    return localStorage.getItem(`novita:${chiave}:${utente}`) === '1';
  } catch {
    return false;
  }
}

export function chiudiNovita(chiave: string, utente: string) {
  try {
    localStorage.setItem(`novita:${chiave}:${utente}`, '1');
  } catch {
    // senza memoria del browser l'avviso si richiude a ogni visita
  }
}
