import { describe, expect, it } from 'vitest';
import type { Utilizzo } from './api';
import { daRicaricare, statoMacchina } from './macchine';

let n = 0;
const uso = (p: Partial<Utilizzo>): Utilizzo => ({
  id: `u${++n}`, istante: '2026-11-07T16:00:00Z', dispositivoId: 'd', dispositivo: 'Phon donne', servizio: 'Phon', codice: 'AA000001', personaId: 'sofia',
  persona: 'Sofia Riva', esito: 'OK', crediti: 2, postazione: null, simulato: false, ...p,
});

describe('chi è da ricaricare', () => {
  it('chi oggi ha avuto «crediti finiti», con i tentativi, il più recente per primo', () => {
    const coda = daRicaricare([
      uso({ istante: '2026-11-07T16:40:00Z', esito: 'CREDITI_FINITI' }),
      uso({ istante: '2026-11-07T16:42:00Z', esito: 'CREDITI_FINITI', servizio: 'Phon 2' }),
      uso({ istante: '2026-11-07T16:30:00Z', esito: 'CREDITI_FINITI', personaId: 'pietro', persona: 'Pietro Sala', servizio: 'Docce', codice: 'BB000002' }),
    ], '2026-11-07');
    expect(coda.map((c) => c.personaId)).toEqual(['sofia', 'pietro']);
    expect(coda[0]).toMatchObject({ tentativi: 2, dove: 'Phon 2', ultimo: '2026-11-07T16:42:00Z', nome: 'Sofia Riva' });
  });

  it('chi poi ha usato crediti ha già ricaricato; i giorni prima non contano', () => {
    expect(daRicaricare([
      uso({ istante: '2026-11-07T16:40:00Z', esito: 'CREDITI_FINITI' }),
      uso({ istante: '2026-11-07T16:50:00Z', esito: 'OK' }),
      uso({ istante: '2026-11-06T16:40:00Z', esito: 'CREDITI_FINITI', personaId: 'ieri' }),
    ], '2026-11-07')).toEqual([]);
    // un uso gratis dopo non vuol dire che abbia ricaricato
    expect(daRicaricare([uso({ istante: '2026-11-07T16:40:00Z', esito: 'CREDITI_FINITI' }), uso({ istante: '2026-11-07T16:45:00Z', esito: 'GRATIS' })], '2026-11-07'))
      .toHaveLength(1);
  });

  it('un braccialetto di nessuno resta, con il suo codice', () => {
    const [c] = daRicaricare([uso({ esito: 'CREDITI_FINITI', personaId: null, persona: null, codice: 'CC000003', servizio: null })], '2026-11-07');
    expect(c).toMatchObject({ personaId: null, nome: null, codice: 'CC000003', dove: 'Phon donne' });
  });
});

describe('stato delle macchine', () => {
  const adesso = new Date('2026-11-07T17:00:00Z').getTime();
  it('spenta, mai collegata, muta da ore o giorni, o in funzione', () => {
    expect(statoMacchina({ attivo: false, ultimoContatto: null, utilizziOggi: 0 }, adesso)).toEqual({ classe: 'spenta', testo: 'spenta' });
    expect(statoMacchina({ attivo: true, ultimoContatto: null, utilizziOggi: 0 }, adesso)).toEqual({ classe: 'nuova', testo: 'mai collegata' });
    expect(statoMacchina({ attivo: true, ultimoContatto: '2026-11-07T13:30:00Z', utilizziOggi: 4 }, adesso)).toEqual({ classe: 'muta', testo: 'nessun segnale da 3 h' });
    expect(statoMacchina({ attivo: true, ultimoContatto: '2026-11-04T17:00:00Z', utilizziOggi: 0 }, adesso).testo).toBe('nessun segnale da 3 giorni');
    expect(statoMacchina({ attivo: true, ultimoContatto: '2026-11-07T16:58:00Z', utilizziOggi: 41 }, adesso)).toEqual({ classe: 'ok', testo: '41 oggi' });
  });
});
