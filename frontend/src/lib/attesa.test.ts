import { describe, expect, it } from 'vitest';
import type { EventoAttesa, PaginaAttesa, TurnoPosti, VoceAttesa } from './api';
import {
  azionePersona, descriviEvento, nelFiltro, notaVoce, ordinaPersone, personeInAttesa, postiDaAssegnare, postoLibero, quandoBreve, riepilogo,
  statoPersona, statoVoce, testoAvviso, testoPoi,
} from './attesa';

const OGGI = '2026-10-28'; // mercoledì
let n = 0;
const voce = (nome: string, o: Partial<VoceAttesa> = {}): VoceAttesa => ({
  id: `v${++n}`, personaId: `p-${nome}`, nome, cognome: 'Rossi', dataNascita: null, eta: 8, aggiuntaIl: '2026-10-01T09:00:00Z', note: null,
  avvisataIl: null, avvisoAVoce: false, rispostaEntro: null, senzaRispostaIl: null, confermaChiestaIl: null, confermataIl: null, posizione: 1,
  contatti: ['famiglia@example.org'], ...o,
});
const turno = (corso: string, attesa: VoceAttesa[], o: Partial<TurnoPosti> = {}): TurnoPosti => ({
  lezioneId: `l-${corso}`, corsoId: `c-${corso}`, corso, tipo: 'SCUOLA_NUOTO', colore: null, etaDa: 6, etaA: 9, giornoSettimana: 1, inizio: '17:00:00',
  fine: '17:45:00', posti: 8, occupati: 8, liberi: 0, liberoDal: null, inAttesa: attesa.length,
  attesa: attesa.map((v, i) => ({ ...v, posizione: i + 1 })), ...o,
});
const pagina = (turni: TurnoPosti[]): PaginaAttesa => ({ oggi: OGGI, societa: 'Leone XIII Sport', giorniRisposta: 3, turni, storico: [] });

describe('stato di una voce', () => {
  it('nuovo, avvisata entro il termine, scaduto dopo, saltata se il posto è passato al prossimo', () => {
    expect(statoVoce(voce('A'), OGGI)).toBe('nuovo');
    expect(statoVoce(voce('A', { avvisataIl: '2026-10-27T10:00:00Z', rispostaEntro: '2026-10-30' }), OGGI)).toBe('avvisata');
    expect(statoVoce(voce('A', { avvisataIl: '2026-10-27T10:00:00Z', rispostaEntro: OGGI }), OGGI)).toBe('avvisata');
    expect(statoVoce(voce('A', { avvisataIl: '2026-10-22T10:00:00Z', rispostaEntro: '2026-10-25' }), OGGI)).toBe('scaduto');
    expect(statoVoce(voce('A', { avvisataIl: '2026-10-22T10:00:00Z', senzaRispostaIl: '2026-10-26T10:00:00Z' }), OGGI)).toBe('saltata');
  });

  it('la nota dice come e quando è stato avvisato', () => {
    expect(notaVoce(voce('A', { avvisataIl: '2026-10-27T10:00:00Z', rispostaEntro: '2026-10-30' }), OGGI).testo)
      .toBe('Avviso per email ieri, risposta entro venerdì 30/10');
    const scaduta = notaVoce(voce('A', { avvisataIl: '2026-10-22T10:00:00Z', avvisoAVoce: true, rispostaEntro: '2026-10-25' }), OGGI);
    expect(scaduta.tono).toBe('warn');
    expect(scaduta.testo).toContain('a voce giovedì 22/10');
    expect(notaVoce(voce('A', { contatti: [] }), OGGI).testo).toContain('va chiamato');
  });

  it('date vicine a parole', () => {
    expect(quandoBreve(OGGI, OGGI)).toBe('oggi');
    expect(quandoBreve('2026-10-29', OGGI)).toBe('domani');
    expect(quandoBreve('2026-11-02', OGGI)).toBe('lunedì 02/11');
  });
});

describe('posti da assegnare', () => {
  const saltata = voce('Giorgia', { avvisataIl: '2026-10-20T10:00:00Z', senzaRispostaIl: '2026-10-25T10:00:00Z' });
  const leo = voce('Leonardo');
  const emma = voce('Emma', { contatti: [] });
  const libero = turno('Squalo', [saltata, leo, emma], { occupati: 7, liberi: 1, liberoDal: OGGI });
  const dal = turno('Delfino', [voce('Chiara')], { liberoDal: '2026-11-02' });
  const pieno = turno('Pesciolini', [voce('Bianca'), voce('Tommaso')]);
  const p = pagina([pieno, dal, libero]);

  it('solo i turni con un posto, prima quelli liberi da oggi; tocca al primo non saltato', () => {
    const posti = postiDaAssegnare(p);
    expect(posti.map((x) => x.turno.corso)).toEqual(['Squalo', 'Delfino']);
    expect(posti[0].tocca?.nome).toBe('Leonardo');
    expect(posti[0].poi.map((v) => v.nome)).toEqual(['Emma']);
    expect(posti[0].saltati.map((v) => v.nome)).toEqual(['Giorgia']);
    expect(postoLibero(dal, OGGI).testo).toBe('Un posto dal 02/11');
    expect(postoLibero(turno('X', [], { liberi: 2, liberoDal: OGGI }), OGGI).testo).toBe('2 posti liberi');
  });

  it('il riepilogo conta posti, persone, avvisi e attesa', () => {
    const r = riepilogo(p);
    expect(r).toMatchObject({ posti: 2, turniConPosto: 2, persone: 6, turniPieni: 1, avvisi: 0, attesaMedia: 27, attesaMassima: 27 });
    const conAvviso = riepilogo(pagina([turno('Squalo', [voce('A', { avvisataIl: '2026-10-20T10:00:00Z', rispostaEntro: '2026-10-23' })], { liberoDal: OGGI, liberi: 1 })]));
    expect(conAvviso).toMatchObject({ avvisi: 1, scaduti: 1 });
  });

  it('chi viene dopo, in breve', () => {
    expect(testoPoi([])).toBe('Nessun altro in lista');
    expect(testoPoi([voce('A'), voce('B'), voce('C'), voce('D')])).toBe('Poi: A Rossi, B Rossi e altri 2');
  });

  it('il testo dell\'avviso dice il turno, da quando e entro quando rispondere', () => {
    const t = testoAvviso(dal.attesa[0], dal, p);
    expect(t.oggetto).toBe('Un posto per Chiara nel corso Delfino');
    expect(t.testo).toContain('lunedì 17:00 dal 02/11/2026');
    expect(t.testo).toContain('entro sabato 31/10');
    expect(t.testo).toContain('Leone XIII Sport');
  });
});

describe('persone in attesa', () => {
  const matteo1 = voce('Matteo', { personaId: 'matteo', aggiuntaIl: '2026-09-15T09:00:00Z' });
  const matteo2 = voce('Matteo', { personaId: 'matteo', aggiuntaIl: '2026-09-20T09:00:00Z' });
  const anna = voce('Anna', { personaId: 'anna', cognome: 'Bianchi', aggiuntaIl: '2026-10-21T09:00:00Z' });
  const zoe = voce('Zoe', { personaId: 'zoe', cognome: 'Alberti', aggiuntaIl: '2026-10-10T09:00:00Z' });
  const p = pagina([
    turno('Delfino Verde', [matteo1, anna], { liberoDal: OGGI, liberi: 1 }),
    turno('Delfino Giallo', [zoe, matteo2]),
    turno('AquaFitness', [voce('Franco', { personaId: 'franco', cognome: 'Neri', aggiuntaIl: '2026-08-01T09:00:00Z' })], { tipo: 'ACQUAGYM' }),
  ]);

  it('una riga per persona, con tutti i turni e da quanto aspetta (il più vecchio)', () => {
    const persone = personeInAttesa(p);
    expect(persone).toHaveLength(4);
    const m = persone.find((x) => x.personaId === 'matteo')!;
    expect(m.turni.map((t) => [t.turno.corso, t.voce.posizione, t.di, t.tocca])).toEqual([['Delfino Verde', 1, 2, true], ['Delfino Giallo', 2, 2, false]]);
    expect(m.giorni).toBe(43);
    expect(statoPersona(m, OGGI)).toEqual({ testo: 'c\'è un posto: da avvisare', tono: 'cloro' });
    expect(azionePersona(m, OGGI)).toEqual({ tipo: 'avvisa', testo: 'Avvisa' });
    expect(azionePersona(persone.find((x) => x.personaId === 'anna')!, OGGI)).toBeNull();
  });

  it('si ordina per nome, per corso (categoria e nome) o per attesa', () => {
    const persone = personeInAttesa(p);
    expect(ordinaPersone(persone, 'nome').map((x) => x.nome)).toEqual(['Zoe', 'Anna', 'Franco', 'Matteo']);
    expect(ordinaPersone(persone, 'attesa').map((x) => x.nome)).toEqual(['Franco', 'Matteo', 'Zoe', 'Anna']);
    expect(ordinaPersone(persone, 'corso').map((x) => x.nome)).toEqual(['Zoe', 'Matteo', 'Anna', 'Franco']);
  });

  it('filtri: con un posto, avvisati, da più di 30 giorni', () => {
    const persone = personeInAttesa(p);
    expect(persone.filter((x) => nelFiltro(x, 'posto', OGGI)).map((x) => x.nome)).toEqual(['Matteo']);
    expect(persone.filter((x) => nelFiltro(x, 'lunga', OGGI)).map((x) => x.nome).sort()).toEqual(['Franco', 'Matteo']);
    expect(persone.filter((x) => nelFiltro(x, 'avvisati', OGGI))).toHaveLength(0);
  });
});

it('lo storico a parole', () => {
  const e: EventoAttesa = { quando: '2026-10-27T16:00:00Z', tipo: 'AVVISATA', personaId: 'x', nome: 'Sara', cognome: 'Neri', corso: 'Delfino Giallo',
    giornoSettimana: 2, inizio: '17:00:00', da: 'Carla Bianchi' };
  expect(descriviEvento(e, OGGI)).toEqual({ chi: 'Sara Neri', cosa: 'avviso per email · Delfino Giallo mar 17:00', quando: 'ieri · Carla Bianchi' });
  expect(descriviEvento({ ...e, tipo: 'CONFERMATA', da: null }, OGGI).cosa).toBe('conferma che aspetta');
});
