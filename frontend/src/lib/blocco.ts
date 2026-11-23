/**
 * I messaggi di blocco («Non si può eliminare…», design system, «Messaggi di blocco»): il backend manda un testo con le
 * righe separate da `\n`; in ogni riga la parte fino al primo «:» compreso è l'attacco, che si scrive in grassetto.
 *   «Non si può eliminare: 120 turni nello storico usano Postazione 3.\nPuoi spegnerla: quei turni restano com'erano.»
 * Una riga senza «:» resta tutta normale.
 */
export interface RigaBlocco {
  /** In grassetto, «:» compreso; vuoto se la riga non ha un attacco */
  attacco: string;
  resto: string;
}

export function righeBlocco(testo: string): RigaBlocco[] {
  return testo
    .split('\n')
    .map((r) => r.trim())
    .filter(Boolean)
    .map((r) => {
      const i = r.indexOf(':');
      return i < 0 ? { attacco: '', resto: r } : { attacco: r.slice(0, i + 1), resto: r.slice(i + 1).trim() };
    });
}
