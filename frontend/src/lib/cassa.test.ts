import { describe, expect, it } from 'vitest';
import type { PagamentoRegistrato, VoceScadenza } from './api';
import type { PaginaCassa, StatoCassa } from './api';
import { andamento, conSegno, contanti, esitoDifferenza, lasciatoProposto, nelFiltro, numeroDa, perGiornoScadenze, quando, registro, serveAprire, settimane, sommaTagli, statoDaMostrare, tipoBreve, urgenza } from './cassa';
import { euro } from './listino';

const voce = (p: Partial<VoceScadenza>): VoceScadenza => ({
  tipo: 'CERTIFICATO', personaId: 'p', nome: 'A', cognome: 'B', eta: 30, descrizione: 'd', corso: null, stagione: null, scadenza: null, giorni: null,
  riferimento: 'r', email: null, ultimoPromemoria: null, importo: null, ...p,
});
let n = 0;
const pag = (data: string, ora: string, metodi: [string, number][], o: Partial<PagamentoRegistrato> = {}): PagamentoRegistrato => ({
  id: String(++n), data, importo: metodi.reduce((a, m) => a + m[1], 0), paganteId: 'x', pagante: 'Paola', note: null, registratoDa: null,
  registratoIl: `${data}T${ora}:00Z`, annullatoIl: null, motivoAnnullamento: null,
  metodi: metodi.map(([metodo, importo], i) => ({ id: `${n}-${i}`, metodoId: metodo, metodo, importo, daIncassare: false, contanti: metodo === 'Contanti', incassatoIl: null, riferimento: null })),
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

  it('i pagamenti scaduti hanno il loro filtro e restano tra gli scaduti', () => {
    const p = voce({ tipo: 'PAGAMENTO', giorni: -12 });
    expect(tipoBreve('PAGAMENTO')).toBe('pag');
    expect(nelFiltro(p, 'pag')).toBe(true);
    expect(nelFiltro(p, 'abb')).toBe(false);
    expect(urgenza(p)).toBe('scaduti');
    expect(quando(p).testo).toBe('Scaduto da 12 giorni');
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
      uscite: [{ id: 'u', data: '2026-10-27', personaId: 'x', persona: 'Mario', metodo: 'Contanti', contanti: true, importo: 5, descrizione: 'Cauzione', registratoDa: null, registratoIl: '2026-10-27T09:30:00Z' }],
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

describe('chiusura di cassa', () => {
  it('legge gli importi scritti a mano', () => {
    expect(numeroDa('1.234,50')).toBe(1234.5);
    expect(numeroDa('12.5')).toBe(12.5);
    expect(numeroDa(' € 20 ')).toBe(20);
    expect(numeroDa('')).toBeNull();
    expect(numeroDa('dieci')).toBeNull();
  });

  it('conta per tagli senza errori di arrotondamento', () => {
    expect(sommaTagli({ '50': 2, '20': 1, '0.1': 3, '0.2': 1, '0.05': 1 })).toBe(120.55);
    expect(sommaTagli({ '10': -2, '5': 1.7 })).toBe(5);
    expect(sommaTagli({})).toBe(0);
  });

  it('dice se la cassa torna e con il segno', () => {
    expect(esitoDifferenza(0.001).tono).toBe('ok');
    expect(esitoDifferenza(3).tono).toBe('piu');
    expect(esitoDifferenza(-2).testo).toBe('Mancano dei contanti');
    expect(conSegno(-2, euro)).toBe(`−${euro(2)}`);
    expect(conSegno(3, euro)).toBe(`+${euro(3)}`);
    expect(lasciatoProposto(80, { importoFondo: 100 })).toBe(80);
    expect(lasciatoProposto(250, { importoFondo: 100 })).toBe(100);
  });

  it('sceglie la cassa del computer e sa quando serve aprire', () => {
    const stato = (id: string, principale: boolean) => ({ cassa: { id, nome: id, attiva: true, principale, ordine: 0 } }) as StatoCassa;
    const p = { impostazioni: { casse: 'PER_POSTAZIONE', fondo: 'RIPORTATO', importoFondo: 100, chiusura: 'TURNO' }, casse: [], stati: [stato('a', true), stato('b', false)] } as PaginaCassa;
    expect(statoDaMostrare(p, 'b')?.cassa.id).toBe('b');
    expect(statoDaMostrare(p, 'sparita')?.cassa.id).toBe('a');
    expect(statoDaMostrare({ ...p, impostazioni: { ...p.impostazioni, casse: 'UNICA' } }, 'b')?.cassa.id).toBe('a');
    expect(serveAprire({ chiusura: 'GIORNATA', fondo: 'RIPORTATO' })).toBe(false);
    expect(serveAprire({ chiusura: 'GIORNATA', fondo: 'CONTATO' })).toBe(true);
    expect(serveAprire({ chiusura: 'TURNO', fondo: 'FISSO' })).toBe(true);
  });
});

