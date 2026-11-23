/**
 * Quando un campo delle impostazioni è scritto male: le stesse misure che controlla il backend, così «Salva» si spegne
 * prima di mandare e il campo dice cosa fare (messaggio breve, collegato con `aria-describedby`).
 * Ogni funzione restituisce `true` se il valore NON va bene.
 */
import { interoFra } from './salvataggio';

export const MSG_GIORNI_ANTICIPO = 'Da 0 a 365 giorni.';
export const MSG_CIFRE = 'Da 1 a 8 cifre.';
export const MSG_PERCENTUALE = 'Le percentuali vanno da 0 a 100.';
export const MSG_PRIMA_RATA = 'La prima rata va da 1 a 99.';
export const msgParametro = (max: number) => `Da 0 a ${max}.`;

/** «Proponi il tesseramento»: giorni interi da 0 a 365. */
export const giorniAnticipoNonValidi = (v: unknown) => !interoFra(v, 0, 365);

/** «Cifre del numero» di una serie di ricevute: da 1 a 8. */
export const cifreNonValide = (v: unknown) => !interoFra(v, 1, 8);

/** Una percentuale di rimborso o di riduzione: da 0 a 100. */
export const percentualeNonValida = (v: unknown) => !interoFra(v, 0, 100);

/** Uno scaglione conta solo se ha una data (gli altri non si salvano): allora la sua percentuale deve andare bene. */
export const scaglioneNonValido = (s: { data: string; percentuale: unknown }) => !!s.data && percentualeNonValida(s.percentuale);

/** La percentuale della 1ª rata: da 1 a 99, sempre (il backend la controlla anche a rate spente). */
export const primaRataNonValida = (v: unknown) => !interoFra(v, 1, 99);

/** Un parametro di una regola del tornello: da 0 al massimo; vuoto solo se è facoltativo e «se vuoto» è spuntato. */
export const parametroRegolaNonValido = (valore: unknown, vuoto: boolean, facoltativo: boolean, max: number) =>
  !(vuoto && facoltativo) && !interoFra(valore, 0, max);
