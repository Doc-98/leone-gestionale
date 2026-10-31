import { describe, expect, it } from 'vitest';
import type { Turno } from './api';
import { areaDi, caselleMese, conMe, fasce, occupato, orePerArea, prossimo, richiestaSul, settimaneDelMese, titoloSettimana, traQuanto } from './turni';
import type { Richiesta } from './api';

let n = 0;
const t = (data: string, inizio: string, fine: string, o: Partial<Turno> = {}): Turno => ({
  id: `t${++n}`, data, inizio: `${inizio}:00`, fine: `${fine}:00`, ore: 3, impiantoId: 'i', postazioneId: 'p', mansione: 'Assistente spogliatoio',
  postazione: 'Spogliatoio M · postazione 1', stato: 'PIANIFICATO', note: null, persona: null, ...o,
});

describe('turni', () => {
  it('area dalla postazione', () => {
    expect(areaDi('Spogliatoio M · postazione 1')).toBe('m');
    expect(areaDi('Spogliatoio F · postazione 2')).toBe('f');
    expect(areaDi('Spogliatoio piccoli · postazione 4')).toBe('p');
    expect(areaDi('Atrio')).toBe('a');
    expect(areaDi('Bar')).toBe('x');
  });

  it('il prossimo turno e tra quanto', () => {
    const oggi = '2026-10-31';
    const lista = [t('2026-10-30', '16:00', '19:00'), t(oggi, '09:30', '13:15'), t('2026-11-02', '16:00', '19:00'), t(oggi, '15:00', '16:00', { stato: 'ANNULLATO' })];
    expect(prossimo(lista, oggi, 8 * 60 + 50)?.inizio).toBe('09:30:00');
    expect(traQuanto(lista[1], oggi, 8 * 60 + 50)).toBe('oggi · tra 40 minuti');
    expect(traQuanto(lista[1], oggi, 10 * 60)).toBe('in corso');
    expect(prossimo(lista, oggi, 14 * 60)?.data).toBe('2026-11-02');
    expect(traQuanto(lista[2], oggi, 0)).toBe('lunedì 2/11');
    expect(traQuanto(t('2026-11-01', '09:00', '10:00'), oggi, 0)).toBe('domani');
  });

  it('chi c\'è con me e chi è occupato', () => {
    const mio = t('2026-11-02', '16:00', '19:00');
    const giulia = t('2026-11-02', '17:00', '20:00', { persona: { id: 'g', nome: 'Giulia' } });
    const marco = t('2026-11-02', '19:00', '21:00', { persona: { id: 'm', nome: 'Marco' } });
    expect(conMe(mio, [mio, giulia, marco]).map((x) => x.persona?.nome)).toEqual(['Giulia']);
    expect(occupato('g', mio, [giulia])?.id).toBe(giulia.id);
    expect(occupato('m', mio, [marco])).toBeNull();
  });

  it('titolo della settimana, caselle del mese, fasce', () => {
    expect(titoloSettimana('2026-10-26')).toBe('26 ottobre – 1 novembre');
    expect(titoloSettimana('2026-11-02')).toBe('2 – 8 novembre');
    expect(titoloSettimana('2026-10-26', true)).toBe('26 ott – 1 nov');
    const ottobre = caselleMese('2026-10');
    expect(ottobre.length % 7).toBe(0);
    expect(ottobre.slice(0, 4)).toEqual([null, null, null, '2026-10-01']);
    expect(ottobre.filter(Boolean)).toHaveLength(31);
    expect(fasce([t('2026-11-02', '07:30', '10:00'), t('2026-11-03', '20:00', '22:15')])).toEqual({ da: 7, a: 23 });
  });

  it('richiesta aperta sul turno, ore per area, settimane del mese', () => {
    const mio = t('2026-11-03', '17:00', '20:30', { postazione: 'Spogliatoio F · postazione 2', ore: 3.5 });
    const r = { id: 'r', tipo: 'SOSTITUZIONE', stato: 'IN_ATTESA_COLLEGA', turno: mio, turnoCollega: null, richiedente: { id: 'io', nome: 'Luca' },
      collega: { id: 'g', nome: 'Giulia Bassi' }, nuovoInizio: null, nuovoFine: null, messaggio: null, creataIl: '', decisaIl: null, notaDecisione: null } as Richiesta;
    expect(richiestaSul(mio, [r], 'io')).toBe('Sostituzione chiesta · attende Giulia');
    expect(richiestaSul(mio, [{ ...r, stato: 'APPROVATA' }], 'io')).toBeNull();
    expect(orePerArea([mio, t('2026-11-02', '16:00', '19:00')])).toEqual([{ area: 'm', ore: 3 }, { area: 'f', ore: 3.5 }]);
    expect(settimaneDelMese('2026-10').map((s) => s.nome)).toEqual(['1–4', '5–11', '12–18', '19–25', '26–31']);
  });
});
