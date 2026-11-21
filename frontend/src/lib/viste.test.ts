import { describe, expect, it } from 'vitest';
import { opzioniUsabili, vistaEffettiva, type OpzioneVista } from './viste';

const TURNI: OpzioneVista[] = [
  { id: 'settimana', nome: 'La settimana', breve: 'Settimana' },
  { id: 'mese', nome: 'Il mese', breve: 'Mese' },
  { id: 'colonne', nome: 'A colonne', solo: 'larga' },
];

describe('viste usabili', () => {
  it('su uno schermo largo ci sono tutte', () => {
    expect(opzioniUsabili(TURNI, true).map((o) => o.id)).toEqual(['settimana', 'mese', 'colonne']);
  });
  it('sugli altri la vista riservata ai larghi non compare', () => {
    expect(opzioniUsabili(TURNI, false).map((o) => o.id)).toEqual(['settimana', 'mese']);
  });
});

describe('vista effettiva', () => {
  const telefono = opzioniUsabili(TURNI, false);
  it('la scelta, se è tra le opzioni', () => {
    expect(vistaEffettiva('mese', telefono)).toBe('mese');
  });
  it('«A colonne» salvata e aperta sul telefono ricade sulla prima', () => {
    expect(vistaEffettiva('colonne', telefono)).toBe('settimana');
  });
  it('sceglie il predefinito se c\'è, altrimenti la prima; un valore sconosciuto non rompe', () => {
    expect(vistaEffettiva('colonne', telefono, 'mese')).toBe('mese');
    expect(vistaEffettiva('boh', telefono, 'non-c-e')).toBe('settimana');
    expect(vistaEffettiva('x', [])).toBe('x');
  });
});
