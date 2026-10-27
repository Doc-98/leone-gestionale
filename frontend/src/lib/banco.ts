import type { EsitoTornello, GiornataBanco, Motivo, Passaggio, TurnoAlBanco } from './api';
import { oraDi } from './date';
import type { NomeIcona } from './icone';

/**
 * Il banco del tornello: le scelte di vista e le funzioni che trasformano la giornata (passaggi, turni di oggi, da
 * sistemare) in quello che si vede. Tutto puro, per poterlo provare.
 */

/** Come mostrare «Passaggi di oggi»: tessere, tabella con i filtri, raggruppati per corso, linea del tempo. */
export type VistaPassaggi = 'tessere' | 'tabella' | 'corsi' | 'tempo';
export const VISTE_PASSAGGI: { id: VistaPassaggi; nome: string }[] = [
  { id: 'tessere', nome: 'Tessere' },
  { id: 'tabella', nome: 'Tabella' },
  { id: 'corsi', nome: 'Per corso' },
  { id: 'tempo', nome: 'Linea del tempo' },
];

/** Come mostrare «Da sistemare»: schede con il rimedio, righe con l'azione, pillole, colonne per motivo. */
export type VistaSistemare = 'schede' | 'righe' | 'pillole' | 'motivi';
export const VISTE_SISTEMARE: { id: VistaSistemare; nome: string }[] = [
  { id: 'schede', nome: 'Schede' },
  { id: 'righe', nome: 'Righe' },
  { id: 'pillole', nome: 'Pillole' },
  { id: 'motivi', nome: 'Per motivo' },
];

export type DaSistemare = GiornataBanco['daSistemare'][number];
export type Sistemato = GiornataBanco['sistemati'][number];

/** Il tipo di problema di chi è fuori, dal primo motivo che blocca: decide icona, colonna e azione. */
export type Problema = 'certificato' | 'abbonamento' | 'braccialetto' | 'altro';
export const PROBLEMI: { id: Problema; nome: string; icona: NomeIcona }[] = [
  { id: 'certificato', nome: 'Certificati', icona: 'certificato' },
  { id: 'abbonamento', nome: 'Abbonamenti e quote', icona: 'euro' },
  { id: 'braccialetto', nome: 'Braccialetti', icona: 'braccialetto' },
  { id: 'altro', nome: 'Orari e altro', icona: 'avviso' },
];

export function problema(motivi: Motivo[]): Problema {
  const m = motivi.find((x) => x.blocca) ?? motivi[0];
  switch (m?.regola) {
    case 'CERTIFICATO': return 'certificato';
    case 'ABBONAMENTO':
    case 'TESSERAMENTO': return 'abbonamento';
    case 'BRACCIALETTO': return 'braccialetto';
    default: return 'altro';
  }
}

/** Il motivo in breve (il primo che blocca), per pillole e righe. */
export function motivoBreve(motivi: Motivo[]): string {
  return (motivi.find((x) => x.blocca) ?? motivi[0])?.messaggio ?? '';
}

/** Cosa fare per chi è fuori: il nome dell'azione e dove porta (la scheda del cliente, o i clienti per assegnare il braccialetto). */
export function rimedio(d: { persona: { id: string } | null; motivi: Motivo[] }): { azione: string; href: string } {
  if (!d.persona) return { azione: 'Assegnalo a un cliente', href: '#/clienti' };
  const href = `#/clienti/${d.persona.id}`;
  switch (problema(d.motivi)) {
    case 'certificato': return { azione: 'Registra il certificato', href };
    case 'abbonamento': return { azione: d.motivi.some((m) => m.regola === 'TESSERAMENTO') ? 'Tessera e rinnova' : "Rinnova l'abbonamento", href };
    case 'braccialetto': return { azione: 'Controlla il braccialetto', href };
    default: return { azione: 'Apri la scheda', href };
  }
}

/** «da 7 min», «da 1 h 20 min»: da quanto tempo. */
export function daQuanto(istante: string, adesso: Date): string {
  const min = Math.max(0, Math.floor((adesso.getTime() - new Date(istante).getTime()) / 60000));
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  return min % 60 ? `${h} h ${min % 60} min` : `${h} h`;
}

const minutiDi = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));

/** Il turno di oggi a cui si riferisce un passaggio: quello in corso o il prossimo all'ora del passaggio, altrimenti l'ultimo. */
export function turnoDelPassaggio(turni: TurnoAlBanco[] | undefined, istante: string): TurnoAlBanco | null {
  if (!turni?.length) return null;
  const ora = minutiDi(oraDi(istante));
  return turni.find((t) => minutiDi(t.fine) >= ora) ?? turni[turni.length - 1];
}

/** L'etichetta del turno: «Delfino Giallo 17:50». */
export const etichettaTurno = (t: TurnoAlBanco) => `${t.corso} ${t.inizio.slice(0, 5)}`;

/** Il quarto d'ora del passaggio («17:30»), per la linea del tempo. */
export function quartoDOra(istante: string): string {
  const o = oraDi(istante);
  const m = Math.floor(Number(o.slice(3, 5)) / 15) * 15;
  return `${o.slice(0, 2)}:${String(m).padStart(2, '0')}`;
}

/** I passaggi raggruppati per quarto d'ora, nell'ordine in cui arrivano (il più recente per primo). */
export function perQuarto<T extends { istante: string }>(lista: T[]): { ora: string; voci: T[] }[] {
  const out: { ora: string; voci: T[] }[] = [];
  for (const p of lista) {
    const q = quartoDOra(p.istante);
    const ultimo = out[out.length - 1];
    if (ultimo?.ora === q) ultimo.voci.push(p);
    else out.push({ ora: q, voci: [p] });
  }
  return out;
}

/** Che passaggio è: fermato, uscita o entrata. */
export type TipoPassaggio = 'fermati' | 'uscite' | 'entrate';
export const tipoPassaggio = (p: Pick<Passaggio, 'verde' | 'verso'>): TipoPassaggio => (!p.verde ? 'fermati' : p.verso === 'USCITA' ? 'uscite' : 'entrate');

export interface Gruppo {
  chiave: string;
  tipo: 'fermati' | 'turno' | 'senza' | 'uscite';
  titolo: string;
  turno: TurnoAlBanco | null;
  /** iscritti del turno già entrati oggi */
  arrivati: number | null;
  passaggi: Passaggio[];
}

/**
 * I passaggi raggruppati per corso: prima i fermati, poi i turni che stanno per iniziare (il più vicino per primo),
 * poi quelli già iniziati, chi è entrato senza un corso oggi e infine le uscite. In ogni gruppo una persona compare
 * una volta sola, con il suo passaggio più recente.
 */
export function perCorso(g: Pick<GiornataBanco, 'passaggi' | 'turni' | 'arrivati'>, adesso: string): Gruppo[] {
  const visti = new Set<string>();
  const fermati: Gruppo = { chiave: 'fermati', tipo: 'fermati', titolo: 'Fermati al tornello', turno: null, arrivati: null, passaggi: [] };
  const senza: Gruppo = { chiave: 'senza', tipo: 'senza', titolo: 'Senza un corso oggi', turno: null, arrivati: null, passaggi: [] };
  const uscite: Gruppo = { chiave: 'uscite', tipo: 'uscite', titolo: 'Usciti', turno: null, arrivati: null, passaggi: [] };
  const perTurno = new Map<string, Gruppo>();
  for (const p of g.passaggi) {
    const chi = p.persona?.id ?? p.codice;
    const tipo = tipoPassaggio(p);
    const chiave = `${tipo}:${chi}`;
    if (visti.has(chiave)) continue;
    visti.add(chiave);
    if (tipo === 'fermati') { fermati.passaggi.push(p); continue; }
    if (tipo === 'uscite') { uscite.passaggi.push(p); continue; }
    const t = turnoDelPassaggio(p.persona ? g.turni[p.persona.id] : undefined, p.istante);
    if (!t) { senza.passaggi.push(p); continue; }
    let gr = perTurno.get(t.lezioneId);
    if (!gr) {
      gr = { chiave: t.lezioneId, tipo: 'turno', titolo: etichettaTurno(t), turno: t, arrivati: g.arrivati[t.lezioneId] ?? null, passaggi: [] };
      perTurno.set(t.lezioneId, gr);
    }
    gr.passaggi.push(p);
  }
  const ora = minutiDi(adesso);
  const turni = [...perTurno.values()].sort((a, b) => {
    const ia = minutiDi(a.turno!.inizio), ib = minutiDi(b.turno!.inizio);
    const fa = ia >= ora, fb = ib >= ora; // non ancora iniziati prima
    if (fa !== fb) return fa ? -1 : 1;
    return fa ? ia - ib : ib - ia;
  });
  return [fermati, ...turni, senza, uscite].filter((x) => x.passaggi.length > 0);
}

/** Un passaggio dello storico riaperto come esito (quello che il tornello aveva risposto). */
export function esitoDi(p: Passaggio): EsitoTornello {
  return {
    passaggioId: p.id, istante: p.istante, codice: p.codice, verso: p.verso, verde: p.verde, persona: p.persona,
    statoBraccialetto: null, goldenPass: p.goldenPass, motivi: p.motivi, carnet: null,
  };
}

/** La posizione (in %) di un istante sulla linea della giornata, dalle 7 alle 22. */
export function posizioneLinea(istante: string, da = 7, a = 22): number {
  const m = minutiDi(oraDi(istante));
  return Math.min(100, Math.max(0, ((m - da * 60) / ((a - da) * 60)) * 100));
}
