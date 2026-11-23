import type { Servizio } from './api';
import { numero } from './numeri';
import { crediti as nCrediti, quanti } from './plurali';

/**
 * Il riassunto di un servizio su una riga, per l'elenco di «Macchine e crediti»: le parti in fila con « · ».
 *   {1, 0, null} → «1 credito»; {1, 1, 5} → «1 credito · 1 gratis al giorno · 5 minuti»; {0, 2, null} → «gratis».
 * Con zero crediti il servizio è gratis e «gratis al giorno» non ha senso: non si scrive.
 */
export function riassuntoServizio(s: Pick<Servizio, 'crediti' | 'gratuitiAlGiorno' | 'minuti'>): string {
  const costo = Number(s.crediti) || 0;
  const gratis = Number(s.gratuitiAlGiorno) || 0;
  const minuti = Number(s.minuti) || 0;
  const parti = [costo === 0 ? 'gratis' : nCrediti(costo)];
  if (gratis > 0 && costo !== 0) parti.push(`${gratis} gratis al giorno`);
  if (minuti > 0) parti.push(quanti(minuti, 'minuto', 'minuti'));
  return parti.join(' · ');
}

/**
 * Con una ricarica da `crediti` crediti, quanti usi dei servizi accesi (e a pagamento): i primi due, nell'ordine dato,
 * arrotondati per difetto.
 */
export function usiConRicarica(crediti: number, servizi: Servizio[]): { nome: string; usi: number }[] {
  return servizi
    .filter((s) => s.attivo && Number(s.crediti) > 0)
    .slice(0, 2)
    .map((s) => ({ nome: s.nome, usi: Math.floor(crediti / Number(s.crediti)) }));
}

/** I campi di un servizio come si scrivono nel foglio: tutti testo, si convertono al salvataggio. */
export interface BozzaServizio {
  nome: string;
  crediti: string;
  gratuiti: string;
  minuti: string;
}

export const bozzaServizioVuota = (): BozzaServizio => ({ nome: '', crediti: '1', gratuiti: '0', minuti: '' });
export const bozzaDiServizio = (s: Servizio): BozzaServizio => ({
  nome: s.nome, crediti: String(s.crediti), gratuiti: String(s.gratuitiAlGiorno ?? 0), minuti: s.minuti == null ? '' : String(s.minuti),
});

const intero = (t: string, min: number) => t.trim() !== '' && Number.isInteger(Number(t)) && Number(t) >= min;

/** Cosa c'è da correggere nel foglio del servizio (vuoto = niente): interi ≥ 0, minuti vuoto o ≥ 1. */
export function erroriServizio(b: BozzaServizio): { nome: string; crediti: string; gratuiti: string; minuti: string } {
  return {
    nome: b.nome.trim() ? '' : 'Scrivi il nome del servizio.',
    crediti: intero(b.crediti, 0) ? '' : 'Un numero intero, da 0 in su.',
    gratuiti: intero(b.gratuiti, 0) ? '' : 'Un numero intero, da 0 in su.',
    minuti: b.minuti.trim() === '' || intero(b.minuti, 1) ? '' : 'Vuoto, oppure un numero intero da 1 in su.',
  };
}

export const servizioValido = (b: BozzaServizio): boolean => Object.values(erroriServizio(b)).every((e) => !e);

/** I dati da mandare al server per una bozza valida. */
export const datiServizio = (b: BozzaServizio) => ({
  nome: b.nome.trim(), crediti: Number(b.crediti), gratuitiAlGiorno: Number(b.gratuiti), minuti: b.minuti.trim() === '' ? null : Number(b.minuti),
});

/** Cosa resta nello storico dopo l'eliminazione: «: i suoi 1.240 utilizzi restano nello storico» (vuoto se non ce n'erano). */
export function restaNelloStorico(utilizzi: number | null | undefined): string {
  if (utilizzi == null) return ': gli utilizzi restano nello storico';
  if (utilizzi === 0) return '';
  return utilizzi === 1 ? ': il suo 1 utilizzo resta nello storico' : `: i suoi ${numero(utilizzi)} utilizzi restano nello storico`;
}
