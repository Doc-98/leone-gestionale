/** Le frequenze di un abbonamento al corso: null = prezzo unico, 0 = open (tutti i giorni), poi 1–7 volte a settimana. */
export const VOLTE: (number | null)[] = [null, 1, 2, 3, 4, 5, 6, 7, 0];

export function etichettaVolte(v: number | null): string {
  if (v == null) return 'Prezzo unico';
  if (v === 0) return 'Open';
  return ['', 'Mono', 'Bi', 'Tri'][v] ?? `${v} volte`;
}

/** «1234.5» → «1.234,50 €»; senza decimali se è un numero intero. */
export function euro(n: number | null | undefined): string {
  if (n == null) return '—';
  return `${Number(n).toLocaleString('it-IT', { minimumFractionDigits: Number(n) % 1 ? 2 : 0, maximumFractionDigits: 2 })} €`;
}
