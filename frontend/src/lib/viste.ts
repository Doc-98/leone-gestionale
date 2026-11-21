/**
 * Le viste di una pagina nella barra (ADR 0028): quali sono usabili su questo schermo e quale mostrare quando la scelta
 * salvata dall'utente non lo è (la vista «A colonne» salvata e aperta sul telefono).
 */
export interface OpzioneVista {
  id: string;
  nome: string;
  /** Il nome corto per il telefono, dove lo spazio è poco («Settimana» invece di «La settimana»). */
  breve?: string;
  /** Una vista che sul telefono non si può usare non compare: non resta spenta. */
  solo?: 'larga';
}

/** Le opzioni che si possono offrire: quelle riservate agli schermi larghi spariscono sugli altri. */
export function opzioniUsabili<T extends OpzioneVista>(opzioni: readonly T[], larga: boolean): T[] {
  return opzioni.filter((o) => o.solo !== 'larga' || larga);
}

/** La vista da mostrare: la scelta se è tra le opzioni, altrimenti la prima (o `predefinita` se c'è tra le opzioni). */
export function vistaEffettiva(valore: string, opzioni: readonly { id: string }[], predefinita?: string): string {
  if (opzioni.some((o) => o.id === valore)) return valore;
  if (predefinita != null && opzioni.some((o) => o.id === predefinita)) return predefinita;
  return opzioni[0]?.id ?? valore;
}
