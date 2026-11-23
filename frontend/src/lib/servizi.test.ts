import { describe, expect, it } from 'vitest';
import type { Servizio } from './api';
import { bozzaDiServizio, bozzaServizioVuota, datiServizio, erroriServizio, riassuntoServizio, servizioValido, usiConRicarica } from './servizi';

const serv = (id: string, nome: string, crediti: number, extra: Partial<Servizio> = {}): Servizio =>
  ({ id, nome, crediti, gratuitiAlGiorno: 0, minuti: null, attivo: true, ordine: 0, ...extra });

describe('riassuntoServizio', () => {
  it('le parti in fila con « · »', () => {
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 0, minuti: null })).toBe('1 credito');
    expect(riassuntoServizio({ crediti: 1, gratuitiAlGiorno: 1, minuti: 5 })).toBe('1 credito · 1 gratis al giorno · 5 minuti');
    expect(riassuntoServizio({ crediti: 3, gratuitiAlGiorno: 2, minuti: 1 })).toBe('3 crediti · 2 gratis al giorno · 1 minuto');
  });
  it('zero crediti: «gratis», senza «gratis al giorno»', () => {
    expect(riassuntoServizio({ crediti: 0, gratuitiAlGiorno: 2, minuti: null })).toBe('gratis');
    expect(riassuntoServizio({ crediti: 0, gratuitiAlGiorno: 0, minuti: 10 })).toBe('gratis · 10 minuti');
  });
});

describe('usiConRicarica', () => {
  it('solo i servizi accesi e a pagamento, al massimo due, per difetto', () => {
    const s = [serv('a', 'Phon', 1), serv('b', 'Docce', 2), serv('c', 'Spento', 1, { attivo: false }), serv('d', 'Gratis', 0), serv('e', 'Terzo', 1)];
    expect(usiConRicarica(25, s)).toEqual([{ nome: 'Phon', usi: 25 }, { nome: 'Docce', usi: 12 }]);
    expect(usiConRicarica(25, [s[2], s[3], s[4]])).toEqual([{ nome: 'Terzo', usi: 25 }]);
  });
});

describe('bozza del servizio', () => {
  it('da un servizio e di nuovo', () => {
    expect(bozzaDiServizio(serv('a', 'Phon', 2, { gratuitiAlGiorno: 1, minuti: 5 }))).toEqual({ nome: 'Phon', crediti: '2', gratuiti: '1', minuti: '5' });
    expect(bozzaServizioVuota()).toEqual({ nome: '', crediti: '1', gratuiti: '0', minuti: '' });
  });
  it('valida: interi ≥ 0, minuti vuoto o ≥ 1', () => {
    expect(servizioValido({ nome: 'Phon', crediti: '1', gratuiti: '0', minuti: '' })).toBe(true);
    expect(erroriServizio({ nome: ' ', crediti: '', gratuiti: '-1', minuti: '0' })).toMatchObject({ nome: expect.any(String), crediti: expect.any(String), gratuiti: expect.any(String), minuti: expect.any(String) });
    expect(servizioValido({ nome: 'Phon', crediti: '1,5', gratuiti: '0', minuti: '' })).toBe(false);
  });
  it('i dati per il server', () => {
    expect(datiServizio({ nome: ' Phon ', crediti: '2', gratuiti: '1', minuti: '' })).toEqual({ nome: 'Phon', crediti: 2, gratuitiAlGiorno: 1, minuti: null });
    expect(datiServizio({ nome: 'Docce', crediti: '1', gratuiti: '0', minuti: '5' }).minuti).toBe(5);
  });
});
