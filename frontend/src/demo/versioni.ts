/**
 * Le versioni che la vetrina della demo sa mostrare (ADR 0027). Una versione è un'app (gestionale o famiglie) vista
 * su un dispositivo: il computer occupa la finestra, telefono e tablet stanno in una cornice della loro misura.
 */
export type IdVersione = 'gestionale-computer' | 'gestionale-telefono' | 'famiglie-telefono' | 'famiglie-tablet' | 'famiglie-computer';
export type App = 'gestionale' | 'famiglie';
export type Dispositivo = 'computer' | 'telefono' | 'tablet';

export interface Versione {
  id: IdVersione;
  app: App;
  dispositivo: Dispositivo;
  /** Breve, per la barra in alto. */
  nome: string;
  descrizione: string;
  /** La pagina da aprire. */
  indirizzo: string;
  /** Misura della cornice in verticale; il computer non ha cornice. */
  misura?: { larghezza: number; altezza: number };
  ruotabile?: boolean;
  /** Falso finché l'app non esiste: la scheda resta «in arrivo». */
  pronta: boolean;
}

export const VERSIONI: Versione[] = [
  {
    id: 'gestionale-computer', app: 'gestionale', dispositivo: 'computer', nome: 'Computer', indirizzo: '/', pronta: true,
    descrizione: 'Segreteria, banco, piano vasca, turni e impostazioni: la versione principale, quella dei PC della società.',
  },
  {
    id: 'gestionale-telefono', app: 'gestionale', dispositivo: 'telefono', nome: 'Telefono', indirizzo: '/', pronta: true,
    misura: { larghezza: 390, altezza: 844 },
    descrizione: 'Lo stesso gestionale sul telefono dello staff: i miei turni, le richieste, le ore e il tornello.',
  },
  {
    id: 'famiglie-telefono', app: 'famiglie', dispositivo: 'telefono', nome: 'Telefono', indirizzo: '/famiglie.html', pronta: false,
    misura: { larghezza: 390, altezza: 844 },
    descrizione: 'L\'app per i genitori: richieste, posto offerto con il conto alla rovescia, pagamento e ricevute.',
  },
  {
    id: 'famiglie-tablet', app: 'famiglie', dispositivo: 'tablet', nome: 'Tablet', indirizzo: '/famiglie.html', pronta: false,
    misura: { larghezza: 834, altezza: 1194 }, ruotabile: true,
    descrizione: 'La stessa app su un tablet, in verticale o in orizzontale.',
  },
  {
    id: 'famiglie-computer', app: 'famiglie', dispositivo: 'computer', nome: 'Computer', indirizzo: '/famiglie.html', pronta: false,
    descrizione: 'La stessa app dal computer di casa: elenco delle richieste a sinistra, dettaglio a destra.',
  },
];

export const NOMI_APP: Record<App, string> = { gestionale: 'Gestionale', famiglie: 'App delle famiglie' };

export function versione(id: string | null | undefined): Versione | undefined {
  return VERSIONI.find((v) => v.id === id && v.pronta);
}

/** Misura della cornice tenendo conto della rotazione (solo per i dispositivi ruotabili). */
export function misuraCornice(v: Versione, orizzontale: boolean): { larghezza: number; altezza: number } | undefined {
  if (!v.misura) return undefined;
  const { larghezza, altezza } = v.misura;
  return orizzontale && v.ruotabile ? { larghezza: altezza, altezza: larghezza } : { larghezza, altezza };
}

/** Bordo del dispositivo disegnato attorno allo schermo, in px per lato. */
export const BORDO = 14;

/**
 * Di quanto rimpicciolire la cornice perché stia nello spazio disponibile (mai ingrandire).
 * `margine` è lo spazio libero da lasciare attorno su ogni lato.
 */
export function scala(schermo: { larghezza: number; altezza: number }, spazio: { larghezza: number; altezza: number }, margine = 24): number {
  const w = schermo.larghezza + 2 * BORDO;
  const h = schermo.altezza + 2 * BORDO;
  const dispW = spazio.larghezza - 2 * margine;
  const dispH = spazio.altezza - 2 * margine;
  if (dispW <= 0 || dispH <= 0) return 0.2;
  return Math.max(0.2, Math.min(1, dispW / w, dispH / h));
}
