import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { createRawSnippet } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Schede from '../components/Schede.svelte';

/**
 * Specifica §2.4 e design system «Schede lunghe»:
 * - `role="tablist"` (con l'etichetta), `role="tab"` con `aria-selected`, un solo `role="tabpanel"` con gli id collegati
 *   (`${id}-tab-${s}` e `${id}-pannello-${s}`); il corpo della scheda è lo snippet `children(idScheda)`;
 * - tabindex mobile: solo la scheda attiva è nel giro del Tab;
 * - frecce sinistra/destra a giro, Home/Fine: spostano il focus E attivano la scheda;
 * - il segno (`1`, «da pagare») sta nel nome della scheda;
 * - se le schede non stanno, ai lati compaiono i pulsanti «Schede precedenti» e «Schede successive», solo quando c'è
 *   qualcosa da quella parte; sono pulsanti veri fuori dal tablist.
 * jsdom non misura: per le frecce di scorrimento si simulano larghezze e scrollLeft (vedi `fingiScorrimento`).
 */
const SCHEDE = [
  { id: 'riepilogo', nome: 'Riepilogo' },
  { id: 'corsi', nome: 'Corsi', segno: { testo: '1' } },
  { id: 'pagamenti', nome: 'Pagamenti', segno: { testo: 'da pagare', warn: true } },
  { id: 'documenti', nome: 'Documenti' },
  { id: 'storico', nome: 'Storico' },
];
/**
 * Il corpo di prova scrive l'id che riceve. `createRawSnippet` disegna una volta sola: lo aggiorniamo noi con un
 * intervallo, così il test legge sempre il valore corrente del parametro, che il componente cambi il pannello
 * rimontando il corpo o dando un nuovo valore al parametro.
 */
const corpo = createRawSnippet((s: () => string) => ({
  render: () => `<p>Contenuto di ${s()}</p>`,
  setup: (el: Element) => {
    const t = setInterval(() => { const v = `Contenuto di ${s()}`; if (el.textContent !== v) el.textContent = v; }, 5);
    return () => clearInterval(t);
  },
}));

function monta(extra: Record<string, unknown> = {}) {
  return render(Schede, { schede: SCHEDE, attiva: 'riepilogo', etichetta: 'Sezioni della scheda', id: 'cliente', children: corpo, ...extra });
}
const tab = (nome: RegExp | string) => screen.getByRole('tab', { name: nome }) as HTMLElement;
const tabs = () => screen.getAllByRole('tab') as HTMLElement[];
const selezionata = () => tabs().filter((t) => t.getAttribute('aria-selected') === 'true');
const focusSu = (el: HTMLElement) => waitFor(() => expect(el).toHaveFocus(), { timeout: 2000 });
const pannello = () => screen.getByRole('tabpanel');

afterEach(cleanup);

describe('Schede – struttura e accessibilità', () => {
  it('c\'è un tablist con l\'etichetta data e tante schede (tab) quante quelle date, nell\'ordine', () => {
    monta();
    expect(screen.getByRole('tablist', { name: 'Sezioni della scheda' })).toBeInTheDocument();
    expect(tabs().map((t) => t.textContent)).toEqual([
      expect.stringContaining('Riepilogo'), expect.stringContaining('Corsi'), expect.stringContaining('Pagamenti'),
      expect.stringContaining('Documenti'), expect.stringContaining('Storico'),
    ]);
  });

  it('solo la scheda attiva ha aria-selected="true" e tabindex 0, le altre "false" e -1', () => {
    monta({ attiva: 'pagamenti' });
    expect(selezionata()).toHaveLength(1);
    expect(selezionata()[0]).toHaveTextContent('Pagamenti');
    for (const t of tabs()) {
      const attiva = t === selezionata()[0];
      expect(t).toHaveAttribute('aria-selected', attiva ? 'true' : 'false');
      expect(t).toHaveAttribute('tabindex', attiva ? '0' : '-1');
    }
  });

  it('c\'è un solo tabpanel, con il corpo della scheda attiva (lo snippet riceve l\'id)', async () => {
    monta({ attiva: 'corsi' });
    expect(screen.getAllByRole('tabpanel')).toHaveLength(1);
    await waitFor(() => expect(within(pannello()).getByText('Contenuto di corsi')).toBeInTheDocument(), { timeout: 2000 });
    expect(screen.queryByText('Contenuto di riepilogo')).toBeNull();
  });

  it('gli id sono collegati: tab `${id}-tab-${s}`, pannello `${id}-pannello-${s}`, aria-controls e aria-labelledby', () => {
    monta({ attiva: 'corsi', id: 'cliente' });
    const t = tab(/Corsi/);
    expect(t.id).toBe('cliente-tab-corsi');
    expect(t).toHaveAttribute('aria-controls', 'cliente-pannello-corsi');
    expect(pannello().id).toBe('cliente-pannello-corsi');
    expect(pannello()).toHaveAttribute('aria-labelledby', 'cliente-tab-corsi');
  });

  it('un altro `id` dà altri id (due elenchi di schede nella stessa pagina non si confondono)', () => {
    monta({ id: 'ruoli', attiva: 'storico' });
    expect(tab(/Storico/).id).toBe('ruoli-tab-storico');
    expect(pannello().id).toBe('ruoli-pannello-storico');
  });

  it('il segno sta nel nome della scheda: «1» e «da pagare»', () => {
    monta();
    expect(tab(/Corsi/)).toHaveTextContent('1');
    expect(tab(/Pagamenti/)).toHaveTextContent('da pagare');
    expect(tab(/Riepilogo/).textContent).not.toMatch(/\d|da pagare/);
  });

  it('le schede sono pulsanti raggiungibili con il focus (non div inerti)', () => {
    monta();
    tab(/Riepilogo/).focus();
    expect(tab(/Riepilogo/)).toHaveFocus();
  });
});

describe('Schede – tocco', () => {
  it('il clic su una scheda la attiva e cambia il pannello', async () => {
    const u = userEvent.setup();
    monta();
    await u.click(tab(/Documenti/));
    await waitFor(() => expect(tab(/Documenti/)).toHaveAttribute('aria-selected', 'true'));
    expect(selezionata()).toHaveLength(1);
    await waitFor(() => expect(within(pannello()).getByText('Contenuto di documenti')).toBeInTheDocument(), { timeout: 2000 });
    await waitFor(() => expect(screen.queryByText('Contenuto di riepilogo')).toBeNull(), { timeout: 2000 });
    expect(tab(/Documenti/)).toHaveAttribute('tabindex', '0');
    expect(tab(/Riepilogo/)).toHaveAttribute('tabindex', '-1');
  });

  it('cambiando la prop `attiva` da fuori (la pagina la tiene, per esempio nell\'indirizzo) la scheda cambia', async () => {
    const { rerender } = monta({ attiva: 'riepilogo' });
    await rerender({ attiva: 'storico' });
    await waitFor(() => expect(tab(/Storico/)).toHaveAttribute('aria-selected', 'true'));
    await waitFor(() => expect(within(pannello()).getByText('Contenuto di storico')).toBeInTheDocument(), { timeout: 2000 });
  });
});

describe('Schede – tastiera', () => {
  it('freccia destra: il focus va alla scheda dopo e la scheda si attiva', async () => {
    const u = userEvent.setup();
    monta();
    tab(/Riepilogo/).focus();
    await u.keyboard('{ArrowRight}');
    await focusSu(tab(/Corsi/));
    expect(tab(/Corsi/)).toHaveAttribute('aria-selected', 'true');
    expect(selezionata()).toHaveLength(1);
    await waitFor(() => expect(within(pannello()).getByText('Contenuto di corsi')).toBeInTheDocument(), { timeout: 2000 });
  });

  it('freccia sinistra torna indietro', async () => {
    const u = userEvent.setup();
    monta({ attiva: 'pagamenti' });
    tab(/Pagamenti/).focus();
    await u.keyboard('{ArrowLeft}');
    await focusSu(tab(/Corsi/));
    expect(tab(/Corsi/)).toHaveAttribute('aria-selected', 'true');
  });

  it('a giro: destra dall\'ultima va alla prima, sinistra dalla prima va all\'ultima', async () => {
    const u = userEvent.setup();
    monta({ attiva: 'storico' });
    tab(/Storico/).focus();
    await u.keyboard('{ArrowRight}');
    await focusSu(tab(/Riepilogo/));
    expect(tab(/Riepilogo/)).toHaveAttribute('aria-selected', 'true');
    await u.keyboard('{ArrowLeft}');
    await focusSu(tab(/Storico/));
    expect(tab(/Storico/)).toHaveAttribute('aria-selected', 'true');
  });

  it('Home va alla prima, Fine all\'ultima, e le attivano', async () => {
    const u = userEvent.setup();
    monta({ attiva: 'pagamenti' });
    tab(/Pagamenti/).focus();
    await u.keyboard('{End}');
    await focusSu(tab(/Storico/));
    expect(tab(/Storico/)).toHaveAttribute('aria-selected', 'true');
    await waitFor(() => expect(within(pannello()).getByText('Contenuto di storico')).toBeInTheDocument(), { timeout: 2000 });
    await u.keyboard('{Home}');
    await focusSu(tab(/Riepilogo/));
    expect(tab(/Riepilogo/)).toHaveAttribute('aria-selected', 'true');
  });

  it('più frecce di fila attraversano le schede una per volta', async () => {
    const u = userEvent.setup();
    monta();
    tab(/Riepilogo/).focus();
    await u.keyboard('{ArrowRight}{ArrowRight}{ArrowRight}');
    await focusSu(tab(/Documenti/));
    expect(selezionata()).toHaveLength(1);
    expect(selezionata()[0]).toBe(tab(/Documenti/));
  });

  it('i tasti verticali non spostano le schede', async () => {
    const u = userEvent.setup();
    monta();
    tab(/Riepilogo/).focus();
    await u.keyboard('{ArrowDown}{ArrowUp}');
    expect(tab(/Riepilogo/)).toHaveAttribute('aria-selected', 'true');
    expect(tab(/Riepilogo/)).toHaveFocus();
  });

  it('Tab esce dal tablist: non passa per le altre schede (tabindex mobile)', async () => {
    const u = userEvent.setup();
    monta({ attiva: 'corsi' });
    tab(/Corsi/).focus();
    await u.tab();
    expect(document.activeElement?.getAttribute('role')).not.toBe('tab');
    await u.tab({ shift: true });
    // tornando indietro non si ferma su una scheda che non sia quella attiva
    const el = document.activeElement as HTMLElement;
    if (el.getAttribute('role') === 'tab') expect(el).toBe(tab(/Corsi/));
  });
});

/**
 * jsdom non misura niente. Si fingono: ogni elemento largo 300 px e con 700 px di contenuto, e un `scrollLeft` che si
 * ricorda il valore e avvisa con l'evento `scroll` (anche se si scorre con scrollTo/scrollBy/scrollIntoView).
 */
function fingiScorrimento(visibile = 300, totale = 700) {
  const memoria = new WeakMap<Element, number>();
  const proto = Element.prototype as unknown as Record<string, unknown>;
  const salvati = new Map<string, PropertyDescriptor | undefined>();
  const def = (nome: string, d: PropertyDescriptor) => {
    salvati.set(nome, Object.getOwnPropertyDescriptor(Element.prototype, nome));
    Object.defineProperty(Element.prototype, nome, { configurable: true, ...d });
  };
  const imposta = (el: Element, v: number) => {
    const nuovo = Math.max(0, Math.min(v, totale - visibile));
    if (nuovo === (memoria.get(el) ?? 0)) return;
    memoria.set(el, nuovo);
    el.dispatchEvent(new Event('scroll'));
  };
  def('clientWidth', { get: () => visibile });
  def('scrollWidth', { get: () => totale });
  def('scrollLeft', { get() { return memoria.get(this as Element) ?? 0; }, set(v: number) { imposta(this as Element, v); } });
  const vecchi = { scrollTo: proto.scrollTo, scrollBy: proto.scrollBy, scrollIntoView: proto.scrollIntoView };
  proto.scrollTo = function (this: Element, a: unknown, b?: unknown) {
    const x = typeof a === 'object' && a ? (a as { left?: number }).left : a;
    if (typeof x === 'number') imposta(this, x);
    else if (typeof b === 'number' && typeof a === 'number') imposta(this, a);
  };
  proto.scrollBy = function (this: Element, a: unknown) {
    const dx = typeof a === 'object' && a ? (a as { left?: number }).left : a;
    if (typeof dx === 'number') imposta(this, (memoria.get(this) ?? 0) + dx);
  };
  proto.scrollIntoView = function () {};
  vi.stubGlobal('ResizeObserver', class { constructor(private f: () => void) {} observe() { queueMicrotask(this.f); } unobserve() {} disconnect() {} });
  return () => {
    for (const [nome, d] of salvati) d ? Object.defineProperty(Element.prototype, nome, d) : delete (Element.prototype as unknown as Record<string, unknown>)[nome];
    Object.assign(proto, vecchi);
    vi.unstubAllGlobals();
  };
}

describe('Schede – frecce di scorrimento', () => {
  it('se le schede stanno (nessuna misura) non ci sono frecce', () => {
    vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
    monta();
    expect(screen.queryByRole('button', { name: 'Schede precedenti' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Schede successive' })).toBeNull();
    vi.unstubAllGlobals();
  });

  describe('quando non stanno', () => {
    let ripristina: () => void;
    beforeEach(() => { ripristina = fingiScorrimento(); });
    afterEach(() => ripristina());

    it('all\'inizio compare solo «Schede successive»', async () => {
      monta();
      await waitFor(() => expect(screen.getByRole('button', { name: 'Schede successive' })).toBeInTheDocument(), { timeout: 2000 });
      expect(screen.queryByRole('button', { name: 'Schede precedenti' })).toBeNull();
    });

    it('le frecce sono pulsanti veri, fuori dal tablist e non sono schede', async () => {
      monta();
      const dx = await screen.findByRole('button', { name: 'Schede successive' });
      expect(dx.tagName).toBe('BUTTON');
      expect(screen.getByRole('tablist').contains(dx)).toBe(false);
      expect(tabs()).toHaveLength(SCHEDE.length);
    });

    it('«Schede successive» scorre: compare «Schede precedenti»', async () => {
      const u = userEvent.setup();
      monta();
      await u.click(await screen.findByRole('button', { name: 'Schede successive' }));
      await waitFor(() => expect(screen.getByRole('button', { name: 'Schede precedenti' })).toBeInTheDocument(), { timeout: 2000 });
    });

    it('in fondo sparisce «Schede successive»; tornando indietro sparisce «Schede precedenti»', async () => {
      const u = userEvent.setup();
      monta();
      for (let i = 0; i < 5; i++) {
        const dx = screen.queryByRole('button', { name: 'Schede successive' });
        if (!dx) break;
        await u.click(dx);
      }
      await waitFor(() => expect(screen.queryByRole('button', { name: 'Schede successive' })).toBeNull(), { timeout: 2000 });
      expect(screen.getByRole('button', { name: 'Schede precedenti' })).toBeInTheDocument();
      for (let i = 0; i < 5; i++) {
        const sx = screen.queryByRole('button', { name: 'Schede precedenti' });
        if (!sx) break;
        await u.click(sx);
      }
      await waitFor(() => expect(screen.queryByRole('button', { name: 'Schede precedenti' })).toBeNull(), { timeout: 2000 });
      expect(screen.getByRole('button', { name: 'Schede successive' })).toBeInTheDocument();
    });

    it('i pulsanti freccia non cambiano la scheda attiva', async () => {
      const u = userEvent.setup();
      monta({ attiva: 'corsi' });
      await u.click(await screen.findByRole('button', { name: 'Schede successive' }));
      expect(selezionata()[0]).toBe(tab(/Corsi/));
    });
  });
});
