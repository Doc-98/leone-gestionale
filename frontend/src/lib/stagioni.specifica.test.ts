import { describe, expect, it } from 'vitest';
import { classePillStagione, ordinaStagioni, stagioneIniziale, statoStagione } from './stagioni';

/**
 * Specifica (brief coerenza 7, lotto 3, A1 «lib/stagioni.ts»):
 * - `statoStagione(s, oggi)`: estremi compresi: `dal <= oggi <= al` «in corso»; `oggi < dal` «futura»; `oggi > al` «finita».
 * - `classePillStagione(stato)`: 'pill passa' | 'pill' | 'pill futura'.
 * - `ordinaStagioni(stagioni)`: copia ordinata per `dal` crescente.
 * - `stagioneIniziale(stagioni, oggi)`: id della stagione in corso; se non c'è, della futura più vicina; se non c'è,
 *   dell'ultima finita; '' se l'elenco è vuoto.
 * Le date sono stringhe ISO `YYYY-MM-DD`.
 */
const s = (id: string, dal: string, al: string) => ({ id, nome: id, dal, al, quota: null, quotaRimborsabile: false });
const s2425 = s('2024/25', '2024-09-01', '2025-08-31');
const s2526 = s('2025/26', '2025-09-01', '2026-08-31');
const s2627 = s('2026/27', '2026-09-01', '2027-08-31');
const s2728 = s('2027/28', '2027-09-01', '2028-08-31');

describe('statoStagione – estremi compresi', () => {
  const st = { dal: '2025-09-01', al: '2026-08-31' };

  it('il primo giorno è «in corso»', () => expect(statoStagione(st, '2025-09-01')).toBe('in corso'));
  it('l\'ultimo giorno è «in corso»', () => expect(statoStagione(st, '2026-08-31')).toBe('in corso'));
  it('un giorno dentro è «in corso»', () => expect(statoStagione(st, '2026-01-15')).toBe('in corso'));
  it('il giorno prima dell\'inizio è «futura»', () => expect(statoStagione(st, '2025-08-31')).toBe('futura'));
  it('molto prima è «futura»', () => expect(statoStagione(st, '2020-01-01')).toBe('futura'));
  it('il giorno dopo la fine è «finita»', () => expect(statoStagione(st, '2026-09-01')).toBe('finita'));
  it('molto dopo è «finita»', () => expect(statoStagione(st, '2030-12-31')).toBe('finita'));

  it('una stagione di un solo giorno è «in corso» in quel giorno e solo in quello', () => {
    const uno = { dal: '2026-03-10', al: '2026-03-10' };
    expect(statoStagione(uno, '2026-03-09')).toBe('futura');
    expect(statoStagione(uno, '2026-03-10')).toBe('in corso');
    expect(statoStagione(uno, '2026-03-11')).toBe('finita');
  });

  it('il confronto è tra date, non tra numeri di giorni del mese (29 febbraio, cambio d\'anno)', () => {
    const bisestile = { dal: '2027-09-01', al: '2028-02-29' };
    expect(statoStagione(bisestile, '2028-02-29')).toBe('in corso');
    expect(statoStagione(bisestile, '2028-03-01')).toBe('finita');
    const annoNuovo = { dal: '2025-12-31', al: '2026-01-01' };
    expect(statoStagione(annoNuovo, '2026-01-01')).toBe('in corso');
    expect(statoStagione(annoNuovo, '2025-12-30')).toBe('futura');
  });
});

describe('classePillStagione', () => {
  it('«in corso» → «pill passa»', () => expect(classePillStagione('in corso')).toBe('pill passa'));
  it('«finita» → «pill» (grigia, nessuna variante)', () => expect(classePillStagione('finita')).toBe('pill'));
  it('«futura» → «pill futura» (azzurra)', () => expect(classePillStagione('futura')).toBe('pill futura'));
});

describe('ordinaStagioni', () => {
  it('ordina per inizio crescente', () => {
    const r = ordinaStagioni([s2627, s2425, s2728, s2526]);
    expect(r.map((x) => x.id)).toEqual(['2024/25', '2025/26', '2026/27', '2027/28']);
  });

  it('non tocca l\'array di partenza e restituisce una copia', () => {
    const origine = [s2627, s2425, s2526];
    const copia = [...origine];
    const r = ordinaStagioni(origine);
    expect(origine).toEqual(copia);
    expect(r).not.toBe(origine);
  });

  it('ordina per `dal`, non per nome né per posizione', () => {
    // nomi in ordine inverso rispetto alle date
    const a = s('zeta', '2024-09-01', '2025-08-31');
    const b = s('alfa', '2026-09-01', '2027-08-31');
    expect(ordinaStagioni([b, a]).map((x) => x.id)).toEqual(['zeta', 'alfa']);
  });

  it('elenco vuoto → elenco vuoto; una sola stagione resta com\'è', () => {
    expect(ordinaStagioni([])).toEqual([]);
    expect(ordinaStagioni([s2526])).toEqual([s2526]);
  });
});

describe('stagioneIniziale', () => {
  it('elenco vuoto → stringa vuota', () => {
    expect(stagioneIniziale([], '2026-11-23')).toBe('');
  });

  it('c\'è una stagione in corso → quella, in qualunque ordine arrivino', () => {
    expect(stagioneIniziale([s2425, s2526, s2627, s2728], '2026-11-23')).toBe('2026/27');
    expect(stagioneIniziale([s2728, s2627, s2526, s2425], '2026-11-23')).toBe('2026/27');
  });

  it('la stagione in corso vince sulla futura più vicina e sull\'ultima finita', () => {
    expect(stagioneIniziale([s2425, s2627, s2728], '2027-01-10')).toBe('2026/27');
  });

  it('nessuna in corso (buco tra due stagioni) → la futura più vicina', () => {
    const luglio = '2026-08-31'; // ultimo giorno della 2025/26: ancora in corso
    expect(stagioneIniziale([s2526, s2627, s2728], luglio)).toBe('2025/26');
    const buco = [s('A', '2025-09-01', '2026-06-30'), s('B', '2026-09-01', '2027-06-30'), s('C', '2027-09-01', '2028-06-30')];
    expect(stagioneIniziale(buco, '2026-07-15')).toBe('B');
    expect(stagioneIniziale([...buco].reverse(), '2026-07-15')).toBe('B');
  });

  it('nessuna in corso e nessuna finita (tutte future) → la più vicina', () => {
    expect(stagioneIniziale([s2728, s2627], '2025-01-01')).toBe('2026/27');
  });

  it('tra una finita e una futura, senza una in corso, vince la futura', () => {
    const buco = [s('vecchia', '2024-09-01', '2025-06-30'), s('nuova', '2026-09-01', '2027-06-30')];
    expect(stagioneIniziale(buco, '2026-01-01')).toBe('nuova');
  });

  it('solo stagioni finite → l\'ultima', () => {
    expect(stagioneIniziale([s2425, s2526], '2030-01-01')).toBe('2025/26');
    expect(stagioneIniziale([s2526, s2425], '2030-01-01')).toBe('2025/26');
  });

  it('il giorno dopo la fine dell\'ultima stagione → l\'ultima finita', () => {
    expect(stagioneIniziale([s2526], '2026-09-01')).toBe('2025/26');
  });

  it('il primo giorno di una stagione è già quella in corso', () => {
    expect(stagioneIniziale([s2526, s2627], '2026-09-01')).toBe('2026/27');
  });

  it('non modifica l\'elenco passato', () => {
    const origine = [s2627, s2425];
    const copia = [...origine];
    stagioneIniziale(origine, '2026-11-23');
    expect(origine).toEqual(copia);
  });
});
