/** Date «da calendario» come stringhe YYYY-MM-DD, senza sorprese di fuso orario. */

export const GIORNI = ['domenica', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato'];
export const MESI = ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno', 'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre'];

export function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function daIso(s: string): Date {
  const [y, m, g] = s.split('-').map(Number);
  return new Date(y, m - 1, g);
}

export function aggiungiGiorni(s: string, n: number): string {
  const d = daIso(s);
  d.setDate(d.getDate() + n);
  return iso(d);
}

/** Lunedì della settimana che contiene la data. */
export function lunedi(s: string): string {
  const d = daIso(s);
  return aggiungiGiorni(s, -((d.getDay() + 6) % 7));
}

/** «15:00:00» → «15:00» */
export function ora(s: string): string {
  return s.slice(0, 5);
}

/** «lunedì 19 ottobre» */
export function giornoEsteso(s: string): string {
  const d = daIso(s);
  return `${GIORNI[d.getDay()]} ${d.getDate()} ${MESI[d.getMonth()]}`;
}

/** 4.25 → «4,25» ; 5 → «5» */
export function formatOre(n: number): string {
  return String(Math.round(n * 100) / 100).replace('.', ',');
}
