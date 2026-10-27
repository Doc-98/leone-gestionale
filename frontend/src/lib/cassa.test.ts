import { describe, expect, it } from 'vitest';
import type { PagamentoRegistrato, VoceScadenza } from './api';
import { andamento, contanti, nelFiltro, perGiornoScadenze, quando, registro, settimane, tipoBreve, urgenza } from './cassa';

const voce = (p: Partial<VoceScadenza>): VoceScadenza => ({
  tipo: 'CERTIFICATO', personaId: 'p', nome: 'A', cognome: 'B', eta: 30, descrizione: 'd', corso: null, stagione: null, scadenza: null, giorni: null,
  riferimento: 'r', email: null, ultimoPromemoria: null, ...p,
});
let n = 0;
const pag = (data: string, ora: string, metodi: [string, number][], o: Partial<PagamentoRegistrato> = {}): PagamentoRegistrato => ({
  id: String(++n), data, importo: metodi.reduce((a, m) => a + m[1], 0), paganteId: 'x', pagante: 'Paola', note: null, registratoDa: null,
  registratoIl: `${data}T${ora}:00Z`, annullatoIl: null, motivoAnnullamento: null,
  metodi: metodi.map(([metodo, importo], i) => ({ id: `${n}-${i}`, metodoId: metodo, metodo, importo, daIncassare: false, incassatoIl: null, riferimento: null })),
  voci: [{ addebitoId: 'a', descrizione: 'Quota', personaId: 's', persona: 'Sofia', importo: 0 }], ...o,
});

describe('scadenze', () => {
  it('raggruppa per urgenza e dice quando', () => {
    expect(urgenza(voce({ giorni: null }))).toBe('scaduti');
    expect(urgenza(voce({ giorni: -3 }))).toBe('scaduti');
    expect(urgenza(voce({ giorni: 0 }))).toBe('oggi');
    expect(urgenza(voce({ giorni: 7 }))).toBe('settimana');
    expect(urgenza(voce({ giorni: 8 }))).toBe('avanti');
    expect(quando(voce({ giorni: -15 })).testo).toBe('Scaduto da 15 giorni');
    expect(quando(voce({ giorni: null, tipo: 'CARNET' })).testo).toBe('Da rinnovare');
    expect(quando(voce({ giorni: 1 }))).toEqual({ testo: 'Domani', classe: 'arancio' });
  });

  it('i carnet stanno con gli abbonamenti nel filtro', () => {
    expect(tipoBreve('TESSERAMENTO_MANCANTE')).toBe('tess');
    expect(nelFiltro(voce({ tipo: 'CARNET' }), 'abb')).toBe(true);
    expect(nelFiltro(voce({ tipo: 'CARNET' }), 'cert')).toBe(false);
  });

  it('il calendario parte dal lunedì della settimana e mette le scadenze nel loro giorno', () => {
    const s = settimane('2026-10-28', 2);
    expect(s[0][0]).toBe('2026-10-26');
    expect(s[1][6]).toBe('2026-11-08');
    const m = perGiornoScadenze([voce({ scadenza: '2026-10-30', giorni: 2 }), voce({ scadenza: '2026-10-20', giorni: -8 }), voce({ giorni: null })]);
    expect([...m.keys()]).toEqual(['2026-10-30']);
  });
});

describe('incassi', () => {
  it('conta i contanti entrati e usciti, senza gli annullati', () => {
    const d = {
      pagamenti: [pag('2026-10-27', '09:00', [['Contanti', 50]]), pag('2026-10-27', '10:00', [['Carta', 40], ['Contanti', 10.5]]),
        pag('2026-10-27', '11:00', [['Contanti', 99]], { annullatoIl: '2026-10-27T11:05:00Z' })],
      uscite: [{ id: 'u', data: '2026-10-27', personaId: 'x', persona: 'Mario', metodo: 'Contanti', importo: 5, descrizione: 'Cauzione', registratoDa: null, registratoIl: '2026-10-27T09:30:00Z' }],
    };
    expect(contanti(d)).toEqual({ entrati: 60.5, usciti: 5 });
    const r = registro(d);
    expect(r.map((x) => x.contanti)).toEqual([50, 45, 55.5, null]);
    expect(r[3].annullato).toBe(true);
  });

  it('raggruppa per giorno e per settimana, divisi per metodo', () => {
    const p = [pag('2026-10-01', '09:00', [['Contanti', 10]]), pag('2026-10-05', '09:00', [['Carta', 20]]), pag('2026-10-06', '09:00', [['Contanti', 5], ['Carta', 5]])];
    const g = andamento(p, ['Contanti', 'Carta'], '2026-10-01', '2026-10-07', 'giorno');
    expect(g).toHaveLength(7);
    expect(g[5]).toMatchObject({ etichetta: '6', totale: 10, pezzi: [5, 5] });
    const s = andamento(p, ['Contanti', 'Carta'], '2026-10-01', '2026-10-07', 'settimana');
    expect(s.map((x) => [x.etichetta, x.totale])).toEqual([['1–4', 10], ['5–7', 30]]);
  });
});
