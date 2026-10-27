import type { Incassi, PagamentoRegistrato, TipoScadenza, VoceScadenza } from './api';
import { aggiungiGiorni, daIso, iso, lunedi } from './date';

/**
 * Scadenze e incassi: le funzioni pure dietro le viste della segreteria (gruppi per urgenza, calendario, grafico per
 * giorno, registro di cassa). Le pagine le usano, i test le provano.
 */

// ── Scadenze ────────────────────────────────────────────────────────────

/** Come mostrare le scadenze: un elenco per urgenza o il calendario delle prossime settimane. */
export type VistaScadenze = 'elenco' | 'calendario';

export type TipoBreve = 'cert' | 'abb' | 'carnet' | 'tess';
export const tipoBreve = (t: TipoScadenza): TipoBreve =>
  t.startsWith('CERTIFICATO') ? 'cert' : t.startsWith('TESSERAMENTO') ? 'tess' : t === 'CARNET' ? 'carnet' : 'abb';

/** Il filtro per tipo: i carnet stanno con gli abbonamenti. */
export type FiltroTipo = 'tutti' | 'cert' | 'abb' | 'tess';
export const nelFiltro = (v: VoceScadenza, f: FiltroTipo) => f === 'tutti' || (tipoBreve(v.tipo) === 'carnet' ? 'abb' : tipoBreve(v.tipo)) === f;

export type Urgenza = 'scaduti' | 'oggi' | 'settimana' | 'avanti';
export const URGENZE: { id: Urgenza; nome: string; spiega: string }[] = [
  { id: 'scaduti', nome: 'Scaduti o mancanti', spiega: 'non passano il tornello' },
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
export const RIMEDI: Record<TipoBreve, string> = { cert: 'Registra il certificato', abb: 'Rinnova', carnet: 'Nuovo carnet', tess: 'Tessera' };

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
export type VistaIncassi = 'cassa' | 'periodo' | 'registro';
export const VISTE_INCASSI: { id: VistaIncassi; nome: string; spiega: string }[] = [
  { id: 'cassa', nome: 'Cassa del giorno', spiega: 'il totale di un giorno, i pagamenti e la chiusura' },
  { id: 'periodo', nome: 'Periodo', spiega: "un periodo con l'andamento giorno per giorno" },
  { id: 'registro', nome: 'Registro', spiega: 'entrate e uscite in ordine, come un libro cassa' },
];

/** I colori dei metodi, nell'ordine in cui la società li ha messi. */
const COLORI = ['var(--passa)', 'var(--area-m)', 'var(--area-p)', 'var(--area-a)', 'var(--cloro)', 'var(--area-x)'];
export const coloreMetodo = (i: number) => COLORI[i % COLORI.length];

export const eContanti = (metodo: string) => /contant/i.test(metodo);

const cent = (n: number) => Math.round(n * 100);
const euro = (c: number) => c / 100;

/** I contanti di un insieme di pagamenti e uscite: quanto è entrato e quanto è uscito in contanti. */
export function contanti(d: Pick<Incassi, 'pagamenti' | 'uscite'>): { entrati: number; usciti: number } {
  let e = 0, u = 0;
  for (const p of d.pagamenti) if (!p.annullatoIl) for (const m of p.metodi) if (eContanti(m.metodo)) e += cent(m.importo);
  for (const x of d.uscite) if (eContanti(x.metodo)) u += cent(x.importo);
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
  return righe.map((r) => {
    let tocca = false;
    if (!r.annullato) {
      if (r.uscita != null && eContanti(r.metodo)) { c -= cent(r.uscita); tocca = true; }
      const p = pag.get(r.id);
      if (p) for (const m of p.metodi) if (eContanti(m.metodo)) { c += cent(m.importo); tocca = true; }
    }
    return { ...r, contanti: tocca ? euro(c) : null };
  });
}

/** Il periodo di oggi in ISO, per le scorciatoie. */
export const oggiIso = () => iso(new Date());

/** Il messaggio della chiusura di cassa, finché non arrivano le informazioni dalla segreteria (ADR 0018, parte 11). */
export const CHIUSURA_IN_SVILUPPO =
  'La chiusura di cassa è in fase di sviluppo: stiamo aspettando dalla segreteria come si chiude la cassa oggi (fondo, versamenti, ' +
  'chi controlla e quando) per costruirla nel modo giusto. Per ora i conti qui sopra sono solo un aiuto e non viene salvato niente.';
