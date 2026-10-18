import type { Occorrenza, SpazioPiano, StatoOccorrenza, TipoCorso, TipoEvento } from './api';

export const TIPI_CORSO: Record<TipoCorso, string> = {
  SCUOLA_NUOTO: 'Scuola nuoto',
  ACQUATICITA: 'Acquaticità',
  AGONISMO: 'Agonismo',
  MASTER: 'Master',
  ACQUAGYM: 'Acquagym',
  CURRICOLARE: 'Curricolare (scuole)',
  NUOTO_LIBERO: 'Nuoto libero',
  ALTRO: 'Altro',
};

export const TIPI_EVENTO: Record<TipoEvento, string> = {
  GARA: 'Gara',
  LEZIONE_PRIVATA: 'Lezione privata',
  LEZIONE_PROVA: 'Lezione di prova',
  CHIUSURA: 'Chiusura',
  ALTRO: 'Altro evento',
};

export const STATI: Record<StatoOccorrenza, string> = {
  REGOLARE: '',
  SPOSTATA_QUI: 'Spostata',
  SALTATA: 'Non si tiene',
  SPOSTATA_VIA: 'Spostata altrove',
  CHIUSO: 'Sospesa per chiusura',
  ANNULLATO: 'Annullato',
};

export function nomeCategoria(o: Pick<Occorrenza, 'tipo' | 'categoria'>): string {
  return o.tipo === 'LEZIONE' ? TIPI_CORSO[o.categoria as TipoCorso] : TIPI_EVENTO[o.categoria as TipoEvento];
}

/** Classe di colore (in app.css) per la categoria. */
export function colore(categoria: string): string {
  return `cat-${categoria.toLowerCase().replace(/_/g, '-')}`;
}

export const inAttivita = (o: Occorrenza) => o.stato === 'REGOLARE' || o.stato === 'SPOSTATA_QUI';

/** «17:00:00» → minuti dalla mezzanotte */
export function minuti(ora: string): number {
  return Number(ora.slice(0, 2)) * 60 + Number(ora.slice(3, 5));
}

// ── Spazi ──────────────────────────────────────────────────────────────────

const ACQUA = new Set(['VASCA', 'CORSIA']);

/**
 * Le colonne della vista giorno: per ogni vasca le sue corsie (o la vasca stessa, se non ne ha).
 * Solo vasche e corsie: spogliatoi e atrio non fanno parte del piano.
 */
export interface ColonnaSpazio {
  id: string;
  nome: string;
  vasca: string;
  vascaId: string;
}

export function colonneSpazi(spazi: SpazioPiano[]): ColonnaSpazio[] {
  const vasche = spazi.filter((s) => !s.padreId && ACQUA.has(s.tipo));
  const out: ColonnaSpazio[] = [];
  for (const v of vasche) {
    const corsie = spazi.filter((s) => s.padreId === v.id);
    if (corsie.length) for (const c of corsie) out.push({ id: c.id, nome: c.nome, vasca: v.nome, vascaId: v.id });
    else out.push({ id: v.id, nome: v.nome, vasca: v.nome, vascaId: v.id });
  }
  return out;
}

/** Gli intervalli di colonne (inizio incluso, fine esclusa) occupati da un insieme di spazi: una vasca intera copre tutte le sue corsie. */
export function intervalliColonne(ids: string[], colonne: ColonnaSpazio[]): [number, number][] {
  const indici = new Set<number>();
  colonne.forEach((c, i) => {
    if (ids.includes(c.id) || ids.includes(c.vascaId)) indici.add(i);
  });
  const ordinati = [...indici].sort((a, b) => a - b);
  const out: [number, number][] = [];
  for (const i of ordinati) {
    const ultimo = out[out.length - 1];
    if (ultimo && ultimo[1] === i) ultimo[1] = i + 1;
    else out.push([i, i + 1]);
  }
  return out;
}

/** «Corsie 1–2 · Vasca grande», «Vasca piccola», «Tutto l'impianto». */
export function etichettaSpazi(ids: string[], spazi: SpazioPiano[]): string {
  if (!ids.length) return "Tutto l'impianto";
  const perId = new Map(spazi.map((s) => [s.id, s]));
  const parti: string[] = [];
  const corsiePerVasca = new Map<string, SpazioPiano[]>();
  for (const id of ids) {
    const s = perId.get(id);
    if (!s) continue;
    if (s.padreId) {
      const l = corsiePerVasca.get(s.padreId) ?? [];
      l.push(s);
      corsiePerVasca.set(s.padreId, l);
    } else parti.push(s.nome);
  }
  for (const [vascaId, corsie] of corsiePerVasca) {
    const numeri = corsie.map((c) => Number(/(\d+)\s*$/.exec(c.nome)?.[1] ?? NaN));
    const vasca = perId.get(vascaId)?.nome ?? '';
    if (numeri.every((n) => !Number.isNaN(n))) {
      numeri.sort((a, b) => a - b);
      const consecutive = numeri.every((n, i) => i === 0 || n === numeri[i - 1] + 1);
      const testo = numeri.length === 1 ? `Corsia ${numeri[0]}` : consecutive ? `Corsie ${numeri[0]}–${numeri[numeri.length - 1]}` : `Corsie ${numeri.join(', ')}`;
      parti.push(`${testo} · ${vasca}`);
    } else parti.push(`${corsie.map((c) => c.nome).join(', ')} · ${vasca}`);
  }
  return parti.join(' + ');
}

// ── Ingombri nella griglia oraria ─────────────────────────────────────────

export interface Riquadro {
  occorrenza: Occorrenza;
  /** colonna iniziale (0 = prima) e numero di colonne */
  colonna: number;
  ampiezza: number;
  /** sotto-corsia e numero di sotto-corsie quando più riquadri si sovrappongono */
  corsia: number;
  corsie: number;
}

/**
 * Dispone i riquadri: ognuno occupa le sue colonne; chi si sovrappone (stesso orario e colonne in comune)
 * si divide la larghezza in sotto-corsie, così nessuno copre l'altro.
 */
export function disponi(voci: { occorrenza: Occorrenza; colonna: number; ampiezza: number }[]): Riquadro[] {
  const ordinate = [...voci].sort((a, b) => minuti(a.occorrenza.inizio) - minuti(b.occorrenza.inizio) || b.ampiezza - a.ampiezza);
  const out: Riquadro[] = [];
  const tocca = (a: Riquadro | (typeof voci)[number], b: Riquadro | (typeof voci)[number]) =>
    minuti(a.occorrenza.inizio) < minuti(b.occorrenza.fine) && minuti(b.occorrenza.inizio) < minuti(a.occorrenza.fine) &&
    a.colonna < b.colonna + b.ampiezza && b.colonna < a.colonna + a.ampiezza;
  for (const v of ordinate) {
    const occupate = new Set(out.filter((r) => tocca(r, v)).map((r) => r.corsia));
    let corsia = 0;
    while (occupate.has(corsia)) corsia++;
    out.push({ ...v, corsia, corsie: 1 });
  }
  // ogni riquadro si divide la larghezza con il gruppo di riquadri che si toccano (anche indirettamente)
  const gruppo = out.map((_, i) => i);
  const radice = (i: number): number => (gruppo[i] === i ? i : (gruppo[i] = radice(gruppo[i])));
  for (let i = 0; i < out.length; i++) for (let j = i + 1; j < out.length; j++) if (tocca(out[i], out[j])) gruppo[radice(i)] = radice(j);
  const massimo = new Map<number, number>();
  out.forEach((r, i) => massimo.set(radice(i), Math.max(massimo.get(radice(i)) ?? 0, r.corsia + 1)));
  out.forEach((r, i) => (r.corsie = massimo.get(radice(i)) ?? 1));
  return out;
}

/** Ora di inizio e fine della griglia: almeno 8–21, allargata se serve, a ore intere. */
export function finestraOraria(occ: Occorrenza[], da = 8 * 60, a = 21 * 60): [number, number] {
  let min = da;
  let max = a;
  for (const o of occ) {
    if (o.categoria === 'CHIUSURA') continue;
    min = Math.min(min, minuti(o.inizio));
    max = Math.max(max, minuti(o.fine));
  }
  return [Math.floor(min / 60) * 60, Math.ceil(max / 60) * 60];
}

/** Colonna della griglia oraria (uno spazio o un giorno). */
export interface Intestazione {
  chiave: string;
  titolo: string;
  /** gruppo sopra la colonna (es. la vasca sopra le sue corsie) */
  sopra?: string;
  oggi?: boolean;
}

/** Chiusura disegnata come banda sotto i riquadri. */
export interface Banda {
  chiave: string;
  colonna: number;
  ampiezza: number;
  inizio: number;
  fine: number;
  titolo: string;
}

/** «8–12 anni», «dai 18 anni», «fino a 5 anni» */
export function eta(da: number | null, a: number | null): string | null {
  if (da == null && a == null) return null;
  if (a == null) return `dai ${da} anni`;
  if (da == null) return `fino a ${a} anni`;
  return `${da}–${a} anni`;
}
