import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DatiSocieta, GruppoClienti, Me, Stagione } from '../lib/api';
import { iso } from '../lib/date';
import { sessione } from '../lib/sessione.svelte';
import Societa from './Societa.svelte';

/** La pagina con un `fetch` finto che segue il contratto: gruppi in ordine, eliminazione pesante, 409 a due righe. */
type Chiamata = { metodo: string; url: string; corpo: any };
let chiamate: Chiamata[];
let gruppi: GruppoClienti[];
let stagioni: Stagione[];
let forzate: Record<string, { status: number; corpo?: unknown }>;

const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
const gruppo = (id: string, nome: string, extra: Partial<GruppoClienti> = {}): GruppoClienti => ({
  id, nome, descrizione: null, attivo: true, persone: 0, precedenza: null, giorniPerPagare: null, dichiarabile: false, fraseDichiarazione: null,
  daVerificare: 0, ordine: 0, eliminabile: true, motivoBlocco: null, eliminazionePesante: false, ...extra,
});
const stagione = (id: string, nome: string, dal: string, al: string, extra: Partial<Stagione> = {}): Stagione => ({ id, nome, dal, al, quota: 35, quotaRimborsabile: false, ...extra });
const societa: DatiSocieta = {
  nome: 'Centro Prova', ragioneSociale: null, partitaIva: null, codiceFiscale: null, indirizzo: null, cap: null, citta: null, provincia: null,
  email: null, telefono: null, giorniAnticipo: 30, pagamentiFamiglia: 'A_SCELTA', giorniRispostaAttesa: 3,
};
const utente: Me = { accountId: 'a1', personaId: 'p1', email: 'prova@example.test', nome: 'Anna', cognome: 'Prova', ruoli: [], permessi: ['IMPOSTAZIONI'], admin: false, deveCambiarePassword: false };

const anno = new Date().getFullYear();
const OGGI = iso(new Date());

beforeEach(() => {
  chiamate = [];
  forzate = {};
  gruppi = [
    gruppo('g1', 'Agonisti', { precedenza: 1, persone: 38, daVerificare: 1, eliminazionePesante: true, ordine: 1 }),
    gruppo('g2', 'Dipendenti', { precedenza: 2, persone: 4, ordine: 2 }),
    gruppo('g3', 'Soci', { persone: 0, ordine: 3 }),
  ];
  stagioni = [
    stagione('s1', `${anno - 1}/${String(anno % 100)}`, `${anno - 1}-01-01`, `${anno - 1}-12-31`),
    stagione('s2', 'In corso', `${anno}-01-01`, `${anno}-12-31`, { eliminabile: false, motivoBlocco: 'Non si può eliminare: la stagione In corso ha 12 offerte nel listino.\nSvuota prima il listino: poi potrai eliminare la stagione.' }),
    stagione('s3', 'Prossima', `${anno + 1}-01-01`, `${anno + 1}-12-31`, { eliminabile: true }),
  ];
  sessione.utente = utente;
  sessione.pronto = true;
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const m = init?.method ?? 'GET';
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url === '/api/auth/csrf') return json(204);
    chiamate.push({ metodo: m, url, corpo });
    const f = forzate[`${m} ${url}`];
    if (f) return json(f.status, f.corpo);
    if (m === 'GET' && url === '/api/societa') return json(200, societa);
    if (m === 'GET' && url === '/api/stagioni') return json(200, stagioni);
    if (m === 'GET' && url === '/api/gruppi') return json(200, gruppi);
    if (m === 'GET' && url.startsWith('/api/listino')) return json(200, { stagione: stagioni[0], offerte: [], regole: [] });
    if (m === 'GET' && url === '/api/gruppi/g1/eliminazione')
      return json(200, { gruppo: 'Agonisti', pesante: true, bloccata: false, motivoBlocco: null, persone: 38, daVerificare: 1, prezzi: 2, offerte: ['Scuola nuoto', 'Nuoto adulti'],
        conseguenze: ['38 persone escono dal gruppo', '1 dichiarazione da verificare si chiude', '2 prezzi del listino per questo gruppo spariscono (Scuola nuoto, Nuoto adulti)'] });
    if (m === 'GET' && url.endsWith('/persone')) return json(200, [{ appartenenzaId: 'ap1', personaId: 'pe1', nome: 'Ada', cognome: 'Esempio', dataNascita: null, dal: '2026-09-01', verificata: true }]);
    if (m === 'PUT' && url === '/api/gruppi/ordine') {
      const ids: string[] = corpo.ids;
      let n = 0;
      gruppi = ids.map((id, i) => {
        const g = gruppi.find((x) => x.id === id)!;
        return { ...g, ordine: i + 1, precedenza: g.precedenza == null ? null : ++n };
      });
      return json(200, gruppi);
    }
    if (m === 'DELETE') {
      const id = url.match(/^\/api\/gruppi\/([^/?]+)/)?.[1];
      if (id) gruppi = gruppi.filter((g) => g.id !== id);
      return json(204);
    }
    return json(204);
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessione.utente = null;
});

const attese = () => screen.findByLabelText('Sposta «Agonisti»');
const chiamata = (metodo: string, url: string) => chiamate.filter((c) => c.metodo === metodo && c.url === url);

describe('Dati, stagioni e gruppi – gruppi', () => {
  it('ogni riga ha i suoi tag; il riquadro «Ordine delle precedenze» non c\'è più', async () => {
    render(Societa);
    await attese();
    expect(screen.getByText('1ª precedenza')).toBeTruthy();
    expect(screen.getByText('2ª precedenza')).toBeTruthy();
    expect(screen.getByText('1 da verificare')).toHaveClass('t-warn');
    expect(screen.getByText('38 persone')).toBeTruthy();
    expect(screen.queryByText(/Ordine delle precedenze/i)).toBeNull();
    expect(screen.getByRole('button', { name: /Aggiungi un gruppo/ })).toBeTruthy();
  });

  it('il riordino chiama ordinaGruppi con tutti gli id e i tag delle precedenze cambiano', async () => {
    render(Societa);
    (await attese()).focus();
    await userEvent.keyboard('{ArrowDown}');
    await waitFor(() => expect(chiamata('PUT', '/api/gruppi/ordine')).toHaveLength(1));
    expect(chiamata('PUT', '/api/gruppi/ordine')[0].corpo).toEqual({ ids: ['g2', 'g1', 'g3'] });
    await waitFor(() => expect(within(screen.getByText('Agonisti').closest('li')!).getByText('2ª precedenza')).toBeTruthy());
  });

  it('il cestino di un gruppo con persone apre la finestra con le conseguenze, senza «Elimina?»', async () => {
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»…' }));
    const finestra = await screen.findByRole('alertdialog', { name: 'Eliminare il gruppo «Agonisti»?' });
    expect(within(finestra).getAllByRole('listitem').map((l) => l.textContent)).toEqual([
      '38 persone escono dal gruppo', '1 dichiarazione da verificare si chiude', '2 prezzi del listino per questo gruppo spariscono (Scuola nuoto, Nuoto adulti)',
    ]);
    expect(within(finestra).getByRole('button', { name: 'Elimina il gruppo (5)' })).toBeDisabled();
    expect(chiamate.some((c) => c.metodo === 'DELETE')).toBe(false);
    await userEvent.click(within(finestra).getByRole('button', { name: 'Annulla' }));
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(chiamate.some((c) => c.metodo === 'DELETE')).toBe(false);
  });

  it('confermando dalla finestra elimina con ?pesante=true e ricarica', async () => {
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»…' }));
    const finestra = await screen.findByRole('alertdialog');
    const rosso = within(finestra).getByRole('button', { name: /^Elimina il gruppo/ });
    await waitFor(() => expect(rosso).toBeEnabled(), { timeout: 7000 });
    await userEvent.click(rosso);
    await waitFor(() => expect(chiamata('DELETE', '/api/gruppi/g1?pesante=true')).toHaveLength(1));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull());
    expect(screen.queryByLabelText('Sposta «Agonisti»')).toBeNull();
    // il fuoco passa alla riga vicina (qui la seguente), non resta sul contenitore generale
    await waitFor(() => expect(screen.getByLabelText('Sposta «Dipendenti»')).toHaveFocus());
  }, 15000);

  it('un gruppo senza conseguenze si elimina con i due tocchi e DELETE senza «pesante»', async () => {
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Soci»' }));
    expect(chiamate.some((c) => c.metodo === 'DELETE')).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamata('DELETE', '/api/gruppi/g3')).toHaveLength(1));
    expect(screen.queryByRole('alertdialog')).toBeNull();
  });

  it('un 409 si mostra sotto la riga su due righe, con l\'attacco in grassetto', async () => {
    forzate['DELETE /api/gruppi/g3'] = { status: 409, corpo: { detail: 'Non si può eliminare così: «Soci» ha 3 persone.\nUsa di nuovo il cestino: si apre la finestra con le conseguenze.' } };
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Soci»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    const msg = await screen.findByRole('alert');
    expect(msg).toHaveTextContent('Non si può eliminare così: «Soci» ha 3 persone.');
    expect([...msg.querySelectorAll('b')].map((b) => b.textContent)).toEqual(['Non si può eliminare così:', 'Usa di nuovo il cestino:']);
  });

  it('l\'interruttore della riga manda solo nome, descrizione e attivo', async () => {
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('switch', { name: 'Acceso: «Dipendenti»' }));
    await waitFor(() => expect(chiamata('PUT', '/api/gruppi/g2')).toHaveLength(1));
    expect(chiamata('PUT', '/api/gruppi/g2')[0].corpo).toEqual({ nome: 'Dipendenti', descrizione: null, attivo: false });
  });

  it('il foglio: riquadro acceso per primo, precedenza con il suo fumetto, elenco delle persone a richiesta', async () => {
    render(Societa);
    await attese();
    await userEvent.click(screen.getByText('Agonisti'));
    const foglio = await screen.findByRole('dialog', { name: 'Agonisti' });
    const campi = [...foglio.querySelectorAll('.riquadro, .field, .riga-sw')];
    expect(campi[0]).toHaveClass('riquadro');
    expect(within(foglio).getByRole('switch', { name: 'Precedenza nelle graduatorie' })).toHaveAttribute('aria-checked', 'true');
    expect(within(foglio).getByRole('button', { name: 'Spiega la precedenza' })).toBeTruthy();
    const mostra = within(foglio).getByRole('button', { name: 'Mostra le 38 persone' });
    expect(mostra).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(mostra);
    expect(await within(foglio).findByRole('link', { name: 'Esempio Ada' })).toHaveAttribute('href', '#/clienti/pe1');
    expect(within(foglio).getByRole('button', { name: 'Salva il gruppo' })).toBeTruthy();
  });

  it('la riga «Spento, non si aggiungono persone.» compare solo con il gruppo spento', async () => {
    gruppi[2] = { ...gruppi[2], attivo: false };
    render(Societa);
    await attese();
    await userEvent.click(screen.getByText('Agonisti'));
    expect(screen.queryByText('Spento, non si aggiungono persone.')).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Chiudi' }));
    await userEvent.click(screen.getByText('Soci'));
    expect(await screen.findByText('Spento, non si aggiungono persone.')).toBeTruthy();
  });

  it('l\'avviso delle novità è una riga corta con il fumetto e un «Chiudi» a pulsante-icona', async () => {
    const { container } = render(Societa);
    await attese();
    expect(screen.getByRole('button', { name: 'Spiega le novità' })).toBeTruthy();
    expect(container.querySelector('.x-btn')).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: "Chiudi l'avviso" }));
    expect(screen.queryByText("Novità dell'ultimo aggiornamento")).toBeNull();
  });

  it('«Aggiungi un gruppo…» apre il foglio «Nuovo gruppo» con «Aggiungi il gruppo»', async () => {
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: /Aggiungi un gruppo/ }));
    const foglio = await screen.findByRole('dialog', { name: 'Nuovo gruppo' });
    const agg = within(foglio).getByRole('button', { name: 'Aggiungi il gruppo' });
    expect(agg).toBeDisabled();
    await userEvent.type(within(foglio).getByLabelText('Nome'), 'Volontari');
    expect(agg).toBeEnabled();
    await userEvent.click(agg);
    await waitFor(() => expect(chiamata('POST', '/api/gruppi')).toHaveLength(1));
    expect(chiamata('POST', '/api/gruppi')[0].corpo).toMatchObject({ nome: 'Volontari', conPrecedenza: false, attivo: true });
  });

  it('un gruppo creato spento usa una sola chiamata, con attivo false', async () => {
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: /Aggiungi un gruppo/ }));
    const foglio = await screen.findByRole('dialog', { name: 'Nuovo gruppo' });
    await userEvent.type(within(foglio).getByLabelText('Nome'), 'Volontari');
    await userEvent.click(within(foglio).getByRole('switch', { name: /^Acceso/ }));
    await userEvent.click(within(foglio).getByRole('button', { name: 'Aggiungi il gruppo' }));
    await waitFor(() => expect(chiamata('POST', '/api/gruppi')).toHaveLength(1));
    expect(chiamata('POST', '/api/gruppi')[0].corpo).toMatchObject({ nome: 'Volontari', attivo: false });
    expect(chiamate.filter((c) => c.metodo === 'PUT')).toHaveLength(0);
  });

  it('un gruppo che nel frattempo si è svuotato non si elimina con un tocco solo', async () => {
    forzate['GET /api/gruppi/g1/eliminazione'] = { status: 200, corpo: { gruppo: 'Agonisti', pesante: false, bloccata: false, motivoBlocco: null, persone: 0, daVerificare: 0, prezzi: 0, offerte: [], conseguenze: [] } };
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»…' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ora è vuoto: tocca di nuovo per eliminarlo.');
    expect(chiamate.some((c) => c.metodo === 'DELETE')).toBe(false);
  });
});

describe('Dati, stagioni e gruppi – stagioni', () => {
  it('le pillole usano le classi dello stato: in corso verde, finita grigia, futura azzurra', async () => {
    render(Societa);
    await attese();
    expect(screen.getByText('futura')).toHaveClass('pill', 'futura');
    expect(screen.getByText('in corso')).toHaveClass('pill', 'passa');
    expect(screen.getByText('finita')).toHaveClass('pill');
  });

  it('il cestino della stagione bloccata mostra il motivo a due righe senza chiamare il server', async () => {
    render(Societa);
    await attese();
    await userEvent.click(await screen.findByRole('button', { name: 'Elimina la stagione In corso' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    const msg = await screen.findByRole('alert');
    expect(msg).toHaveTextContent('Non si può eliminare: la stagione In corso ha 12 offerte nel listino.');
    expect([...msg.querySelectorAll('b')].map((b) => b.textContent)).toEqual(['Non si può eliminare:', 'Svuota prima il listino:']);
    expect(chiamate.some((c) => c.metodo === 'DELETE')).toBe(false);
  });

  it('una stagione eliminabile chiama DELETE; un 409 si mostra a due righe', async () => {
    forzate[`DELETE /api/stagioni/s3`] = { status: 409, corpo: { detail: 'Non si può eliminare: la stagione Prossima ha 2 offerte nel listino.\nSvuota prima il listino: poi potrai eliminare la stagione.' } };
    render(Societa);
    await attese();
    await userEvent.click(screen.getByRole('button', { name: /Prossima/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Elimina la stagione Prossima' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(chiamata('DELETE', '/api/stagioni/s3')).toHaveLength(1));
    expect(await screen.findByRole('alert')).toHaveTextContent('Svuota prima il listino');
  });

  it('senza classi vecchie: niente mini-btn e niente frase «Una stagione si elimina solo se…»', async () => {
    const { container } = render(Societa);
    await attese();
    expect(container.querySelector('.mini-btn')).toBeNull();
    expect(screen.queryByText(/si elimina solo se/)).toBeNull();
    expect(OGGI).toBeTruthy();
  });
});
