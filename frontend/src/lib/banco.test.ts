import { describe, expect, it } from 'vitest';
import type { Motivo, Passaggio, TurnoAlBanco } from './api';
import { daQuanto, perCorso, perQuarto, problema, rimedio, turnoDelPassaggio } from './banco';

const motivo = (regola: string, blocca = true): Motivo => ({ codice: regola + '_X', regola, messaggio: `m ${regola}`, rimedio: 'r', blocca });
// istanti di ottobre (ora solare dal 25): le 17 a Roma sono le 16 UTC
const alle = (hhmm: string) => `2026-10-27T${String(Number(hhmm.slice(0, 2)) - 1).padStart(2, '0')}:${hhmm.slice(3)}:00Z`;
const turno = (id: string, inizio: string, fine: string, corso = 'Delfino'): TurnoAlBanco =>
  ({ lezioneId: id, corsoId: 'c' + id, corso, tipo: 'SCUOLA_NUOTO', colore: null, inizio, fine, iscritti: 8 });
let n = 0;
const passaggio = (persona: string | null, hhmm: string, o: Partial<Passaggio> = {}): Passaggio => ({
  id: String(++n), istante: alle(hhmm), codice: 'C' + (persona ?? n), verso: 'ENTRATA', verde: true, goldenPass: false, segnalato: false,
  persona: persona ? { id: persona, nome: persona, cognome: 'X', eta: 30, braccialettiAttivi: 1 } : null, motivi: [], carnetScalato: false, ...o,
});

describe('banco del tornello', () => {
  it('riconosce il tipo di problema dal primo motivo che blocca', () => {
    expect(problema([motivo('FASCIA_ORARIA', false), motivo('CERTIFICATO')])).toBe('certificato');
    expect(problema([motivo('TESSERAMENTO'), motivo('ABBONAMENTO')])).toBe('abbonamento');
    expect(problema([motivo('BRACCIALETTO')])).toBe('braccialetto');
    expect(problema([motivo('RIPASSAGGIO')])).toBe('altro');
  });

  it('propone il rimedio: la scheda del cliente, o i clienti per un braccialetto di nessuno', () => {
    expect(rimedio({ persona: null, motivi: [motivo('BRACCIALETTO')] })).toEqual({ azione: 'Assegnalo a un cliente', href: '#/clienti' });
    expect(rimedio({ persona: { id: 'p' }, motivi: [motivo('CERTIFICATO')] })).toEqual({ azione: 'Registra il certificato', href: '#/clienti/p' });
  });

  it('dice da quanto tempo', () => {
    const adesso = new Date(alle('18:30'));
    expect(daQuanto(alle('18:23'), adesso)).toBe('7 min');
    expect(daQuanto(alle('17:10'), adesso)).toBe('1 h 20 min');
    expect(daQuanto(alle('16:30'), adesso)).toBe('2 h');
  });

  it('collega il passaggio al turno in corso o al prossimo', () => {
    const turni = [turno('a', '16:10', '16:55'), turno('b', '17:50', '18:35')];
    expect(turnoDelPassaggio(turni, alle('16:00'))?.lezioneId).toBe('a');
    expect(turnoDelPassaggio(turni, alle('17:40'))?.lezioneId).toBe('b');
    expect(turnoDelPassaggio(turni, alle('19:00'))?.lezioneId).toBe('b');
    expect(turnoDelPassaggio(undefined, alle('19:00'))).toBeNull();
  });

  it('raggruppa per quarto d’ora', () => {
    const g = perQuarto([passaggio('a', '17:42'), passaggio('b', '17:31'), passaggio('c', '17:29')]);
    expect(g.map((x) => [x.ora, x.voci.length])).toEqual([['17:30', 2], ['17:15', 1]]);
  });

  it('raggruppa per corso: fermati in cima, poi i turni che iniziano, poi gli altri, le uscite in fondo', () => {
    const g = perCorso({
      passaggi: [
        passaggio('a', '17:42'), passaggio('r', '17:41', { verde: false, motivi: [motivo('CERTIFICATO')] }), passaggio('u', '17:38', { verso: 'USCITA' }),
        passaggio('b', '17:30'), passaggio('l', '17:20'), passaggio('a', '17:10'),
      ],
      turni: { a: [turno('giallo', '17:50', '18:35')], b: [turno('agon', '17:10', '18:30', 'Propaganda')] },
      arrivati: { giallo: 1, agon: 11 },
    }, '17:45');
    expect(g.map((x) => x.titolo)).toEqual(['Fermati al tornello', 'Delfino 17:50', 'Propaganda 17:10', 'Senza un corso oggi', 'Usciti']);
    expect(g[1].passaggi).toHaveLength(1); // «a» passato due volte: compare una volta
    expect(g[2].arrivati).toBe(11);
  });
});
