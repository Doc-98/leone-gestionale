import { cleanup, render, screen, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { tick } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EliminazionePesante from '../components/EliminazionePesante.svelte';
import { ErroreApi } from './api';
import { schermo } from './schermo.svelte';

/**
 * Specifica (brief coerenza 7, lotto 3, A1 «components/EliminazionePesante.svelte» e design system «Eliminazione
 * pesante»). Props: `titolo`, `conseguenze: string[]` (una per riga, in un `<ul>`), `azione` («Elimina il gruppo»),
 * `onconferma` (può restituire una promessa), `onannulla`, `secondi = 5`.
 * - `role="alertdialog"`, `aria-modal="true"`, nome accessibile = titolo, descrizione = le conseguenze.
 * - All'apertura il fuoco va su «Annulla»; Invio su «Annulla» ed Esc chiamano `onannulla` e mai `onconferma`; Tab gira
 *   dentro la finestra; alla chiusura il fuoco torna a chi l'ha aperta; il tocco sul velo = «Annulla».
 * - Il pulsante rosso dice «Elimina il gruppo (5)», «(4)»… ed è disabilitato; a 0 dice «Elimina il gruppo» e si preme;
 *   il conto riparte a ogni apertura. Mentre `onconferma` lavora dice «Elimino…» ed è disabilitato; se la promessa
 *   fallisce la finestra resta aperta e mostra il messaggio sotto le conseguenze, su due righe con l'attacco in grassetto.
 * Gli errori delle API sono `ErroreApi` (lib/api.ts): è con quelli che si prova il messaggio.
 * IPOTESI: il velo è un elemento `.velo` (come in `Foglio`); i pulsanti si chiamano «Annulla» e `azione` (+ «(n)»).
 * Timer finti: solo setTimeout/setInterval/Date, così le promesse e i microtask girano normalmente.
 */
const TITOLO = 'Eliminare il gruppo «Agonisti»?';
const CONSEGUENZE = [
  '38 persone escono dal gruppo',
  '1 dichiarazione da verificare si chiude',
  '2 prezzi del listino per questo gruppo spariscono (Scuola nuoto, Nuoto adulti)',
];
const AZIONE = 'Elimina il gruppo';

let utente: ReturnType<typeof userEvent.setup>;
const larga0 = schermo.larga;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'] });
  utente = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) });
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  schermo.larga = larga0;
});

function monta(extra: Record<string, unknown> = {}) {
  const onconferma = vi.fn();
  const onannulla = vi.fn();
  const r = render(EliminazionePesante, {
    props: { titolo: TITOLO, conseguenze: CONSEGUENZE, azione: AZIONE, onconferma, onannulla, ...extra } as never,
  });
  return { onconferma, onannulla, ...r };
}
const finestra = () => screen.getByRole('alertdialog');
const annulla = () => screen.getByRole('button', { name: 'Annulla' });
const rosso = () => within(finestra()).getByRole('button', { name: new RegExp(`^${AZIONE}( \\(\\d+\\)|)$`) }) as HTMLButtonElement;
const passa = async (ms: number) => { await vi.advanceTimersByTimeAsync(ms); await tick(); };

describe('EliminazionePesante – struttura e accessibilità', () => {
  it('è un alertdialog modale col titolo come nome', () => {
    monta();
    const f = finestra();
    expect(f).toHaveAttribute('aria-modal', 'true');
    expect(f).toHaveAccessibleName(TITOLO);
    expect(screen.getByRole('heading', { name: TITOLO })).toBeInTheDocument();
  });

  it('le conseguenze sono una lista, una riga ciascuna, nel testo dato', () => {
    monta();
    const lista = within(finestra()).getByRole('list');
    const righe = within(lista).getAllByRole('listitem').map((l) => l.textContent?.trim());
    expect(righe).toEqual(CONSEGUENZE);
  });

  it('la descrizione accessibile contiene le conseguenze', () => {
    monta();
    const d = finestra().getAttribute('aria-describedby');
    expect(d).toBeTruthy();
    const testo = document.getElementById(d!)?.textContent ?? '';
    for (const c of CONSEGUENZE) expect(testo).toContain(c);
  });

  it('è portata in <body>, fuori dal contenitore in cui si monta', () => {
    const { container } = monta();
    expect(container.contains(finestra())).toBe(false);
    expect(document.body.contains(finestra())).toBe(true);
  });

  it('con una sola conseguenza la lista ha una riga', () => {
    monta({ conseguenze: ['1 persona esce dal gruppo'] });
    expect(within(finestra()).getAllByRole('listitem')).toHaveLength(1);
  });

  it('funziona sia sul computer sia sotto i 1024 px (dal basso): stesso contenuto e stessi pulsanti', () => {
    for (const larga of [true, false]) {
      schermo.larga = larga;
      monta();
      expect(finestra()).toHaveAccessibleName(TITOLO);
      expect(annulla()).toBeInTheDocument();
      expect(rosso()).toBeInTheDocument();
      cleanup();
    }
  });
});

describe('EliminazionePesante – fuoco e tastiera', () => {
  it('all\'apertura il fuoco è su «Annulla»', async () => {
    monta();
    await tick();
    expect(annulla()).toHaveFocus();
  });

  it('Esc chiama `onannulla` e non `onconferma`', async () => {
    const { onannulla, onconferma } = monta();
    await tick();
    await utente.keyboard('{Escape}');
    expect(onannulla).toHaveBeenCalledTimes(1);
    expect(onconferma).not.toHaveBeenCalled();
  });

  it('Invio con il fuoco su «Annulla» chiama `onannulla` e non `onconferma`', async () => {
    const { onannulla, onconferma } = monta();
    await tick();
    expect(annulla()).toHaveFocus();
    await utente.keyboard('{Enter}');
    expect(onannulla).toHaveBeenCalledTimes(1);
    expect(onconferma).not.toHaveBeenCalled();
  });

  it('Invio appena aperta non conferma, nemmeno dopo i 5 secondi se il fuoco è rimasto su «Annulla»', async () => {
    const { onconferma } = monta();
    await tick();
    await passa(5000);
    await utente.keyboard('{Enter}');
    expect(onconferma).not.toHaveBeenCalled();
  });

  it('il clic su «Annulla» chiama `onannulla`', async () => {
    const { onannulla, onconferma } = monta();
    await utente.click(annulla());
    expect(onannulla).toHaveBeenCalledTimes(1);
    expect(onconferma).not.toHaveBeenCalled();
  });

  it('Tab e Maiusc+Tab restano dentro la finestra, in tutti e due i versi', async () => {
    monta();
    await tick();
    await passa(5000); // il pulsante rosso è attivo: due pulsanti da percorrere
    for (let i = 0; i < 6; i++) {
      await utente.tab();
      expect(finestra().contains(document.activeElement)).toBe(true);
    }
    for (let i = 0; i < 6; i++) {
      await utente.tab({ shift: true });
      expect(finestra().contains(document.activeElement)).toBe(true);
    }
  });

  it('Tab resta dentro la finestra anche mentre il pulsante rosso è ancora disabilitato', async () => {
    monta();
    await tick();
    for (let i = 0; i < 4; i++) {
      await utente.tab();
      expect(finestra().contains(document.activeElement)).toBe(true);
    }
  });

  it('il tocco sul velo equivale ad «Annulla»', async () => {
    const { onannulla, onconferma } = monta();
    const velo = document.querySelector('.velo');
    expect(velo, 'ipotesi: il velo ha classe .velo').not.toBeNull();
    await utente.click(velo as Element);
    expect(onannulla).toHaveBeenCalledTimes(1);
    expect(onconferma).not.toHaveBeenCalled();
  });

  it('alla chiusura il fuoco torna a chi l\'ha aperta', async () => {
    const apre = document.createElement('button');
    apre.textContent = 'Elimina «Agonisti»…';
    document.body.appendChild(apre);
    apre.focus();
    const { unmount } = monta();
    await tick();
    expect(annulla()).toHaveFocus();
    unmount();
    await tick();
    expect(apre).toHaveFocus();
    apre.remove();
  });
});

describe('EliminazionePesante – il conto alla rovescia', () => {
  it('all\'apertura il pulsante rosso dice «Elimina il gruppo (5)» ed è disabilitato', () => {
    monta();
    const b = screen.getByRole('button', { name: 'Elimina il gruppo (5)' });
    expect(b).toBeDisabled();
    expect(b).toHaveClass('btn', 'btn-danger');
  });

  it('scende di un numero al secondo: (4), (3), (2), (1)', async () => {
    monta();
    for (const n of [4, 3, 2, 1]) {
      await passa(1000);
      const b = screen.getByRole('button', { name: `Elimina il gruppo (${n})` });
      expect(b).toBeDisabled();
    }
  });

  it('prima dei 5 secondi un clic sul pulsante non conferma', async () => {
    const { onconferma } = monta();
    await passa(4000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo (1)' }));
    expect(onconferma).not.toHaveBeenCalled();
  });

  it('dopo 5 secondi dice «Elimina il gruppo», senza numero, ed è abilitato', async () => {
    monta();
    await passa(5000);
    const b = screen.getByRole('button', { name: 'Elimina il gruppo' });
    expect(b).toBeEnabled();
    expect(screen.queryByRole('button', { name: /\(\d+\)/ })).toBeNull();
  });

  it('il clic dopo il conto chiama `onconferma` una volta sola e `onannulla` mai', async () => {
    const { onconferma, onannulla } = monta();
    await passa(5000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo' }));
    expect(onconferma).toHaveBeenCalledTimes(1);
    expect(onannulla).not.toHaveBeenCalled();
  });

  it('con `secondi` diverso il conto parte da quel numero e si sblocca a tempo', async () => {
    const { onconferma } = monta({ secondi: 2 });
    expect(screen.getByRole('button', { name: 'Elimina il gruppo (2)' })).toBeDisabled();
    await passa(1000);
    expect(screen.getByRole('button', { name: 'Elimina il gruppo (1)' })).toBeDisabled();
    await passa(1000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo' }));
    expect(onconferma).toHaveBeenCalledTimes(1);
  });

  it('il conto riparte da capo a ogni apertura', async () => {
    const { unmount } = monta();
    await passa(5000);
    expect(screen.getByRole('button', { name: 'Elimina il gruppo' })).toBeEnabled();
    unmount();
    monta();
    expect(screen.getByRole('button', { name: 'Elimina il gruppo (5)' })).toBeDisabled();
  });
});

describe('EliminazionePesante – mentre lavora e se fallisce', () => {
  it('mentre `onconferma` è in corso dice «Elimino…» ed è disabilitato', async () => {
    let finisci!: () => void;
    const onconferma = vi.fn(() => new Promise<void>((r) => (finisci = r)));
    monta({ onconferma });
    await passa(5000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo' }));
    const b = screen.getByRole('button', { name: 'Elimino…' });
    expect(b).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Elimina il gruppo' })).toBeNull();
    // un secondo clic non fa partire una seconda eliminazione
    await utente.click(b);
    expect(onconferma).toHaveBeenCalledTimes(1);
    finisci();
    await tick();
  });

  it('se la promessa fallisce la finestra resta aperta e mostra il messaggio su due righe, attacco in grassetto', async () => {
    const onconferma = vi.fn(() => Promise.reject(new ErroreApi(409, 'Non si può eliminare così: «Agonisti» ha 38 persone.\nUsa di nuovo il cestino: si apre la finestra con le conseguenze.')));
    monta({ onconferma });
    await passa(5000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo' }));
    await vi.waitFor(() => expect(within(finestra()).getByText(/«Agonisti» ha 38 persone\./)).toBeInTheDocument());

    expect(finestra()).toBeInTheDocument();
    const f = finestra();
    const grassetti = [...f.querySelectorAll('b, strong')].map((e) => e.textContent?.trim());
    expect(grassetti).toContain('Non si può eliminare così:');
    expect(grassetti).toContain('Usa di nuovo il cestino:');
    expect(within(f).getByText(/«Agonisti» ha 38 persone\./)).toBeInTheDocument();
    expect(within(f).getByText(/si apre la finestra con le conseguenze\./)).toBeInTheDocument();
    // le due righe sono elementi distinti (non un solo paragrafo a capo)
    const primo = within(f).getByText(/«Agonisti» ha 38 persone\./);
    const secondo = within(f).getByText(/si apre la finestra con le conseguenze\./);
    expect(primo.parentElement === secondo.parentElement ? primo !== secondo : true).toBe(true);
  });

  it('il messaggio sta sotto le conseguenze', async () => {
    const onconferma = vi.fn(() => Promise.reject(new ErroreApi(409, 'Non si può eliminare: qualcosa è cambiato.')));
    monta({ onconferma });
    await passa(5000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo' }));
    await vi.waitFor(() => expect(within(finestra()).getByText(/qualcosa è cambiato\./)).toBeInTheDocument());
    const ultimaConseguenza = within(finestra()).getAllByRole('listitem').at(-1)!;
    const messaggio = within(finestra()).getByText(/qualcosa è cambiato\./);
    expect(ultimaConseguenza.compareDocumentPosition(messaggio) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('dopo l\'errore non si richiama `onannulla` e «Annulla» chiude ancora', async () => {
    const onconferma = vi.fn(() => Promise.reject(new ErroreApi(409, 'Non si può eliminare: no.')));
    const { onannulla } = monta({ onconferma });
    await passa(5000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo' }));
    await passa(0);
    expect(onannulla).not.toHaveBeenCalled();
    await utente.click(annulla());
    expect(onannulla).toHaveBeenCalledTimes(1);
  });

  it('dopo l\'errore il pulsante torna al nome dell\'azione (non resta «Elimino…»)', async () => {
    const onconferma = vi.fn(() => Promise.reject(new ErroreApi(409, 'Non si può eliminare: no.')));
    monta({ onconferma });
    await passa(5000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo' }));
    await passa(0);
    expect(screen.queryByRole('button', { name: 'Elimino…' })).toBeNull();
    expect(screen.getByRole('button', { name: /^Elimina il gruppo/ })).toBeInTheDocument();
  });
});

describe('EliminazionePesante – errore non dell\'API', () => {
  it('un errore qualunque (non ErroreApi) lascia comunque la finestra aperta, con un testo che dice che non è riuscita', async () => {
    const onconferma = vi.fn(() => Promise.reject(new TypeError('boom interno')));
    monta({ onconferma });
    await passa(5000);
    await utente.click(screen.getByRole('button', { name: 'Elimina il gruppo' }));
    await vi.waitFor(() => expect(within(finestra()).getByRole('alert')).toBeInTheDocument());
    expect(finestra()).toBeInTheDocument();
    expect(within(finestra()).getByRole('alert')).not.toHaveTextContent('boom interno');
  });
});
