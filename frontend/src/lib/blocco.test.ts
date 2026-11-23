import { describe, expect, it } from 'vitest';
import { righeBlocco } from './blocco';

describe('righeBlocco', () => {
  it('due righe: l\'attacco è la parte fino ai due punti compresi', () => {
    expect(righeBlocco('Non si può eliminare: 120 turni usano Postazione 3.\nPuoi spegnerla: i turni restano com\'erano.')).toEqual([
      { attacco: 'Non si può eliminare:', resto: '120 turni usano Postazione 3.' },
      { attacco: 'Puoi spegnerla:', resto: "i turni restano com'erano." },
    ]);
  });
  it('una riga senza due punti resta normale', () => {
    expect(righeBlocco('Satispay ha ancora 2 pagamenti da incassare')).toEqual([{ attacco: '', resto: 'Satispay ha ancora 2 pagamenti da incassare' }]);
  });
  it('vale solo il primo «:» di ogni riga', () => {
    expect(righeBlocco('Chiudi la cassa: poi alle 18:30 puoi eliminarlo.')).toEqual([{ attacco: 'Chiudi la cassa:', resto: 'poi alle 18:30 puoi eliminarlo.' }]);
  });
  it('ignora righe vuote e spazi, anche con \\r', () => {
    expect(righeBlocco('  A: uno \r\n\n B: due  ')).toEqual([{ attacco: 'A:', resto: 'uno' }, { attacco: 'B:', resto: 'due' }]);
  });
  it('testo vuoto: nessuna riga', () => {
    expect(righeBlocco('')).toEqual([]);
  });
});
