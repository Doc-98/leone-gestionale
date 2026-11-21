import { cleanup, render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MenuAltro from '../components/MenuAltro.svelte';
import { schermo } from './schermo.svelte';
import type { VoceMenu } from './menu';

/**
 * Specifica §2.3 e design system «Menu «⋯»»:
 * - pulsante `aria-haspopup="menu"`, `aria-expanded`, `aria-controls`; etichetta predefinita «Altre azioni»;
 * - pannello `role="menu"` con voci `menuitem` (al massimo 7), all'apertura il focus va alla prima voce;
 * - su/giù a giro, Home/Fine; Esc chiude e rimette il focus sul pulsante; Tab chiude; tocco fuori chiude;
 * - le voci normali chiudono il menu e poi eseguono;
 * - la voce `elimina` è l'ultima, rossa, in due tocchi dentro la voce (primo: «Elimina?»; secondo: esegue e chiude);
 * - sul computer il pannello è portato in <body>, fuori dalla card, e sparisce allo smontaggio;
 *   sul telefono e sul tablet (sotto i 1024 px) il menu è un foglio dal basso: il comportamento da tastiera è lo stesso.
 */
const VOCI = () => {
  const f = { a: vi.fn(), b: vi.fn(), c: vi.fn(), el: vi.fn() };
  const voci: VoceMenu[] = [
    { testo: "Modifica l'anagrafica", icona: 'matita' as const, onclick: f.a },
    { testo: 'Nuovo certificato…', icona: 'piu' as const, onclick: f.b },
    { testo: 'Scarica la scheda', icona: 'scarica' as const, onclick: f.c },
  ];
  return { f, voci };
};
const conElimina = (testo = 'Elimina il cliente', elimina: 'Elimina?' | 'Rimuovi?' = 'Elimina?') => {
  const { f, voci } = VOCI();
  return { f, voci: [...voci, { testo, icona: 'cestino' as const, onclick: f.el, elimina }] };
};

const pulsante = (nome = 'Altre azioni') => screen.getByRole('button', { name: nome }) as HTMLButtonElement;
const pannello = () => screen.queryByRole('menu');
const voci = () => screen.queryAllByRole('menuitem') as HTMLElement[];
const chiuso = () => waitFor(() => expect(pannello()).toBeNull(), { timeout: 2000 });
const aperto = () => waitFor(() => expect(pannello()).not.toBeNull(), { timeout: 2000 });
const focusSu = (el: HTMLElement) => waitFor(() => expect(el).toHaveFocus(), { timeout: 2000 });

const prima = { larga: schermo.larga };
afterEach(() => {
  cleanup();
  schermo.larga = prima.larga;
});

describe.each([
  ['computer', true],
  ['telefono e tablet', false],
])('Menu «⋯» – %s', (_nome, larga) => {
  beforeEach(() => {
    schermo.larga = larga;
  });

  it('il pulsante ha etichetta «Altre azioni», aria-haspopup="menu" e aria-expanded="false" da chiuso', () => {
    render(MenuAltro, { voci: VOCI().voci });
    const p = pulsante();
    expect(p).toHaveAttribute('aria-haspopup', 'menu');
    expect(p).toHaveAttribute('aria-expanded', 'false');
    expect(pannello()).toBeNull();
  });

  it('l\'etichetta si può cambiare', () => {
    render(MenuAltro, { voci: VOCI().voci, etichetta: 'Altre azioni sul cliente' });
    expect(screen.getByRole('button', { name: 'Altre azioni sul cliente' })).toBeInTheDocument();
  });

  it('il clic apre: aria-expanded="true", un solo menu con le voci come menuitem, aria-controls punta a un elemento', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    expect(pulsante()).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getAllByRole('menu')).toHaveLength(1);
    expect(voci().map((v) => v.textContent?.trim())).toEqual(["Modifica l'anagrafica", 'Nuovo certificato…', 'Scarica la scheda']);
    const id = pulsante().getAttribute('aria-controls');
    expect(id).toBeTruthy();
    const bersaglio = document.getElementById(id!);
    expect(bersaglio).not.toBeNull();
    expect(bersaglio === pannello() || bersaglio!.contains(pannello())).toBe(true);
  });

  it('all\'apertura il focus va alla prima voce', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    await focusSu(voci()[0]);
  });

  it('Invio sul pulsante apre il menu, e anche lo spazio', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    pulsante().focus();
    await u.keyboard('{Enter}');
    await aperto();
    await u.keyboard('{Escape}');
    await chiuso();
    pulsante().focus();
    await u.keyboard(' ');
    await aperto();
  });

  it('freccia giù e su spostano il focus tra le voci, a giro', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    const v = voci();
    await focusSu(v[0]);
    await u.keyboard('{ArrowDown}');
    await focusSu(v[1]);
    await u.keyboard('{ArrowDown}');
    await focusSu(v[2]);
    await u.keyboard('{ArrowDown}');
    await focusSu(v[0]); // ritorno circolare
    await u.keyboard('{ArrowUp}');
    await focusSu(v[2]); // e all'indietro
    await u.keyboard('{ArrowUp}');
    await focusSu(v[1]);
  });

  it('Home e Fine vanno alla prima e all\'ultima voce', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    const v = voci();
    await u.keyboard('{End}');
    await focusSu(v[2]);
    await u.keyboard('{Home}');
    await focusSu(v[0]);
  });

  it('Esc chiude il menu e rimette il focus sul pulsante', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    await u.keyboard('{Escape}');
    await chiuso();
    expect(pulsante()).toHaveAttribute('aria-expanded', 'false');
    await focusSu(pulsante());
  });

  it('Esc dopo essersi mossi tra le voci chiude e il focus torna sul pulsante', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    await u.keyboard('{ArrowDown}{ArrowDown}');
    await u.keyboard('{Escape}');
    await chiuso();
    await focusSu(pulsante());
  });

  it('un tocco fuori chiude il menu', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    // sul telefono «fuori» è il velo del foglio (dietro il menu); sul computer qualunque punto della pagina
    await u.click(document.querySelector('.velo') ?? document.body);
    await chiuso();
    expect(pulsante()).toHaveAttribute('aria-expanded', 'false');
  });

  it('Tab chiude il menu', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    await u.keyboard('{Tab}');
    await chiuso();
  });

  it('un secondo clic sul pulsante chiude il menu aperto', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    await u.click(pulsante());
    await chiuso();
  });

  it('una voce normale chiude il menu ed esegue, una volta sola (clic)', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = VOCI();
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    await u.click(voci()[1]);
    await chiuso();
    expect(f.b).toHaveBeenCalledTimes(1);
    expect(f.a).not.toHaveBeenCalled();
    expect(f.c).not.toHaveBeenCalled();
  });

  it('una voce normale si esegue con Invio e con lo spazio da tastiera', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = VOCI();
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    await u.keyboard('{Enter}'); // prima voce, col focus
    await chiuso();
    expect(f.a).toHaveBeenCalledTimes(1);
    await u.click(pulsante());
    await aperto();
    await u.keyboard('{ArrowDown}{ArrowDown} ');
    await chiuso();
    expect(f.c).toHaveBeenCalledTimes(1);
  });

  it('una voce disabilitata non esegue e la freccia la salta', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = VOCI();
    vv[1] = { ...vv[1], disabled: true };
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    const v = voci();
    await focusSu(v[0]);
    await u.keyboard('{ArrowDown}');
    await focusSu(v[2]);
    await u.click(v[1]);
    expect(f.b).not.toHaveBeenCalled();
  });

  it('la voce che elimina, data per prima, sta ultima nel menu', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = conElimina();
    const [a, b, c, el] = vv;
    render(MenuAltro, { voci: [el, a, b, c] });
    await u.click(pulsante());
    await aperto();
    const nomi = voci().map((v) => v.textContent?.trim());
    expect(nomi.slice(0, 3)).toEqual(["Modifica l'anagrafica", 'Nuovo certificato…', 'Scarica la scheda']);
    expect(nomi[3]).toContain('Elimina il cliente');
    expect(f.el).not.toHaveBeenCalled();
  });

  it('elimina: il primo tocco arma («Elimina?»), non esegue e il menu resta aperto; il secondo esegue e chiude', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = conElimina();
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    const ultima = voci()[3];
    await u.click(ultima);
    await waitFor(() => expect(voci()[3]).toHaveTextContent('Elimina?'), { timeout: 2000 });
    // a vista c'è la domanda: la voce a riposo resta nel pulsante solo nascosta (aria-hidden), per non cambiare larghezza
    expect(voci()[3]).toHaveAccessibleName(/Elimina\?/);
    expect(voci()[3]).not.toHaveAccessibleName(/Elimina il cliente/);
    expect(f.el).not.toHaveBeenCalled();
    expect(pannello()).not.toBeNull();
    await u.click(voci()[3]);
    await chiuso();
    expect(f.el).toHaveBeenCalledTimes(1);
  });

  it('elimina da tastiera: Invio arma, un secondo Invio esegue', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = conElimina();
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    await u.keyboard('{End}');
    await focusSu(voci()[3]);
    await u.keyboard('{Enter}');
    await waitFor(() => expect(voci()[3]).toHaveTextContent('Elimina?'), { timeout: 2000 });
    expect(f.el).not.toHaveBeenCalled();
    await u.keyboard('{Enter}');
    await chiuso();
    expect(f.el).toHaveBeenCalledTimes(1);
  });

  it('elimina con la domanda «Rimuovi?»', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = conElimina('Rimuovi il tutore', 'Rimuovi?');
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    await u.click(voci()[3]);
    await waitFor(() => expect(voci()[3]).toHaveTextContent('Rimuovi?'), { timeout: 2000 });
    expect(f.el).not.toHaveBeenCalled();
  });

  it('chiudere il menu con Esc con la voce armata non esegue, e riaprendo la voce non è più armata', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = conElimina();
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    await u.click(voci()[3]);
    await waitFor(() => expect(voci()[3]).toHaveTextContent('Elimina?'), { timeout: 2000 });
    await u.keyboard('{Escape}');
    await chiuso();
    expect(f.el).not.toHaveBeenCalled();
    await u.click(pulsante());
    await aperto();
    expect(voci()[3]).toHaveAccessibleName(/Elimina il cliente/);
    expect(f.el).not.toHaveBeenCalled();
  });

  it('sette voci vanno bene', async () => {
    const u = userEvent.setup();
    const sette = Array.from({ length: 7 }, (_, i) => ({ testo: `Voce ${i + 1}`, icona: 'piu' as const, onclick: vi.fn() }));
    render(MenuAltro, { voci: sette });
    await u.click(pulsante());
    await aperto();
    expect(voci()).toHaveLength(7);
  });

  /** In sviluppo (nei test `import.meta.env.DEV` è vero) l'errore di `ordinaVoci` esce al disegno. */
  it('più di sette voci: errore al disegno', () => {
    const otto = Array.from({ length: 8 }, (_, i) => ({ testo: `Voce ${i + 1}`, icona: 'piu' as const, onclick: vi.fn() }));
    const muto = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(MenuAltro, { voci: otto })).toThrow();
    muto.mockRestore();
  });

  it('due voci che eliminano: errore al disegno', () => {
    const { voci: vv } = conElimina();
    const doppia = [...vv, { testo: 'Rimuovi il tutore', icona: 'cestino' as const, onclick: vi.fn(), elimina: 'Rimuovi?' as const }];
    const muto = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(MenuAltro, { voci: doppia })).toThrow();
    muto.mockRestore();
  });

  it('i separatori non contano nelle sette voci', async () => {
    const u = userEvent.setup();
    const sette = Array.from({ length: 7 }, (_, i) => ({ testo: `Voce ${i + 1}`, icona: 'piu' as const, onclick: vi.fn(), separatore: i === 3 || i === 5 }));
    render(MenuAltro, { voci: sette });
    await u.click(pulsante());
    await aperto();
    expect(voci()).toHaveLength(7);
  });

  it('aria-controls c\'è solo a menu aperto', async () => {
    const u = userEvent.setup();
    render(MenuAltro, { voci: VOCI().voci });
    expect(pulsante()).not.toHaveAttribute('aria-controls');
    await u.click(pulsante());
    await aperto();
    expect(pulsante()).toHaveAttribute('aria-controls');
    await u.keyboard('{Escape}');
    await chiuso();
    expect(pulsante()).not.toHaveAttribute('aria-controls');
  });

  it('scegliere una voce: il menu si chiude, il focus torna al pulsante e poi parte l\'azione', async () => {
    const u = userEvent.setup();
    let focusAlLancio: Element | null = null;
    const onclick = vi.fn(() => {
      focusAlLancio = document.activeElement;
    });
    render(MenuAltro, { voci: [{ testo: 'Scarica la scheda', icona: 'scarica' as const, onclick }, { testo: 'Altro', icona: 'piu' as const, onclick: vi.fn() }] });
    await u.click(pulsante());
    await aperto();
    await u.click(voci()[0]);
    await chiuso();
    expect(onclick).toHaveBeenCalledTimes(1);
    expect(focusAlLancio).toBe(pulsante());
    await focusSu(pulsante());
  });

  it('voce `rossa`: ultima tra le normali, prima dell\'eliminazione, un solo tocco esegue e chiude', async () => {
    const u = userEvent.setup();
    const { f, voci: vv } = conElimina();
    const banna = vi.fn();
    const rossa: VoceMenu = { testo: 'Banna la persona…', icona: 'divieto', onclick: banna, rossa: true };
    const [a, b, c, el] = vv;
    render(MenuAltro, { voci: [rossa, el, a, b, c] });
    await u.click(pulsante());
    await aperto();
    const nomi = voci().map((v) => v.textContent ?? '');
    expect(nomi).toHaveLength(5);
    expect(nomi[0]).toContain("Modifica l'anagrafica");
    expect(nomi[2]).toContain('Scarica la scheda');
    expect(nomi[3]).toContain('Banna la persona');
    expect(nomi[4]).toContain('Elimina il cliente');
    await u.click(voci()[3]);
    await chiuso();
    expect(banna).toHaveBeenCalledTimes(1); // niente armatura: il modulo fa da conferma
    expect(f.el).not.toHaveBeenCalled();
  });

  it('voce `rossa` da tastiera: un Invio esegue', async () => {
    const u = userEvent.setup();
    const banna = vi.fn();
    render(MenuAltro, { voci: [{ testo: 'Altra', icona: 'piu' as const, onclick: vi.fn() }, { testo: 'Banna la persona…', icona: 'divieto' as const, onclick: banna, rossa: true }] });
    await u.click(pulsante());
    await aperto();
    await u.keyboard('{End}{Enter}');
    await chiuso();
    expect(banna).toHaveBeenCalledTimes(1);
  });

  it('più voci `rossa` sono ammesse e restano nell\'ordine dato (ordinamento stabile)', async () => {
    const u = userEvent.setup();
    const r = (testo: string): VoceMenu => ({ testo, icona: 'divieto', onclick: vi.fn(), rossa: true });
    render(MenuAltro, { voci: [r('R1'), { testo: 'N1', icona: 'piu', onclick: vi.fn() }, r('R2'), { testo: 'N2', icona: 'piu', onclick: vi.fn() }] });
    await u.click(pulsante());
    await aperto();
    expect(voci().map((v) => v.textContent?.trim())).toEqual(['N1', 'N2', 'R1', 'R2']);
  });

  it('`separatore`: una linea role="separator" sopra la voce, che non è un menuitem', async () => {
    const u = userEvent.setup();
    const vv: VoceMenu[] = [
      { testo: 'Prima', icona: 'piu', onclick: vi.fn() },
      { testo: 'Dopo la linea', icona: 'piu', onclick: vi.fn(), separatore: true },
      { testo: 'Ultima', icona: 'piu', onclick: vi.fn() },
    ];
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    const sep = screen.getAllByRole('separator');
    expect(sep).toHaveLength(1);
    expect(voci()).toHaveLength(3);
    expect(sep[0].textContent?.trim()).toBe('');
    // la linea sta nel menu, subito prima della voce che la chiede
    const pos = sep[0].compareDocumentPosition(voci()[1]);
    expect(pos & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(voci()[0].compareDocumentPosition(sep[0]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(pannello()!.contains(sep[0])).toBe(true);
  });

  it('`separatore`: le frecce lo saltano (a giro) e il focus non ci va mai', async () => {
    const u = userEvent.setup();
    const vv: VoceMenu[] = [
      { testo: 'Prima', icona: 'piu', onclick: vi.fn() },
      { testo: 'Seconda', icona: 'piu', onclick: vi.fn(), separatore: true },
      { testo: 'Terza', icona: 'piu', onclick: vi.fn() },
    ];
    render(MenuAltro, { voci: vv });
    await u.click(pulsante());
    await aperto();
    const v = voci();
    await focusSu(v[0]);
    await u.keyboard('{ArrowDown}');
    await focusSu(v[1]);
    await u.keyboard('{ArrowDown}');
    await focusSu(v[2]);
    await u.keyboard('{ArrowDown}');
    await focusSu(v[0]);
    await u.keyboard('{ArrowUp}');
    await focusSu(v[2]);
    await u.keyboard('{ArrowUp}');
    await focusSu(v[1]);
  });

  it('smontando il componente da aperto il menu sparisce dal documento', async () => {
    const u = userEvent.setup();
    const { unmount } = render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    unmount();
    await waitFor(() => {
      expect(screen.queryByRole('menu')).toBeNull();
      expect(screen.queryAllByRole('menuitem')).toHaveLength(0);
    }, { timeout: 2000 });
  });
});

describe('Menu «⋯» – sul computer il pannello esce dalla card', () => {
  beforeEach(() => {
    schermo.larga = true;
  });

  it('il pannello è in <body>, non dentro il contenitore del componente (né dentro una card con overflow nascosto)', async () => {
    const u = userEvent.setup();
    const card = document.createElement('div');
    card.className = 'card';
    card.style.overflow = 'hidden';
    document.body.appendChild(card);
    render(MenuAltro, { target: card, props: { voci: VOCI().voci } });
    await u.click(pulsante());
    await aperto();
    expect(card.contains(pannello())).toBe(false);
    expect(document.body.contains(pannello())).toBe(true);
    // un antenato con overflow nascosto non c'è: il pannello sta sotto <body> senza passare dalla card
    let n: HTMLElement | null = pannello();
    while (n && n !== document.body) {
      expect(getComputedStyle(n).overflow).not.toBe('hidden');
      n = n.parentElement;
    }
    card.remove();
  });

  it('da aperto, il pannello non rimane in <body> dopo lo smontaggio e da chiuso non lascia residui', async () => {
    const u = userEvent.setup();
    const prima = document.body.children.length;
    const { unmount } = render(MenuAltro, { voci: VOCI().voci });
    await u.click(pulsante());
    await aperto();
    await u.keyboard('{Escape}');
    await chiuso();
    unmount();
    await waitFor(() => expect(document.querySelector('[role="menu"]')).toBeNull(), { timeout: 2000 });
    // il contenitore di render è rimosso dal cleanup: il numero di figli di <body> torna come prima
    cleanup();
    expect(document.body.children.length).toBe(prima);
  });
});
