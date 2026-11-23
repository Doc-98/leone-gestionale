import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { createRawSnippet } from 'svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ElencoModificabile from './ElencoModificabile.svelte';

afterEach(cleanup);

interface Voce { id: string; nome: string; acceso: boolean; fisso?: boolean }
const voci = (): Voce[] => [
  { id: 'a', nome: 'Contanti', acceso: true },
  { id: 'b', nome: 'Carta', acceso: false },
  { id: 'c', nome: 'Satispay', acceso: true },
  { id: 'd', nome: 'Borsellino', acceso: true, fisso: true },
];
const riga = createRawSnippet((el: () => Voce) => ({ render: () => `<strong>${el().nome}</strong>` }));

function monta(extra: Record<string, unknown> = {}) {
  const prop = {
    elementi: voci(),
    modificabile: true,
    nome: (v: Voce) => v.nome,
    nomeElemento: 'metodo',
    riga,
    acceso: (v: Voce) => v.acceso,
    haCestino: (v: Voce) => !v.fisso,
    onriordina: vi.fn(),
    onaggiungi: vi.fn(),
    onacceso: vi.fn(),
    onelimina: vi.fn(),
    ...extra,
  };
  render(ElencoModificabile, { props: prop as never });
  return prop as typeof prop & { onriordina: ReturnType<typeof vi.fn>; onaggiungi: ReturnType<typeof vi.fn>; onacceso: ReturnType<typeof vi.fn>; onelimina: ReturnType<typeof vi.fn> };
}
const nomiInOrdine = () => screen.getAllByLabelText(/^Sposta «/).map((e) => e.getAttribute('aria-label')!.slice(8, -1));

describe('ElencoModificabile – maniglia e tastiera', () => {
  it('freccia giù sposta di un posto, chiama onriordina con gli id e annuncia', async () => {
    const p = monta();
    screen.getByLabelText('Sposta «Contanti»').focus();
    await userEvent.keyboard('{ArrowDown}');
    expect(p.onriordina).toHaveBeenCalledWith(['b', 'a', 'c', 'd']);
    expect(nomiInOrdine()).toEqual(['Carta', 'Contanti', 'Satispay', 'Borsellino']);
    expect(screen.getByRole('status')).toHaveTextContent('Contanti spostato al posto 2 di 4');
  });

  it('il fuoco resta sulla maniglia dopo lo spostamento, così si può continuare', async () => {
    monta();
    screen.getByLabelText('Sposta «Contanti»').focus();
    await userEvent.keyboard('{ArrowDown}{ArrowDown}');
    await waitFor(() => expect(nomiInOrdine()).toEqual(['Carta', 'Satispay', 'Contanti', 'Borsellino']));
    expect(screen.getByLabelText('Sposta «Contanti»')).toHaveFocus();
  });

  it('ai bordi non cambia nulla e non chiama onriordina', async () => {
    const p = monta();
    screen.getByLabelText('Sposta «Contanti»').focus();
    await userEvent.keyboard('{ArrowUp}');
    screen.getByLabelText('Sposta «Borsellino»').focus();
    await userEvent.keyboard('{ArrowDown}');
    expect(p.onriordina).not.toHaveBeenCalled();
    expect(nomiInOrdine()).toEqual(['Contanti', 'Carta', 'Satispay', 'Borsellino']);
  });

  it('se onriordina fallisce: prima l\'ordine cambia, poi torna com\'era, il fuoco torna sulla maniglia e si annuncia', async () => {
    let rifiuta!: (e: Error) => void;
    const p = monta({ onriordina: vi.fn(() => new Promise((_, r) => (rifiuta = r))) });
    screen.getByLabelText('Sposta «Contanti»').focus();
    await userEvent.keyboard('{ArrowDown}');
    expect(p.onriordina).toHaveBeenCalledWith(['b', 'a', 'c', 'd']);
    expect(nomiInOrdine()).toEqual(['Carta', 'Contanti', 'Satispay', 'Borsellino']);
    rifiuta(new Error('no'));
    await waitFor(() => expect(nomiInOrdine()).toEqual(['Contanti', 'Carta', 'Satispay', 'Borsellino']));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Ordine non salvato: Contanti è tornato al posto 1'));
    expect(screen.getByLabelText('Sposta «Contanti»')).toHaveFocus();
  });

  it('due frecce di fila con salvataggio lento: l\'ordine non salta, le chiamate partono una alla volta e il fuoco resta', async () => {
    const attesa = () => new Promise((r) => setTimeout(r, 15));
    let ordine = voci();
    const chiamate: string[][] = [];
    let aggiorna!: (props: Record<string, unknown>) => Promise<void>;
    const onriordina = vi.fn(async (ids: string[]) => {
      chiamate.push(ids);
      await attesa();
      ordine = ids.map((id) => ordine.find((v) => v.id === id)!);
      await aggiorna({ elementi: ordine });
    });
    const risultato = render(ElencoModificabile, { props: {
      elementi: ordine, modificabile: true, nome: (v: Voce) => v.nome, nomeElemento: 'metodo', riga, acceso: (v: Voce) => v.acceso, onriordina,
    } as never });
    aggiorna = risultato.rerender as never;
    screen.getByLabelText('Sposta «Contanti»').focus();
    await userEvent.keyboard('{ArrowDown}{ArrowDown}');
    expect(nomiInOrdine()).toEqual(['Carta', 'Satispay', 'Contanti', 'Borsellino']);
    await waitFor(() => expect(onriordina).toHaveBeenCalledTimes(2));
    await new Promise((r) => setTimeout(r, 80));
    expect(chiamate).toEqual([['b', 'a', 'c', 'd'], ['b', 'c', 'a', 'd']]);
    expect(nomiInOrdine()).toEqual(['Carta', 'Satispay', 'Contanti', 'Borsellino']);
    expect(screen.getByLabelText('Sposta «Contanti»')).toHaveFocus();
  });

  it('la maniglia dice come si usa (aria-describedby)', () => {
    monta();
    const d = screen.getByLabelText('Sposta «Contanti»').getAttribute('aria-describedby')!;
    expect(document.getElementById(d)).toHaveTextContent('Frecce su e giù per spostare');
  });

  it('due spostamenti uguali di fila vengono annunciati tutti e due (la regione si svuota prima)', async () => {
    monta();
    screen.getByLabelText('Sposta «Contanti»').focus();
    await userEvent.keyboard('{ArrowDown}');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Contanti spostato al posto 2 di 4'));
    await userEvent.keyboard('{ArrowUp}{ArrowDown}');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Contanti spostato al posto 2 di 4'));
  });
});

describe('ElencoModificabile – interruttore', () => {
  it('è un switch con aria-checked secondo lo stato e chiama onacceso con il valore nuovo', async () => {
    const p = monta();
    expect(screen.getByRole('switch', { name: 'Acceso: «Contanti»' })).toHaveAttribute('aria-checked', 'true');
    const carta = screen.getByRole('switch', { name: 'Acceso: «Carta»' });
    expect(carta).toHaveAttribute('aria-checked', 'false');
    await userEvent.click(carta);
    expect(p.onacceso).toHaveBeenCalledWith(expect.objectContaining({ id: 'b' }), true);
    await userEvent.click(screen.getByRole('switch', { name: 'Acceso: «Contanti»' }));
    expect(p.onacceso).toHaveBeenLastCalledWith(expect.objectContaining({ id: 'a' }), false);
  });

  it('una riga spenta ha tutti i controlli attivi', () => {
    monta();
    expect(screen.getByRole('switch', { name: 'Acceso: «Carta»' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Elimina «Carta»' })).toBeEnabled();
    expect(screen.getByLabelText('Sposta «Carta»')).toBeEnabled();
  });

  it('haInterruttore falso nasconde l\'interruttore di quella riga', () => {
    monta({ haInterruttore: (v: Voce) => !v.fisso });
    expect(screen.queryByRole('switch', { name: 'Acceso: «Borsellino»' })).toBeNull();
    expect(screen.getByRole('switch', { name: 'Acceso: «Carta»' })).toBeTruthy();
  });
});

describe('ElencoModificabile – cestino a due tocchi', () => {
  it('il primo tocco arma («Elimina?»), il secondo elimina', async () => {
    const p = monta();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Carta»' }));
    expect(p.onelimina).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /Elimina\?/ })).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    expect(p.onelimina).toHaveBeenCalledWith(expect.objectContaining({ id: 'b' }));
  });

  it('il messaggio di blocco restituito da onelimina compare sotto la riga', async () => {
    monta({ onelimina: vi.fn().mockResolvedValue('Satispay ha ancora 2 pagamenti da incassare') });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Satispay»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    const avviso = await screen.findByRole('alert');
    expect(avviso).toHaveTextContent('Satispay ha ancora 2 pagamenti da incassare');
    expect(screen.getByLabelText('Sposta «Satispay»')).toBeTruthy();
  });

  it('il messaggio di blocco su due righe: ogni riga ha l\'attacco in grassetto', async () => {
    monta({ onelimina: vi.fn().mockResolvedValue("Non si può eliminare: 120 turni nello storico usano Postazione 3.\nPuoi spegnerla: quei turni restano com'erano.") });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Satispay»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    const avviso = await screen.findByRole('alert');
    const grassetti = [...avviso.querySelectorAll('b')].map((b) => b.textContent);
    expect(grassetti).toEqual(['Non si può eliminare:', 'Puoi spegnerla:']);
    expect(avviso.querySelectorAll('.riga-b')).toHaveLength(2);
    expect(avviso).toHaveTextContent("Non si può eliminare: 120 turni nello storico usano Postazione 3. Puoi spegnerla: quei turni restano com'erano.");
  });

  it('un messaggio di una riga senza «:» resta normale, senza grassetto', async () => {
    monta({ onelimina: vi.fn().mockResolvedValue('Satispay ha ancora 2 pagamenti da incassare') });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Satispay»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    const avviso = await screen.findByRole('alert');
    expect(avviso.querySelector('b')).toBeNull();
  });

  it('con conferma «Rimuovi?» il cestino si chiama «Rimuovi «X»»', async () => {
    const p = monta({ conferma: 'Rimuovi?' });
    expect(screen.queryByRole('button', { name: 'Elimina «Carta»' })).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Rimuovi «Carta»' }));
    await userEvent.click(screen.getByRole('button', { name: /Rimuovi\?/ }));
    expect(p.onelimina).toHaveBeenCalledWith(expect.objectContaining({ id: 'b' }));
  });

  it('il messaggio di blocco sparisce alla prossima azione nell\'elenco', async () => {
    monta({ onelimina: vi.fn().mockResolvedValue('Satispay ha ancora 2 pagamenti da incassare') });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Satispay»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await screen.findByRole('alert');
    screen.getByLabelText('Sposta «Contanti»').focus();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('dopo un\'eliminazione riuscita il fuoco va alla maniglia della riga dopo, o prima se era l\'ultima, o ad «Aggiungi»', async () => {
    let aggiorna!: (props: Record<string, unknown>) => Promise<void>;
    let el = voci().filter((v) => !v.fisso);
    const onelimina = vi.fn(async (v: Voce) => {
      el = el.filter((x) => x.id !== v.id);
      await aggiorna({ elementi: el });
    });
    const r = render(ElencoModificabile, { props: { elementi: el, modificabile: true, nome: (v: Voce) => v.nome, nomeElemento: 'metodo', riga, acceso: (v: Voce) => v.acceso, onelimina, onaggiungi: vi.fn() } as never });
    aggiorna = r.rerender as never;
    const elimina = async (nome: string) => {
      await userEvent.click(screen.getByRole('button', { name: `Elimina «${nome}»` }));
      await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    };
    await elimina('Carta');
    await waitFor(() => expect(screen.getByLabelText('Sposta «Satispay»')).toHaveFocus());
    await elimina('Satispay');
    await waitFor(() => expect(screen.getByLabelText('Sposta «Contanti»')).toHaveFocus());
    await elimina('Contanti');
    await waitFor(() => expect(screen.getByRole('button', { name: /Aggiungi un metodo/ })).toHaveFocus());
  });

  it('una riga senza cestino (il borsellino) non lo mostra', () => {
    monta();
    expect(screen.queryByRole('button', { name: 'Elimina «Borsellino»' })).toBeNull();
  });
});

describe('ElencoModificabile – aggiungere, vuoto, sola lettura', () => {
  it('«Aggiungi un metodo…» (con i puntini) chiama onaggiungi', async () => {
    const p = monta();
    await userEvent.click(screen.getByRole('button', { name: 'Aggiungi un metodo…' }));
    expect(p.onaggiungi).toHaveBeenCalledOnce();
  });

  it('senza foglio niente puntini', () => {
    monta({ apreFoglio: false });
    expect(screen.getByRole('button', { name: 'Aggiungi un metodo' })).toBeTruthy();
  });

  it('elenco vuoto: «Nessun metodo.» senza pulsante e sotto la riga «Aggiungi»', () => {
    monta({ elementi: [] });
    const vuoto = screen.getByText('Nessun metodo.');
    expect(within(vuoto.parentElement!).queryByRole('button')).toBeNull();
    expect(screen.getAllByRole('button', { name: /Aggiungi un metodo/ })).toHaveLength(1);
  });

  it('con «una» il testo si accorda', () => {
    monta({ elementi: [], nomeElemento: 'voce', articolo: 'una' });
    expect(screen.getByText('Nessuna voce.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Aggiungi una voce…' })).toBeTruthy();
  });

  it('sola lettura: niente maniglia, interruttore, cestino né «Aggiungi»; la riga spenta dice «Spento»', () => {
    monta({ modificabile: false });
    expect(screen.queryByLabelText(/^Sposta «/)).toBeNull();
    expect(screen.queryByRole('switch')).toBeNull();
    expect(screen.queryByRole('button', { name: /^Elimina «/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Aggiungi/ })).toBeNull();
    expect(screen.getByText('Contanti')).toBeVisible();
    expect(screen.getAllByText('Spento')).toHaveLength(1);
  });

  it('sola lettura e vuoto: «Nessun metodo.» e nessun «Aggiungi»', () => {
    monta({ elementi: [], modificabile: false });
    expect(screen.getByText('Nessun metodo.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Aggiungi/ })).toBeNull();
  });
});

describe('ElencoModificabile – riordinabile', () => {
  it('riordinabile false: niente maniglia né suggerimento delle frecce, il resto resta', () => {
    monta({ riordinabile: false });
    expect(screen.queryByLabelText(/^Sposta «/)).toBeNull();
    expect(screen.queryByText('Frecce su e giù per spostare')).toBeNull();
    expect(screen.getAllByRole('switch')).toHaveLength(4);
    expect(screen.getByRole('button', { name: 'Elimina «Carta»' })).toBeTruthy();
  });
  it('di default si riordina (come nei metodi di pagamento)', () => {
    monta();
    expect(screen.getAllByLabelText(/^Sposta «/)).toHaveLength(4);
  });
});

describe('ElencoModificabile – fuoco senza maniglie', () => {
  it('riordinabile false: dopo un\'eliminazione il fuoco passa a un controllo della riga vicina, non si perde', async () => {
    const { default: Prova } = await import('../prova/ElencoRiordinaProva.svelte');
    render(Prova);
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Contanti»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    await waitFor(() => expect(screen.queryByText('Contanti')).toBeNull());
    expect(document.body).not.toBe(document.activeElement);
    expect(within(screen.getByRole('list')).getByText('Carta').closest('li')!.contains(document.activeElement)).toBe(true);
  });
});

describe('ElencoModificabile – apertura', () => {
  it('con onapri il contenuto è un pulsante che passa l\'elemento e dichiara il foglio', async () => {
    const onapri = vi.fn();
    monta({ onapri });
    expect(screen.getByText('Carta').closest('button')).toHaveAttribute('aria-haspopup', 'dialog');
    await userEvent.click(screen.getByText('Carta'));
    expect(onapri).toHaveBeenCalledWith(expect.objectContaining({ id: 'b' }));
  });
});

describe('ElencoModificabile – cestinoApre', () => {
  it('per la riga che apre il primo tocco chiama subito onelimina, senza «Elimina?», e l\'etichetta finisce con «…»', async () => {
    const p = monta({ cestinoApre: (v: Voce) => v.id === 'a' });
    expect(screen.getByLabelText('Elimina «Contanti»…')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Elimina «Contanti»…'));
    expect(p.onelimina).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('Elimina?')).toBeNull();
  });

  it('le altre righe restano a due tocchi', async () => {
    const p = monta({ cestinoApre: (v: Voce) => v.id === 'a' });
    await userEvent.click(screen.getByLabelText('Elimina «Carta»'));
    expect(p.onelimina).not.toHaveBeenCalled();
    expect(screen.getByText('Elimina?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    expect(p.onelimina).toHaveBeenCalledTimes(1);
  });

  it('un testo restituito da onelimina compare sotto la riga', async () => {
    monta({ cestinoApre: () => true, onelimina: vi.fn(async () => 'Non si può eliminare: ha persone.\nUsa la finestra.') });
    await userEvent.click(screen.getByLabelText('Elimina «Contanti»…'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Non si può eliminare: ha persone.');
  });
});

describe('ElencoModificabile – sotto e controlli', () => {
  it('`sotto` compare sotto ogni riga, a tutta larghezza', () => {
    const sotto = createRawSnippet((el: () => Voce) => ({ render: () => `<span class="tagprova">tag di ${el().nome}</span>` }));
    monta({ sotto });
    expect(screen.getByText('tag di Carta').closest('.sotto-riga')).toBeTruthy();
    expect(document.querySelectorAll('.sotto-riga')).toHaveLength(4);
  });

  it('la colonna stretta si attiva solo con `soloCestino`, non da sola', () => {
    monta({ haInterruttore: () => false });
    expect(document.querySelectorAll('.ctl.solo-cestino').length).toBe(0);
    cleanup();
    monta({ haInterruttore: () => false, soloCestino: true });
    expect(document.querySelectorAll('.ctl.solo-cestino').length).toBe(4);
  });
});
