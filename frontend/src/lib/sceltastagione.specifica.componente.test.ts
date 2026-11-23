import { cleanup, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import SceltaStagione from '../components/SceltaStagione.svelte';
import type { Stagione } from './api';

/**
 * Specifica (brief coerenza 7, lotto 3, A1 «components/SceltaStagione.svelte»): una riga con una tendina col solo nome
 * («2026/27», in ordine di inizio) e a destra la pillola dello stato: «in corso» `.pill.passa`, «finita» `.pill`
 * (nessuna variante), «futura» `.pill.futura`. La tendina ha `aria-label` = `etichetta` (default «Stagione») e
 * `aria-describedby` verso la pillola. Senza stagioni non disegna niente. Con `valore` vuoto (o non presente) sceglie
 * la stagione iniziale e chiama `onchange`.
 * IPOTESI sulle props (il brief le fissa): `stagioni`, `valore` (bindable, id), `oggi` ('YYYY-MM-DD'), `etichetta`,
 * `onchange(id)`. Il testo della pillola è «in corso» / «finita» / «futura» (lo stato di `lib/stagioni.ts`).
 * Oggi di riferimento: 2026-11-23, quindi la 2026/27 è in corso, la 2025/26 finita e la 2027/28 futura.
 */
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const s = (id: string, nome: string, dal: string, al: string): Stagione => ({ id, nome, dal, al, quota: null, quotaRimborsabile: false });
// volutamente fuori ordine: il componente deve ordinarle
const stagioni = (): Stagione[] => [
  s('id27', '2026/27', '2026-09-01', '2027-08-31'),
  s('id28', '2027/28', '2027-09-01', '2028-08-31'),
  s('id25', '2025/26', '2025-09-01', '2026-08-31'),
];
const OGGI = '2026-11-23';

const tendina = () => screen.getByRole('combobox', { name: 'Stagione' }) as HTMLSelectElement;
/** La pillola collegata alla tendina con aria-describedby */
function pillola(): HTMLElement {
  const id = tendina().getAttribute('aria-describedby');
  expect(id, 'la tendina deve avere aria-describedby').toBeTruthy();
  const el = document.getElementById(id!);
  expect(el, 'aria-describedby deve puntare a un elemento esistente').not.toBeNull();
  return el as HTMLElement;
}
const monta = (extra: Record<string, unknown> = {}) => {
  const onchange = vi.fn();
  render(SceltaStagione, { props: { stagioni: stagioni(), valore: 'id27', oggi: OGGI, onchange, ...extra } as never });
  return { onchange };
};

describe('SceltaStagione – la tendina', () => {
  it('le opzioni portano il solo nome, in ordine di inizio', () => {
    monta();
    const opzioni = screen.getAllByRole('option').map((o) => o.textContent?.trim());
    expect(opzioni).toEqual(['2025/26', '2026/27', '2027/28']);
  });

  it('ha come nome accessibile «Stagione» e si può cambiare con `etichetta`', () => {
    monta({ etichetta: 'Stagione del listino' });
    expect(screen.getByRole('combobox', { name: 'Stagione del listino' })).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Stagione' })).toBeNull();
  });

  it('mostra la stagione di `valore`', () => {
    monta({ valore: 'id25' });
    expect(tendina().value).toBe('id25');
  });
});

describe('SceltaStagione – la pillola dello stato', () => {
  it('«in corso»: .pill.passa, e testo «in corso»', () => {
    monta({ valore: 'id27' });
    const p = pillola();
    expect(p).toHaveClass('pill', 'passa');
    expect(p).toHaveTextContent('in corso');
  });

  it('«finita»: .pill senza varianti, e testo «finita»', () => {
    monta({ valore: 'id25' });
    const p = pillola();
    expect(p).toHaveClass('pill');
    expect(p).not.toHaveClass('passa');
    expect(p).not.toHaveClass('futura');
    expect(p).toHaveTextContent('finita');
  });

  it('«futura»: .pill.futura, e testo «futura»', () => {
    monta({ valore: 'id28' });
    const p = pillola();
    expect(p).toHaveClass('pill', 'futura');
    expect(p).not.toHaveClass('passa');
    expect(p).toHaveTextContent('futura');
  });

  it('gli estremi contano: l\'ultimo giorno è ancora «in corso», il giorno dopo è «finita»', () => {
    monta({ valore: 'id27', oggi: '2027-08-31' });
    expect(pillola()).toHaveClass('passa');
    cleanup();
    monta({ valore: 'id27', oggi: '2027-09-01' });
    expect(pillola()).not.toHaveClass('passa');
    expect(pillola()).toHaveTextContent('finita');
  });

  it('senza `oggi` usa la data di oggi', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-23T12:00:00'));
    render(SceltaStagione, { props: { stagioni: stagioni(), valore: 'id27', onchange: vi.fn() } as never });
    expect(pillola()).toHaveClass('passa');
  });
});

describe('SceltaStagione – cambio della scelta', () => {
  it('scegliere un\'altra stagione chiama `onchange` con il suo id', async () => {
    const { onchange } = monta({ valore: 'id27' });
    await userEvent.selectOptions(tendina(), '2027/28');
    expect(onchange).toHaveBeenCalledWith('id28');
  });

  it('la pillola si aggiorna alla stagione scelta', async () => {
    monta({ valore: 'id27' });
    expect(pillola()).toHaveClass('passa');
    await userEvent.selectOptions(tendina(), '2027/28');
    expect(tendina().value).toBe('id28');
    expect(pillola()).toHaveClass('futura');
    expect(pillola()).toHaveTextContent('futura');
    await userEvent.selectOptions(tendina(), '2025/26');
    expect(pillola()).not.toHaveClass('futura');
    expect(pillola()).not.toHaveClass('passa');
    expect(pillola()).toHaveTextContent('finita');
  });

  it('con una `valore` valida non chiama `onchange` all\'apertura', () => {
    // Deduzione dal brief: `onchange` parte quando il componente sceglie al posto di chi l'ha montato, o quando si cambia.
    const { onchange } = monta({ valore: 'id25' });
    expect(onchange).not.toHaveBeenCalled();
  });
});

describe('SceltaStagione – scelta iniziale', () => {
  it('con `valore` vuoto sceglie la stagione in corso e chiama `onchange`', () => {
    const { onchange } = monta({ valore: '' });
    expect(tendina().value).toBe('id27');
    expect(onchange).toHaveBeenCalledWith('id27');
    expect(pillola()).toHaveClass('passa');
  });

  it('con `valore` assente si comporta come vuoto', () => {
    const onchange = vi.fn();
    render(SceltaStagione, { props: { stagioni: stagioni(), oggi: OGGI, onchange } as never });
    expect(tendina().value).toBe('id27');
    expect(onchange).toHaveBeenCalledWith('id27');
  });

  it('con un `valore` che non è tra le stagioni sceglie quella iniziale', () => {
    const { onchange } = monta({ valore: 'sparita' });
    expect(tendina().value).toBe('id27');
    expect(onchange).toHaveBeenCalledWith('id27');
  });

  it('nessuna in corso: la futura più vicina', () => {
    const { onchange } = monta({ valore: '', oggi: '2026-08-31', stagioni: [s('a', '2024/25', '2024-09-01', '2025-08-31'), s('b', '2026/27', '2026-09-01', '2027-08-31'), s('c', '2027/28', '2027-09-01', '2028-08-31')] });
    expect(onchange).toHaveBeenCalledWith('b');
    expect(pillola()).toHaveClass('futura');
  });

  it('solo stagioni finite: l\'ultima', () => {
    const { onchange } = monta({ valore: '', oggi: '2030-01-01' });
    expect(onchange).toHaveBeenCalledWith('id28');
    expect(pillola()).toHaveTextContent('finita');
  });
});

describe('SceltaStagione – nessuna stagione', () => {
  it('con l\'elenco vuoto non disegna niente: né tendina né pillola', () => {
    const { container } = render(SceltaStagione, { props: { stagioni: [], valore: '', oggi: OGGI, onchange: vi.fn() } as never });
    expect(screen.queryByRole('combobox')).toBeNull();
    expect(screen.queryByRole('option')).toBeNull();
    expect(container.querySelector('.pill')).toBeNull();
    expect(container.textContent?.trim()).toBe('');
  });

  it('con l\'elenco vuoto non chiama `onchange`', () => {
    const onchange = vi.fn();
    render(SceltaStagione, { props: { stagioni: [], valore: '', oggi: OGGI, onchange } as never });
    expect(onchange).not.toHaveBeenCalled();
  });
});
