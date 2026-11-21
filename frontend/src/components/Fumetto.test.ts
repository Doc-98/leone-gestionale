import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { createRawSnippet } from 'svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fumetti } from '../lib/fumetto.svelte';
import Foglio from './Foglio.svelte';
import Fumetto from './Fumetto.svelte';

/**
 * Toggletip: accanto alla «i» c'è sempre una regione `status` (vuota da chiusa) in cui entra il testo all'apertura;
 * la bolla visibile (`.bolla`) è aria-hidden. Qui tastiera vera, focus, fogli, registro e annuncio.
 */
const testo = (t: string) => createRawSnippet(() => ({ render: () => `<span>${t}</span>` }));
const iDi = (tema: string) => screen.getByRole('button', { name: `Spiega ${tema}` });
const annunciato = () => screen.getByRole('status');
const bolla = () => document.querySelector('.bolla');

function fingiHover(si: boolean) {
  vi.stubGlobal('matchMedia', (q: string) => ({ matches: si && q.includes('hover: hover'), media: q, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {}, onchange: null, dispatchEvent: () => false }));
}

beforeEach(() => {
  fumetti.chiudi();
  fingiHover(false);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('Fumetto – lettore di schermo (toggletip)', () => {
  it('chiuso: la regione status c\'è ma è vuota, e non c\'è bolla', () => {
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    expect(annunciato()).toBeEmptyDOMElement();
    expect(bolla()).toBeNull();
  });

  it('aperto: il testo entra nella regione status; la bolla visibile è aria-hidden e non ha role', async () => {
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    await fireEvent.click(iDi('le rate'));
    await vi.waitFor(() => expect(annunciato()).toHaveTextContent('Spiegazione.'));
    expect(bolla()).toHaveAttribute('aria-hidden', 'true');
    expect(bolla()).not.toHaveAttribute('role');
  });

  it('la regione status resta lo stesso elemento: da aperto a chiuso si svuota', async () => {
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    const regione = annunciato();
    await fireEvent.click(iDi('le rate'));
    await vi.waitFor(() => expect(regione).toHaveTextContent('Spiegazione.'));
    await fireEvent.click(iDi('le rate'));
    await vi.waitFor(() => expect(regione).toBeEmptyDOMElement());
    expect(annunciato()).toBe(regione);
  });

  it('la regione sta accanto alla «i», non in <body>', () => {
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    expect(annunciato().parentElement).toBe(iDi('le rate').parentElement);
  });
});

describe('Fumetto – tastiera', () => {
  it('Invio sulla «i» apre e il focus resta lì', async () => {
    const u = userEvent.setup();
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    await u.tab();
    expect(iDi('le rate')).toHaveFocus();
    await u.keyboard('{Enter}');
    expect(iDi('le rate')).toHaveAttribute('aria-expanded', 'true');
    expect(bolla()).toHaveTextContent('Spiegazione.');
    expect(iDi('le rate')).toHaveFocus();
  });

  it('la barra spaziatrice apre e Invio richiude', async () => {
    const u = userEvent.setup();
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    await u.tab();
    await u.keyboard(' ');
    expect(bolla()).not.toBeNull();
    await u.keyboard('{Enter}');
    expect(bolla()).toBeNull();
  });

  it('Esc chiude e il focus resta sulla «i»', async () => {
    const u = userEvent.setup();
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    await u.tab();
    await u.keyboard('{Enter}{Escape}');
    expect(bolla()).toBeNull();
    expect(iDi('le rate')).toHaveFocus();
  });

  it('Tab verso un altro controllo chiude il fumetto', async () => {
    const u = userEvent.setup();
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    const dopo = document.createElement('button');
    dopo.textContent = 'Dopo';
    document.body.appendChild(dopo);
    await u.tab();
    await u.keyboard('{Enter}');
    await u.tab();
    expect(dopo).toHaveFocus();
    expect(bolla()).toBeNull();
    dopo.remove();
  });
});

describe('Fumetto – tocco, uno solo aperto, registro', () => {
  it('tocco fuori chiude, tocco sulla bolla no', async () => {
    const u = userEvent.setup();
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    await u.click(iDi('le rate'));
    await u.click(bolla()!);
    expect(bolla()).not.toBeNull();
    await u.click(document.body);
    expect(bolla()).toBeNull();
  });

  it('con due fumetti ne resta aperto uno solo, anche con la tastiera', async () => {
    const u = userEvent.setup();
    render(Fumetto, { props: { tema: 'le rate', children: testo('Prima.') } });
    render(Fumetto, { props: { tema: 'il saldo', children: testo('Seconda.') } });
    await u.tab();
    await u.keyboard('{Enter}');
    await u.tab();
    await u.keyboard('{Enter}');
    expect(document.querySelectorAll('.bolla')).toHaveLength(1);
    expect(bolla()).toHaveTextContent('Seconda.');
  });

  it('smontare il componente da aperto toglie la bolla e libera il registro', async () => {
    const { unmount } = render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') } });
    await fireEvent.click(iDi('le rate'));
    expect(fumetti.aperto).not.toBeNull();
    unmount();
    expect(bolla()).toBeNull();
    expect(fumetti.aperto).toBeNull();
  });

  it('smontare un fumetto chiuso non chiude quello aperto di un altro', async () => {
    const a = render(Fumetto, { props: { tema: 'le rate', children: testo('Prima.') } });
    render(Fumetto, { props: { tema: 'il saldo', children: testo('Seconda.') } });
    await fireEvent.click(iDi('il saldo'));
    a.unmount();
    expect(bolla()).toHaveTextContent('Seconda.');
  });
});

describe('Fumetto – dentro un foglio', () => {
  const figli = createRawSnippet(() => ({ render: () => '<div id="dentro"></div>' }));
  const monta = (chiudi: () => void) => {
    render(Foglio, { props: { titolo: 'Prova', chiudi, children: figli } });
    render(Fumetto, { props: { tema: 'le rate', children: testo('Spiegazione.') }, target: document.getElementById('dentro')! });
  };

  it('aperto con clic: Esc chiude prima il fumetto, il foglio resta; al secondo Esc si chiude', async () => {
    const u = userEvent.setup();
    const chiudi = vi.fn();
    monta(chiudi);
    await u.click(iDi('le rate'));
    await u.keyboard('{Escape}');
    expect(bolla()).toBeNull();
    expect(chiudi).not.toHaveBeenCalled();
    await u.keyboard('{Escape}');
    expect(chiudi).toHaveBeenCalledTimes(1);
  });

  it('aperto solo dal passaggio del mouse: Esc chiude il fumetto e passa anche al foglio', async () => {
    fingiHover(true);
    const chiudi = vi.fn();
    monta(chiudi);
    await fireEvent.pointerEnter(iDi('le rate'));
    expect(bolla()).not.toBeNull();
    await fireEvent.keyDown(document.body, { key: 'Escape' });
    expect(bolla()).toBeNull();
    expect(chiudi).toHaveBeenCalledTimes(1);
  });

  it('aperto dal passaggio ma con il focus sulla «i»: Esc non arriva al foglio', async () => {
    fingiHover(true);
    const chiudi = vi.fn();
    monta(chiudi);
    iDi('le rate').focus();
    await fireEvent.pointerEnter(iDi('le rate'));
    await fireEvent.keyDown(iDi('le rate'), { key: 'Escape' });
    expect(bolla()).toBeNull();
    expect(chiudi).not.toHaveBeenCalled();
  });

  it('la bolla sta in <body>, fuori dal foglio (con z-index sopra)', async () => {
    monta(() => {});
    await fireEvent.click(iDi('le rate'));
    expect(screen.getByRole('dialog').contains(bolla())).toBe(false);
    expect(bolla()!.parentElement).toBe(document.body);
  });
});
