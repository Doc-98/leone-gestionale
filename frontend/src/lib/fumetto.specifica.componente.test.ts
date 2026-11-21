import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import { createRawSnippet } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Fumetto from '../components/Fumetto.svelte';
import { fumetti } from './fumetto.svelte';

/**
 * Specifica §2.2 e design system «Fumetto «i»» (modello «toggletip»):
 * - accanto alla «i», nello stesso contenitore, c'è sempre una regione role="status" visivamente nascosta: vuota da
 *   chiuso, col testo da aperto (inserito subito dopo l'apertura), vuota di nuovo alla chiusura. La legge lo screen reader;
 * - la bolla visibile è `.bolla`, portata in <body>, aria-hidden="true", senza role; esiste solo da aperto; il suo id è
 *   quello di aria-controls della «i»;
 * - Esc: se aperto con clic/tastiera, o se il focus è sulla «i» o nella bolla, chiude e NON arriva agli ascoltatori in
 *   bubbling; se aperto solo dal passaggio del mouse, chiude e passa anche agli altri;
 * - allo smontaggio di un fumetto aperto `fumetti.aperto` torna null; smontare uno chiuso non chiude quello di un altro.
 * La geometria si prova in `posiziona.specifica.test.ts`: jsdom non misura.
 */
const testo = (t: string) =>
  createRawSnippet(() => ({ render: () => `<span>${t}</span>` }));

function monta(tema = 'le rate', t = 'Spiegazione delle rate.') {
  return render(Fumetto, { tema, children: testo(t) });
}
const pulsante = (tema = 'le rate') => screen.getByRole('button', { name: `Spiega ${tema}` }) as HTMLButtonElement;
/** La regione status del fumetto: nello stesso contenitore della «i». */
const regione = (tema = 'le rate') =>
  pulsante(tema).parentElement!.querySelector('[role="status"]') as HTMLElement | null;
const bolle = () => Array.from(document.querySelectorAll('.bolla')) as HTMLElement[];
const bolla = () => bolle()[0] ?? null;
const lettoDa = (tema = 'le rate') => regione(tema)?.textContent?.trim() ?? '';

/** Il testo entra nella regione subito dopo l'apertura, non per forza nello stesso istante. */
const letto = (t: string, tema = 'le rate') =>
  waitFor(() => expect(lettoDa(tema)).toContain(t), { timeout: 2000 });
/** La bolla può uscire con un'animazione: la specifica chiede che alla fine non ci sia più, non quanto ci mette. */
const scompare = () =>
  waitFor(() => { expect(bolle()).toHaveLength(0); }, { timeout: 2000 });
const una = () => waitFor(() => expect(bolle()).toHaveLength(1), { timeout: 2000 });
/** Come `scompare`, ma col tempo finto (gruppo del passaggio del mouse). */
const scompareFinto = async () => {
  await vi.advanceTimersByTimeAsync(1000);
  expect(bolle()).toHaveLength(0);
};

/** Lo stesso che fa un dito: tutti gli eventi di un tocco, nell'ordine. */
async function tocca(el: Element) {
  await fireEvent.pointerDown(el);
  await fireEvent.mouseDown(el);
  await fireEvent.pointerUp(el);
  await fireEvent.mouseUp(el);
  await fireEvent.click(el);
}

function fingiHover(si: boolean) {
  vi.stubGlobal('matchMedia', (q: string) => ({
    matches: si && q.includes('hover: hover'),
    media: q,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    onchange: null,
    dispatchEvent: () => false,
  }));
}

async function passaSopra(el: Element) {
  await fireEvent.pointerEnter(el);
  await fireEvent.mouseEnter(el);
  await fireEvent.mouseOver(el);
}
async function esci(el: Element) {
  await fireEvent.pointerLeave(el);
  await fireEvent.mouseLeave(el);
  await fireEvent.mouseOut(el);
}

/** Ascoltatori «di sotto» (come un Foglio): vedono Esc solo se nessuno lo ferma. */
function ascoltaEsc() {
  const visti = vi.fn();
  const f = (e: Event) => { if ((e as KeyboardEvent).key === 'Escape') visti(); };
  document.addEventListener('keydown', f);
  window.addEventListener('keydown', f);
  return { visti, fine: () => { document.removeEventListener('keydown', f); window.removeEventListener('keydown', f); } };
}

beforeEach(() => {
  fumetti.aperto = null;
  fingiHover(false);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  fumetti.aperto = null;
});

describe('Fumetto – il pulsante «i» e la regione status', () => {
  it('ha l\'etichetta «Spiega <tema>», che segue il tema', () => {
    monta('il saldo');
    expect(pulsante('il saldo')).toBeTruthy();
  });

  it('è un <button type="button"> vero, raggiungibile col Tab (Invio e spazio sono quelli del browser)', () => {
    monta();
    const b = pulsante();
    expect(b.tagName).toBe('BUTTON');
    expect(b.getAttribute('type')).toBe('button');
    expect(b.disabled).toBe(false);
    expect(b.tabIndex).toBeGreaterThanOrEqual(0);
  });

  it('il nome è solo l\'etichetta: l\'icona non ha testo', () => {
    monta();
    expect(pulsante().textContent?.trim()).toBe('');
  });

  it('chiusa: aria-expanded="false", nessuna .bolla, regione status presente ma vuota', () => {
    monta();
    expect(pulsante().getAttribute('aria-expanded')).toBe('false');
    expect(bolle()).toHaveLength(0);
    expect(regione()).not.toBeNull();
    expect(lettoDa()).toBe('');
    expect(screen.queryByText('Spiegazione delle rate.')).toBeNull();
  });

  it('la regione status sta nello stesso contenitore della «i», non in <body>', () => {
    const { container } = monta();
    expect(container.contains(regione())).toBe(true);
    expect(regione()!.parentElement).not.toBe(document.body);
  });

  it('la regione non è nascosta agli screen reader (niente aria-hidden, niente display:none/hidden)', () => {
    monta();
    const r = regione()!;
    expect(r.getAttribute('aria-hidden')).not.toBe('true');
    expect(r.hasAttribute('hidden')).toBe(false);
    expect(r.closest('[aria-hidden="true"]')).toBeNull();
  });

  it('aprendo, la regione status riceve il testo', async () => {
    monta();
    await fireEvent.click(pulsante());
    await letto('Spiegazione delle rate.');
  });

  it('chiudendo, la regione torna vuota', async () => {
    monta();
    await fireEvent.click(pulsante());
    await letto('Spiegazione delle rate.');
    await fireEvent.click(pulsante());
    await waitFor(() => expect(lettoDa()).toBe(''), { timeout: 2000 });
    expect(regione()).not.toBeNull(); // la regione resta: è sempre presente
  });

  it('riaprendo, il testo rientra nella regione', async () => {
    monta();
    await fireEvent.click(pulsante());
    await fireEvent.click(pulsante());
    await waitFor(() => expect(lettoDa()).toBe(''), { timeout: 2000 });
    await fireEvent.click(pulsante());
    await letto('Spiegazione delle rate.');
  });
});

describe('Fumetto – apertura e bolla visibile', () => {
  it('il clic apre: aria-expanded="true" e compare una sola .bolla col testo', async () => {
    monta();
    await fireEvent.click(pulsante());
    expect(pulsante().getAttribute('aria-expanded')).toBe('true');
    expect(bolle()).toHaveLength(1);
    expect(bolla().textContent).toContain('Spiegazione delle rate.');
  });

  it('la bolla ha aria-hidden="true" e nessun role (la legge la regione, non la bolla)', async () => {
    monta();
    await fireEvent.click(pulsante());
    expect(bolla().getAttribute('aria-hidden')).toBe('true');
    expect(bolla().hasAttribute('role')).toBe(false);
    expect(bolla().querySelector('[role]')).toBeNull();
  });

  it('aria-controls della «i» punta all\'id della bolla', async () => {
    monta();
    await fireEvent.click(pulsante());
    const id = pulsante().getAttribute('aria-controls');
    expect(id).toBeTruthy();
    expect(document.getElementById(id!)).toBe(bolla());
  });

  it('la bolla non è collegata con aria-describedby', async () => {
    monta();
    await fireEvent.click(pulsante());
    expect(pulsante().getAttribute('aria-describedby')).toBeNull();
  });

  it('la bolla è portata in <body>, fuori dalla card (niente overflow: hidden che la tagli)', async () => {
    const { container } = monta();
    await fireEvent.click(pulsante());
    expect(bolla().parentElement).toBe(document.body);
    expect(container.contains(bolla())).toBe(false);
  });

  it('un tocco completo (pointer, mouse, clic) lascia il fumetto aperto', async () => {
    monta();
    await tocca(pulsante());
    expect(pulsante().getAttribute('aria-expanded')).toBe('true');
    expect(bolle()).toHaveLength(1);
  });

  it('il focus resta sulla «i» dopo l\'apertura', async () => {
    monta();
    const b = pulsante();
    b.focus();
    await fireEvent.click(b);
    expect(document.activeElement).toBe(b);
  });

  it('un secondo clic sulla «i» chiude: aria-expanded false, bolla sparita', async () => {
    monta();
    await fireEvent.click(pulsante());
    await fireEvent.click(pulsante());
    expect(pulsante().getAttribute('aria-expanded')).toBe('false');
    await scompare();
  });

  it('riaprendo dopo la chiusura la bolla torna', async () => {
    monta();
    await fireEvent.click(pulsante());
    await fireEvent.click(pulsante());
    await scompare();
    await fireEvent.click(pulsante());
    expect(bolla().textContent).toContain('Spiegazione delle rate.');
  });
});

describe('Fumetto – chiusura', () => {
  it('Esc chiude (bolla e regione) e il focus resta sulla «i»', async () => {
    monta();
    const b = pulsante();
    b.focus();
    await fireEvent.click(b);
    await fireEvent.keyDown(b, { key: 'Escape' });
    expect(b.getAttribute('aria-expanded')).toBe('false');
    await scompare();
    await waitFor(() => expect(lettoDa()).toBe(''), { timeout: 2000 });
    expect(document.activeElement).toBe(b);
  });

  it('Esc premuto con il focus altrove (sul documento) chiude un fumetto aperto col clic', async () => {
    monta();
    await fireEvent.click(pulsante());
    await fireEvent.keyDown(document.body, { key: 'Escape' });
    await scompare();
  });

  it('Esc con il fumetto chiuso non fa niente e passa agli altri', async () => {
    monta();
    const { visti, fine } = ascoltaEsc();
    await fireEvent.keyDown(document.body, { key: 'Escape' });
    fine();
    expect(bolle()).toHaveLength(0);
    expect(pulsante().getAttribute('aria-expanded')).toBe('false');
    expect(visti).toHaveBeenCalled();
  });

  it('un altro tasto non chiude', async () => {
    monta();
    await fireEvent.click(pulsante());
    await fireEvent.keyDown(document.body, { key: 'a' });
    expect(bolle()).toHaveLength(1);
  });

  it('un tocco fuori (sul corpo della pagina) chiude', async () => {
    monta();
    await fireEvent.click(pulsante());
    await tocca(document.body);
    expect(pulsante().getAttribute('aria-expanded')).toBe('false');
    await scompare();
  });

  it('un tocco su un altro elemento della pagina chiude', async () => {
    const { container } = monta();
    const altro = document.createElement('p');
    altro.textContent = 'altrove';
    container.appendChild(altro);
    await fireEvent.click(pulsante());
    await tocca(altro);
    await scompare();
  });

  it('un tocco dentro la bolla non chiude (si può selezionare il testo)', async () => {
    monta();
    await fireEvent.click(pulsante());
    await tocca(bolla());
    expect(bolle()).toHaveLength(1);
    expect(pulsante().getAttribute('aria-expanded')).toBe('true');
  });
});

describe('Fumetto – Esc non arriva a chi sta sotto (un Foglio resta aperto)', () => {
  it('aperto col clic, Esc sulla «i» chiude e non arriva agli ascoltatori in bubbling', async () => {
    monta();
    const b = pulsante();
    b.focus();
    await fireEvent.click(b);
    const { visti, fine } = ascoltaEsc();
    await fireEvent.keyDown(b, { key: 'Escape' });
    fine();
    expect(visti).not.toHaveBeenCalled();
    await scompare();
  });

  it('aperto col clic, Esc con il focus fuori dalla «i» chiude e non arriva agli altri', async () => {
    monta();
    await fireEvent.click(pulsante());
    pulsante().blur();
    const { visti, fine } = ascoltaEsc();
    await fireEvent.keyDown(document.body, { key: 'Escape' });
    fine();
    expect(visti).not.toHaveBeenCalled();
    await scompare();
  });

  it('aperto con la tastiera (Invio sul pulsante → clic), Esc non arriva agli altri', async () => {
    monta();
    const b = pulsante();
    b.focus();
    await fireEvent.keyDown(b, { key: 'Enter' });
    await fireEvent.click(b); // il browser trasforma Invio/spazio in clic su un <button>
    const { visti, fine } = ascoltaEsc();
    await fireEvent.keyDown(b, { key: 'Escape' });
    fine();
    expect(visti).not.toHaveBeenCalled();
    await scompare();
  });

  it('con il fumetto chiuso Esc passa agli altri (chiude il Foglio, non il fumetto)', async () => {
    monta();
    const { visti, fine } = ascoltaEsc();
    await fireEvent.keyDown(pulsante(), { key: 'Escape' });
    fine();
    expect(visti).toHaveBeenCalled();
  });

  it('un solo Esc basta per il fumetto: il secondo passa agli altri', async () => {
    monta();
    await fireEvent.click(pulsante());
    await fireEvent.keyDown(pulsante(), { key: 'Escape' });
    await scompare();
    const { visti, fine } = ascoltaEsc();
    await fireEvent.keyDown(pulsante(), { key: 'Escape' });
    fine();
    expect(visti).toHaveBeenCalled();
  });
});

describe('Fumetto – smontaggio', () => {
  it('smontando da aperto la bolla non resta in <body>', async () => {
    const { unmount } = monta();
    await fireEvent.click(pulsante());
    unmount();
    await scompare();
  });

  it('smontando da aperto `fumetti.aperto` torna null', async () => {
    const { unmount } = monta();
    await fireEvent.click(pulsante());
    expect(fumetti.aperto).not.toBeNull();
    unmount();
    expect(fumetti.aperto).toBeNull();
  });

  it('smontare un fumetto chiuso non chiude quello aperto di un altro', async () => {
    const a = render(Fumetto, { tema: 'le rate', children: testo('Prima.') });
    const b = render(Fumetto, { tema: 'il saldo', children: testo('Seconda.') });
    await fireEvent.click(pulsante('le rate'));
    const aperto = fumetti.aperto;
    expect(aperto).not.toBeNull();
    b.unmount();
    expect(fumetti.aperto).toBe(aperto);
    expect(pulsante('le rate').getAttribute('aria-expanded')).toBe('true');
    expect(bolle()).toHaveLength(1);
    a.unmount();
  });
});

describe('Fumetto – uno solo aperto', () => {
  function duePulsanti() {
    render(Fumetto, { tema: 'le rate', children: testo('Prima spiegazione.') });
    render(Fumetto, { tema: 'il saldo', children: testo('Seconda spiegazione.') });
  }

  it('con due fumetti in pagina ci sono due regioni status (una per fumetto), tutte e due vuote da chiusi', () => {
    duePulsanti();
    expect(screen.getAllByRole('status')).toHaveLength(2);
    expect(lettoDa('le rate')).toBe('');
    expect(lettoDa('il saldo')).toBe('');
    expect(bolle()).toHaveLength(0);
  });

  it('aprire il secondo chiude il primo: una sola .bolla, regione piena solo per il secondo', async () => {
    duePulsanti();
    await fireEvent.click(pulsante('le rate'));
    await fireEvent.click(pulsante('il saldo'));
    expect(pulsante('le rate').getAttribute('aria-expanded')).toBe('false');
    expect(pulsante('il saldo').getAttribute('aria-expanded')).toBe('true');
    await una();
    expect(bolla().textContent).toContain('Seconda spiegazione.');
    await letto('Seconda spiegazione.', 'il saldo');
    await waitFor(() => expect(lettoDa('le rate')).toBe(''), { timeout: 2000 });
    expect(screen.getAllByRole('status')).toHaveLength(2);
  });

  it('e viceversa', async () => {
    duePulsanti();
    await fireEvent.click(pulsante('il saldo'));
    await fireEvent.click(pulsante('le rate'));
    await una();
    expect(bolla().textContent).toContain('Prima spiegazione.');
    await letto('Prima spiegazione.', 'le rate');
    await waitFor(() => expect(lettoDa('il saldo')).toBe(''), { timeout: 2000 });
  });

  it('chiudere il secondo non riapre il primo', async () => {
    duePulsanti();
    await fireEvent.click(pulsante('le rate'));
    await fireEvent.click(pulsante('il saldo'));
    await fireEvent.click(pulsante('il saldo'));
    await scompare();
    expect(lettoDa('le rate')).toBe('');
  });

  it('un tocco fuori chiude quello aperto e non ne apre altri', async () => {
    duePulsanti();
    await fireEvent.click(pulsante('le rate'));
    await tocca(document.body);
    await scompare();
    expect(fumetti.aperto).toBeNull();
  });

  it('`fumetti.aperto` vale null quando nessuno è aperto e non è null quando uno lo è', async () => {
    duePulsanti();
    expect(fumetti.aperto).toBeNull();
    await fireEvent.click(pulsante('le rate'));
    expect(fumetti.aperto).not.toBeNull();
    await fireEvent.click(pulsante('le rate'));
    expect(fumetti.aperto).toBeNull();
  });

  it('due fumetti con lo stesso tema hanno id diversi (bolla e aria-controls)', async () => {
    render(Fumetto, { tema: 'le rate', children: testo('Uno.') });
    render(Fumetto, { tema: 'le rate', children: testo('Due.') });
    const [a, b] = screen.getAllByRole('button', { name: 'Spiega le rate' });
    expect(a.getAttribute('aria-controls')).not.toBe(b.getAttribute('aria-controls'));
    await fireEvent.click(a);
    await fireEvent.click(b);
    await una();
    expect(bolla().textContent).toContain('Due.');
    expect(bolla().id).toBe(b.getAttribute('aria-controls'));
  });
});

describe('Fumetto – passaggio del mouse (solo con hover e puntatore preciso)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  it('con hover: il passaggio apre', async () => {
    fingiHover(true);
    monta();
    await passaSopra(pulsante());
    expect(bolle()).toHaveLength(1);
  });

  it('con hover: il passaggio riempie anche la regione status', async () => {
    fingiHover(true);
    monta();
    await passaSopra(pulsante());
    await vi.advanceTimersByTimeAsync(100);
    expect(lettoDa()).toContain('Spiegazione delle rate.');
  });

  it('senza hover (touch che emula il mouse): il passaggio non apre', async () => {
    fingiHover(false);
    monta();
    await passaSopra(pulsante());
    expect(bolle()).toHaveLength(0);
    expect(lettoDa()).toBe('');
    expect(pulsante().getAttribute('aria-expanded')).toBe('false');
  });

  it('aperto dal passaggio, uscendo si chiude dopo 150 ms (non prima)', async () => {
    fingiHover(true);
    monta();
    await passaSopra(pulsante());
    await esci(pulsante());
    await vi.advanceTimersByTimeAsync(100);
    expect(bolle()).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(100);
    await scompareFinto();
  });

  it('entrando nella bolla entro i 150 ms resta aperto', async () => {
    fingiHover(true);
    monta();
    await passaSopra(pulsante());
    await esci(pulsante());
    await vi.advanceTimersByTimeAsync(50);
    await passaSopra(bolla());
    await vi.advanceTimersByTimeAsync(1000);
    expect(bolle()).toHaveLength(1);
  });

  it('uscendo dalla bolla si chiude', async () => {
    fingiHover(true);
    monta();
    await passaSopra(pulsante());
    await esci(pulsante());
    await vi.advanceTimersByTimeAsync(50);
    const b = bolla();
    await passaSopra(b);
    await esci(b);
    await scompareFinto();
  });

  it('aperto dal clic, uscire col mouse non chiude', async () => {
    fingiHover(true);
    monta();
    await fireEvent.click(pulsante());
    await esci(pulsante());
    await vi.advanceTimersByTimeAsync(1000);
    expect(bolle()).toHaveLength(1);
  });

  it('aperto dal passaggio e poi cliccato, resta aperto anche uscendo (diventa «aperto dal clic»)', async () => {
    fingiHover(true);
    monta();
    await passaSopra(pulsante());
    await fireEvent.click(pulsante());
    await esci(pulsante());
    await vi.advanceTimersByTimeAsync(1000);
    expect(bolle()).toHaveLength(1);
  });

  it('il passaggio su un secondo fumetto chiude il primo (una sola .bolla)', async () => {
    fingiHover(true);
    render(Fumetto, { tema: 'le rate', children: testo('Prima.') });
    render(Fumetto, { tema: 'il saldo', children: testo('Seconda.') });
    await passaSopra(pulsante('le rate'));
    await passaSopra(pulsante('il saldo'));
    await vi.advanceTimersByTimeAsync(1000);
    expect(bolle()).toHaveLength(1);
    expect(bolla().textContent).toContain('Seconda.');
  });

  describe('Esc su un fumetto aperto solo dal passaggio', () => {
    it('senza focus sulla «i»: chiude e passa anche agli altri (un Foglio sotto si chiude)', async () => {
      fingiHover(true);
      monta();
      await passaSopra(pulsante());
      const { visti, fine } = ascoltaEsc();
      await fireEvent.keyDown(document.body, { key: 'Escape' });
      fine();
      await scompareFinto();
      expect(visti).toHaveBeenCalled();
    });

    it('con il focus sulla «i»: chiude e NON passa agli altri', async () => {
      fingiHover(true);
      monta();
      pulsante().focus();
      await passaSopra(pulsante());
      const { visti, fine } = ascoltaEsc();
      await fireEvent.keyDown(pulsante(), { key: 'Escape' });
      fine();
      await scompareFinto();
      expect(visti).not.toHaveBeenCalled();
    });

    it('aperto dal passaggio e poi cliccato (aperto dal clic): Esc non passa agli altri', async () => {
      fingiHover(true);
      monta();
      await passaSopra(pulsante());
      await fireEvent.click(pulsante());
      const { visti, fine } = ascoltaEsc();
      await fireEvent.keyDown(document.body, { key: 'Escape' });
      fine();
      await scompareFinto();
      expect(visti).not.toHaveBeenCalled();
    });
  });
});
