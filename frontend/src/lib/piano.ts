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

// ── Fasce ──────────────────────────────────────────────────────────────────

/**
 * Oltre questa durata un'attività (es. nuoto libero 7:00–10:20, Propaganda da 90 minuti) non fa fascia:
 * compare in tutte le fasce che attraversa.
 */
export const DURATA_FASCIA_MAX = 60;

export interface Fascia {
  chiave: string;
  /** orario della fascia: quello delle lezioni più numerose del gruppo */
  inizio: number;
  fine: number;
}

const sovrapposizione = (a: [number, number], b: [number, number]) => Math.max(0, Math.min(a[1], b[1]) - Math.max(a[0], b[0]));
const etichettaFascia = (i: number, f: number) => `${String(Math.floor(i / 60)).padStart(2, '0')}:${String(i % 60).padStart(2, '0')}–${String(Math.floor(f / 60)).padStart(2, '0')}:${String(f % 60).padStart(2, '0')}`;
export const orarioFascia = (f: Fascia) => etichettaFascia(f.inizio, f.fine);

/**
 * Le fasce di un insieme di attività (un giorno o una settimana, le date non contano): orari che si
 * sovrappongono per almeno metà della lezione più corta finiscono nella stessa fascia (es. vasca piccola
 * 16:55–17:35 con vasca grande 17:00–17:45). Restituisce le fasce e, per ogni attività, la sua fascia.
 */
export function raggruppaFasce(occ: Occorrenza[]): { fasce: Fascia[]; fasciaDi: Map<string, string> } {
  const attivita = occ.filter((o) => o.categoria !== 'CHIUSURA');
  const brevi = attivita.filter((o) => minuti(o.fine) - minuti(o.inizio) <= DURATA_FASCIA_MAX);
  // coppie di orario distinte, con quante attività le usano
  const coppie = new Map<string, { i: number; f: number; n: number }>();
  for (const o of brevi) {
    const k = `${minuti(o.inizio)}-${minuti(o.fine)}`;
    const c = coppie.get(k) ?? { i: minuti(o.inizio), f: minuti(o.fine), n: 0 };
    c.n++;
    coppie.set(k, c);
  }
  const ordinate = [...coppie.values()].sort((a, b) => a.i - b.i || a.f - b.f);
  const gruppi: { ancora: { i: number; f: number; n: number }; membri: Set<string> }[] = [];
  for (const c of ordinate) {
    const g = gruppi[gruppi.length - 1];
    const minima = Math.min(c.f - c.i, g ? g.ancora.f - g.ancora.i : Infinity);
    if (g && sovrapposizione([c.i, c.f], [g.ancora.i, g.ancora.f]) * 2 >= minima) {
      g.membri.add(`${c.i}-${c.f}`);
      // l'orario della fascia è quello più usato; a parità, quello della lezione più lunga (la vasca grande)
      if (c.n > g.ancora.n || (c.n === g.ancora.n && c.f - c.i > g.ancora.f - g.ancora.i)) g.ancora = c;
    } else gruppi.push({ ancora: c, membri: new Set([`${c.i}-${c.f}`]) });
  }
  const fasce: Fascia[] = gruppi.map((g) => ({ chiave: etichettaFascia(g.ancora.i, g.ancora.f), inizio: g.ancora.i, fine: g.ancora.f }));
  const perCoppia = new Map<string, string>();
  gruppi.forEach((g, n) => g.membri.forEach((m) => perCoppia.set(m, fasce[n].chiave)));

  const fasciaDi = new Map<string, string>();
  for (const o of brevi) fasciaDi.set(o.chiave, perCoppia.get(`${minuti(o.inizio)}-${minuti(o.fine)}`)!);
  // le attività lunghe stanno nella fascia in cui cominciano; se nessuna, ne fanno una loro
  for (const o of attivita) {
    if (fasciaDi.has(o.chiave)) continue;
    const i = minuti(o.inizio);
    const f = minuti(o.fine);
    let dove = fasce.find((x) => i >= x.inizio && i < x.fine);
    if (!dove) {
      dove = { chiave: etichettaFascia(i, f), inizio: i, fine: f };
      if (!fasce.some((x) => x.chiave === dove!.chiave)) fasce.push(dove);
    }
    fasciaDi.set(o.chiave, dove.chiave);
  }
  fasce.sort((a, b) => a.inizio - b.inizio || a.fine - b.fine);
  return { fasce, fasciaDi };
}

/** Cosa c'è in vasca durante la fascia: le attività della fascia e quelle lunghe che la attraversano. */
export function inFascia(f: Fascia, occ: Occorrenza[], fasciaDi: Map<string, string>): Occorrenza[] {
  return occ.filter((o) => {
    if (o.categoria === 'CHIUSURA') return sovrapposizione([minuti(o.inizio), minuti(o.fine)], [f.inizio, f.fine]) > 0;
    if (fasciaDi.get(o.chiave) === f.chiave) return true;
    const lunga = minuti(o.fine) - minuti(o.inizio) > DURATA_FASCIA_MAX;
    return lunga && sovrapposizione([minuti(o.inizio), minuti(o.fine)], [f.inizio, f.fine]) > 0;
  });
}

// ── Pianta della vasca ─────────────────────────────────────────────────────

export interface CorsiaPianta {
  id: string;
  numero: number;
  nome: string;
}

export interface PezzoPianta {
  occorrenza: Occorrenza;
  /** prima corsia (0 = corsia 1) e quante corsie occupa */
  da: number;
  quante: number;
  /** colonna, quando più attività si dividono la stessa corsia */
  colonna: number;
  colonne: number;
}

export interface VascaPianta {
  id: string;
  nome: string;
  /** assetto in uso (numero di corsie); null per le vasche senza corsie */
  assetto: number | null;
  /** nella fascia ci sono corsie di assetti diversi */
  assettiMisti: boolean;
  corsie: CorsiaPianta[];
  pezzi: PezzoPianta[];
}

/**
 * Le vasche come si vedono dall'alto in quella fascia: per ogni vasca l'assetto in uso (quello delle corsie
 * occupate, altrimenti il più fitto), le corsie e le attività su ciascuna. Una vasca intera occupa tutte le corsie.
 */
export function pianta(spazi: SpazioPiano[], occ: Occorrenza[]): { vasche: VascaPianta[]; altrove: Occorrenza[] } {
  const attivita = occ.filter((o) => o.categoria !== 'CHIUSURA');
  const vasche = spazi.filter((s) => !s.padreId && (s.tipo === 'VASCA' || s.tipo === 'CORSIA'));
  const usate = new Set<string>();
  const out: VascaPianta[] = [];
  for (const v of vasche) {
    const corsieTutte = spazi.filter((s) => s.padreId === v.id);
    const qui = attivita.filter((o) => o.spazi.includes(v.id) || o.spazi.some((id) => corsieTutte.some((c) => c.id === id)));
    qui.forEach((o) => usate.add(o.chiave));
    if (!corsieTutte.length) {
      out.push({ id: v.id, nome: v.nome, assetto: null, assettiMisti: false, corsie: [], pezzi: qui.map((o, i) => ({ occorrenza: o, da: 0, quante: 1, colonna: i, colonne: qui.length })) });
      continue;
    }
    const assettiUsati = new Set(qui.flatMap((o) => o.spazi.map((id) => corsieTutte.find((c) => c.id === id)?.assetto).filter((a): a is number => a != null)));
    const disponibili = [...new Set(corsieTutte.map((c) => c.assetto ?? 0))].sort((a, b) => b - a);
    const assetto = assettiUsati.size ? Math.max(...assettiUsati) : disponibili[0];
    const corsie = corsieTutte
      .filter((c) => (c.assetto ?? 0) === assetto)
      .map((c) => ({ id: c.id, nome: c.nome, numero: Number(/(\d+)\s*$/.exec(c.nome)?.[1] ?? 0) }))
      .sort((a, b) => a.numero - b.numero);
    // pezzi: tratti contigui di corsie; le corsie dell'altro assetto si riportano per numero
    const grezzi: { occorrenza: Occorrenza; da: number; quante: number }[] = [];
    for (const o of qui) {
      const indici = o.spazi.includes(v.id)
        ? corsie.map((_, i) => i)
        : o.spazi.map((id) => {
            const c = corsieTutte.find((x) => x.id === id);
            return c ? corsie.findIndex((x) => x.numero === Number(/(\d+)\s*$/.exec(c.nome)?.[1] ?? -1)) : -1;
          }).filter((i) => i >= 0).sort((a, b) => a - b);
      let inizio = -1;
      indici.forEach((x, n) => {
        if (inizio < 0) inizio = x;
        if (indici[n + 1] !== x + 1) {
          grezzi.push({ occorrenza: o, da: inizio, quante: x - inizio + 1 });
          inizio = -1;
        }
      });
    }
    // colonne: chi condivide corsie si mette accanto
    const pezzi: PezzoPianta[] = [];
    for (const g of grezzi.sort((a, b) => a.da - b.da || minuti(a.occorrenza.inizio) - minuti(b.occorrenza.inizio))) {
      const tocca = (p: PezzoPianta) => p.da < g.da + g.quante && g.da < p.da + p.quante;
      const occupate = new Set(pezzi.filter(tocca).map((p) => p.colonna));
      let colonna = 0;
      while (occupate.has(colonna)) colonna++;
      pezzi.push({ ...g, colonna, colonne: 1 });
    }
    for (const p of pezzi) {
      const vicini = pezzi.filter((q) => q.da < p.da + p.quante && p.da < q.da + q.quante);
      p.colonne = Math.max(...vicini.map((q) => q.colonna)) + 1;
    }
    out.push({ id: v.id, nome: v.nome, assetto, assettiMisti: assettiUsati.size > 1, corsie, pezzi });
  }
  return { vasche: out, altrove: attivita.filter((o) => !usate.has(o.chiave)) };
}
