import { describe, expect, it } from 'vitest';
import type { PersonaStaff, Postazione } from './api';
import { ammessa, candidati, gruppi, nomeCorto, nomeNuovaPostazione, orePerPersona, type TurnoBreve } from './gestione';

const post = (id: string, nome: string, mansione = 'Assistente spogliatoio', soloSesso: 'M' | 'F' | null = null): Postazione =>
  ({ id, nome, mansione, impiantoId: 'i', soloSesso });
const M1 = post('m1', 'Spogliatoio M · postazione 1', undefined, 'M');
const M2 = post('m2', 'Spogliatoio M · postazione 2', undefined, 'M');
const F1 = post('f1', 'Spogliatoio F · postazione 1', undefined, 'F');
const A = post('a', 'Atrio · tornelli', 'Assistente atrio');
const BAR = post('b', 'Bar', 'Barista');

const luca: PersonaStaff = { id: 'l', nome: 'Luca Bianchi', sesso: 'M' };
const paolo: PersonaStaff = { id: 'p', nome: 'Paolo Galli', sesso: 'M' };
const giulia: PersonaStaff = { id: 'g', nome: 'Giulia Conti', sesso: 'F' };
const t = (id: string, giorno: string, inizio: string, fine: string, postazioneId: string, persona: PersonaStaff | null): TurnoBreve =>
  ({ id, giorno, inizio, fine, postazioneId, persona });

describe('gestione turni', () => {
  it('raggruppa le postazioni per area', () => {
    const g = gruppi([M1, M2, F1, A, BAR]);
    expect(g.map((x) => [x.nome, x.area, x.righe.map((r) => r.etichetta)])).toEqual([
      ['Spogliatoio M', 'm', ['Postazione 1', 'Postazione 2']],
      ['Spogliatoio F', 'f', ['Postazione 1']],
      ['Atrio', 'a', ['Tornelli']],
      ['Barista', 'x', ['Bar']],
    ]);
    expect(nomeNuovaPostazione(g[0])).toBe('Spogliatoio M · postazione 3');
  });

  it('nomi corti e postazioni riservate', () => {
    expect(nomeCorto('Giulia Conti')).toBe('Giulia C.');
    expect(nomeCorto('Wei')).toBe('Wei');
    expect(ammessa(F1, giulia)).toBe(true);
    expect(ammessa(F1, luca)).toBe(false);
    expect(ammessa(F1, { sesso: null })).toBe(false);
    expect(ammessa(A, luca)).toBe(true);
  });

  it('candidati: solo chi è ammesso, prima i liberi dal meno carico', () => {
    const turni = [
      t('1', 'lun', '16:00', '20:00', 'm1', null),
      t('2', 'lun', '15:00', '17:00', 'a', paolo),
      t('3', 'mar', '16:00', '19:00', 'm2', luca),
    ];
    const c = candidati(turni[0], M1, [giulia, paolo, luca], turni);
    expect(c.map((x) => x.persona.nome)).toEqual(['Luca Bianchi', 'Paolo Galli']);
    expect(c[0].occupato).toBeUndefined();
    expect(c[1].occupato?.id).toBe('2');
    expect(orePerPersona(turni).get('l')).toMatchObject({ ore: 3, turni: 1 });
  });
});

describe('anteprima della generazione', () => {
  it('toglie chiusure e turni già presenti', async () => {
    const { anteprima, aperto } = await import('./gestione');
    expect(aperto('16:00', '20:00', [{ inizio: '18:00', fine: '23:59' }])).toEqual(['16:00', '18:00']);
    expect(aperto('09:00', '13:00', [{ inizio: '00:00', fine: '10:00' }])).toEqual(['10:00', '13:00']);
    expect(aperto('09:00', '13:00', [{ inizio: '00:00', fine: '23:59' }])).toBeNull();
    // lun 7 e mar 8 giugno 2027
    const righe = [
      { postazioneId: 'm1', giornoSettimana: 1, inizio: '16:00:00', fine: '20:00:00', persona: null },
      { postazioneId: 'm1', giornoSettimana: 2, inizio: '16:00:00', fine: '20:00:00', persona: { id: 'l', nome: 'Luca' } },
      { postazioneId: 'm2', giornoSettimana: 2, inizio: '16:00:00', fine: '19:00:00', persona: { id: 'p', nome: 'Paolo' } },
    ];
    const a = anteprima(righe, ['2027-06-07', '2027-06-08', '2027-06-14', '2027-06-15'],
      [{ titolo: 'Chiuso', data: '2027-06-07', dataFine: null, inizio: '00:00:00', fine: '23:59:00' },
       { titolo: 'Ridotto', data: '2027-06-08', dataFine: null, inizio: '18:00:00', fine: '23:59:00' }],
      [{ postazioneId: 'm1', data: '2027-06-15', inizio: '16:00:00', fine: '20:00:00' }, { postazioneId: 'm2', data: '2027-06-15', inizio: '16:00:00', fine: '19:00:00' }]);
    expect(a.giorni.map((g) => [g.nuovi, g.stato])).toEqual([[0, 'chiuso'], [2, 'ridotto'], [1, ''], [0, 'gia']]);
    expect(a).toMatchObject({ nuovi: 3, ore: 8, scoperti: 1, chiusure: ['Chiuso', 'Ridotto'] });
  });
});
