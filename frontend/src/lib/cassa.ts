import type { ControlloModo, ImpostazioniCassa, Incassi, PaginaCassa, PagamentoRegistrato, SessioneCassa, StatoCassa, TipoMovimentoCassa, TipoScadenza, VoceScadenza } from './api';
import { aggiungiGiorni, daIso, iso, lunedi } from './date';

/**
 * Scadenze e incassi: le funzioni pure dietro le viste della segreteria (gruppi per urgenza, calendario, grafico per
 * giorno, registro di cassa). Le pagine le usano, i test le provano.
 */

// ── Scadenze ────────────────────────────────────────────────────────────

/** Come mostrare le scadenze: un elenco per urgenza o il calendario delle prossime settimane. */
export type VistaScadenze = 'elenco' | 'calendario';

export type TipoBreve = 'cert' | 'abb' | 'carnet' | 'tess' | 'pag';
export const tipoBreve = (t: TipoScadenza): TipoBreve =>
  t.startsWith('CERTIFICATO') ? 'cert' : t.startsWith('TESSERAMENTO') ? 'tess' : t === 'CARNET' ? 'carnet' : t === 'PAGAMENTO' ? 'pag' : 'abb';

/** Il filtro per tipo: i carnet stanno con gli abbonamenti. */
export type FiltroTipo = 'tutti' | 'cert' | 'abb' | 'tess' | 'pag';
export const nelFiltro = (v: VoceScadenza, f: FiltroTipo) => f === 'tutti' || (tipoBreve(v.tipo) === 'carnet' ? 'abb' : tipoBreve(v.tipo)) === f;

export type Urgenza = 'scaduti' | 'oggi' | 'settimana' | 'avanti';
export const URGENZE: { id: Urgenza; nome: string; spiega: string }[] = [
  { id: 'scaduti', nome: 'Scaduti o mancanti', spiega: 'da sistemare subito' },
  { id: 'oggi', nome: 'Scadono oggi', spiega: 'da domani rossi al tornello' },
  { id: 'settimana', nome: 'Entro una settimana', spiega: 'nei prossimi 7 giorni' },
  { id: 'avanti', nome: 'Più avanti', spiega: '' },
];

export function urgenza(v: Pick<VoceScadenza, 'giorni'>): Urgenza {
  if (v.giorni == null || v.giorni < 0) return 'scaduti';
  if (v.giorni === 0) return 'oggi';
  return v.giorni <= 7 ? 'settimana' : 'avanti';
}

/** Quando, a parole, e il colore: «Scaduto da 15 giorni», «Scade oggi», «Tra 3 giorni». */
export function quando(v: Pick<VoceScadenza, 'giorni' | 'tipo'>): { testo: string; classe: 'rosso' | 'arancio' | 'neutro' } {
  if (v.giorni == null) return { testo: v.tipo === 'CARNET' ? 'Da rinnovare' : 'Manca', classe: 'rosso' };
  if (v.giorni < 0) return { testo: v.giorni === -1 ? 'Scaduto ieri' : `Scaduto da ${-v.giorni} giorni`, classe: 'rosso' };
  if (v.giorni === 0) return { testo: 'Scade oggi', classe: 'arancio' };
  return { testo: v.giorni === 1 ? 'Domani' : `Tra ${v.giorni} giorni`, classe: v.giorni <= 7 ? 'arancio' : 'neutro' };
}

/** Cosa fare per sistemare (si fa nella scheda del cliente). */
export const RIMEDI: Record<TipoBreve, string> = { cert: 'Registra il certificato', abb: 'Rinnova', carnet: 'Nuovo carnet', tess: 'Tessera', pag: 'Fai pagare' };

/** A una voce si può mandare il promemoria: serve l'email (e per il certificato mai consegnato non c'è un modello). */
export const promemoriaPossibile = (v: VoceScadenza) => !!v.email && v.tipo !== 'CERTIFICATO_MANCANTE';

/** Le settimane del calendario da lunedì: `n` righe di sette date ISO, a partire dalla settimana di `oggi`. */
export function settimane(oggi: string, n = 5): string[][] {
  const primo = lunedi(oggi);
  return Array.from({ length: n }, (_, w) => Array.from({ length: 7 }, (_, i) => aggiungiGiorni(primo, w * 7 + i)));
}

/** Le scadenze con una data, per giorno. */
export function perGiornoScadenze(voci: VoceScadenza[]): Map<string, VoceScadenza[]> {
  const m = new Map<string, VoceScadenza[]>();
  for (const v of voci) {
    if (!v.scadenza || v.giorni == null || v.giorni < 0) continue;
    m.set(v.scadenza, [...(m.get(v.scadenza) ?? []), v]);
  }
  return m;
}

// ── Incassi ─────────────────────────────────────────────────────────────

/** Come mostrare gli incassi: la cassa del giorno, il periodo con l'andamento, il registro. */
export type VistaIncassi = 'cassa' | 'periodo' | 'registro' | 'chiusure';
export const VISTE_INCASSI: { id: VistaIncassi; nome: string; spiega: string }[] = [
  { id: 'cassa', nome: 'Cassa del giorno', spiega: 'il totale di un giorno, i pagamenti e la chiusura' },
  { id: 'periodo', nome: 'Periodo', spiega: "un periodo con l'andamento giorno per giorno" },
  { id: 'registro', nome: 'Registro', spiega: 'entrate e uscite in ordine, come un libro cassa' },
  { id: 'chiusure', nome: 'Chiusure di cassa', spiega: 'le chiusure di un periodo, con le differenze e i riepiloghi da stampare' },
];

/** I colori dei metodi, nell'ordine in cui la società li ha messi. */
const COLORI = ['var(--passa)', 'var(--area-m)', 'var(--area-p)', 'var(--area-a)', 'var(--cloro)', 'var(--area-x)'];
export const coloreMetodo = (i: number) => COLORI[i % COLORI.length];

const cent = (n: number) => Math.round(n * 100);
const euro = (c: number) => c / 100;

/** I contanti di un insieme di pagamenti e uscite: quanto è entrato e quanto è uscito in contanti. */
export function contanti(d: Pick<Incassi, 'pagamenti' | 'uscite'>): { entrati: number; usciti: number } {
  let e = 0, u = 0;
  for (const p of d.pagamenti) if (!p.annullatoIl) for (const m of p.metodi) if (m.contanti) e += cent(m.importo);
  for (const x of d.uscite) if (x.contanti) u += cent(x.importo);
  return { entrati: euro(e), usciti: euro(u) };
}

export interface Colonna {
  etichetta: string;
  da: string;
  a: string;
  totale: number;
  /** per metodo, nello stesso ordine di `metodi` */
  pezzi: number[];
}

/** Gli incassi raggruppati per giorno (o per settimana, da lunedì) tra `da` e `a`, divisi per metodo. */
export function andamento(pagamenti: PagamentoRegistrato[], metodi: string[], da: string, a: string, per: 'giorno' | 'settimana'): Colonna[] {
  const out: Colonna[] = [];
  const chiave = (d: string) => (per === 'giorno' ? d : lunedi(d) < da ? da : lunedi(d));
  for (let d = da; d <= a; d = aggiungiGiorni(d, 1)) {
    const k = chiave(d);
    let c = out[out.length - 1];
    if (!c || c.da !== k) {
      c = { etichetta: per === 'giorno' ? String(daIso(d).getDate()) : '', da: k, a: d, totale: 0, pezzi: metodi.map(() => 0) };
      out.push(c);
    }
    c.a = d;
  }
  for (const c of out) if (per === 'settimana') c.etichetta = c.da === c.a ? String(daIso(c.da).getDate()) : `${daIso(c.da).getDate()}–${daIso(c.a).getDate()}`;
  for (const p of pagamenti) {
    if (p.annullatoIl) continue;
    const c = out.find((x) => p.data >= x.da && p.data <= x.a);
    if (!c) continue;
    for (const m of p.metodi) {
      const i = metodi.indexOf(m.metodo);
      if (i >= 0) c.pezzi[i] = euro(cent(c.pezzi[i]) + cent(m.importo));
    }
    c.totale = euro(cent(c.totale) + cent(p.importo));
  }
  return out;
}

export interface RigaRegistro {
  id: string;
  istante: string;
  chi: string;
  per: string;
  metodo: string;
  entrata: number | null;
  uscita: number | null;
  annullato: boolean;
  motivo: string | null;
  /** contanti entrati meno usciti fino a questa riga compresa; null se la riga non tocca i contanti */
  contanti: number | null;
}

/** Entrate e uscite in ordine di tempo, con i contanti progressivi (gli annullati restano, ma non contano). */
export function registro(d: Pick<Incassi, 'pagamenti' | 'uscite'>): RigaRegistro[] {
  const righe: Omit<RigaRegistro, 'contanti'>[] = [];
  for (const p of d.pagamenti)
    righe.push({
      id: p.id, istante: p.registratoIl, chi: p.pagante ?? '', annullato: !!p.annullatoIl, motivo: p.motivoAnnullamento,
      per: p.voci.map((v) => `${v.persona && v.persona !== p.pagante ? v.persona + ' · ' : ''}${v.descrizione}`).join(' · '),
      metodo: p.metodi.map((m) => m.metodo).join(' + '), entrata: p.importo, uscita: null,
    });
  for (const u of d.uscite)
    righe.push({ id: u.id, istante: u.registratoIl, chi: u.persona ?? '', per: u.descrizione, metodo: u.metodo, entrata: null, uscita: u.importo, annullato: false, motivo: null });
  righe.sort((x, y) => x.istante.localeCompare(y.istante));
  let c = 0;
  const pag = new Map(d.pagamenti.map((p) => [p.id, p]));
  const usc = new Map(d.uscite.map((u) => [u.id, u]));
  return righe.map((r) => {
    let tocca = false;
    if (!r.annullato) {
      if (r.uscita != null && usc.get(r.id)?.contanti) { c -= cent(r.uscita); tocca = true; }
      const p = pag.get(r.id);
      if (p) for (const m of p.metodi) if (m.contanti) { c += cent(m.importo); tocca = true; }
    }
    return { ...r, contanti: tocca ? euro(c) : null };
  });
}

/** Il periodo di oggi in ISO, per le scorciatoie. */
export const oggiIso = () => iso(new Date());

// ── Chiusura di cassa (ADR 0018, parte 11) ──────────────────────────────

/** Un importo scritto a mano («1.234,50», «12.5», «€ 20»): il numero, o null se è vuoto o non si capisce. */
export function numeroDa(s: string): number | null {
  const t = s.replace(/\s|€/g, '');
  if (!t) return null;
  // con la virgola, i punti sono le migliaia; senza, il punto è il decimale
  const n = Number(t.includes(',') ? t.replace(/\./g, '').replace(',', '.') : t);
  return Number.isFinite(n) ? Math.round(n * 100) / 100 : null;
}

/** I tagli di banconote e monete, per contare il cassetto. */
export const TAGLI = [500, 200, 100, 50, 20, 10, 5, 2, 1, 0.5, 0.2, 0.1, 0.05, 0.02, 0.01];

/** Quanto fa un conteggio per tagli (quanti pezzi per taglio). */
export function sommaTagli(pezzi: Record<string, number | null | undefined>): number {
  return TAGLI.reduce((t, v) => t + Math.max(0, Math.floor(Number(pezzi[String(v)]) || 0)) * cent(v), 0) / 100;
}

/** La differenza tra contati e attesi, a parole e con il tono. */
export function esitoDifferenza(d: number): { testo: string; tono: 'ok' | 'piu' | 'meno' } {
  const c = cent(d);
  if (c === 0) return { testo: 'La cassa torna', tono: 'ok' };
  return c > 0 ? { testo: 'Ci sono più contanti del previsto', tono: 'piu' } : { testo: 'Mancano dei contanti', tono: 'meno' };
}

/** La differenza con il segno: «+3,00 €», «−2,00 €», «0,00 €». */
export const conSegno = (d: number, euroFn: (n: number) => string) => `${cent(d) > 0 ? '+' : cent(d) < 0 ? '−' : ''}${euroFn(Math.abs(d))}`;

/** Quanto si lascia di solito per la volta dopo: il fondo delle impostazioni, o tutto se c'è meno. */
export const lasciatoProposto = (contati: number, imp: Pick<ImpostazioniCassa, 'importoFondo'>) => Math.min(contati, imp.importoFondo);

/** La cassa da mostrare su questo computer: con una cassa per postazione quella scelta (se c'è ancora), se no la principale. */
export function statoDaMostrare(p: PaginaCassa, scelta: string | null): StatoCassa | undefined {
  if (p.impostazioni.casse === 'PER_POSTAZIONE') {
    const s = p.stati.find((x) => x.cassa.id === scelta);
    if (s) return s;
  }
  return p.stati.find((x) => x.cassa.principale) ?? p.stati[0];
}

/** Con la chiusura per turno chi entra apre la cassa; con il fondo da contare anche a fine giornata. */
export const serveAprire = (imp: Pick<ImpostazioniCassa, 'chiusura' | 'fondo'>) => imp.chiusura === 'TURNO' || imp.fondo === 'CONTATO';

export const MOVIMENTI: { id: TipoMovimentoCassa; nome: string; spiega: string; segno: '+' | '−' }[] = [
  { id: 'VERSAMENTO', nome: 'Versamento', spiega: 'soldi tolti per la banca o la cassaforte', segno: '−' },
  { id: 'SPESA', nome: 'Spesa', spiega: 'una piccola spesa pagata con la cassa', segno: '−' },
  { id: 'AGGIUNTA', nome: 'Aggiunta', spiega: 'soldi messi nel cassetto (es. monete per il resto)', segno: '+' },
];

/** Come si parte con il fondo, a parole, per le impostazioni e l'apertura. */
export function spiegaFondo(imp: Pick<ImpostazioniCassa, 'fondo' | 'importoFondo'>, euroFn: (n: number) => string): string {
  if (imp.fondo === 'FISSO') return `Si parte sempre con ${euroFn(imp.importoFondo)}; il resto si versa.`;
  if (imp.fondo === 'RIPORTATO') return 'Si parte da quello lasciato alla chiusura prima.';
  return 'A ogni apertura si riconta il fondo e si vede se torna con quello lasciato.';
}

/** La differenza va oltre la tolleranza (con tolleranza 0, ogni differenza conta). */
export function oltreTolleranza(differenza: number, tolleranza: number): boolean {
  const d = Math.abs(cent(differenza));
  return cent(tolleranza) === 0 ? d !== 0 : d > cent(tolleranza);
}

/** Lo stato di una chiusura nello storico, dal più importante: da controllare, controllata, non torna, torna. */
export function statoChiusura(s: Pick<SessioneCassa, 'daControllare' | 'controllataIl' | 'differenza' | 'riscontri'>): { testo: string; tono: 'warn' | 'cloro' | 'ferma' | 'passa' } {
  if (s.daControllare && !s.controllataIl) return { testo: 'Da controllare', tono: 'warn' };
  if (s.controllataIl) return { testo: 'Controllata', tono: 'cloro' };
  const torna = cent(s.differenza ?? 0) === 0 && !(s.riscontri ?? []).some((r) => r.dichiarato != null && cent(r.differenza) !== 0);
  return torna ? { testo: 'Torna', tono: 'passa' } : { testo: 'Non torna', tono: 'ferma' };
}

/** Chi ha chiuso, quante volte e con che differenze sommate (per vedere se si ripetono): le persone con più chiusure prima. */
export function perPersona(chiusure: Pick<SessioneCassa, 'chiusaDa' | 'differenza'>[]): { chi: string; chiusure: number; differenze: number; nonTornano: number }[] {
  const m = new Map<string, { chi: string; chiusure: number; c: number; nonTornano: number }>();
  for (const s of chiusure) {
    const chi = s.chiusaDa ?? '—';
    const x = m.get(chi) ?? { chi, chiusure: 0, c: 0, nonTornano: 0 };
    x.chiusure++;
    x.c += cent(s.differenza ?? 0);
    if (cent(s.differenza ?? 0) !== 0) x.nonTornano++;
    m.set(chi, x);
  }
  return [...m.values()].sort((a, b) => b.chiusure - a.chiusure || a.chi.localeCompare(b.chi)).map((x) => ({ chi: x.chi, chiusure: x.chiusure, differenze: x.c / 100, nonTornano: x.nonTornano }));
}

export const CONTROLLI: { id: ControlloModo; nome: string }[] = [
  { id: 'NESSUNA', nome: 'Nessuna' },
  { id: 'OLTRE', nome: 'Oltre la tolleranza' },
  { id: 'TUTTE', nome: 'Tutte' },
];

