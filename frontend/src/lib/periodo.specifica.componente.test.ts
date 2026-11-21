import { cleanup, fireEvent, render, screen, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { createRawSnippet } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BarraPagina from '../components/BarraPagina.svelte';
import Periodo from '../components/Periodo.svelte';
import SceltaVista from '../components/SceltaVista.svelte';
import { leggiPreferenza } from './preferenze';
import { schermo } from './schermo.svelte';

/**
 * Specifica `/tmp/b7/piano.md` §2.5 e design system «Barra di pagina»:
 * - Periodo: `sx` · scritta · `dx`, poi «oggi» (spento se il periodo contiene già oggi); la scritta è un pulsante che apre
 *   il selettore nativo di data (giorno, settimana) o di mese; `inizio` è bindable: si osserva dalla scritta;
 * - SceltaVista: fino a 3 opzioni un segmentato (role="group", aria-pressed), oltre una tendina (select);
 *   una vista che non c'è tra le opzioni non compare; `preferenza` salva la scelta con `lib/preferenze`;
 * - BarraPagina: computer = titolo a sinistra, poi periodo · vista · azioni; telefono = titolo + azioni, poi periodo e vista.
 */
afterEach(() => {
  cleanup();
  localStorage.clear();
  schermo.larga = true;
  schermo.telefono = false;
  vi.useRealTimers();
});

const testo = (t: string) => createRawSnippet(() => ({ render: () => `<span>${t}</span>` }));

describe('Periodo', () => {
  const settimana = (extra: Record<string, unknown> = {}) =>
    render(Periodo, { inizio: '2026-11-09', passo: 'settimana', oggi: '2026-11-21', ...extra });
  const dx = () => screen.getByRole('button', { name: /success|prossim|avanti/i });
  const sx = () => screen.getByRole('button', { name: /precedent|indietro/i });
  const oggiBtn = () => screen.getByRole('button', { name: /oggi|torna a/i });

  it('mostra la scritta del periodo, nel formato del design system', () => {
    settimana();
    expect(screen.getByText('9–15 novembre')).toBeTruthy();
  });

  it('mese e giorno: scritte «Novembre 2026» e «Lunedì 9 novembre»', () => {
    render(Periodo, { inizio: '2026-11-01', passo: 'mese', oggi: '2026-11-21' });
    expect(screen.getByText('Novembre 2026')).toBeTruthy();
    cleanup();
    render(Periodo, { inizio: '2026-11-09', passo: 'giorno', oggi: '2026-11-21' });
    expect(screen.getByText('Lunedì 9 novembre')).toBeTruthy();
  });

  it('le etichette dei pulsanti dicono l\'unità: «Settimana precedente», «Settimana successiva», «Torna a questa settimana»', () => {
    settimana();
    expect(screen.getByRole('button', { name: /Settimana precedente/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Settimana successiva|Settimana seguente|Prossima settimana/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Torna a questa settimana/i })).toBeTruthy();
  });

  it('con passo mese le etichette parlano di mese, con passo giorno di giorno', () => {
    render(Periodo, { inizio: '2026-11-01', passo: 'mese', oggi: '2026-12-05' });
    expect(screen.getByRole('button', { name: /Mese precedente/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Torna a questo mese/i })).toBeTruthy();
    cleanup();
    render(Periodo, { inizio: '2026-11-09', passo: 'giorno', oggi: '2026-12-05' });
    expect(screen.getByRole('button', { name: /Giorno precedente/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Torna a oggi/i })).toBeTruthy();
  });

  it('tutti i pulsanti sono <button type="button"> (non mandano moduli)', () => {
    settimana();
    for (const b of screen.getAllByRole('button')) expect(b.getAttribute('type')).toBe('button');
  });

  it('la freccia avanti porta alla settimana dopo, quella indietro torna', async () => {
    settimana();
    await fireEvent.click(dx());
    expect(screen.getByText('16–22 novembre')).toBeTruthy();
    await fireEvent.click(sx());
    await fireEvent.click(sx());
    expect(screen.getByText('2–8 novembre')).toBeTruthy();
  });

  it('passando da un mese all\'altro e da un anno all\'altro la scritta segue (settimana 28 dic – 3 gen)', async () => {
    settimana({ inizio: '2026-12-21' });
    await fireEvent.click(dx());
    expect(screen.getByText('28 dicembre – 3 gennaio')).toBeTruthy();
    await fireEvent.click(dx());
    expect(screen.getByText('4–10 gennaio')).toBeTruthy();
  });

  it('mese: dicembre → gennaio dell\'anno dopo e febbraio bisestile', async () => {
    render(Periodo, { inizio: '2026-12-01', passo: 'mese', oggi: '2026-11-21' });
    await fireEvent.click(screen.getByRole('button', { name: /Mese successivo|Mese seguente|Prossimo mese/i }));
    expect(screen.getByText('Gennaio 2027')).toBeTruthy();
    cleanup();
    render(Periodo, { inizio: '2028-03-01', passo: 'mese', oggi: '2026-11-21' });
    await fireEvent.click(screen.getByRole('button', { name: /Mese precedente/i }));
    expect(screen.getByText('Febbraio 2028')).toBeTruthy();
  });

  it('giorno: avanti di un giorno attraversa il cambio dell\'ora legale senza salti (28 → 29 → 30 marzo 2026)', async () => {
    render(Periodo, { inizio: '2026-03-28', passo: 'giorno', oggi: '2026-11-21' });
    const avanti = screen.getByRole('button', { name: /Giorno successivo|Giorno seguente|Prossimo giorno/i });
    await fireEvent.click(avanti);
    expect(screen.getByText('Domenica 29 marzo')).toBeTruthy();
    await fireEvent.click(avanti);
    expect(screen.getByText('Lunedì 30 marzo')).toBeTruthy();
  });

  it('«oggi» riporta al periodo che contiene oggi (da lontano, in avanti e indietro)', async () => {
    settimana({ inizio: '2026-02-02' });
    expect(screen.getByText('2–8 febbraio')).toBeTruthy();
    await fireEvent.click(oggiBtn());
    expect(screen.getByText('16–22 novembre')).toBeTruthy();
  });

  it('«oggi» con oggi = ultimo giorno del mese e passo mese porta a quel mese', async () => {
    render(Periodo, { inizio: '2027-01-01', passo: 'mese', oggi: '2026-11-30' });
    await fireEvent.click(screen.getByRole('button', { name: /Torna a questo mese|oggi/i }));
    expect(screen.getByText('Novembre 2026')).toBeTruthy();
  });

  it('«oggi» spento quando il periodo contiene già oggi, acceso altrimenti', () => {
    settimana({ inizio: '2026-11-16' });
    const b = oggiBtn() as HTMLButtonElement;
    expect(b.disabled || b.getAttribute('aria-disabled') === 'true').toBe(true);
    cleanup();
    settimana({ inizio: '2026-11-09' });
    const c = oggiBtn() as HTMLButtonElement;
    expect(c.disabled || c.getAttribute('aria-disabled') === 'true').toBe(false);
  });

  it('«oggi» è spento anche se oggi è il primo o l\'ultimo giorno del periodo', () => {
    for (const oggi of ['2026-11-16', '2026-11-22']) {
      render(Periodo, { inizio: '2026-11-16', passo: 'settimana', oggi });
      const b = oggiBtn() as HTMLButtonElement;
      expect(b.disabled || b.getAttribute('aria-disabled') === 'true').toBe(true);
      cleanup();
    }
    render(Periodo, { inizio: '2026-11-16', passo: 'settimana', oggi: '2026-11-23' });
    const b = oggiBtn() as HTMLButtonElement;
    expect(b.disabled || b.getAttribute('aria-disabled') === 'true').toBe(false);
  });

  it('con `max` la freccia avanti si ferma: oltre non si va', async () => {
    render(Periodo, { inizio: '2026-11-21', passo: 'giorno', oggi: '2026-11-21', max: '2026-11-21' });
    const avanti = screen.getByRole('button', { name: /Giorno successivo|Giorno seguente|Prossimo giorno/i }) as HTMLButtonElement;
    expect(avanti.disabled || avanti.getAttribute('aria-disabled') === 'true').toBe(true);
    await userEvent.click(avanti); // come un dito vero: su un pulsante spento non succede niente
    expect(screen.getByText('Sabato 21 novembre')).toBeTruthy();
  });

  it('con `max` si può ancora tornare indietro e poi avanti fino al limite', async () => {
    render(Periodo, { inizio: '2026-11-21', passo: 'giorno', oggi: '2026-11-21', max: '2026-11-21' });
    await fireEvent.click(screen.getByRole('button', { name: /Giorno precedente/i }));
    expect(screen.getByText('Venerdì 20 novembre')).toBeTruthy();
    await fireEvent.click(screen.getByRole('button', { name: /Giorno successivo|Giorno seguente|Prossimo giorno/i }));
    expect(screen.getByText('Sabato 21 novembre')).toBeTruthy();
  });

  it('mostra la riga `sotto` (es. «12 turni · 38 h») quando c\'è, e niente quando manca', () => {
    settimana({ sotto: '12 turni · 38 h' });
    expect(screen.getByText('12 turni · 38 h')).toBeTruthy();
    cleanup();
    settimana();
    expect(screen.queryByText(/turni/)).toBeNull();
  });

  describe('selettore nativo', () => {
    let apri: ReturnType<typeof vi.fn>;
    beforeEach(() => {
      apri = vi.fn();
      (HTMLInputElement.prototype as unknown as { showPicker: () => void }).showPicker = apri as unknown as () => void;
    });
    afterEach(() => {
      delete (HTMLInputElement.prototype as unknown as { showPicker?: () => void }).showPicker;
    });

    const scritta = (t: string) => screen.getByText(t).closest('button') as HTMLButtonElement;

    it('la scritta è un pulsante (non testo morto)', () => {
      settimana();
      expect(scritta('9–15 novembre')).toBeTruthy();
    });

    it('toccando la scritta si apre il selettore nativo di data (giorno e settimana)', async () => {
      settimana();
      await userEvent.click(scritta('9–15 novembre'));
      const campo = document.querySelector('input[type="date"]') as HTMLInputElement;
      expect(campo).toBeTruthy();
      expect(apri).toHaveBeenCalled();
    });

    it('con passo mese il selettore è quello di mese', async () => {
      render(Periodo, { inizio: '2026-11-01', passo: 'mese', oggi: '2026-11-21' });
      await userEvent.click(scritta('Novembre 2026'));
      expect(document.querySelector('input[type="month"]')).toBeTruthy();
      expect(document.querySelector('input[type="date"]')).toBeNull();
      expect(apri).toHaveBeenCalled();
    });

    it('scegliere una data nel selettore porta alla settimana che la contiene (anche a cavallo dell\'anno)', async () => {
      settimana();
      await userEvent.click(scritta('9–15 novembre'));
      const campo = document.querySelector('input[type="date"]') as HTMLInputElement;
      await fireEvent.input(campo, { target: { value: '2026-01-01' } });
      await fireEvent.change(campo, { target: { value: '2026-01-01' } });
      expect(screen.getByText('29 dicembre – 4 gennaio')).toBeTruthy();
    });

    it('scegliere un mese nel selettore porta a quel mese', async () => {
      render(Periodo, { inizio: '2026-11-01', passo: 'mese', oggi: '2026-11-21' });
      await userEvent.click(scritta('Novembre 2026'));
      const campo = document.querySelector('input[type="month"]') as HTMLInputElement;
      await fireEvent.input(campo, { target: { value: '2028-02' } });
      await fireEvent.change(campo, { target: { value: '2028-02' } });
      expect(screen.getByText('Febbraio 2028')).toBeTruthy();
    });

    it('il selettore parte dal periodo mostrato', async () => {
      settimana();
      await userEvent.click(scritta('9–15 novembre'));
      const campo = document.querySelector('input[type="date"]') as HTMLInputElement;
      expect(campo.value).toBe('2026-11-09');
    });

    it('un campo svuotato (annulla nel selettore) non cambia il periodo', async () => {
      settimana();
      await userEvent.click(scritta('9–15 novembre'));
      const campo = document.querySelector('input[type="date"]') as HTMLInputElement;
      await fireEvent.change(campo, { target: { value: '' } });
      expect(screen.getByText('9–15 novembre')).toBeTruthy();
    });
  });
});

describe('SceltaVista', () => {
  const tre = [
    { id: 'lista', nome: 'Lista' },
    { id: 'settimana', nome: 'Settimana' },
    { id: 'colonne', nome: 'A colonne' },
  ];
  const quattro = [...tre, { id: 'mese', nome: 'Mese' }];

  it('fino a 3 opzioni: un gruppo con un pulsante per opzione e niente tendina', () => {
    render(SceltaVista, { opzioni: tre, valore: 'lista', etichetta: 'Vista dei turni' });
    expect(screen.getByRole('group', { name: 'Vista dei turni' })).toBeTruthy();
    expect(screen.getAllByRole('button')).toHaveLength(3);
    expect(screen.queryByRole('combobox')).toBeNull();
  });

  it('2 opzioni: ancora un segmentato', () => {
    render(SceltaVista, { opzioni: tre.slice(0, 2), valore: 'lista', etichetta: 'Vista' });
    expect(screen.getAllByRole('button')).toHaveLength(2);
    expect(screen.queryByRole('combobox')).toBeNull();
  });

  it('più di 3 opzioni: una tendina con tutte le opzioni, niente segmentato', () => {
    render(SceltaVista, { opzioni: quattro, valore: 'mese', etichetta: 'Vista dei turni' });
    const sel = screen.getByRole('combobox', { name: 'Vista dei turni' }) as HTMLSelectElement;
    expect(within(sel).getAllByRole('option')).toHaveLength(4);
    expect(sel.value).toBe('mese');
    expect(screen.queryByRole('group')).toBeNull();
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });

  it('segmentato: solo il pulsante del valore è premuto (aria-pressed)', () => {
    render(SceltaVista, { opzioni: tre, valore: 'settimana', etichetta: 'Vista' });
    expect(screen.getByRole('button', { name: 'Lista' }).getAttribute('aria-pressed')).toBe('false');
    expect(screen.getByRole('button', { name: 'Settimana' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'A colonne' }).getAttribute('aria-pressed')).toBe('false');
  });

  it('segmentato: il clic sposta la scelta; ricliccare la stessa non la toglie', async () => {
    render(SceltaVista, { opzioni: tre, valore: 'lista', etichetta: 'Vista' });
    await userEvent.click(screen.getByRole('button', { name: 'A colonne' }));
    expect(screen.getByRole('button', { name: 'A colonne' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Lista' }).getAttribute('aria-pressed')).toBe('false');
    await userEvent.click(screen.getByRole('button', { name: 'A colonne' }));
    expect(screen.getByRole('button', { name: 'A colonne' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('segmentato: i pulsanti sono type="button" e si usano con la tastiera (Tab, Invio)', async () => {
    render(SceltaVista, { opzioni: tre, valore: 'lista', etichetta: 'Vista' });
    for (const b of screen.getAllByRole('button')) expect(b.getAttribute('type')).toBe('button');
    await userEvent.tab();
    await userEvent.tab();
    await userEvent.keyboard('{Enter}');
    const premuti = screen.getAllByRole('button').filter((b) => b.getAttribute('aria-pressed') === 'true');
    expect(premuti).toHaveLength(1);
  });

  it('tendina: scegliere un\'opzione cambia il valore', async () => {
    render(SceltaVista, { opzioni: quattro, valore: 'lista', etichetta: 'Vista' });
    const sel = screen.getByRole('combobox') as HTMLSelectElement;
    await userEvent.selectOptions(sel, 'mese');
    expect(sel.value).toBe('mese');
  });

  it('una vista che non c\'è tra le opzioni (non disponibile sul telefono) non compare', () => {
    render(SceltaVista, { opzioni: tre.slice(0, 2), valore: 'lista', etichetta: 'Vista' });
    expect(screen.queryByRole('button', { name: 'A colonne' })).toBeNull();
  });

  it('se il valore non è tra le opzioni si ricade su una valida: un solo pulsante premuto, tra quelli che ci sono', () => {
    render(SceltaVista, { opzioni: tre.slice(0, 2), valore: 'colonne', etichetta: 'Vista' });
    const premuti = screen.getAllByRole('button').filter((b) => b.getAttribute('aria-pressed') === 'true');
    expect(premuti).toHaveLength(1);
    expect(['Lista', 'Settimana']).toContain(premuti[0].textContent?.trim());
  });

  it('con `preferenza` la scelta si salva su questo dispositivo', async () => {
    render(SceltaVista, { opzioni: tre, valore: 'lista', etichetta: 'Vista', preferenza: 'turni.vista' });
    await userEvent.click(screen.getByRole('button', { name: 'Settimana' }));
    expect(leggiPreferenza('turni.vista', 'lista')).toBe('settimana');
  });

  it('con `preferenza` anche la tendina salva', async () => {
    render(SceltaVista, { opzioni: quattro, valore: 'lista', etichetta: 'Vista', preferenza: 'turni.vista' });
    await userEvent.selectOptions(screen.getByRole('combobox'), 'mese');
    expect(leggiPreferenza('turni.vista', 'lista')).toBe('mese');
  });

  it('senza `preferenza` non scrive niente nella memoria del browser', async () => {
    render(SceltaVista, { opzioni: tre, valore: 'lista', etichetta: 'Vista' });
    await userEvent.click(screen.getByRole('button', { name: 'Settimana' }));
    expect(localStorage.length).toBe(0);
  });
});

describe('BarraPagina', () => {
  const monta = (extra: Record<string, unknown> = {}) =>
    render(BarraPagina, {
      titolo: 'I miei turni',
      periodo: testo('PERIODO'),
      vista: testo('VISTA'),
      azioni: testo('AZIONI'),
      ...extra,
    });
  const el = (t: string) => screen.getByText(t);
  const prima = (a: Node, b: Node) => !!(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);

  it('il titolo è il titolo della pagina (h1)', () => {
    monta();
    expect(screen.getByRole('heading', { level: 1, name: 'I miei turni' })).toBeTruthy();
  });

  it('sopra il titolo (telefono) c\'è la riga piccola `sopra` quando la si passa', () => {
    monta({ sopra: 'Leone XIII Sport' });
    expect(prima(el('Leone XIII Sport'), screen.getByRole('heading', { level: 1 }))).toBe(true);
  });

  it('senza `sopra` non c\'è nessuna riga in più', () => {
    monta();
    expect(screen.queryByText('Leone XIII Sport')).toBeNull();
  });

  it('computer: titolo, poi periodo, vista, azioni, in questo ordine', () => {
    schermo.larga = true;
    monta();
    const h = screen.getByRole('heading', { level: 1 });
    expect(prima(h, el('PERIODO'))).toBe(true);
    expect(prima(el('PERIODO'), el('VISTA'))).toBe(true);
    expect(prima(el('VISTA'), el('AZIONI'))).toBe(true);
  });

  it('telefono: titolo e azioni sulla prima riga, periodo e vista sulla seconda', () => {
    schermo.larga = false;
    schermo.telefono = true;
    monta();
    const h = screen.getByRole('heading', { level: 1 });
    expect(prima(h, el('AZIONI'))).toBe(true);
    expect(prima(el('AZIONI'), el('PERIODO'))).toBe(true);
    expect(prima(el('PERIODO'), el('VISTA'))).toBe(true);
    // periodo e vista stanno in una riga a parte, che non contiene né il titolo né le azioni
    let nodo: HTMLElement | null = el('PERIODO');
    while (nodo && !nodo.contains(el('VISTA'))) nodo = nodo.parentElement;
    expect(nodo).toBeTruthy();
    expect(nodo!.contains(h)).toBe(false);
    expect(nodo!.contains(el('AZIONI'))).toBe(false);
  });

  it('senza periodo né vista (telefono) c\'è solo la prima riga: titolo e azioni', () => {
    schermo.larga = false;
    schermo.telefono = true;
    monta({ periodo: undefined, vista: undefined });
    expect(screen.getByRole('heading', { level: 1 })).toBeTruthy();
    expect(screen.getByText('AZIONI')).toBeTruthy();
    expect(screen.queryByText('PERIODO')).toBeNull();
    expect(screen.queryByText('VISTA')).toBeNull();
  });

  it('senza azioni il titolo si vede lo stesso, e viceversa', () => {
    monta({ azioni: undefined });
    expect(screen.getByRole('heading', { level: 1 })).toBeTruthy();
    expect(screen.queryByText('AZIONI')).toBeNull();
    cleanup();
    monta({ periodo: undefined, vista: undefined });
    expect(screen.getByText('AZIONI')).toBeTruthy();
  });

  it('solo il titolo: nessun errore e nessun controllo', () => {
    render(BarraPagina, { titolo: 'Incassi' });
    expect(screen.getByRole('heading', { level: 1, name: 'Incassi' })).toBeTruthy();
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });

  it('con Periodo e SceltaVista veri dentro la barra funzionano come da soli', async () => {
    const reale = createRawSnippet(() => ({ render: () => '<span data-prova="reale">x</span>' }));
    monta({ periodo: reale, vista: reale });
    expect(document.querySelectorAll('[data-prova="reale"]')).toHaveLength(2);
  });
});
