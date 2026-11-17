/** Le frequenze di un abbonamento al corso: null = prezzo unico, 0 = open (tutti i giorni), poi 1–7 volte a settimana. */
export const VOLTE: (number | null)[] = [null, 1, 2, 3, 4, 5, 6, 7, 0];

export function etichettaVolte(v: number | null): string {
  if (v == null) return 'Prezzo unico';
  if (v === 0) return 'Open';
  return ['', 'Mono', 'Bi', 'Tri'][v] ?? `${v} volte`;
}
