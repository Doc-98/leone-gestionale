import type { Dispositivo, Utilizzo } from './api';
import { dataDi } from './date';

/** Gli esiti delle macchine, come li legge una persona. */
export const ESITI_MACCHINA: Record<string, string> = {
  OK: 'Usato', GRATIS: 'Gratis', CREDITI_FINITI: 'Crediti finiti', SCONOSCIUTO: 'Sconosciuto', NON_ATTIVO: 'Non attivo', SERVIZIO_SPENTO: 'Spento', SALDO: 'Saldo',
};

export interface DaRicaricare {
  personaId: string | null;
  nome: string | null;
  codice: string;
  /** il servizio o la macchina dell'ultimo «crediti finiti» */
  dove: string;
  ultimo: string;
  tentativi: number;
}

/**
 * Chi oggi si è sentito dire «crediti finiti» da una macchina e non ha più usato crediti dopo (cioè non ha ancora
 * ricaricato): uno per persona (o per braccialetto, se non è di nessuno), il più recente per primo.
 */
export function daRicaricare(utilizzi: Utilizzo[], oggi: string): DaRicaricare[] {
  const perChi = new Map<string, Utilizzo[]>();
  for (const u of utilizzi) {
    if (dataDi(u.istante) !== oggi || u.simulato && u.esito === 'SALDO') continue;
    const k = u.personaId ?? u.codice;
    perChi.set(k, [...(perChi.get(k) ?? []), u]);
  }
  const out: DaRicaricare[] = [];
  for (const suoi of perChi.values()) {
    const ordinati = [...suoi].sort((a, b) => a.istante.localeCompare(b.istante));
    const finiti = ordinati.filter((u) => u.esito === 'CREDITI_FINITI');
    if (!finiti.length) continue;
    const ultimo = finiti[finiti.length - 1];
    if (ordinati.some((u) => u.esito === 'OK' && u.istante > ultimo.istante)) continue;
    out.push({ personaId: ultimo.personaId, nome: ultimo.persona, codice: ultimo.codice, dove: ultimo.servizio ?? ultimo.dispositivo ?? 'una macchina', ultimo: ultimo.istante,
      tentativi: finiti.length });
  }
  return out.sort((a, b) => b.ultimo.localeCompare(a.ultimo));
}

/**
 * Come sta una macchina collegata: spenta, mai collegata (aggiunta ma non ancora installata), muta (si sentiva e da 3
 * ore non più: da controllare) o in funzione con gli usi di oggi.
 */
export function statoMacchina(d: Pick<Dispositivo, 'attivo' | 'ultimoContatto' | 'utilizziOggi'>, adesso: number): { classe: 'ok' | 'muta' | 'nuova' | 'spenta'; testo: string } {
  if (!d.attivo) return { classe: 'spenta', testo: 'spenta' };
  if (!d.ultimoContatto) return { classe: 'nuova', testo: 'mai collegata' };
  const ore = Math.floor((adesso - new Date(d.ultimoContatto).getTime()) / 3_600_000);
  if (ore >= 3) return { classe: 'muta', testo: ore >= 48 ? `nessun segnale da ${Math.floor(ore / 24)} giorni` : `nessun segnale da ${ore} h` };
  return { classe: 'ok', testo: `${d.utilizziOggi} oggi` };
}
