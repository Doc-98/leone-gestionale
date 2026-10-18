import type { Richiesta, Turno } from './api';
import { giornoEsteso, ora } from './date';

export function quando(t: Turno): string {
  return `${giornoEsteso(t.data)}, ${ora(t.inizio)}–${ora(t.fine)}`;
}

/** Titolo e dettaglio di una richiesta, in parole. Con «io» le mie richieste sono in seconda persona. */
export function descrivi(r: Richiesta, io?: string): { titolo: string; dettaglio: string } {
  const t = r.turno;
  const mia = !!io && r.richiedente.id === io;
  const rivoltaAMe = !!io && r.collega?.id === io;
  switch (r.tipo) {
    case 'CAMBIO_ORARIO':
      return {
        titolo: mia ? 'Hai chiesto un cambio orario' : `${r.richiedente.nome} chiede un cambio orario`,
        dettaglio: `${giornoEsteso(t.data)} · ${t.postazione}: ${ora(t.inizio)}–${ora(t.fine)} → ${ora(r.nuovoInizio!)}–${ora(r.nuovoFine!)}`,
      };
    case 'SOSTITUZIONE':
      return {
        titolo: r.collega
          ? mia ? `${r.collega.nome} ti sostituisce` : rivoltaAMe ? `${r.richiedente.nome} ti chiede di sostituirlo` : `${r.collega.nome} sostituisce ${r.richiedente.nome}`
          : mia ? 'Cerchi un sostituto' : `${r.richiedente.nome} cerca un sostituto`,
        dettaglio: `${quando(t)} · ${t.postazione}`,
      };
    case 'SCAMBIO':
      return {
        titolo: mia ? `Scambio con ${r.collega?.nome ?? '?'}` : rivoltaAMe ? `${r.richiedente.nome} ti propone uno scambio` : `Scambio tra ${r.richiedente.nome} e ${r.collega?.nome ?? '?'}`,
        dettaglio: `${quando(t)} (${t.postazione}) ↔ ${r.turnoCollega ? `${quando(r.turnoCollega)} (${r.turnoCollega.postazione})` : '?'}`,
      };
  }
}
