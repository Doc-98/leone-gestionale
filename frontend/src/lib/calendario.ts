/**
 * Il calendario della stagione: i giorni in cui l'impianto è chiuso o a orario ridotto. Ogni voce è una chiusura di
 * tutto l'impianto del piano vasca (un evento CHIUSURA senza spazi); un orario ridotto che apre più tardi e chiude
 * prima sono due eventi con lo stesso nome e le stesse date.
 */
import type { Evento } from './api';
import { aggiungiGiorni, daIso, iso, MESI } from './date';

export type TipoChiusura = 'chiuso' | 'ridotto';

export interface Voce {
  nome: string;
  dal: string;
  al: string;
  tipo: TipoChiusura;
  /** Orario ridotto: da che ora apre (se più tardi del solito) e a che ora chiude (se prima). */
  apre: string | null;
  chiude: string | null;
  eventi: string[];
}

const hhmm = (s: string) => s.slice(0, 5);
const tuttoIlGiorno = (e: Evento) => hhmm(e.inizio) === '00:00' && hhmm(e.fine) >= '23:59';

/** Le chiusure raggruppate in voci, in ordine di data. */
export function voci(eventi: Evento[]): Voce[] {
  const per = new Map<string, Voce>();
  for (const e of eventi) {
    const al = e.dataFine ?? e.data;
    const k = `${e.titolo}|${e.data}|${al}`;
    const v = per.get(k) ?? { nome: e.titolo, dal: e.data, al, tipo: 'ridotto' as TipoChiusura, apre: null, chiude: null, eventi: [] };
    v.eventi.push(e.id);
    if (tuttoIlGiorno(e)) v.tipo = 'chiuso';
    else if (hhmm(e.inizio) === '00:00') v.apre = hhmm(e.fine);
    else v.chiude = hhmm(e.inizio);
    per.set(k, v);
  }
  return [...per.values()].sort((a, b) => a.dal.localeCompare(b.dal) || a.nome.localeCompare(b.nome));
}

/** Lo stato di un giorno: la voce che lo copre (una chiusura totale vince su un orario ridotto). */
export function statoDel(giorno: string, elenco: Voce[]): Voce | undefined {
  const qui = elenco.filter((v) => giorno >= v.dal && giorno <= v.al);
  return qui.find((v) => v.tipo === 'chiuso') ?? qui[0];
}

/** «8 dic 2026» */
export function breve(d: string): string {
  const x = daIso(d);
  return `${x.getDate()} ${MESI[x.getMonth()].slice(0, 3)} ${x.getFullYear()}`;
}

export function giorniTra(dal: string, al: string): number {
  return Math.round((daIso(al).getTime() - daIso(dal).getTime()) / 86_400_000) + 1;
}

/** «apre alle 10:00 e chiude alle 13:00», «chiude alle 13:00»… */
export function orarioRidotto(v: Pick<Voce, 'apre' | 'chiude'>): string {
  if (v.apre && v.chiude) return `apre alle ${v.apre} e chiude alle ${v.chiude}`;
  if (v.apre) return `apre alle ${v.apre}`;
  return `chiude alle ${v.chiude ?? '?'}`;
}

/** «8 dic 2026 · tutto il giorno», «dal 25 dic 2026 al 6 gen 2027 · 13 giorni», «24 dic 2026 · chiude alle 13:00» */
export function quando(v: Voce): string {
  const coda = v.tipo === 'ridotto' ? orarioRidotto(v) : 'tutto il giorno';
  if (v.dal === v.al) return `${breve(v.dal)} · ${coda}`;
  return `dal ${breve(v.dal)} al ${breve(v.al)} · ${giorniTra(v.dal, v.al)} giorni${v.tipo === 'ridotto' ? ` · ${coda}` : ''}`;
}

export interface Mese {
  chiave: string;
  nome: string;
  /** Giorni vuoti prima del primo, per partire dal lunedì. */
  vuote: number;
  giorni: string[];
}

/** I mesi della stagione, dal primo all'ultimo giorno. */
export function mesi(dal: string, al: string): Mese[] {
  const out: Mese[] = [];
  const inizio = daIso(dal);
  for (let d = new Date(inizio.getFullYear(), inizio.getMonth(), 1); iso(d) <= al; d = new Date(d.getFullYear(), d.getMonth() + 1, 1)) {
    const n = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    const giorni = Array.from({ length: n }, (_, i) => iso(new Date(d.getFullYear(), d.getMonth(), i + 1))).filter((g) => g >= dal && g <= al);
    out.push({
      chiave: iso(d).slice(0, 7),
      nome: `${MESI[d.getMonth()][0].toUpperCase()}${MESI[d.getMonth()].slice(1)} ${d.getFullYear()}`,
      vuote: (daIso(giorni[0]).getDay() + 6) % 7,
      giorni,
    });
  }
  return out;
}

export interface Conteggi {
  chiusi: number;
  ridotti: number;
  /** Tutti i giorni della stagione tranne quelli chiusi (l'orario ridotto conta come aperto). */
  aperti: number;
}

export function conteggi(dal: string, al: string, elenco: Voce[]): Conteggi {
  let chiusi = 0, ridotti = 0, tutti = 0;
  for (let g = dal; g <= al; g = aggiungiGiorni(g, 1)) {
    tutti++;
    const v = statoDel(g, elenco);
    if (v?.tipo === 'chiuso') chiusi++;
    else if (v) ridotti++;
  }
  return { chiusi, ridotti, aperti: tutti - chiusi };
}

/** La prima voce non ancora finita, con quanto manca. */
export function prossima(elenco: Voce[], oggi: string): { voce: Voce; traGiorni: number } | null {
  const v = elenco.find((x) => x.al >= oggi);
  if (!v) return null;
  return { voce: v, traGiorni: v.dal > oggi ? giorniTra(oggi, v.dal) - 1 : 0 };
}

/** Domenica di Pasqua (algoritmo di Meeus/Jones/Butcher). */
export function pasqua(anno: number): string {
  const a = anno % 19, b = Math.floor(anno / 100), c = anno % 100, d = Math.floor(b / 4), e = b % 4;
  const f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4), k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
  const mese = Math.floor((h + l - 7 * m + 114) / 31), giorno = ((h + l - 7 * m + 114) % 31) + 1;
  return iso(new Date(anno, mese - 1, giorno));
}

/** Le festività nazionali che cadono nella stagione, come [nome, dal, al]. */
export function festivita(dal: string, al: string): [string, string, string][] {
  const fisse: [string, number, number][] = [
    ['Capodanno', 1, 1], ['Epifania', 1, 6], ['Festa della Liberazione', 4, 25], ['Festa del lavoro', 5, 1],
    ['Festa della Repubblica', 6, 2], ['Ferragosto', 8, 15], ['Ognissanti', 11, 1], ['Immacolata', 12, 8],
    ['Natale', 12, 25], ['Santo Stefano', 12, 26],
  ];
  const out: [string, string, string][] = [];
  for (let anno = daIso(dal).getFullYear(); anno <= daIso(al).getFullYear(); anno++) {
    for (const [nome, m, g] of fisse) {
      const d = iso(new Date(anno, m - 1, g));
      out.push([nome, d, d]);
    }
    const p = pasqua(anno);
    out.push(['Pasqua e Pasquetta', p, aggiungiGiorni(p, 1)]);
  }
  return out.filter(([, d1, d2]) => d1 >= dal && d2 <= al).sort((a, b) => a[1].localeCompare(b[1]));
}

/** Le stesse date un anno dopo (il 29 febbraio diventa il 28). */
export function unAnnoDopo(d: string): string {
  const [y, m, g] = d.split('-').map(Number);
  const ultimo = new Date(y + 1, m, 0).getDate();
  return iso(new Date(y + 1, m - 1, Math.min(g, ultimo)));
}

/** Gli eventi da creare per una voce nuova: uno per tutto il giorno, o uno/due per l'orario ridotto. */
export function eventiDaCreare(v: { nome: string; dal: string; al: string; tipo: TipoChiusura; apre: string | null; chiude: string | null }):
  { titolo: string; data: string; dataFine: string | null; inizio: string; fine: string }[] {
  const base = { titolo: v.nome, data: v.dal, dataFine: v.al === v.dal ? null : v.al };
  if (v.tipo === 'chiuso') return [{ ...base, inizio: '00:00', fine: '23:59' }];
  const out = [];
  if (v.apre) out.push({ ...base, inizio: '00:00', fine: v.apre });
  if (v.chiude) out.push({ ...base, inizio: v.chiude, fine: '23:59' });
  return out;
}
