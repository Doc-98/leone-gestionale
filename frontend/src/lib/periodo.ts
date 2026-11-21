import { aggiungiGiorni, dataAgenda, giornoTitolo, lunedi, nomeMese, spostaMese, titoloSettimana } from './date';

/**
 * Il periodo della barra di pagina (ADR 0028): un giorno, una settimana (da lunedì) o un mese. Le date sono
 * stringhe «aaaa-mm-gg» come in `lib/date.ts`; `inizio` può essere un giorno qualsiasi del periodo: le funzioni lo
 * riportano da sole al primo giorno.
 */
export type Passo = 'giorno' | 'settimana' | 'mese';

/** Il primo giorno del periodo che contiene `data`: il giorno stesso, il lunedì, il primo del mese. */
export function inizioPeriodo(data: string, passo: Passo): string {
  if (passo === 'giorno') return data;
  if (passo === 'settimana') return lunedi(data);
  return `${data.slice(0, 7)}-01`;
}

/** Il periodo `verso` passi più in là (negativo: più indietro), sempre dal suo primo giorno. */
export function spostaPeriodo(inizio: string, passo: Passo, verso: number): string {
  const da = inizioPeriodo(inizio, passo);
  if (passo === 'giorno') return aggiungiGiorni(da, verso);
  if (passo === 'settimana') return aggiungiGiorni(da, 7 * verso);
  return `${spostaMese(da.slice(0, 7), verso)}-01`;
}

/** L'ultimo giorno del periodo: il giorno stesso, la domenica, l'ultimo del mese. */
export function finePeriodo(inizio: string, passo: Passo): string {
  const da = inizioPeriodo(inizio, passo);
  if (passo === 'giorno') return da;
  if (passo === 'settimana') return aggiungiGiorni(da, 6);
  return aggiungiGiorni(`${spostaMese(da.slice(0, 7), 1)}-01`, -1);
}

/** Vero se `data` cade dentro il periodo che comincia a `inizio`. */
export function contiene(inizio: string, passo: Passo, data: string): boolean {
  const da = inizioPeriodo(inizio, passo);
  return data >= da && data <= finePeriodo(da, passo);
}

/**
 * La scritta del periodo: «9–15 novembre», «26 ottobre – 1 novembre» (con `corta` «26 ott – 1 nov»), «Novembre 2026»,
 * «Lunedì 9 novembre» (con `corta` «lun 9/11»).
 */
export function etichettaPeriodo(inizio: string, passo: Passo, corta: boolean): string {
  const da = inizioPeriodo(inizio, passo);
  if (passo === 'settimana') return titoloSettimana(da, corta);
  if (passo === 'mese') return nomeMese(da.slice(0, 7)).replace(/^./, (c) => c.toUpperCase());
  return corta ? dataAgenda(da) : giornoTitolo(da);
}

/** Fino a 3 opzioni un segmentato, oltre una tendina (regola «Interfaccia»). */
export function vistaControllo(n: number): 'segmentato' | 'tendina' {
  return n <= 3 ? 'segmentato' : 'tendina';
}

/** Il tipo di calendario nativo da aprire sulla scritta: il mese ha il suo selettore, gli altri la data. */
export function tipoCalendario(passo: Passo, haMese: boolean): 'date' | 'month' {
  return passo === 'mese' && haMese ? 'month' : 'date';
}

/** Dal valore del calendario nativo al primo giorno del periodo («2026-11» per il mese diventa «2026-11-01»). */
export function daCalendario(valore: string, passo: Passo): string | null {
  if (/^\d{4}-\d{2}$/.test(valore)) return inizioPeriodo(`${valore}-01`, passo);
  if (/^\d{4}-\d{2}-\d{2}$/.test(valore)) return inizioPeriodo(valore, passo);
  return null;
}

/** Il valore da mettere nel calendario nativo, per il tipo scelto. */
export function perCalendario(inizio: string, tipo: 'date' | 'month'): string {
  return tipo === 'month' ? inizio.slice(0, 7) : inizio;
}

/** Il periodo successivo si può aprire? Con `max` (es. oggi) non si va oltre. */
export function sidopo(inizio: string, passo: Passo, max?: string): boolean {
  return max == null || spostaPeriodo(inizio, passo, 1) <= max;
}

/** Un periodo scelto oltre `max` (dal calendario nativo) torna al periodo che contiene `max`. */
export function limitaPeriodo(inizio: string, passo: Passo, max?: string): string {
  const da = inizioPeriodo(inizio, passo);
  return max != null && da > max ? inizioPeriodo(max, passo) : da;
}
