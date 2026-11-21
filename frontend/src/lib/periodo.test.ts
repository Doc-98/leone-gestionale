import { describe, expect, it } from 'vitest';
import { contiene, limitaPeriodo, daCalendario, etichettaPeriodo, finePeriodo, inizioPeriodo, perCalendario, sidopo, spostaPeriodo, tipoCalendario, vistaControllo } from './periodo';

describe('inizio e fine del periodo', () => {
  it('il giorno è se stesso', () => {
    expect(inizioPeriodo('2026-11-11', 'giorno')).toBe('2026-11-11');
    expect(finePeriodo('2026-11-11', 'giorno')).toBe('2026-11-11');
  });
  it('la settimana va da lunedì a domenica', () => {
    expect(inizioPeriodo('2026-11-11', 'settimana')).toBe('2026-11-09');
    expect(inizioPeriodo('2026-11-15', 'settimana')).toBe('2026-11-09');
    expect(finePeriodo('2026-11-11', 'settimana')).toBe('2026-11-15');
  });
  it('la settimana a cavallo di due mesi e di due anni', () => {
    expect(finePeriodo('2026-10-26', 'settimana')).toBe('2026-11-01');
    expect(inizioPeriodo('2026-12-31', 'settimana')).toBe('2026-12-28');
    expect(finePeriodo('2026-12-31', 'settimana')).toBe('2027-01-03');
  });
  it('il mese va dal primo all\'ultimo, anche a febbraio bisestile', () => {
    expect(inizioPeriodo('2026-11-18', 'mese')).toBe('2026-11-01');
    expect(finePeriodo('2026-11-18', 'mese')).toBe('2026-11-30');
    expect(finePeriodo('2028-02-10', 'mese')).toBe('2028-02-29');
    expect(finePeriodo('2027-02-10', 'mese')).toBe('2027-02-28');
    expect(finePeriodo('2026-12-31', 'mese')).toBe('2026-12-31');
  });
});

describe('sposta il periodo', () => {
  it('di un giorno, una settimana, un mese', () => {
    expect(spostaPeriodo('2026-11-11', 'giorno', 1)).toBe('2026-11-12');
    expect(spostaPeriodo('2026-11-09', 'settimana', 1)).toBe('2026-11-16');
    expect(spostaPeriodo('2026-11-09', 'settimana', -1)).toBe('2026-11-02');
    expect(spostaPeriodo('2026-11-01', 'mese', 1)).toBe('2026-12-01');
    expect(spostaPeriodo('2026-01-01', 'mese', -1)).toBe('2025-12-01');
  });
  it('parte sempre dal primo giorno del periodo', () => {
    expect(spostaPeriodo('2026-11-12', 'settimana', 1)).toBe('2026-11-16');
    expect(spostaPeriodo('2026-01-31', 'mese', 1)).toBe('2026-02-01');
  });
  it('attraversa il cambio d\'anno', () => {
    expect(spostaPeriodo('2026-12-28', 'settimana', 1)).toBe('2027-01-04');
  });
});

describe('contiene', () => {
  it('estremi compresi', () => {
    expect(contiene('2026-11-09', 'settimana', '2026-11-09')).toBe(true);
    expect(contiene('2026-11-09', 'settimana', '2026-11-15')).toBe(true);
    expect(contiene('2026-11-09', 'settimana', '2026-11-16')).toBe(false);
    expect(contiene('2026-11-09', 'settimana', '2026-11-08')).toBe(false);
    expect(contiene('2026-11-01', 'mese', '2026-11-30')).toBe(true);
    expect(contiene('2026-11-01', 'mese', '2026-12-01')).toBe(false);
    expect(contiene('2026-11-11', 'giorno', '2026-11-11')).toBe(true);
  });
});

describe('la scritta del periodo', () => {
  it('settimana nello stesso mese e tra due mesi', () => {
    expect(etichettaPeriodo('2026-11-09', 'settimana', false)).toBe('9–15 novembre');
    expect(etichettaPeriodo('2026-10-26', 'settimana', false)).toBe('26 ottobre – 1 novembre');
    expect(etichettaPeriodo('2026-10-26', 'settimana', true)).toBe('26 ott – 1 nov');
  });
  it('mese e giorno', () => {
    expect(etichettaPeriodo('2026-11-01', 'mese', false)).toBe('Novembre 2026');
    expect(etichettaPeriodo('2026-11-09', 'giorno', false)).toBe('Lunedì 9 novembre');
    expect(etichettaPeriodo('2026-11-09', 'giorno', true)).toBe('lun 9/11');
  });
});

describe('vista: segmentato o tendina', () => {
  it('fino a 3 opzioni il segmentato', () => {
    expect([1, 2, 3].map(vistaControllo)).toEqual(['segmentato', 'segmentato', 'segmentato']);
    expect([4, 5, 9].map(vistaControllo)).toEqual(['tendina', 'tendina', 'tendina']);
  });
});

describe('calendario nativo', () => {
  it('il mese ha il suo selettore solo se il browser lo sa fare', () => {
    expect(tipoCalendario('mese', true)).toBe('month');
    expect(tipoCalendario('mese', false)).toBe('date');
    expect(tipoCalendario('settimana', true)).toBe('date');
  });
  it('dal valore scelto al primo giorno del periodo', () => {
    expect(daCalendario('2026-11-12', 'settimana')).toBe('2026-11-09');
    expect(daCalendario('2026-11-12', 'mese')).toBe('2026-11-01');
    expect(daCalendario('2026-11', 'mese')).toBe('2026-11-01');
    expect(daCalendario('', 'mese')).toBeNull();
    expect(daCalendario('boh', 'giorno')).toBeNull();
  });
  it('dal periodo al valore del campo', () => {
    expect(perCalendario('2026-11-01', 'month')).toBe('2026-11');
    expect(perCalendario('2026-11-09', 'date')).toBe('2026-11-09');
  });
});

describe('oltre il massimo', () => {
  it('con max non si va avanti oltre', () => {
    expect(sidopo('2026-11-09', 'settimana', '2026-11-15')).toBe(false);
    expect(sidopo('2026-11-09', 'settimana', '2026-11-16')).toBe(true);
    expect(sidopo('2026-11-01', 'mese')).toBe(true);
  });
});

describe('limitaPeriodo', () => {
  it('oltre il massimo torna al periodo del massimo', () => {
    expect(limitaPeriodo('2026-12-10', 'mese', '2026-11-18')).toBe('2026-11-01');
    expect(limitaPeriodo('2026-11-30', 'settimana', '2026-11-18')).toBe('2026-11-16');
    expect(limitaPeriodo('2026-11-19', 'giorno', '2026-11-18')).toBe('2026-11-18');
  });
  it('dentro il massimo (o senza) porta solo al primo giorno del periodo', () => {
    expect(limitaPeriodo('2026-11-18', 'mese', '2026-11-18')).toBe('2026-11-01');
    expect(limitaPeriodo('2026-11-12', 'settimana')).toBe('2026-11-09');
  });
});
