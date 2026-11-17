/**
 * Numeri e importi all'italiana: una sola regola per tutta l'app.
 * - Le migliaia sono sempre raggruppate col punto («1.175»), anche da 1.000 a 9.999 (il motore dei browser,
 *   con `it-IT`, non lo farebbe da solo).
 * - Tra la cifra e «€» c'è uno spazio non divisibile: «35 €» non si spezza mai a capo.
 */

const NBSP = '\u00a0';

const formato = (decimali: 0 | 2) => new Intl.NumberFormat('it-IT', { minimumFractionDigits: decimali, maximumFractionDigits: 2, useGrouping: 'always' });
const F0 = formato(0);
const F2 = formato(2);

/** Un conteggio o una quantità: «1.175», «3». */
export function numero(n: number): string {
  return F0.format(n);
}

const intero = (n: number) => Math.round(n * 100) % 100 === 0;

/**
 * Un importo nel testo e nei riquadri: tondo se è intero («35 €»), con due decimali altrimenti («12,50 €»).
 * `null` → «—». Per elenchi e tabelle con gli importi incolonnati c'è `euroColonna`.
 */
export function euro(n: number | null | undefined): string {
  if (n == null) return '—';
  return `${(intero(n) ? F0 : F2).format(n)}${NBSP}€`;
}

/** Un importo incolonnato (tabelle, elenchi allineati): sempre due decimali, «35,00 €», «1.175,50 €». `null` → «—». */
export function euroColonna(n: number | null | undefined): string {
  if (n == null) return '—';
  return `${F2.format(n)}${NBSP}€`;
}

/** Un importo da mettere in un campo di testo, con la virgola: 0.2 → «0,20», 9.5 → «9,50», 35 → «35». Senza punto delle migliaia. */
export function perCampo(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n)) return '';
  return intero(n) ? String(Math.round(n)) : (Math.round(n * 100) / 100).toFixed(2).replace('.', ',');
}

/**
 * Un importo scritto a mano («1.234,50», «12.5», «0,2», «€ 20», «2.000»): il numero, o null se è vuoto o non si capisce.
 * Spazi (anche non divisibili) e «€» si ignorano; sono ammessi solo cifre, «.», «,» e un segno davanti.
 * Con punto e virgola, l'ultimo dei due è il decimale e l'altro separa le migliaia. Con la sola virgola è il decimale.
 * Con il solo punto, «1.175» e «2.000» sono migliaia, altrimenti è il decimale. Al massimo due decimali.
 */
export function numeroDa(s: string): number | null {
  const t = s.replace(/[\s€\u00a0]/g, '');
  const m = /^([+-]?)([0-9.,]+)$/.exec(t);
  if (!m || !/[0-9]/.test(m[2])) return null;
  const segno = m[1] === '-' ? -1 : 1;
  const c = m[2];
  const migliaia = (parte: string, sep: string) => new RegExp(`^\\d{1,3}(\\${sep}\\d{3})*$`).test(parte);
  let intera: string, dec = '';
  const iP = c.lastIndexOf('.'), iV = c.lastIndexOf(',');
  if (iP >= 0 && iV >= 0) {
    const decSep = iP > iV ? '.' : ',', migSep = decSep === '.' ? ',' : '.';
    const i = Math.max(iP, iV);
    const prima = c.slice(0, i);
    if (prima.includes(decSep) || !migliaia(prima, migSep)) return null;
    intera = prima.split(migSep).join(''); dec = c.slice(i + 1);
  } else if (iV >= 0) {
    if (c.indexOf(',') !== iV) return null;
    intera = c.slice(0, iV); dec = c.slice(iV + 1);
  } else if (iP >= 0) {
    if (/^\d{1,3}(\.\d{3})+$/.test(c)) intera = c.split('.').join('');
    else {
      if (c.indexOf('.') !== iP) return null;
      intera = c.slice(0, iP); dec = c.slice(iP + 1);
    }
  } else intera = c;
  if (dec.length > 2 || (intera === '' && dec === '')) return null;
  const n = Number(`${intera || '0'}.${dec || '0'}`);
  return Number.isFinite(n) ? segno * n : null;
}

/** Gli euro per un file CSV: due decimali con la virgola e niente punto delle migliaia. */
export const euroPerFile = (n: number): string => n.toFixed(2).replace('.', ',');

/** Un campo importo con del testo che non è un importo valido (vuoto non conta: lo decide chi lo usa). Negativi non ammessi. */
export function importoNonValido(s: string): boolean {
  if (s.trim() === '') return false;
  const n = numeroDa(s);
  return n == null || n < 0;
}
