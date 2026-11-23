import type { RegolaPromemoria, TipoPromemoria } from './api';
import { quanti } from './plurali';

/** I tipi di promemoria, con la riga corta che sta sotto il nome sulla linea dei giorni. */
export const TIPI: { tipo: TipoPromemoria; nome: string; sotto: string }[] = [
  { tipo: 'CERTIFICATO', nome: 'Certificato medico', sotto: 'prima che scada' },
  { tipo: 'ABBONAMENTO', nome: 'Abbonamento', sotto: 'se non è rinnovato' },
  { tipo: 'TESSERAMENTO', nome: 'Tesseramento in scadenza', sotto: 'prima che scada' },
  { tipo: 'TESSERAMENTO_MANCANTE', nome: 'Tesseramento mancante', sotto: 'dal giorno in cui manca' },
  { tipo: 'PAGAMENTO', nome: 'Pagamento scaduto', sotto: 'dopo la scadenza' },
];

/** Questi si ripetono ogni N giorni, al massimo M volte; gli altri partono una volta, N giorni prima della scadenza. */
export const ripetuto = (t: TipoPromemoria): boolean => t === 'TESSERAMENTO_MANCANTE' || t === 'PAGAMENTO';

/** I giorni delle email rispetto alla scadenza: negativi prima, 0 il giorno stesso, positivi dopo. */
export function pillole(r: Pick<RegolaPromemoria, 'tipo' | 'giorni' | 'maxInvii'>): { g: number; testo: string }[] {
  // il tesseramento mancante parte dal giorno in cui manca; il pagamento N giorni dopo la scadenza
  if (ripetuto(r.tipo)) {
    const passo = Number(r.giorni) || 7, primo = r.tipo === 'PAGAMENTO' ? passo : 0;
    return Array.from({ length: Math.max(1, Number(r.maxInvii) || 1) }, (_, i) => ({ g: primo + i * passo, testo: `${i + 1}ª` }));
  }
  const g = Number(r.giorni) || 0;
  return [{ g: g === 0 ? 0 : -g, testo: g === 0 ? 'il giorno' : `${g} g prima` }];
}

/**
 * La riga sotto il nome di un tipo: per i tipi ripetuti con almeno una regola «ogni 7 giorni, 3 volte» (dalla prima
 * regola del tipo), altrimenti il testo corto del tipo.
 */
export function sottoTipo(tipo: TipoPromemoria, regole: RegolaPromemoria[]): string {
  const t = TIPI.find((x) => x.tipo === tipo);
  const r = regole.find((x) => x.tipo === tipo);
  if (!ripetuto(tipo) || !r) return t?.sotto ?? '';
  const giorni = Number(r.giorni);
  const volte = r.maxInvii ?? 1;
  return `${giorni === 1 ? 'ogni giorno' : `ogni ${giorni} giorni`}, ${quanti(volte, 'volta', 'volte')}`;
}

/** L'interruttore del tipo: `vuoto` se il tipo non ha regole (disabilitato), `acceso` se almeno una è accesa. */
export function statoTipo(tipo: TipoPromemoria, regole: RegolaPromemoria[]): 'acceso' | 'spento' | 'vuoto' {
  const sue = regole.filter((r) => r.tipo === tipo);
  if (!sue.length) return 'vuoto';
  return sue.some((r) => r.attivo) ? 'acceso' : 'spento';
}
