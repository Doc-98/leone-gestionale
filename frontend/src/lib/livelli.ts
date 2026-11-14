import type { BambinoLivelli, TurnoLivelli } from './api';
import { GIORNI_BREVI } from './date';

/** «Mar 17:00» */
export function quandoBreve(t: Pick<TurnoLivelli, 'giornoSettimana' | 'inizio'>): string {
  const g = GIORNI_BREVI[t.giornoSettimana] ?? '';
  return `${g.charAt(0).toUpperCase()}${g.slice(1)} ${t.inizio.slice(0, 5)}`;
}

/** Quanti esiti mancano in un turno, la percentuale fatta e la scritta per la scheda del turno. */
export function statoTurno(t: Pick<TurnoLivelli, 'iscritti' | 'passano' | 'continuano'>): { fatti: number; completo: boolean; perc: number; testo: string } {
  const fatti = t.passano + t.continuano;
  const completo = t.iscritti > 0 && fatti >= t.iscritti;
  const perc = t.iscritti ? Math.round((fatti / t.iscritti) * 100) : 0;
  const testo = t.iscritti === 0 ? 'nessun iscritto' : completo ? '✓ completo' : fatti ? `${fatti}/${t.iscritti} esiti` : 'da fare';
  return { fatti, completo, perc, testo };
}

/** I conteggi del turno aperto: passano, continuano, senza esito. */
export function conteggi(bambini: BambinoLivelli[]): { passano: number; continuano: number; mancano: number } {
  const passano = bambini.filter((b) => b.esito === 'PASSA').length;
  const continuano = bambini.filter((b) => b.esito === 'CONTINUA').length;
  return { passano, continuano, mancano: bambini.length - passano - continuano };
}

/** «Tutti passano»: a chi non ha ancora un esito si dà «passa»; gli esiti già dati restano. */
export function tuttiPassano(bambini: BambinoLivelli[]): { personaId: string; esito: 'PASSA' }[] {
  return bambini.filter((b) => !b.esito).map((b) => ({ personaId: b.personaId, esito: 'PASSA' as const }));
}

/** Il prossimo turno da completare dopo quello aperto (nell'ordine dell'elenco, ricominciando dall'inizio). */
export function prossimoDaCompletare(turni: TurnoLivelli[], aperto: string | null): TurnoLivelli | null {
  const i = turni.findIndex((t) => t.lezioneId === aperto);
  const giro = [...turni.slice(i + 1), ...turni.slice(0, Math.max(i, 0))];
  return giro.find((t) => t.lezioneId !== aperto && t.iscritti > 0 && !statoTurno(t).completo) ?? null;
}

/** I corsi distinti dell'elenco, in ordine di nome (per chi vede i turni di tutti). */
export function corsiDi(turni: TurnoLivelli[]): { id: string; nome: string; colore: string | null }[] {
  const visti = new Map<string, { id: string; nome: string; colore: string | null }>();
  for (const t of turni) if (!visti.has(t.corsoId)) visti.set(t.corsoId, { id: t.corsoId, nome: t.corso, colore: t.colore });
  return [...visti.values()].sort((a, b) => a.nome.localeCompare(b.nome, 'it'));
}
