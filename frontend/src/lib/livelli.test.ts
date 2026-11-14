import { describe, expect, it } from 'vitest';
import type { BambinoLivelli, TurnoLivelli } from './api';
import { conteggi, corsiDi, prossimoDaCompletare, quandoBreve, statoTurno, tuttiPassano } from './livelli';

const turno = (id: string, o: Partial<TurnoLivelli> = {}): TurnoLivelli => ({
  lezioneId: id, corsoId: 'giallo', corso: 'Delfino Giallo', colore: '#E6B400', giornoSettimana: 2, inizio: '17:00:00', fine: '17:45:00',
  successivoId: 'verde', successivo: 'Delfino Verde', coloreSuccessivo: '#2E9E5B', iscritti: 8, passano: 0, continuano: 0, mio: true, ...o,
});
const bimbo = (nome: string, esito: BambinoLivelli['esito'] = null): BambinoLivelli => ({
  iscrizioneId: `i-${nome}`, personaId: `p-${nome}`, nome, cognome: 'Prova', eta: 8, esito, passaggio: null,
});

describe('livelli', () => {
  it('scrive giorno e ora brevi', () => {
    expect(quandoBreve(turno('a'))).toBe('Mar 17:00');
    expect(quandoBreve(turno('a', { giornoSettimana: 7, inizio: '09:30:00' }))).toBe('Dom 09:30');
  });

  it('dice a che punto è un turno', () => {
    expect(statoTurno(turno('a'))).toMatchObject({ fatti: 0, completo: false, perc: 0, testo: 'da fare' });
    expect(statoTurno(turno('a', { passano: 3, continuano: 1 }))).toMatchObject({ fatti: 4, perc: 50, testo: '4/8 esiti' });
    expect(statoTurno(turno('a', { passano: 6, continuano: 2 }))).toMatchObject({ completo: true, perc: 100, testo: '✓ completo' });
    expect(statoTurno(turno('a', { iscritti: 0 })).testo).toBe('nessun iscritto');
  });

  it('conta gli esiti e dà «passa» solo a chi non ne ha', () => {
    const b = [bimbo('Anna', 'PASSA'), bimbo('Leo', 'CONTINUA'), bimbo('Mia'), bimbo('Ugo')];
    expect(conteggi(b)).toEqual({ passano: 1, continuano: 1, mancano: 2 });
    expect(tuttiPassano(b)).toEqual([{ personaId: 'p-Mia', esito: 'PASSA' }, { personaId: 'p-Ugo', esito: 'PASSA' }]);
  });

  it('trova il prossimo turno da completare, ricominciando dall\'inizio', () => {
    const t = [turno('a', { passano: 8 }), turno('b'), turno('c', { iscritti: 0 }), turno('d', { passano: 2 })];
    expect(prossimoDaCompletare(t, 'b')?.lezioneId).toBe('d');
    expect(prossimoDaCompletare(t, 'd')?.lezioneId).toBe('b');
    expect(prossimoDaCompletare([turno('a', { passano: 8 })], 'a')).toBeNull();
    expect(prossimoDaCompletare(t, null)?.lezioneId).toBe('b');
  });

  it('elenca i corsi distinti in ordine di nome', () => {
    const t = [turno('a'), turno('b'), turno('c', { corsoId: 'stella', corso: 'Stella Marina' }), turno('d', { corsoId: 'azz', corso: 'Delfino Azzurro' })];
    expect(corsiDi(t).map((c) => c.nome)).toEqual(['Delfino Azzurro', 'Delfino Giallo', 'Stella Marina']);
  });
});
