import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ImpostazioniCassa from '../routes/ImpostazioniCassa.svelte';
import ImpostazioniRicevute from '../routes/ImpostazioniRicevute.svelte';
import type { Cassa, Me, MetodoPagamento, SerieRicevute } from './api';
import { sessione } from './sessione.svelte';

/**
 * Specifica (brief coerenza blocco 7, lotto 2): serie delle ricevute e casse come righe di elenco modificabile.
 * Si prova solo attraverso ruoli ed etichette accessibili: maniglia «Sposta «X»», interruttore role=switch
 * «Acceso: «X»», cestino «Elimina «X»» (primo tocco «Elimina?», secondo elimina), riga «Aggiungi una serie…» che
 * apre un foglio (dialog). Il server è un `fetch` finto che segue il contratto del brief:
 *   PUT /api/ricevute/serie/ordine  {ids}  -> 200 con l'elenco delle serie
 *   DELETE /api/ricevute/serie/{id}        -> 204, o 409 con il messaggio a righe separate da \n
 *   PUT /api/cassa/casse/ordine     {ids}  -> 200 con l'elenco delle casse
 *   DELETE /api/cassa/casse/{id}           -> 204, o 409
 */
type Chiamata = { metodo: string; url: string; corpo: unknown };
type SerieDto = SerieRicevute & { eliminabile?: boolean | null; motivoBlocco?: string | null };
type CassaDto = Cassa & { eliminabile?: boolean | null; motivoBlocco?: string | null };

let chiamate: Chiamata[];
let serie: SerieDto[];
let casse: CassaDto[];
let metodi: MetodoPagamento[];
let modoCasse: 'UNICA' | 'PER_POSTAZIONE';
/** risposta forzata per una chiamata «METODO url» */
let forzate: Record<string, { status: number; corpo?: unknown }>;

const BLOCCO_SERIE =
  'Non si può eliminare: la serie «Ricevute» ha emesso 412 ricevute.\nPuoi spegnerla: le ricevute restano con il loro numero, ma la serie non verrà più usata per le nuove.';
const BLOCCO_CASSA =
  'Non si può eliminare: la cassa «Piscina» ha 58 sessioni nello storico.\nPuoi spegnerla: le sessioni restano com\'erano, ma la cassa non verrà più proposta per le nuove.';

const unaSerie = (id: string, nome: string, extra: Partial<SerieDto> = {}): SerieDto => ({
  id, nome, tipo: 'RICEVUTA', attiva: true, formato: '{n}/{anno}', cifre: 4, riparte: 'STAGIONE', prossimoNumero: null,
  periodo: '2026/27', emesse: 0, prossima: '0001', eliminabile: true, motivoBlocco: null, ...extra,
});
const unaCassa = (id: string, nome: string, extra: Partial<CassaDto> = {}): CassaDto => ({
  id, nome, attiva: true, principale: false, ordine: 0, eliminabile: true, motivoBlocco: null, ...extra,
});
const unMetodo = (id: string, nome: string, extra: Partial<MetodoPagamento> = {}): MetodoPagamento => ({
  id, nome, daIncassare: false, contanti: false, riscontro: false, attivo: true, ordine: 0, borsellino: false,
  eliminabile: true, motivoBlocco: null, ...extra,
});

function utente(permessi: Me['permessi']): Me {
  return { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi, admin: false, deveCambiarePassword: false };
}

const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });

const impostazioniRicevute = {
  intestata: 'PAGANTE', codiciFiscali: true, bollo: true, bolloSoglia: 77.47, bolloImporto: 2, dicitura: null, firma: false, dopo: 'CHIEDI', email: true, famiglia: 'CHIEDI',
};
const impostazioniCassa = {
  casse: 'PER_POSTAZIONE', fondo: 'FISSO', importoFondo: 100, chiusura: 'TURNO', cieca: false, tolleranza: 0, controllo: 'NESSUNA', spese: true, banca: false, oreAvviso: 12,
};

const aggiornaOrdine = <T extends { id: string; ordine?: number }>(lista: T[], ids: string[]): T[] =>
  ids.map((id, i) => ({ ...lista.find((x) => x.id === id)!, ordine: i + 1 }));

beforeEach(() => {
  chiamate = [];
  forzate = {};
  modoCasse = 'PER_POSTAZIONE';
  serie = [
    // due serie di ricevute attive (la prima ha emesso: il server blocca l'eliminazione), una spenta mai usata
    unaSerie('s1', 'Ricevute', { emesse: 412, eliminabile: false, motivoBlocco: BLOCCO_SERIE }),
    unaSerie('s2', 'Ricevute estate'),
    unaSerie('s3', 'Ricevute vecchie', { attiva: false }),
  ].map((s, i) => ({ ...s, ordine: i + 1 }) as SerieDto);
  casse = [
    unaCassa('c1', 'Segreteria', { principale: true, ordine: 1, eliminabile: false, motivoBlocco: 'La cassa principale non si elimina' }),
    unaCassa('c2', 'Piscina', { ordine: 2, eliminabile: false, motivoBlocco: BLOCCO_CASSA }),
    unaCassa('c3', 'Palestra', { ordine: 3 }),
  ];
  metodi = [
    unMetodo('m1', 'Contanti', { contanti: true, ordine: 1 }),
    unMetodo('m2', 'Carta', { riscontro: true, ordine: 2 }),
    unMetodo('m3', 'Satispay', { riscontro: true, ordine: 3 }),
    unMetodo('m4', 'Bonifico', { daIncassare: true, ordine: 4 }),
  ];
  sessione.utente = utente(['IMPOSTAZIONI', 'PAGAMENTI']);
  sessione.pronto = true;

  const paginaCassa = () => ({
    impostazioni: { ...impostazioniCassa, casse: modoCasse },
    casse,
    stati: casse.map((cassa) => ({ cassa, aperta: false, apertaDaTroppo: false, corrente: {}, ultimaChiusa: null, fondoProposto: 100 })),
    daRiscontrare: metodi.filter((m) => m.riscontro).map((m) => ({ id: m.id, nome: m.nome })),
    avvisi: {},
  });

  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    const f = forzate[`${m} ${url}`];
    if (f) return json(f.status, f.corpo);

    if (m === 'GET' && url === '/api/ricevute/impostazioni') return json(200, { impostazioni: impostazioniRicevute, serie, mancaIntestazione: [] });
    if (m === 'GET' && url === '/api/cassa') return json(200, paginaCassa());
    if (m === 'GET' && url === '/api/pagamenti/metodi') return json(200, metodi);
    if (m === 'GET') return json(200, {});

    if (m === 'PUT' && url === '/api/ricevute/serie/ordine') {
      serie = aggiornaOrdine(serie, (corpo as { ids: string[] }).ids);
      return json(200, serie);
    }
    if (m === 'PUT' && url === '/api/cassa/casse/ordine') {
      casse = aggiornaOrdine(casse, (corpo as { ids: string[] }).ids);
      return json(200, casse);
    }
    const idSerie = url.match(/^\/api\/ricevute\/serie\/([^/]+)$/)?.[1];
    if (idSerie && m === 'DELETE') {
      serie = serie.filter((x) => x.id !== idSerie);
      return json(204);
    }
    if (idSerie && m === 'PUT') {
      const i = serie.findIndex((x) => x.id === idSerie);
      serie[i] = { ...serie[i], ...(corpo as object) } as SerieDto;
      return json(200, serie[i]);
    }
    const idCassa = url.match(/^\/api\/cassa\/casse\/([^/]+)$/)?.[1];
    if (idCassa && m === 'DELETE') {
      casse = casse.filter((x) => x.id !== idCassa);
      return json(204);
    }
    if (idCassa && m === 'PUT') {
      const i = casse.findIndex((x) => x.id === idCassa);
      casse[i] = { ...casse[i], ...(corpo as object) } as CassaDto;
      return json(200, casse[i]);
    }
    return json(200, {});
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
  location.hash = '';
});

const put = (url: string) => chiamate.filter((c) => c.metodo === 'PUT' && c.url === url);
const cancellazioni = () => chiamate.filter((c) => c.metodo === 'DELETE');
/** i nomi delle righe, nell'ordine in cui compaiono (letti dalle maniglie) */
const ordineRighe = () =>
  screen.queryAllByLabelText(/^Sposta «/).map((e) => e.getAttribute('aria-label')!.replace(/^Sposta «|»$/g, ''));
const interruttoriRiga = () => screen.queryAllByRole('switch', { name: /^Acceso: «/ });

const attendiSerie = async () => { await screen.findByLabelText('Sposta «Ricevute»'); };
const attendiCasse = async () => { await screen.findByLabelText('Sposta «Piscina»'); };

/** Il grassetto: un elemento strong/b, oppure uno stile con font-weight pesante. */
function èGrassetto(el: HTMLElement): boolean {
  if (el.closest('strong, b')) return true;
  const peso = getComputedStyle(el).fontWeight;
  return peso === 'bold' || peso === 'bolder' || Number(peso) >= 600;
}

/**
 * Il messaggio di blocco a due righe, sotto la riga: «Non si può eliminare:» in grassetto in una riga,
 * «Puoi spegnerla: …» in un'altra, tutte e due dentro l'elemento della riga di elenco.
 */
function verificaBloccoADueRighe(nomeRiga: string, dettaglio: RegExp) {
  const riga = screen.getByLabelText(`Sposta «${nomeRiga}»`).closest('li')!;
  expect(riga).toBeTruthy();
  const attacco = within(riga).getByText('Non si può eliminare:');
  expect(èGrassetto(attacco)).toBe(true);
  const primaRiga = attacco.parentElement!;
  expect(primaRiga.textContent).toMatch(dettaglio);
  expect(primaRiga.textContent).not.toContain('Puoi spegnerla');
  const secondaRiga = within(riga).getByText('Puoi spegnerla:').parentElement!;
  expect(secondaRiga).not.toBe(primaRiga);
  expect(secondaRiga.contains(primaRiga)).toBe(false);
  expect(primaRiga.contains(secondaRiga)).toBe(false);
  expect(primaRiga.compareDocumentPosition(secondaRiga) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  // il messaggio non è ripetuto altrove (niente messaggio doppio sopra la card)
  expect(screen.getAllByText('Non si può eliminare:')).toHaveLength(1);
}

describe('Ricevute – le serie come righe', () => {
  it('ogni serie ha maniglia, interruttore e cestino, nell\'ordine del server, e c\'è «Aggiungi una serie…»', async () => {
    render(ImpostazioniRicevute);
    await attendiSerie();
    expect(ordineRighe()).toEqual(['Ricevute', 'Ricevute estate', 'Ricevute vecchie']);
    for (const nome of ['Ricevute', 'Ricevute estate', 'Ricevute vecchie']) {
      expect(screen.getByLabelText(`Sposta «${nome}»`)).toBeTruthy();
      expect(screen.getByRole('switch', { name: `Acceso: «${nome}»` })).toBeTruthy();
      expect(screen.getByRole('button', { name: `Elimina «${nome}»` })).toBeTruthy();
    }
    expect(screen.getByRole('button', { name: /Aggiungi una serie/ })).toBeTruthy();
  });

  it('l\'interruttore riflette «attiva» (role=switch, aria-checked)', async () => {
    render(ImpostazioniRicevute);
    await attendiSerie();
    expect(screen.getByRole('switch', { name: 'Acceso: «Ricevute estate»' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('switch', { name: 'Acceso: «Ricevute vecchie»' })).toHaveAttribute('aria-checked', 'false');
  });

  it('un tocco sull\'interruttore salva subito (PUT della serie con attiva=false) senza passare dalla barra', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Ricevute estate»' }));
    await waitFor(() => expect(put('/api/ricevute/serie/s2')).toHaveLength(1));
    expect((put('/api/ricevute/serie/s2')[0].corpo as { attiva: boolean }).attiva).toBe(false);
    await waitFor(() =>
      expect(screen.getByRole('switch', { name: 'Acceso: «Ricevute estate»' })).toHaveAttribute('aria-checked', 'false'));
  });

  it('la serie spenta si riaccende con un tocco (attiva=true)', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Ricevute vecchie»' }));
    await waitFor(() => expect(put('/api/ricevute/serie/s3')).toHaveLength(1));
    expect((put('/api/ricevute/serie/s3')[0].corpo as { attiva: boolean }).attiva).toBe(true);
  });

  it('un tocco sul nome apre il foglio della serie senza chiamare il server per modificare', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    const prima = chiamate.length;
    await user.click(screen.getByText('Ricevute estate'));
    const foglio = await screen.findByRole('dialog');
    expect(within(foglio).getByDisplayValue('Ricevute estate')).toBeTruthy();
    expect(within(foglio).getByRole('button', { name: /Salva la serie/ })).toBeTruthy();
    expect(chiamate.slice(prima).filter((c) => c.metodo !== 'GET')).toHaveLength(0);
  });
});

describe('Ricevute – l\'ultima serie attiva', () => {
  beforeEach(() => {
    // resta attiva una sola serie di ricevute («Ricevute»): il sistema ne ha bisogno per emettere
    serie = serie.map((s) => (s.id === 's2' ? { ...s, attiva: false } : s));
    serie = serie.map((s) =>
      s.id === 's1' ? { ...s, eliminabile: false, motivoBlocco: 'Non si può eliminare: è l\'ultima serie attiva delle ricevute.\nPuoi aggiungerne un\'altra e spegnere questa.' } : s);
  });

  it('non ha né cestino né interruttore; le altre serie sì', async () => {
    render(ImpostazioniRicevute);
    await attendiSerie();
    expect(screen.getByText('Ricevute')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Elimina «Ricevute»' })).toBeNull();
    expect(screen.queryByRole('switch', { name: 'Acceso: «Ricevute»' })).toBeNull();
    expect(screen.getByRole('switch', { name: 'Acceso: «Ricevute estate»' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Elimina «Ricevute estate»' })).toBeTruthy();
    expect(screen.getByRole('switch', { name: 'Acceso: «Ricevute vecchie»' })).toBeTruthy();
  });

  it('riaccesa un\'altra serie, la prima torna a poter essere spenta e eliminata', async () => {
    serie = serie.map((s) => (s.id === 's2' ? { ...s, attiva: true } : s));
    serie = serie.map((s) => (s.id === 's1' ? { ...s, eliminabile: true, motivoBlocco: null } : s));
    render(ImpostazioniRicevute);
    await attendiSerie();
    expect(screen.getByRole('switch', { name: 'Acceso: «Ricevute»' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Elimina «Ricevute»' })).toBeTruthy();
  });
});

describe('Ricevute – riordino', () => {
  it('freccia giù sulla maniglia chiama PUT /api/ricevute/serie/ordine con tutti gli id nel nuovo ordine', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    screen.getByLabelText('Sposta «Ricevute»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(put('/api/ricevute/serie/ordine')).toHaveLength(1));
    expect(put('/api/ricevute/serie/ordine')[0].corpo).toEqual({ ids: ['s2', 's1', 's3'] });
    await waitFor(() => expect(ordineRighe()).toEqual(['Ricevute estate', 'Ricevute', 'Ricevute vecchie']));
  });

  it('freccia su: un posto prima, sempre con tutti gli id', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    screen.getByLabelText('Sposta «Ricevute vecchie»').focus();
    await user.keyboard('{ArrowUp}');
    await waitFor(() => expect(put('/api/ricevute/serie/ordine')).toHaveLength(1));
    expect(put('/api/ricevute/serie/ordine')[0].corpo).toEqual({ ids: ['s1', 's3', 's2'] });
  });

  it('ai bordi (la prima in su) non cambia nulla e non chiama il server', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    screen.getByLabelText('Sposta «Ricevute»').focus();
    await user.keyboard('{ArrowUp}');
    expect(put('/api/ricevute/serie/ordine')).toHaveLength(0);
    expect(ordineRighe()).toEqual(['Ricevute', 'Ricevute estate', 'Ricevute vecchie']);
  });

  it('se il server risponde con un errore l\'elenco torna com\'era', async () => {
    forzate['PUT /api/ricevute/serie/ordine'] = { status: 500 };
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    const prima = ordineRighe();
    screen.getByLabelText('Sposta «Ricevute»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(put('/api/ricevute/serie/ordine')).toHaveLength(1));
    await waitFor(() => expect(ordineRighe()).toEqual(prima));
  });
});

describe('Ricevute – cestino a due tocchi', () => {
  it('il primo tocco mostra «Elimina?» e non cancella nulla', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    await user.click(screen.getByRole('button', { name: 'Elimina «Ricevute estate»' }));
    expect(screen.getByRole('button', { name: /Elimina\?/ })).toBeTruthy();
    expect(cancellazioni()).toHaveLength(0);
    expect(screen.getByLabelText('Sposta «Ricevute estate»')).toBeTruthy();
  });

  it('il secondo tocco chiama DELETE /api/ricevute/serie/{id} e la riga sparisce', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    await user.click(screen.getByRole('button', { name: 'Elimina «Ricevute vecchie»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(cancellazioni().map((c) => c.url)).toEqual(['/api/ricevute/serie/s3']));
    await waitFor(() => expect(screen.queryByLabelText('Sposta «Ricevute vecchie»')).toBeNull());
    expect(ordineRighe()).toEqual(['Ricevute', 'Ricevute estate']);
  });

  it('409 (la serie ha emesso ricevute): messaggio a due righe sotto la riga, «Non si può eliminare:» in grassetto, la riga resta', async () => {
    forzate['DELETE /api/ricevute/serie/s1'] = { status: 409, corpo: { detail: BLOCCO_SERIE } };
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    await user.click(screen.getByRole('button', { name: 'Elimina «Ricevute»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await screen.findByText('Non si può eliminare:');
    verificaBloccoADueRighe('Ricevute', /la serie «Ricevute» ha emesso 412 ricevute\./);
    expect(screen.getByText(/le ricevute restano con il loro numero/)).toBeTruthy();
    expect(screen.getByLabelText('Sposta «Ricevute»')).toBeTruthy();
    expect(ordineRighe()).toContain('Ricevute');
  });
});

describe('Ricevute – aggiunta', () => {
  it('«Aggiungi una serie…» apre un foglio (dialog) con «Aggiungi la serie» e non chiama il server', async () => {
    const user = userEvent.setup();
    render(ImpostazioniRicevute);
    await attendiSerie();
    const prima = chiamate.length;
    await user.click(screen.getByRole('button', { name: /Aggiungi una serie/ }));
    const foglio = await screen.findByRole('dialog');
    expect(within(foglio).getByRole('button', { name: /Aggiungi la serie/ })).toBeTruthy();
    expect(chiamate.slice(prima).filter((c) => c.metodo !== 'GET')).toHaveLength(0);
  });
});

describe('Ricevute – senza il permesso', () => {
  it('con solo PAGAMENTI le serie si leggono ma niente maniglia, interruttore, cestino né «Aggiungi»', async () => {
    sessione.utente = utente(['PAGAMENTI']);
    render(ImpostazioniRicevute);
    await screen.findByText('Ricevute estate');
    expect(screen.getByText('Ricevute vecchie')).toBeTruthy();
    expect(screen.queryByLabelText(/^Sposta «/)).toBeNull();
    expect(interruttoriRiga()).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /^Elimina «/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Aggiungi/ })).toBeNull();
  });
});

describe('Cassa – le casse come righe', () => {
  it('con IMPOSTAZIONI: maniglia, interruttore e cestino sulle casse non principali, nell\'ordine del server', async () => {
    render(ImpostazioniCassa);
    await attendiCasse();
    expect(ordineRighe()).toEqual(expect.arrayContaining(['Segreteria', 'Piscina', 'Palestra']));
    const ordine = ordineRighe();
    expect(ordine.indexOf('Segreteria')).toBeLessThan(ordine.indexOf('Piscina'));
    expect(ordine.indexOf('Piscina')).toBeLessThan(ordine.indexOf('Palestra'));
    for (const nome of ['Piscina', 'Palestra']) {
      expect(screen.getByRole('switch', { name: `Acceso: «${nome}»` })).toBeTruthy();
      expect(screen.getByRole('button', { name: `Elimina «${nome}»` })).toBeTruthy();
    }
    expect(screen.getByRole('button', { name: /Aggiungi/ })).toBeTruthy();
  });

  it('la cassa principale si legge ma non ha né cestino né interruttore', async () => {
    render(ImpostazioniCassa);
    await attendiCasse();
    expect(screen.getByText('Segreteria')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Elimina «Segreteria»' })).toBeNull();
    expect(screen.queryByRole('switch', { name: 'Acceso: «Segreteria»' })).toBeNull();
  });

  it('un tocco sull\'interruttore spegne subito la cassa (PUT con attiva=false)', async () => {
    const user = userEvent.setup();
    render(ImpostazioniCassa);
    await attendiCasse();
    await user.click(screen.getByRole('switch', { name: 'Acceso: «Palestra»' }));
    await waitFor(() => expect(put('/api/cassa/casse/c3')).toHaveLength(1));
    expect((put('/api/cassa/casse/c3')[0].corpo as { attiva: boolean }).attiva).toBe(false);
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Acceso: «Palestra»' })).toHaveAttribute('aria-checked', 'false'));
  });

  it('«Aggiungi…» apre un foglio (dialog) con «Aggiungi la cassa» e non chiama il server', async () => {
    const user = userEvent.setup();
    render(ImpostazioniCassa);
    await attendiCasse();
    const prima = chiamate.length;
    await user.click(screen.getByRole('button', { name: /Aggiungi/ }));
    const foglio = await screen.findByRole('dialog');
    expect(within(foglio).getByRole('button', { name: /Aggiungi la cassa/ })).toBeTruthy();
    expect(chiamate.slice(prima).filter((c) => c.metodo !== 'GET')).toHaveLength(0);
  });
});

describe('Cassa – riordino', () => {
  it('freccia giù sulla maniglia chiama PUT /api/cassa/casse/ordine con tutti gli id nel nuovo ordine', async () => {
    const user = userEvent.setup();
    render(ImpostazioniCassa);
    await attendiCasse();
    screen.getByLabelText('Sposta «Piscina»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(put('/api/cassa/casse/ordine')).toHaveLength(1));
    expect(put('/api/cassa/casse/ordine')[0].corpo).toEqual({ ids: ['c1', 'c3', 'c2'] });
    await waitFor(() => {
      const o = ordineRighe();
      expect(o.indexOf('Palestra')).toBeLessThan(o.indexOf('Piscina'));
    });
  });

  it('se il server risponde con un errore l\'elenco torna com\'era', async () => {
    forzate['PUT /api/cassa/casse/ordine'] = { status: 500 };
    const user = userEvent.setup();
    render(ImpostazioniCassa);
    await attendiCasse();
    const prima = ordineRighe();
    screen.getByLabelText('Sposta «Piscina»').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(put('/api/cassa/casse/ordine')).toHaveLength(1));
    await waitFor(() => expect(ordineRighe()).toEqual(prima));
  });
});

describe('Cassa – cestino a due tocchi', () => {
  it('il primo tocco mostra «Elimina?» e non cancella nulla', async () => {
    const user = userEvent.setup();
    render(ImpostazioniCassa);
    await attendiCasse();
    await user.click(screen.getByRole('button', { name: 'Elimina «Palestra»' }));
    expect(screen.getByRole('button', { name: /Elimina\?/ })).toBeTruthy();
    expect(cancellazioni()).toHaveLength(0);
  });

  it('il secondo tocco chiama DELETE /api/cassa/casse/{id} e la riga sparisce', async () => {
    const user = userEvent.setup();
    render(ImpostazioniCassa);
    await attendiCasse();
    await user.click(screen.getByRole('button', { name: 'Elimina «Palestra»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(cancellazioni().map((c) => c.url)).toEqual(['/api/cassa/casse/c3']));
    await waitFor(() => expect(screen.queryByLabelText('Sposta «Palestra»')).toBeNull());
    expect(screen.getByLabelText('Sposta «Piscina»')).toBeTruthy();
  });

  it('409 (cassa aperta almeno una volta): messaggio a due righe sotto la riga, «Non si può eliminare:» in grassetto, la riga resta', async () => {
    forzate['DELETE /api/cassa/casse/c2'] = { status: 409, corpo: { detail: BLOCCO_CASSA } };
    const user = userEvent.setup();
    render(ImpostazioniCassa);
    await attendiCasse();
    await user.click(screen.getByRole('button', { name: 'Elimina «Piscina»' }));
    await user.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await screen.findByText('Non si può eliminare:');
    verificaBloccoADueRighe('Piscina', /la cassa «Piscina» ha 58 sessioni nello storico\./);
    expect(screen.getByLabelText('Sposta «Piscina»')).toBeTruthy();
  });
});

describe('Cassa – riscontro con il POS in sola lettura', () => {
  /** il testo «Si riscontrano: …» può essere spezzato in più elementi: si guarda il più piccolo contenitore che lo contiene tutto */
  const rigaRiscontro = async (): Promise<string> => {
    const etichetta = await screen.findByText(/Si riscontrano/);
    let el: HTMLElement | null = etichetta;
    for (let i = 0; i < 3 && el; i += 1, el = el.parentElement) {
      if (/Si riscontrano:?\s*(Carta|Nessun metodo)/.test((el.textContent ?? '').replace(/\s+/g, ' '))) return (el.textContent ?? '').replace(/\s+/g, ' ');
    }
    return (etichetta.textContent ?? '').replace(/\s+/g, ' ');
  };

  it('elenca i metodi riscontrati («Si riscontrano: Carta, Satispay»)', async () => {
    render(ImpostazioniCassa);
    expect(await rigaRiscontro()).toContain('Si riscontrano: Carta, Satispay');
  });

  it('nessun metodo riscontrato: «Nessun metodo»', async () => {
    metodi = metodi.map((m) => ({ ...m, riscontro: false }));
    render(ImpostazioniCassa);
    expect(await rigaRiscontro()).toMatch(/Si riscontrano:?\s*Nessun metodo/);
  });

  it('non ci sono chip che salvano: nessun pulsante «Carta» o «Satispay», nessun PUT ai metodi', async () => {
    render(ImpostazioniCassa);
    await rigaRiscontro();
    await attendiCasse();
    for (const nome of ['Carta', 'Satispay', 'Contanti', 'Bonifico']) {
      expect(screen.queryByRole('button', { name: nome })).toBeNull();
    }
    expect(screen.queryByRole('button', { pressed: true, name: /^(Carta|Satispay)$/ })).toBeNull();
    expect(chiamate.filter((c) => c.metodo === 'PUT' && c.url.startsWith('/api/pagamenti/metodi'))).toHaveLength(0);
  });

  it('«Metodi di pagamento» porta a #/metodi (link) senza salvare niente', async () => {
    const user = userEvent.setup();
    render(ImpostazioniCassa);
    await rigaRiscontro();
    const link = screen.queryByRole('link', { name: /Metodi di pagamento/ }) ?? screen.queryByRole('button', { name: /Metodi di pagamento/ });
    expect(link).toBeTruthy();
    if (link!.tagName === 'A') expect(link).toHaveAttribute('href', '#/metodi');
    const prima = chiamate.length;
    await user.click(link!);
    if (link!.tagName !== 'A') expect(location.hash).toBe('#/metodi');
    expect(chiamate.slice(prima).filter((c) => c.metodo !== 'GET')).toHaveLength(0);
  });
});

describe('Cassa – senza il permesso', () => {
  it('con solo PAGAMENTI le casse si leggono ma niente maniglia, interruttore, cestino né «Aggiungi»', async () => {
    sessione.utente = utente(['PAGAMENTI']);
    render(ImpostazioniCassa);
    await screen.findByText('Piscina');
    expect(screen.getByText('Palestra')).toBeTruthy();
    expect(screen.queryByLabelText(/^Sposta «/)).toBeNull();
    expect(interruttoriRiga()).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /^Elimina «/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Aggiungi/ })).toBeNull();
  });
});
