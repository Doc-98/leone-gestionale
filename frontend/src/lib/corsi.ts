import type { Corso, FasciaOraria, Lezione, SpazioPiano } from './api';
import { aggiungiGiorni, daIso, ora } from './date';

/** Calcoli per l'elenco dei corsi, la scheda del corso e il menu rapido dei turni. */

/** I turni ancora in programma (non finiti prima di oggi). */
export function turniAttivi(lezioni: Lezione[], oggi: string): Lezione[] {
  return lezioni.filter((l) => !l.validaAl || l.validaAl >= oggi);
}

/** Quanti turni ogni giorno della settimana: indice 0 = lunedì … 6 = domenica. */
export function turniPerGiorno(lezioni: Lezione[]): number[] {
  const out = [0, 0, 0, 0, 0, 0, 0];
  for (const l of lezioni) out[l.giornoSettimana - 1]++;
  return out;
}

export interface Riempimento {
  iscritti: number;
  /** posti dei turni che hanno un limite; null se nessun turno ne ha */
  posti: number | null;
  /** posti liberi oggi e in quanti turni */
  liberi: number;
  turniLiberi: number;
  inAttesa: number;
  /** percentuale di posti occupati (0–100), null senza posti */
  percentuale: number | null;
}

export function riempimento(lezioni: Lezione[]): Riempimento {
  let iscritti = 0, posti = 0, conPosti = 0, liberi = 0, turniLiberi = 0, inAttesa = 0;
  for (const l of lezioni) {
    iscritti += l.iscritti;
    inAttesa += l.inAttesa;
    if (l.posti != null) {
      conPosti++;
      posti += l.posti;
      const qui = l.posti - l.iscritti;
      if (qui > 0) (liberi += qui, turniLiberi++);
    }
  }
  return {
    iscritti, posti: conPosti ? posti : null, liberi, turniLiberi, inAttesa,
    percentuale: conPosti && posti ? Math.round((iscritti / posti) * 100) : null,
  };
}

export interface Slot {
  chiave: string;
  inizio: string;
  fine: string;
}

const slotDi = (l: Pick<Lezione, 'inizio' | 'fine'>): Slot => ({ chiave: `${ora(l.inizio)}-${ora(l.fine)}`, inizio: ora(l.inizio), fine: ora(l.fine) });

/** Gli orari standard del corso (le coppie inizio–fine dei suoi turni), in ordine. */
export function slotStandard(lezioni: Lezione[]): Slot[] {
  const visti = new Map<string, Slot>();
  for (const l of lezioni) {
    const s = slotDi(l);
    visti.set(s.chiave, s);
  }
  return [...visti.values()].sort((a, b) => a.inizio.localeCompare(b.inizio) || a.fine.localeCompare(b.fine));
}

/** La griglia «per orario»: una riga per orario standard, sette celle (lunedì…domenica) con i turni di quell'ora. */
export function righeOrario(lezioni: Lezione[]): { slot: Slot; celle: Lezione[][] }[] {
  return slotStandard(lezioni).map((slot) => ({
    slot,
    celle: [1, 2, 3, 4, 5, 6, 7].map((g) => lezioni.filter((l) => l.giornoSettimana === g && slotDi(l).chiave === slot.chiave)),
  }));
}

/** Giorno della settimana di una data ISO: 1 = lunedì … 7 = domenica. */
export function giornoDi(data: string): number {
  return ((daIso(data).getDay() + 6) % 7) + 1;
}

/** 1 per i primi sette giorni del mese, 2 per i sette dopo… (il «quarto mercoledì» è la 4). */
export function settimanaDelMese(data: string): number {
  return Math.floor((Number(data.slice(8, 10)) - 1) / 7) + 1;
}

/** La prima data dopo oggi che cade in quel giorno della settimana. */
export function prossimaData(giorno: number, oggi: string): string {
  let d = aggiungiGiorni(oggi, 1);
  while (giornoDi(d) !== giorno) d = aggiungiGiorni(d, 1);
  return d;
}

export type Ripeti = 'una' | 'settimana' | 'mese' | 'giorni';

/** Le date in cui si terrebbero i turni creati dal menu rapido (fino al: compreso; senza fine: un anno). */
export function dateDi(ripeti: Ripeti, giorno: number, giorni: number[], oggi: string, finoAl: string | null): string[] {
  const primo = prossimaData(giorno, oggi);
  if (ripeti === 'una') return [primo];
  const fine = finoAl || aggiungiGiorni(oggi, 365);
  const out: string[] = [];
  if (ripeti === 'settimana') for (let d = primo; d <= fine; d = aggiungiGiorni(d, 7)) out.push(d);
  else if (ripeti === 'mese') {
    const n = settimanaDelMese(primo);
    for (let d = primo; d <= fine; d = aggiungiGiorni(d, 7)) if (settimanaDelMese(d) === n) out.push(d);
  } else {
    for (let d = aggiungiGiorni(oggi, 1); d <= fine; d = aggiungiGiorni(d, 1)) if (giorni.includes(giornoDi(d))) out.push(d);
  }
  return out;
}

const ORDINALI = ['primo', 'secondo', 'terzo', 'quarto', 'quinto'];
export const ordinale = (n: number) => ORDINALI[n - 1] ?? `${n}°`;

/** Il turno cade in quella data (giorno, validità e, se mensile, la stessa settimana del mese della prima data)? */
export function cadeIl(l: Lezione, data: string): boolean {
  if (giornoDi(data) !== l.giornoSettimana || data < l.validaDal || (l.validaAl && data > l.validaAl)) return false;
  return l.cadenza !== 'MENSILE' || settimanaDelMese(data) === settimanaDelMese(l.validaDal);
}

const minuti = (h: string) => Number(h.slice(0, 2)) * 60 + Number(h.slice(3, 5));

/**
 * Gli spazi già occupati da un altro turno (di qualunque corso) in quel giorno e orario: una vasca intera occupa
 * tutte le sue corsie, una corsia occupa anche la vasca intera.
 */
export function spaziOccupati(corsi: Corso[], spazi: SpazioPiano[], data: string, inizio: string, fine: string): Set<string> {
  const padre = new Map(spazi.map((s) => [s.id, s.padreId]));
  const figli = (id: string) => spazi.filter((s) => s.padreId === id).map((s) => s.id);
  const out = new Set<string>();
  const [a, b] = [minuti(inizio), minuti(fine)];
  for (const c of corsi) {
    if (!c.attivo) continue;
    for (const l of c.lezioni) {
      if (!cadeIl(l, data) || minuti(ora(l.inizio)) >= b || minuti(ora(l.fine)) <= a) continue;
      for (const id of l.spazi) {
        out.add(id);
        figli(id).forEach((f) => out.add(f));
        const p = padre.get(id);
        if (p) out.add(p);
      }
    }
  }
  return out;
}

/** Il valore più frequente (per i posti tipici del corso, la corsia abituale…). */
export function piuFrequente<T>(valori: T[]): T | undefined {
  const conta = new Map<T, number>();
  for (const v of valori) conta.set(v, (conta.get(v) ?? 0) + 1);
  let migliore: T | undefined, quante = 0;
  for (const [v, n] of conta) if (n > quante) (migliore = v, quante = n);
  return migliore;
}

/**
 * Tra quali spazi si sceglie la corsia di un nuovo turno: le corsie della vasca dove il corso sta di solito, nello
 * stesso assetto; se il corso usa una vasca intera, quella vasca.
 */
export function corsieDelCorso(lezioni: Lezione[], spazi: SpazioPiano[]): SpazioPiano[] {
  const perId = new Map(spazi.map((s) => [s.id, s]));
  const abituale = perId.get(piuFrequente(lezioni.flatMap((l) => l.spazi)) ?? '');
  const acqua = spazi.filter((s) => s.tipo === 'CORSIA' || s.tipo === 'VASCA');
  if (!abituale) {
    const vasca = acqua.find((s) => !s.padreId && spazi.some((c) => c.padreId === s.id));
    return vasca ? corsieDi(vasca.id, null, spazi) : acqua.filter((s) => !s.padreId);
  }
  if (!abituale.padreId) {
    const sue = spazi.filter((s) => s.padreId === abituale.id);
    return sue.length && lezioni.every((l) => !l.spazi.includes(abituale.id)) ? corsieDi(abituale.id, null, spazi) : [abituale];
  }
  return corsieDi(abituale.padreId, abituale.assetto, spazi);
}

/** L'assetto (numero di corsie) della vasca in quel giorno e orario, dalle fasce definite; null se nessuna lo dice. */
export function assettoIn(fasce: FasciaOraria[], vascaId: string, data: string, inizio: string, fine: string): number | null {
  const [a, b] = [minuti(inizio), minuti(fine)];
  let migliore: number | null = null, sovrap = 0;
  for (const f of fasce) {
    if (f.vascaId !== vascaId || f.assetto == null || f.giornoSettimana !== giornoDi(data) || f.validaDal > data || (f.validaAl && f.validaAl < data)) continue;
    const s = Math.min(b, minuti(ora(f.fine))) - Math.max(a, minuti(ora(f.inizio)));
    if (s > sovrap) (sovrap = s, migliore = f.assetto);
  }
  return migliore;
}

/** Le corsie tra cui scegliere, nell'assetto in uso in quel momento (se le fasce ne indicano uno diverso). */
export function corsieInAssetto(opzioni: SpazioPiano[], assetto: number | null, spazi: SpazioPiano[]): SpazioPiano[] {
  const vasca = opzioni[0]?.padreId;
  if (!vasca || assetto == null || opzioni[0].assetto === assetto) return opzioni;
  const altre = corsieDi(vasca, assetto, spazi);
  return altre.length ? altre : opzioni;
}

function corsieDi(vascaId: string, assetto: number | null, spazi: SpazioPiano[]): SpazioPiano[] {
  const tutte = spazi.filter((s) => s.padreId === vascaId);
  const quale = assetto ?? Math.max(...tutte.map((s) => s.assetto ?? 0));
  return tutte.filter((s) => (s.assetto ?? 0) === quale).sort((a, b) => numeroDi(a) - numeroDi(b));
}

export const numeroDi = (s: Pick<SpazioPiano, 'nome'>) => Number(/(\d+)\s*$/.exec(s.nome)?.[1] ?? 0);

/** «Corsia 4», «Corsie 1–2», «Vasca piccola · calda»: il dove di un turno, corto. */
export function doveBreve(ids: string[], spazi: SpazioPiano[]): string {
  const sp = ids.map((id) => spazi.find((s) => s.id === id)).filter((s): s is SpazioPiano => !!s);
  if (!sp.length) return '';
  const corsie = sp.filter((s) => s.padreId);
  const vasche = sp.filter((s) => !s.padreId).map((s) => s.nome);
  const numeri = [...new Set(corsie.map(numeroDi))].sort((a, b) => a - b);
  const consecutive = numeri.every((n, i) => i === 0 || n === numeri[i - 1] + 1);
  const testo = !numeri.length ? '' : numeri.length === 1 ? `Corsia ${numeri[0]}` : consecutive ? `Corsie ${numeri[0]}–${numeri[numeri.length - 1]}` : `Corsie ${numeri.join(', ')}`;
  return [testo, ...vasche].filter(Boolean).join(' + ');
}

/** «S. Moretti, L. Ferri» */
export function nomiBrevi(persone: { nome: string }[]): string {
  return persone.map((p) => {
    const parti = p.nome.trim().split(/\s+/);
    return parti.length > 1 ? `${parti[0][0]}. ${parti.slice(1).join(' ')}` : p.nome;
  }).join(', ');
}
