/** Il numero con il suo nome al singolare o al plurale: quanti(1, 'turno', 'turni') → «1 turno», quanti(3, …) → «3 turni». */
export function quanti(n: number, singolare: string, plurale: string): string {
  return `${n} ${n === 1 ? singolare : plurale}`;
}

/** «1 credito», «20 crediti». */
export const crediti = (n: number | string) => quanti(Number(n), 'credito', 'crediti');

/** «1 giorno», «7 giorni». */
export const giorni = (n: number) => quanti(n, 'giorno', 'giorni');

/** «1 turno», «3 turni». */
export const turni = (n: number) => quanti(n, 'turno', 'turni');

/** Da quanto aspetta qualcuno: 0 → «da oggi», 1 → «da ieri», 5 → «da 5 giorni». */
export function daGiorni(n: number): string {
  return n <= 0 ? 'da oggi' : n === 1 ? 'da ieri' : `da ${n} giorni`;
}
