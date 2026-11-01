/**
 * Logica comune alle pagine di gestione dei turni (pianificazione, settimana tipo): le postazioni raggruppate per
 * area, chi può lavorare dove, i candidati per un turno e le ore per persona.
 */
import type { PersonaBreve, PersonaStaff, Postazione } from './api';
import { oreTra } from './date';
import { areaDi, type Area } from './turni';

export interface RigaPostazione {
  postazione: Postazione;
  /** Il nome senza quello del gruppo: «Spogliatoio M · postazione 1» → «Postazione 1». */
  etichetta: string;
}

export interface Gruppo {
  chiave: string;
  nome: string;
  area: Area;
  righe: RigaPostazione[];
}

const maiuscola = (s: string) => (s ? s[0].toUpperCase() + s.slice(1) : s);

/** Il gruppo di una postazione: la parte del nome prima di « · », altrimenti la mansione. */
export function gruppoDi(p: Postazione): { nome: string; etichetta: string } {
  const i = p.nome.indexOf(' · ');
  if (i > 0) return { nome: p.nome.slice(0, i), etichetta: maiuscola(p.nome.slice(i + 3)) };
  return { nome: p.mansione, etichetta: p.nome };
}

/** Le postazioni nell'ordine della pianificazione, raggruppate (es. spogliatoio maschile, femminile, piccoli, atrio). */
export function gruppi(postazioni: Postazione[]): Gruppo[] {
  const out: Gruppo[] = [];
  for (const p of postazioni) {
    const { nome, etichetta } = gruppoDi(p);
    const chiave = `${p.mansione}|${nome}`;
    let g = out.find((x) => x.chiave === chiave);
    if (!g) out.push((g = { chiave, nome, area: areaDi(p.nome), righe: [] }));
    g.righe.push({ postazione: p, etichetta });
  }
  return out;
}

/** Il nome per una postazione nuova del gruppo: «Spogliatoio M · postazione 4». */
export function nomeNuovaPostazione(g: Gruppo): string {
  const numeri = g.righe.map((r) => Number(/(\d+)\s*$/.exec(r.postazione.nome)?.[1] ?? 0));
  const n = Math.max(g.righe.length, ...numeri) + 1;
  return `${g.nome} · postazione ${n}`;
}

/** «Giulia Bassi» → «Giulia B.» */
export function nomeCorto(nome: string): string {
  const [n, ...resto] = nome.split(' ');
  return resto.length ? `${n} ${resto[resto.length - 1][0]}.` : n;
}

/** La persona può lavorare su quella postazione? (gli spogliatoi riservati a un sesso) */
export function ammessa(p: Pick<Postazione, 'soloSesso'> | undefined, persona: Pick<PersonaStaff, 'sesso'> | undefined): boolean {
  return !p?.soloSesso || persona?.sesso === p.soloSesso;
}

export function testoRiservata(p: Pick<Postazione, 'soloSesso'> | undefined): string {
  return p?.soloSesso === 'F' ? 'Solo donne. ' : p?.soloSesso === 'M' ? 'Solo uomini. ' : '';
}

/** Un turno come lo vede la logica di assegnazione (della settimana o della settimana tipo). */
export interface TurnoBreve {
  id: string;
  giorno: string;
  inizio: string;
  fine: string;
  postazioneId: string;
  persona: PersonaBreve | null;
}

export const ore = (t: Pick<TurnoBreve, 'inizio' | 'fine'>) => oreTra(t.inizio, t.fine);

/** Le ore di ciascuno nell'insieme di turni. */
export function orePerPersona(turni: TurnoBreve[]): Map<string, { nome: string; ore: number; turni: number }> {
  const m = new Map<string, { nome: string; ore: number; turni: number }>();
  for (const t of turni) {
    if (!t.persona) continue;
    const r = m.get(t.persona.id) ?? { nome: t.persona.nome, ore: 0, turni: 0 };
    r.ore += ore(t);
    r.turni++;
    m.set(t.persona.id, r);
  }
  return m;
}

export const siAccavallano = (a: Pick<TurnoBreve, 'giorno' | 'inizio' | 'fine'>, b: Pick<TurnoBreve, 'giorno' | 'inizio' | 'fine'>) =>
  a.giorno === b.giorno && a.inizio < b.fine && b.inizio < a.fine;

/** Il turno della persona che si accavalla con questo (escluso il turno stesso). */
export function accavallato(personaId: string, t: TurnoBreve, turni: TurnoBreve[]): TurnoBreve | undefined {
  return turni.find((x) => x.id !== t.id && x.persona?.id === personaId && siAccavallano(x, t));
}

export interface Candidato {
  persona: PersonaStaff;
  ore: number;
  /** L'altro turno che ha alla stessa ora (si può assegnare comunque, con un avviso). */
  occupato?: TurnoBreve;
  attuale: boolean;
}

/** Chi può fare il turno: chi è ammesso sulla postazione; prima chi è libero, dal meno carico. */
export function candidati(t: TurnoBreve, postazione: Postazione | undefined, staff: PersonaStaff[], turni: TurnoBreve[]): Candidato[] {
  const oreDi = orePerPersona(turni);
  return staff
    .filter((p) => ammessa(postazione, p))
    .map((p) => ({ persona: p, ore: oreDi.get(p.id)?.ore ?? 0, occupato: accavallato(p.id, t, turni), attuale: t.persona?.id === p.id }))
    .sort((a, b) => Number(b.attuale) - Number(a.attuale) || Number(!!a.occupato) - Number(!!b.occupato) || a.ore - b.ore || a.persona.nome.localeCompare(b.persona.nome));
}

/** «12,5 h» */
export function formatH(n: number): string {
  return `${String(Math.round(n * 100) / 100).replace('.', ',')} h`;
}

// ── Anteprima della generazione di un mese ──────────────────────────────────

export interface FasciaChiusa {
  inizio: string;
  fine: string;
}

/**
 * L'orario del turno tolte le chiusure del giorno (come fa il server): null se l'impianto è chiuso per tutto il turno.
 * Una chiusura a cavallo dell'inizio lo posticipa, una a cavallo della fine lo anticipa.
 */
export function aperto(inizio: string, fine: string, chiuso: FasciaChiusa[]): [string, string] | null {
  for (const f of chiuso) {
    if (f.inizio >= fine || f.fine <= inizio) continue;
    if (f.inizio <= inizio && f.fine >= fine) return null;
    if (f.inizio <= inizio) inizio = f.fine;
    else if (f.fine >= fine) fine = f.inizio;
  }
  return fine > inizio ? [inizio, fine] : null;
}

export interface GiornoAnteprima {
  giorno: string;
  /** turni che verrebbero creati */
  nuovi: number;
  stato: 'chiuso' | 'ridotto' | 'gia' | '';
  titolo: string;
}

export interface Anteprima {
  giorni: GiornoAnteprima[];
  nuovi: number;
  ore: number;
  scoperti: number;
  /** i nomi delle chiusure del mese */
  chiusure: string[];
}

/**
 * Cosa creerebbe la generazione del mese: per ogni giorno le righe della settimana tipo di quel giorno, tolte le
 * chiusure e le postazioni che in quell'orario hanno già un turno.
 */
export function anteprima(
  righe: { postazioneId: string; giornoSettimana: number; inizio: string; fine: string; persona: PersonaBreve | null }[],
  giorni: string[],
  chiusure: { titolo: string; data: string; dataFine: string | null; inizio: string; fine: string }[],
  esistenti: { postazioneId: string; data: string; inizio: string; fine: string }[],
): Anteprima {
  const hh = (s: string) => s.slice(0, 5);
  let nuovi = 0, oreTot = 0, scoperti = 0;
  const nomi = new Set<string>();
  const out = giorni.map((g) => {
    const dow = ((new Date(`${g}T12:00:00`).getDay() + 6) % 7) + 1;
    const ch = chiusure.filter((c) => g >= c.data && g <= (c.dataFine ?? c.data));
    ch.forEach((c) => nomi.add(c.titolo));
    const fasce = ch.map((c) => ({ inizio: hh(c.inizio), fine: hh(c.fine) }));
    const tutto = fasce.some((f) => f.inizio === '00:00' && f.fine >= '23:59');
    const gia = esistenti.filter((t) => t.data === g);
    let n = 0;
    for (const r of righe.filter((x) => x.giornoSettimana === dow)) {
      const o = aperto(hh(r.inizio), hh(r.fine), fasce);
      if (!o) continue;
      if (gia.some((t) => t.postazioneId === r.postazioneId && hh(t.inizio) < o[1] && o[0] < hh(t.fine))) continue;
      n++;
      oreTot += oreTra(o[0], o[1]);
      if (!r.persona) scoperti++;
    }
    nuovi += n;
    const stato: GiornoAnteprima['stato'] = tutto ? 'chiuso' : gia.length && !n ? 'gia' : ch.length ? 'ridotto' : '';
    return { giorno: g, nuovi: n, stato, titolo: ch.map((c) => c.titolo).join(', ') };
  });
  return { giorni: out, nuovi, ore: oreTot, scoperti, chiusure: [...nomi] };
}
