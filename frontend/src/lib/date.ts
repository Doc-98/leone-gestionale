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

/** Lungo, dentro una frase: «lunedì 19 ottobre» */
export function giornoEsteso(s: string): string {
  const d = daIso(s);
  return `${GIORNI[d.getDay()]} ${d.getDate()} ${MESI[d.getMonth()]}`;
}

/** Lungo, all'inizio di un titolo: «Lunedì 19 ottobre» */
export function giornoTitolo(s: string): string {
  return giornoEsteso(s).replace(/^./, (c) => c.toUpperCase());
}

/** Breve per l'agenda (turni, richieste, attesa, cassa del giorno): «lun 9/11». Il giorno e il mese senza zeri. */
export function dataAgenda(s: string): string {
  const d = daIso(s.slice(0, 10));
  return `${GIORNI[d.getDay()].slice(0, 3)} ${d.getDate()}/${d.getMonth() + 1}`;
}

/** 4.25 → «4,25» ; 5 → «5» */
export function formatOre(n: number): string {
  return String(Math.round(n * 100) / 100).replace('.', ',');
}

/** «2026-10» */
export function meseIso(d: Date): string {
  return iso(d).slice(0, 7);
}

/** «2026-10» → «ottobre 2026» */
export function nomeMese(m: string): string {
  const [y, mm] = m.split('-').map(Number);
  return `${MESI[mm - 1]} ${y}`;
}

export function spostaMese(m: string, delta: number): string {
  const [y, mm] = m.split('-').map(Number);
  return meseIso(new Date(y, mm - 1 + delta, 1));
}

export const GIORNI_BREVI = ['', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab', 'dom'];
export const GIORNI_ESTESI = ['', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato', 'domenica'];

/** «2026-01-01» → «1 gennaio 2026» */
export function dataLunga(s: string): string {
  const d = daIso(s);
  return `${d.getDate()} ${MESI[d.getMonth()]} ${d.getFullYear()}`;
}

/** Ore tra due orari «HH:mm[:ss]» dello stesso giorno. */
export function oreTra(inizio: string, fine: string): number {
  const min = (s: string) => Number(s.slice(0, 2)) * 60 + Number(s.slice(3, 5));
  return (min(fine) - min(inizio)) / 60;
}

/** Breve per il registro (scadenze, nascite, storico, documenti): «2026-10-12» → «12/10/2026» */
export function dataBreve(s: string): string {
  const [a, m, g] = s.slice(0, 10).split('-');
  return `${g}/${m}/${a}`;
}

/** Ora locale di un istante ISO («2026-10-19T15:04:00Z» → «17:04» a Roma). */
export function oraDi(istante: string): string {
  return new Date(istante).toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Rome' });
}

/** Data locale di un istante ISO, come «YYYY-MM-DD». */
export function dataDi(istante: string): string {
  return new Date(istante).toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' });
}

/**
 * Un turno che si ripete ogni settimana, senza data: «lun 17:00–17:45» (breve) o «lunedì 17:00–17:45» (lungo).
 * `giorno` è 1 (lunedì) … 7 (domenica); senza `fine` c'è solo l'inizio.
 */
export function turnoSettimanale(giorno: number, inizio: string, fine?: string | null, lungo = false): string {
  const g = (lungo ? GIORNI_ESTESI : GIORNI_BREVI)[giorno] ?? '';
  return `${g} ${ora(inizio)}${fine ? `–${ora(fine)}` : ''}`;
}

/**
 * Il titolo di una settimana: «9–15 novembre» nello stesso mese, «26 ottobre – 1 novembre» tra due mesi.
 * Con `breve` i mesi di due mesi diversi hanno tre lettere («26 ott – 1 nov»), per il telefono.
 */
export function titoloSettimana(lun: string, breve = false): string {
  const a = daIso(lun);
  const b = daIso(aggiungiGiorni(lun, 6));
  const m = (d: Date) => (breve ? MESI[d.getMonth()].slice(0, 3) : MESI[d.getMonth()]);
  return a.getMonth() === b.getMonth() ? `${a.getDate()}–${b.getDate()} ${MESI[a.getMonth()]}` : `${a.getDate()} ${m(a)} – ${b.getDate()} ${m(b)}`;
}

/** Data e ora in un elenco: «12/10/2026 · 17:04» (un istante ISO, ora di Roma). */
export function dataOra(istante: string): string {
  return `${dataBreve(dataDi(istante))} · ${oraDi(istante)}`;
}

/** Data e ora dentro una frase: «il 12/10/2026 alle 17:04». */
export function dataOraFrase(istante: string): string {
  return `il ${dataBreve(dataDi(istante))} alle ${oraDi(istante)}`;
}
