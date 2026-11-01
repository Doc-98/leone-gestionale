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
          ? mia ? `${r.collega.nome} ti sostituisce`
            : rivoltaAMe ? (r.propostaDa ? `${r.propostaDa.nome} ti propone di sostituire ${r.richiedente.nome}` : `${r.richiedente.nome} ti chiede di sostituirlo`)
            : `${r.collega.nome} sostituisce ${r.richiedente.nome}`
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

// ── Area personale (ADR 0022) ──────────────────────────────────────────────

export type VistaRichieste = 'pagina' | 'schede';
export const VISTE_RICHIESTE: { id: VistaRichieste; nome: string }[] = [
  { id: 'pagina', nome: 'Tutto in una pagina' },
  { id: 'schede', nome: 'A schede' },
];

export const aperta = (r: Richiesta) => r.stato === 'IN_ATTESA_COLLEGA' || r.stato === 'IN_ATTESA_APPROVAZIONE';

/** «lun 2/11» */
export function giornoCorto(d: string): string {
  const x = new Date(`${d}T12:00:00`);
  return `${['dom', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab'][x.getDay()]} ${x.getDate()}/${x.getMonth() + 1}`;
}
const nome1 = (n: string) => n.split(' ')[0];

/** I passi di una richiesta: inviata → collega (se serve) → coordinatore, con il punto in cui si trova. */
export function passi(r: Richiesta): { nome: string; stato: 'fatto' | 'ora' | 'no' | '' }[] {
  const conCollega = r.tipo !== 'CAMBIO_ORARIO';
  const out: { nome: string; stato: 'fatto' | 'ora' | 'no' | '' }[] = [{ nome: 'Inviata', stato: 'fatto' }];
  const finita = r.stato === 'APPROVATA' || r.stato === 'RESPINTA' || r.stato === 'ANNULLATA';
  if (conCollega) out.push({ nome: r.collega ? nome1(r.collega.nome) : 'Un collega', stato: r.stato === 'IN_ATTESA_COLLEGA' ? 'ora' : finita && r.stato !== 'APPROVATA' && !r.decisaIl ? 'no' : 'fatto' });
  out.push({ nome: 'Coordinatore', stato: r.stato === 'IN_ATTESA_APPROVAZIONE' ? 'ora' : r.stato === 'APPROVATA' ? 'fatto' : r.stato === 'RESPINTA' ? 'no' : '' });
  return out;
}

/** Lo stato in breve e il suo tono. Una sostituzione non è mai «respinta»: l'assenza è comunicata, non chiesta. */
export function esito(r: Richiesta, io?: string): { testo: string; tono: 'warn' | 'ok' | 'muted' | 'ferma' } {
  const mia = r.richiedente.id === io;
  if (r.stato === 'IN_ATTESA_COLLEGA') return { testo: mia ? (r.collega ? `aspetta ${nome1(r.collega.nome)}` : 'aspetta un collega') : 'in attesa', tono: 'warn' };
  if (r.stato === 'IN_ATTESA_APPROVAZIONE') return { testo: 'decide il coordinatore', tono: 'warn' };
  if (r.stato === 'ANNULLATA') return { testo: 'annullata', tono: 'muted' };
  if (r.tipo === 'SOSTITUZIONE') return r.stato === 'APPROVATA' ? { testo: 'coperta', tono: 'ok' } : { testo: 'non confermata', tono: 'muted' };
  return r.stato === 'APPROVATA' ? { testo: r.tipo === 'SCAMBIO' ? 'approvato' : 'approvata', tono: 'ok' } : { testo: r.tipo === 'SCAMBIO' ? 'respinto' : 'respinta', tono: 'ferma' };
}

/** Cosa è successo, in una riga: il giorno e cosa cambia. */
export function cosaCambia(r: Richiesta, io?: string): string {
  const t = r.turno;
  if (r.tipo === 'CAMBIO_ORARIO') return `${giornoCorto(t.data)} · ${ora(t.inizio)} → ${ora(r.nuovoInizio!)}–${ora(r.nuovoFine!)}`;
  if (r.tipo === 'SCAMBIO') return `${giornoCorto(t.data)} ↔ ${r.turnoCollega ? giornoCorto(r.turnoCollega.data) : '?'} · con ${r.richiedente.id === io ? r.collega?.nome ?? '?' : r.richiedente.nome}`;
  const chi = r.collega ? (r.richiedente.id === io ? `ti ha sostituito ${r.collega.nome}` : r.collega.id === io ? `hai sostituito ${r.richiedente.nome}` : `${r.collega.nome} per ${r.richiedente.nome}`) : 'chiesta a tutti';
  return `${giornoCorto(t.data)} ${ora(t.inizio)}–${ora(t.fine)} · ${r.stato === 'APPROVATA' ? chi : r.collega ? `chiesta a ${r.collega.nome}` : 'chiesta a tutti'}`;
}

export const NOMI_TIPO: Record<Richiesta['tipo'], string> = { SOSTITUZIONE: 'Sostituzione', SCAMBIO: 'Scambio', CAMBIO_ORARIO: 'Cambio orario' };

/** I turni da coprire: prima quelli che posso prendere, in fondo quelli che si sovrappongono a un mio turno. */
export function ordinaDaCoprire(aperte: Richiesta[], occupato: (r: Richiesta) => boolean): { liberi: Richiesta[]; occupati: Richiesta[] } {
  const perData = (a: Richiesta, b: Richiesta) => a.turno.data.localeCompare(b.turno.data) || a.turno.inizio.localeCompare(b.turno.inizio);
  return { liberi: aperte.filter((r) => !occupato(r)).sort(perData), occupati: aperte.filter(occupato).sort(perData) };
}


// ── Per il coordinatore (pianificazione e «Richieste») ──────────────────────

/** Sul turno in pianificazione: cosa c'è da fare per la richiesta aperta. */
export function descriviRichiestaSulTurno(r: Richiesta): string {
  switch (r.tipo) {
    case 'SOSTITUZIONE':
      return r.stato === 'IN_ATTESA_APPROVAZIONE' && r.collega
        ? `${r.collega.nome} sostituirebbe ${r.richiedente.nome}: da decidere in «Richieste»`
        : `${r.richiedente.nome} ha chiesto una sostituzione: proponila a un collega da «Richieste»`;
    case 'CAMBIO_ORARIO':
      return `C'è un cambio orario da decidere su questo turno (${ora(r.nuovoInizio!)}–${ora(r.nuovoFine!)})`;
    case 'SCAMBIO':
      return `C'è uno scambio con ${r.collega?.nome ?? 'un collega'} in corso su questo turno`;
  }
}

/** Il nome breve del tipo di cosa da fare, per gli elenchi «Da sistemare». */
export function cosaDaFare(r: Richiesta): string {
  if (r.tipo === 'SOSTITUZIONE') return r.stato === 'IN_ATTESA_COLLEGA' ? 'Sostituzione da coprire' : 'Sostituzione da decidere';
  if (r.tipo === 'CAMBIO_ORARIO') return 'Cambio orario da decidere';
  return r.stato === 'IN_ATTESA_COLLEGA' ? 'Scambio in attesa del collega' : 'Scambio da decidere';
}

export type GruppoRichieste = 'Da coprire' | 'Da decidere' | 'Aspettano il collega';

/** Dove sta la richiesta nell'elenco del coordinatore. */
export function gruppoRichiesta(r: Richiesta): GruppoRichieste {
  if (r.stato === 'IN_ATTESA_APPROVAZIONE') return 'Da decidere';
  return r.tipo === 'SOSTITUZIONE' && !r.collega ? 'Da coprire' : 'Aspettano il collega';
}

/** Le parti del vecchio orario che il nuovo non copre più (come il server con «Risolvi»). */
export function buchi(vi: string, vf: string, ni: string, nf: string): [string, string][] {
  const out: [string, string][] = [];
  const h = (s: string) => s.slice(0, 5);
  [vi, vf, ni, nf] = [h(vi), h(vf), h(ni), h(nf)];
  if (ni > vi) out.push([vi, ni < vf ? ni : vf]);
  if (nf < vf) out.push([nf > vi ? nf : vi, vf]);
  return out.filter(([a, b]) => b > a);
}

/** «chiesta oggi alle 9:12», «chiesta ieri alle 21:40», «chiesta il 28/10» */
export function quandoInviata(istante: string, oggi: string, verbo = 'chiesta'): string {
  const d = new Date(istante);
  const giorno = d.toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' });
  const ore = d.toLocaleTimeString('it-IT', { hour: 'numeric', minute: '2-digit', timeZone: 'Europe/Rome' });
  const ieri = new Date(`${oggi}T12:00:00`);
  ieri.setDate(ieri.getDate() - 1);
  if (giorno === oggi) return `${verbo} oggi alle ${ore}`;
  if (giorno === ieri.toLocaleDateString('sv-SE')) return `${verbo} ieri alle ${ore}`;
  const [, m, g] = giorno.split('-');
  return `${verbo} il ${Number(g)}/${Number(m)}`;
}

/** L'asse orario di una linea del tempo: dall'ora piena prima dell'inizio a quella dopo la fine. */
export function asse(orari: [string, string][]): { da: number; a: number; ore: string[]; pos: (h: string) => number } {
  const min = (s: string) => Number(s.slice(0, 2)) * 60 + Number(s.slice(3, 5));
  const da = Math.floor(Math.min(...orari.map(([i]) => min(i))) / 60) * 60;
  const a = Math.ceil(Math.max(...orari.map(([, f]) => min(f))) / 60) * 60;
  const ore = Array.from({ length: (a - da) / 60 + 1 }, (_, i) => `${String(da / 60 + i).padStart(2, '0')}:00`);
  return { da, a, ore, pos: (h: string) => ((min(h) - da) / (a - da)) * 100 };
}
