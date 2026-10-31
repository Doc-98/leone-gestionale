import type { Richiesta, Turno } from './api';
import { aggiungiGiorni, GIORNI_BREVI, GIORNI_ESTESI, lunedi, MESI, ora } from './date';

/**
 * «I miei turni» (mockup di ottobre 2026): tre viste a scelta, la settimana (telefono), il mese e la settimana a colonne
 * (PC). Qui i calcoli che non dipendono dalla pagina.
 */
export type VistaTurni = 'settimana' | 'mese' | 'colonne';
export const VISTE_TURNI: { id: VistaTurni; nome: string }[] = [
  { id: 'settimana', nome: 'La settimana' },
  { id: 'mese', nome: 'Il mese' },
  { id: 'colonne', nome: 'A colonne' },
];

/** L'area di una postazione, per il colore: spogliatoio maschile, femminile, piccoli, atrio, altro. */
export type Area = 'm' | 'f' | 'p' | 'a' | 'x';
export function areaDi(postazione: string): Area {
  const p = postazione.toLowerCase();
  if (/piccol|bimb/.test(p)) return 'p';
  if (/atrio|ingresso|reception/.test(p)) return 'a';
  if (/femmin|donne|\bf\b/.test(p)) return 'f';
  if (/maschil|uomini|\bm\b/.test(p)) return 'm';
  return 'x';
}
export const NOMI_AREA: Record<Area, string> = { m: 'Maschile', f: 'Femminile', p: 'Piccoli', a: 'Atrio', x: 'Altro' };

export const attivo = (t: Turno) => t.stato === 'PIANIFICATO';
const minuti = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));

/** Il primo turno non ancora finito, da adesso. */
export function prossimo(turni: Turno[], oggi: string, adessoMin: number): Turno | null {
  return turni.filter(attivo)
    .filter((t) => t.data > oggi || (t.data === oggi && minuti(t.fine) > adessoMin))
    .sort((a, b) => a.data.localeCompare(b.data) || a.inizio.localeCompare(b.inizio))[0] ?? null;
}

/** «in corso», «tra 40 minuti», «tra 2 h», «domani», «sabato 7/11». */
export function traQuanto(t: Turno, oggi: string, adessoMin: number): string {
  if (t.data === oggi) {
    const mancano = minuti(t.inizio) - adessoMin;
    if (mancano <= 0) return 'in corso';
    if (mancano < 60) return `oggi · tra ${mancano} minuti`;
    const h = Math.floor(mancano / 60);
    return `oggi · tra ${h} ${h === 1 ? 'ora' : 'ore'}`;
  }
  if (t.data === aggiungiGiorni(oggi, 1)) return 'domani';
  const d = new Date(`${t.data}T12:00:00`);
  return `${GIORNI_ESTESI[d.getDay() === 0 ? 7 : d.getDay()]} ${d.getDate()}/${d.getMonth() + 1}`;
}

/** I colleghi in turno nello stesso giorno con un orario che si sovrappone (escluso il turno stesso). */
export function conMe(t: Turno, staff: Turno[]): Turno[] {
  return staff.filter((x) => x.id !== t.id && attivo(x) && x.persona && x.data === t.data && x.inizio < t.fine && x.fine > t.inizio)
    .sort((a, b) => a.inizio.localeCompare(b.inizio));
}

/** Il collega è già in turno in quell'orario? Restituisce il suo turno. */
export function occupato(personaId: string, t: Turno, staff: Turno[]): Turno | null {
  return staff.find((x) => x.persona?.id === personaId && attivo(x) && x.data === t.data && x.inizio < t.fine && x.fine > t.inizio) ?? null;
}

export const oreTotali = (turni: Turno[]) => turni.filter(attivo).reduce((s, t) => s + Number(t.ore), 0);

/** «26 ottobre – 1 novembre»; con «breve» i mesi a tre lettere quando sono due (per il telefono: niente a capo). */
export function titoloSettimana(lun: string, breve = false): string {
  const a = new Date(`${lun}T12:00:00`);
  const b = new Date(`${aggiungiGiorni(lun, 6)}T12:00:00`);
  const m = (d: Date) => (breve ? MESI[d.getMonth()].slice(0, 3) : MESI[d.getMonth()]);
  return a.getMonth() === b.getMonth() ? `${a.getDate()} – ${b.getDate()} ${MESI[a.getMonth()]}` : `${a.getDate()} ${m(a)} – ${b.getDate()} ${m(b)}`;
}

/** «Giulia, Marco, Sara, Elisa e altri 3»: i colleghi in turno con me, senza riempire lo schermo. */
export function nomiCon(colleghi: Turno[], quanti = 4): string {
  const nomi = [...new Set(colleghi.map((c) => c.persona?.nome ?? ''))].filter(Boolean);
  if (!nomi.length) return 'Nessun collega in turno con te in quell\'orario.';
  const primi = nomi.slice(0, quanti).join(', ');
  return `Con te: ${primi}${nomi.length > quanti ? ` e altri ${nomi.length - quanti}` : ''}`;
}

/** Le caselle di un mese (da lunedì): null per i giorni fuori dal mese. */
export function caselleMese(mese: string): (string | null)[] {
  const primo = `${mese}-01`;
  const inizio = lunedi(primo);
  const out: (string | null)[] = [];
  let d = inizio;
  while (true) {
    out.push(d.slice(0, 7) === mese ? d : null);
    d = aggiungiGiorni(d, 1);
    if (d.slice(0, 7) > mese && out.length % 7 === 0) break;
  }
  return out;
}

/** «lun 26» */
export function giornoBreve(d: string): { l: string; n: number } {
  const x = new Date(`${d}T12:00:00`);
  return { l: GIORNI_BREVI[x.getDay() === 0 ? 7 : x.getDay()], n: x.getDate() };
}

export const orario = (t: Pick<Turno, 'inizio' | 'fine'>) => `${ora(t.inizio)}–${ora(t.fine)}`;

/** Le fasce della settimana a colonne: dalla prima ora piena prima del primo turno all'ora dopo l'ultimo (almeno 8–21). */
export function fasce(turni: Turno[]): { da: number; a: number } {
  let da = 8;
  let a = 21;
  for (const t of turni) {
    da = Math.min(da, Math.floor(minuti(t.inizio) / 60));
    a = Math.max(a, Math.ceil(minuti(t.fine) / 60));
  }
  return { da, a };
}
export const posizione = (hhmm: string, da: number, pxOra: number) => ((minuti(hhmm) - da * 60) / 60) * pxOra;

/** Una mia richiesta ancora aperta su questo turno, a parole: «Sostituzione chiesta · attende Giulia». */
export function richiestaSul(t: Turno, richieste: Richiesta[], io: string | undefined): string | null {
  const r = richieste.find((x) => x.turno.id === t.id && x.richiedente.id === io && (x.stato === 'IN_ATTESA_COLLEGA' || x.stato === 'IN_ATTESA_APPROVAZIONE'));
  if (!r) return null;
  const cosa = r.tipo === 'SOSTITUZIONE' ? 'Sostituzione chiesta' : r.tipo === 'SCAMBIO' ? 'Scambio proposto' : 'Cambio orario chiesto';
  if (r.stato === 'IN_ATTESA_APPROVAZIONE') return `${cosa} · decide il coordinatore`;
  return `${cosa} · ${r.collega ? `attende ${r.collega.nome.split(' ')[0]}` : 'attende un collega'}`;
}

/** Ore per area di un insieme di turni, nell'ordine maschile, femminile, piccoli, atrio, altro. */
export function orePerArea(turni: Turno[]): { area: Area; ore: number }[] {
  const per = new Map<Area, number>();
  for (const t of turni.filter(attivo)) per.set(areaDi(t.postazione), (per.get(areaDi(t.postazione)) ?? 0) + Number(t.ore));
  return (['m', 'f', 'p', 'a', 'x'] as Area[]).filter((a) => per.has(a)).map((area) => ({ area, ore: per.get(area)! }));
}

/** Le settimane di un mese (da lunedì a domenica, tagliate ai bordi del mese): «1–4», «5–11»… */
export function settimaneDelMese(mese: string): { da: string; a: string; nome: string }[] {
  const out: { da: string; a: string; nome: string }[] = [];
  let d = `${mese}-01`;
  while (d.slice(0, 7) === mese) {
    const fineSett = aggiungiGiorni(lunedi(d), 6);
    let a = fineSett;
    while (a.slice(0, 7) !== mese) a = aggiungiGiorni(a, -1);
    out.push({ da: d, a, nome: `${Number(d.slice(8))}–${Number(a.slice(8))}` });
    d = aggiungiGiorni(a, 1);
  }
  return out;
}
