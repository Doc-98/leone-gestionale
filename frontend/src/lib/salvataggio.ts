/**
 * Il salvataggio delle impostazioni di pagina (barra unica, ADR 0028): quante cose sono cambiate rispetto a com'erano
 * quando la pagina è stata letta, e la frase che lo dice («1 modifica da salvare», «3 modifiche da salvare»).
 */

const eOggetto = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
/** «Niente»: null, undefined e il testo vuoto (un campo svuotato e tornato vuoto non è una modifica). */
const vuoto = (v: unknown) => v === null || v === undefined || v === '';

/**
 * Quanti valori sono cambiati: si confronta campo per campo (anche dentro oggetti e elenchi) e ogni campo diverso vale 1.
 * - Un elenco si confronta per posizione; un elemento in più o in meno vale 1, qualunque cosa contenga.
 * - `null`, `undefined` e `''` sono la stessa cosa («niente»); un numero e il suo testo («10» e 10) sono lo stesso valore.
 * - Uguali (o entrambi vuoti) → 0.
 */
export function contaModifiche(originale: unknown, bozza: unknown): number {
  if (vuoto(originale) && vuoto(bozza)) return 0;
  if (Array.isArray(originale) && Array.isArray(bozza)) {
    let n = Math.abs(originale.length - bozza.length);
    for (let i = 0; i < Math.min(originale.length, bozza.length); i++) n += contaModifiche(originale[i], bozza[i]);
    return n;
  }
  if (eOggetto(originale) && eOggetto(bozza)) {
    let n = 0;
    for (const k of new Set([...Object.keys(originale), ...Object.keys(bozza)])) n += contaModifiche(originale[k], bozza[k]);
    return n;
  }
  // un campo numerico scritto a mano è un testo, ma «10» e 10 sono lo stesso valore
  if (typeof originale === 'number' && typeof bozza === 'string') return uguali(originale, bozza) ? 0 : 1;
  if (typeof originale === 'string' && typeof bozza === 'number') return uguali(bozza, originale) ? 0 : 1;
  return Object.is(originale, bozza) ? 0 : 1;
}
const uguali = (n: number, testo: string) => testo.trim() !== '' && Number(testo) === n;

/** «1 modifica da salvare», «3 modifiche da salvare». */
export const testoModifiche = (n: number): string => `${n} ${n === 1 ? 'modifica' : 'modifiche'} da salvare`;

/** Un numero intero tra `min` e `max` (estremi compresi), scritto come numero o come testo; vuoto, decimale o fuori misura → false. */
export function interoFra(valore: unknown, min: number, max: number): boolean {
  if (valore === null || valore === undefined) return false;
  if (typeof valore === 'string' && valore.trim() === '') return false;
  const n = Number(valore);
  return Number.isInteger(n) && n >= min && n <= max;
}
