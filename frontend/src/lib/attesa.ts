import type { EventoAttesa, PaginaAttesa, TurnoPosti, VoceAttesa } from './api';
import { dataBreve, dataDi, GIORNI_BREVI, GIORNI_ESTESI, ora } from './date';

/**
 * Le liste d'attesa (ADR 0015, mockup di ottobre 2026): due viste a scelta, «Posti da assegnare» (i turni dove c'è un
 * posto, con chi tocca e cosa fare) e «Persone in attesa» (una riga per persona, con tutti i turni che aspetta).
 */
export type VistaAttesa = 'posti' | 'persone';
export const VISTE_ATTESA: { id: VistaAttesa; nome: string }[] = [
  { id: 'posti', nome: 'Posti da assegnare' },
  { id: 'persone', nome: 'Persone in attesa' },
];

/** A che punto è una voce: mai avvisata, avvisata (entro i giorni per rispondere), non ha risposto in tempo, saltata. */
export type StatoVoce = 'nuovo' | 'avvisata' | 'scaduto' | 'saltata';

export function statoVoce(v: VoceAttesa, oggi: string): StatoVoce {
  if (v.senzaRispostaIl) return 'saltata';
  if (!v.avvisataIl) return 'nuovo';
  if (v.rispostaEntro && v.rispostaEntro < oggi) return 'scaduto';
  return 'avvisata';
}

/** Giorni tra due date ISO (b − a). */
export function giorniTra(a: string, b: string): number {
  return Math.round((Date.parse(b.slice(0, 10)) - Date.parse(a.slice(0, 10))) / 86_400_000);
}

/** Da quanti giorni aspetta. */
export const giorniDiAttesa = (v: VoceAttesa, oggi: string) => Math.max(0, giorniTra(dataDi(v.aggiuntaIl), oggi));

const giornoDellaSettimana = (d: string) => {
  const g = new Date(`${d.slice(0, 10)}T12:00:00`).getDay();
  return GIORNI_ESTESI[g === 0 ? 7 : g];
};

/** «venerdì 30/10», «domani», «oggi» */
export function quandoBreve(d: string, oggi: string): string {
  const n = giorniTra(oggi, d);
  if (n === 0) return 'oggi';
  if (n === 1) return 'domani';
  if (n === -1) return 'ieri';
  return `${giornoDellaSettimana(d)} ${dataBreve(d).slice(0, 5)}`;
}

/** C'è un posto: subito (quanti) o da una data. */
export function postoLibero(t: TurnoPosti, oggi: string): { libero: boolean; testo: string; dal: string } {
  if (!t.liberoDal) return { libero: false, testo: 'Al completo', dal: oggi };
  if (t.liberoDal <= oggi) {
    const n = t.liberi == null ? null : Math.max(1, t.liberi);
    return { libero: true, testo: n == null ? 'Posti liberi' : n === 1 ? '1 posto libero' : `${n} posti liberi`, dal: oggi };
  }
  return { libero: true, testo: `Un posto dal ${dataBreve(t.liberoDal).slice(0, 5)}`, dal: t.liberoDal };
}

export const quandoTurno = (t: Pick<TurnoPosti, 'giornoSettimana' | 'inizio'>) => `${GIORNI_ESTESI[t.giornoSettimana]} ${ora(t.inizio)}`;
export const quandoTurnoBreve = (t: Pick<TurnoPosti, 'giornoSettimana' | 'inizio'>) => `${GIORNI_BREVI[t.giornoSettimana]} ${ora(t.inizio)}`;
export const eta = (n: number | null) => (n == null ? '' : n === 1 ? '1 anno' : `${n} anni`);

/** La riga sotto il nome di chi tocca: a che punto è l'avviso. */
export function notaVoce(v: VoceAttesa, oggi: string): { testo: string; tono: 'muted' | 'warn' | 'cloro' } {
  const s = statoVoce(v, oggi);
  const avvisata = v.avvisataIl ? quandoBreve(dataDi(v.avvisataIl), oggi) : '';
  const come = v.avvisoAVoce ? 'a voce' : 'per email';
  if (s === 'nuovo') return { testo: v.contatti.length ? 'Nessun avviso ancora' : 'Nessun avviso ancora · senza email: va chiamato', tono: 'muted' };
  if (s === 'scaduto') return { testo: `Avviso ${come} ${avvisata} senza risposta: il tempo è finito ${quandoBreve(v.rispostaEntro!, oggi)}`, tono: 'warn' };
  if (s === 'saltata') return { testo: 'Nessuna risposta: il posto è passato al prossimo', tono: 'muted' };
  return { testo: `Avviso ${come} ${avvisata}${v.rispostaEntro ? `, risposta entro ${quandoBreve(v.rispostaEntro, oggi)}` : ''}`, tono: 'muted' };
}

/** Un turno con un posto: chi tocca (il primo che non è stato saltato), chi viene dopo, chi è stato saltato. */
export interface PostoDaAssegnare {
  turno: TurnoPosti;
  posto: ReturnType<typeof postoLibero>;
  tocca: VoceAttesa | null;
  poi: VoceAttesa[];
  saltati: VoceAttesa[];
}

export function postiDaAssegnare(pagina: PaginaAttesa): PostoDaAssegnare[] {
  return pagina.turni
    .map((turno) => ({ turno, posto: postoLibero(turno, pagina.oggi) }))
    .filter((x) => x.posto.libero)
    .map(({ turno, posto }) => {
      const attivi = turno.attesa.filter((v) => !v.senzaRispostaIl);
      return { turno, posto, tocca: attivi[0] ?? null, poi: attivi.slice(1), saltati: turno.attesa.filter((v) => !!v.senzaRispostaIl) };
    })
    .sort((a, b) => a.posto.dal.localeCompare(b.posto.dal) || a.turno.corso.localeCompare(b.turno.corso));
}

export const alCompleto = (pagina: PaginaAttesa) => pagina.turni.filter((t) => !postoLibero(t, pagina.oggi).libero);

/** «Poi: Elena Greco, Franco Neri e altri 2» */
export function testoPoi(poi: VoceAttesa[]): string {
  if (!poi.length) return 'Nessun altro in lista';
  const nomi = poi.slice(0, 2).map((v) => `${v.nome} ${v.cognome}`);
  return `Poi: ${nomi.join(', ')}${poi.length > 2 ? ` e altri ${poi.length - 2}` : ''}`;
}

/** I quattro numeri in alto. */
export function riepilogo(pagina: PaginaAttesa) {
  const posti = postiDaAssegnare(pagina);
  const tutte = pagina.turni.flatMap((t) => t.attesa);
  const persone = new Set(tutte.map((v) => v.personaId));
  const giorni = tutte.map((v) => giorniDiAttesa(v, pagina.oggi));
  const avvisi = posti.map((p) => p.tocca).filter((v): v is VoceAttesa => !!v && ['avvisata', 'scaduto'].includes(statoVoce(v, pagina.oggi)));
  return {
    posti: posti.reduce((n, p) => n + (p.posto.dal === pagina.oggi ? Math.max(1, p.turno.liberi ?? 1) : 1), 0),
    turniConPosto: posti.length,
    persone: persone.size,
    turniPieni: alCompleto(pagina).length,
    avvisi: avvisi.length,
    scaduti: avvisi.filter((v) => statoVoce(v, pagina.oggi) === 'scaduto').length,
    attesaMedia: giorni.length ? Math.round(giorni.reduce((a, b) => a + b, 0) / giorni.length) : 0,
    attesaMassima: giorni.length ? Math.max(...giorni) : 0,
  };
}

// ── Persone in attesa ─────────────────────────────────────────────────────

/** Un turno che la persona aspetta, con la sua posizione e se tocca a lei un posto libero. */
export interface TurnoAspettato {
  turno: TurnoPosti;
  voce: VoceAttesa;
  di: number;
  tocca: boolean;
}

export interface PersonaInAttesa {
  personaId: string;
  nome: string;
  cognome: string;
  eta: number | null;
  contatti: string[];
  /** dal giorno in cui aspetta da più tempo */
  dal: string;
  giorni: number;
  turni: TurnoAspettato[];
}

export function personeInAttesa(pagina: PaginaAttesa): PersonaInAttesa[] {
  const toccano = new Set(postiDaAssegnare(pagina).map((p) => p.tocca?.id).filter(Boolean));
  const perPersona = new Map<string, PersonaInAttesa>();
  for (const turno of pagina.turni)
    for (const voce of turno.attesa) {
      let p = perPersona.get(voce.personaId);
      if (!p) {
        p = { personaId: voce.personaId, nome: voce.nome, cognome: voce.cognome, eta: voce.eta, contatti: voce.contatti, dal: dataDi(voce.aggiuntaIl), giorni: 0, turni: [] };
        perPersona.set(voce.personaId, p);
      }
      if (dataDi(voce.aggiuntaIl) < p.dal) p.dal = dataDi(voce.aggiuntaIl);
      p.turni.push({ turno, voce, di: turno.attesa.length, tocca: toccano.has(voce.id) });
    }
  return [...perPersona.values()].map((p) => ({
    ...p,
    giorni: Math.max(0, giorniTra(p.dal, pagina.oggi)),
    turni: p.turni.sort((a, b) => Number(b.tocca) - Number(a.tocca) || a.voce.posizione - b.voce.posizione),
  }));
}

/** Il turno che conta di più per l'azione: quello dove c'è un posto per lei, altrimenti il primo. */
export const principale = (p: PersonaInAttesa) => p.turni.find((t) => t.tocca) ?? p.turni[0];

export type FiltroPersone = 'tutti' | 'posto' | 'avvisati' | 'lunga';
export const FILTRI_PERSONE: { id: FiltroPersone; nome: string }[] = [
  { id: 'tutti', nome: 'Tutti' },
  { id: 'posto', nome: 'Con un posto' },
  { id: 'avvisati', nome: 'Avvisati' },
  { id: 'lunga', nome: 'Da più di 30 giorni' },
];

export function nelFiltro(p: PersonaInAttesa, f: FiltroPersone, oggi: string): boolean {
  if (f === 'posto') return p.turni.some((t) => t.tocca);
  if (f === 'avvisati') return p.turni.some((t) => ['avvisata', 'scaduto'].includes(statoVoce(t.voce, oggi)));
  if (f === 'lunga') return p.giorni > 30;
  return true;
}

export type OrdinePersone = 'nome' | 'corso' | 'attesa';

/** Le categorie dei corsi nell'ordine «dai più piccoli ai grandi». */
const ORDINE_CATEGORIE = ['ACQUATICITA', 'SCUOLA_NUOTO', 'AGONISMO', 'CURRICOLARE', 'MASTER', 'ACQUAGYM', 'NUOTO_LIBERO', 'ALTRO'];
const rangoCategoria = (t: TurnoPosti) => {
  const i = ORDINE_CATEGORIE.indexOf(t.tipo ?? 'ALTRO');
  return i < 0 ? ORDINE_CATEGORIE.length : i;
};

export function ordinaPersone(persone: PersonaInAttesa[], ordine: OrdinePersone): PersonaInAttesa[] {
  const cognome = (a: PersonaInAttesa, b: PersonaInAttesa) => a.cognome.localeCompare(b.cognome, 'it') || a.nome.localeCompare(b.nome, 'it');
  const attesa = (a: PersonaInAttesa, b: PersonaInAttesa) => b.giorni - a.giorni || cognome(a, b);
  if (ordine === 'nome') return [...persone].sort(cognome);
  if (ordine === 'corso')
    return [...persone].sort((a, b) => {
      const ta = principale(a).turno;
      const tb = principale(b).turno;
      return rangoCategoria(ta) - rangoCategoria(tb) || ta.corso.localeCompare(tb.corso, 'it') || attesa(a, b);
    });
  return [...persone].sort(attesa);
}

/** La riga «da quanto» di una persona: lo stato dell'avviso più importante, o da quando aspetta. */
export function statoPersona(p: PersonaInAttesa, oggi: string): { testo: string; tono: 'muted' | 'warn' | 'cloro' } {
  const t = principale(p);
  const s = statoVoce(t.voce, oggi);
  if (s === 'scaduto') return { testo: `avviso del ${dataBreve(dataDi(t.voce.avvisataIl!)).slice(0, 5)} senza risposta`, tono: 'warn' };
  if (s === 'avvisata') return { testo: `avviso fatto, risposta entro ${quandoBreve(t.voce.rispostaEntro ?? oggi, oggi)}`, tono: 'muted' };
  if (s === 'saltata') return { testo: 'nessuna risposta, resta in lista', tono: 'muted' };
  if (t.tocca) return { testo: 'c\'è un posto: da avvisare', tono: 'cloro' };
  const confermata = p.turni.map((x) => x.voce.confermataIl).filter((x): x is string => !!x).sort().at(-1);
  if (confermata) return { testo: `aspetta ancora (detto il ${dataBreve(dataDi(confermata)).slice(0, 5)})`, tono: 'muted' };
  return { testo: `dal ${dataBreve(p.dal).slice(0, 5)}`, tono: 'muted' };
}

/** L'azione in evidenza sulla riga di una persona (null = nessuna: solo il menu). */
export type AzionePersona = { tipo: 'avvisa' | 'proponi' | 'iscrivi'; testo: string } | null;

export function azionePersona(p: PersonaInAttesa, oggi: string): AzionePersona {
  const t = principale(p);
  if (!t.tocca) return null;
  const s = statoVoce(t.voce, oggi);
  if (s === 'scaduto') return { tipo: 'proponi', testo: 'Proponi al prossimo' };
  if (s === 'avvisata') return { tipo: 'iscrivi', testo: 'Iscrivi' };
  return { tipo: 'avvisa', testo: 'Avvisa' };
}

// ── Testi ─────────────────────────────────────────────────────────────────

export function testoAvviso(v: VoceAttesa, t: TurnoPosti, pagina: PaginaAttesa): { oggetto: string; testo: string } {
  const posto = postoLibero(t, pagina.oggi);
  const dal = posto.dal > pagina.oggi ? ` dal ${dataBreve(posto.dal)}` : '';
  const entro = quandoBreve(aggiungi(pagina.oggi, pagina.giorniRisposta), pagina.oggi);
  return {
    oggetto: `Un posto per ${v.nome} nel corso ${t.corso}`,
    testo: `Buongiorno,\n\nsi è liberato un posto per ${v.nome} nel corso ${t.corso}, ${quandoTurno(t)}${dal}.\n`
      + `Se vi interessa, rispondete a questa email o passate in segreteria entro ${entro}: dopo lo proporremo al prossimo in lista.\n\n`
      + pagina.societa,
  };
}

export function testoConferma(societa: string): { oggetto: string; testo: string } {
  return {
    oggetto: 'Lista d\'attesa: aspettate ancora un posto per {nome}?',
    testo: 'Buongiorno,\n\n{nome} è nella nostra lista d\'attesa. Ci fate sapere se aspettate ancora un posto? Basta rispondere a questa email.\n'
      + 'Se non vi interessa più, lo togliamo dalla lista.\n\n' + societa,
  };
}

function aggiungi(d: string, n: number): string {
  const x = new Date(`${d}T12:00:00`);
  x.setDate(x.getDate() + n);
  return x.toLocaleDateString('sv-SE');
}

const COSA: Record<EventoAttesa['tipo'], string> = {
  AGGIUNTA: 'in lista per',
  AVVISATA: 'avviso per email ·',
  AVVISATA_A_VOCE: 'avviso a voce ·',
  SENZA_RISPOSTA: 'nessuna risposta ·',
  CONFERMA_CHIESTA: 'domanda: aspetta ancora?',
  CONFERMATA: 'conferma che aspetta',
  ISCRITTA: 'iscrizione dalla lista ·',
  RINUNCIA: 'esce dalla lista ·',
};

/** «Sara Neri · avviso per email · Delfino Giallo mar 17:00 · ieri» */
export function descriviEvento(e: EventoAttesa, oggi: string): { chi: string; cosa: string; quando: string } {
  const conCorso = !['CONFERMA_CHIESTA', 'CONFERMATA'].includes(e.tipo);
  return {
    chi: `${e.nome} ${e.cognome}`,
    cosa: conCorso ? `${COSA[e.tipo]} ${e.corso} ${quandoTurnoBreve(e)}` : COSA[e.tipo],
    quando: quandoBreve(dataDi(e.quando), oggi) + (e.da ? ` · ${e.da}` : ''),
  };
}

/** Le azioni che la pagina offre alle due viste (ricaricano la pagina e mostrano l'esito). */
export interface AzioniAttesa {
  iscrivi: (v: VoceAttesa, t: TurnoPosti) => Promise<void>;
  esegui: (azione: () => Promise<unknown>, fatto: string) => Promise<boolean>;
  fatto: (messaggio: string) => void;
}
