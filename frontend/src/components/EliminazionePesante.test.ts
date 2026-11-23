import { act, cleanup, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EliminazionePesante from './EliminazionePesante.svelte';
import { ErroreApi } from '../lib/api';

beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const CONSEGUENZE = ['38 persone escono dal gruppo', '1 dichiarazione da verificare si chiude'];
function monta(extra: Record<string, unknown> = {}) {
  const p = { titolo: 'Eliminare il gruppo «Agonisti»?', conseguenze: CONSEGUENZE, azione: 'Elimina il gruppo', onconferma: vi.fn(), onannulla: vi.fn(), ...extra };
  render(EliminazionePesante, { props: p as never });
  return p as typeof p & { onconferma: ReturnType<typeof vi.fn>; onannulla: ReturnType<typeof vi.fn> };
}
const avanza = (ms: number) => act(() => vi.advanceTimersByTimeAsync(ms));
const rosso = () => screen.getByRole('button', { name: /^Elimin/ });

describe('EliminazionePesante', () => {
  it('è una finestra di avviso modale, col titolo e le conseguenze una per riga', () => {
    monta();
    const f = screen.getByRole('alertdialog', { name: 'Eliminare il gruppo «Agonisti»?' });
    expect(f).toHaveAttribute('aria-modal', 'true');
    expect(screen.getAllByRole('listitem').map((l) => l.textContent)).toEqual(CONSEGUENZE);
    const d = f.getAttribute('aria-describedby')!;
    expect(document.getElementById(d)).toHaveTextContent('38 persone escono dal gruppo');
    expect(f.parentElement?.parentElement).toBe(document.body);
  });

  it('il fuoco parte su «Annulla»; Esc e Invio su «Annulla» chiudono senza eliminare', async () => {
    const p = monta();
    expect(screen.getByRole('button', { name: 'Annulla' })).toHaveFocus();
    await userEvent.keyboard('{Enter}');
    expect(p.onannulla).toHaveBeenCalledTimes(1);
    await userEvent.keyboard('{Escape}');
    expect(p.onannulla).toHaveBeenCalledTimes(2);
    expect(p.onconferma).not.toHaveBeenCalled();
  });

  it('il tocco sul velo equivale ad «Annulla»', async () => {
    const p = monta();
    await userEvent.click(document.querySelector('.velo')!);
    expect(p.onannulla).toHaveBeenCalled();
  });

  it('il pulsante rosso fa il conto alla rovescia e solo a 0 si può premere', async () => {
    const p = monta();
    expect(rosso()).toHaveTextContent('Elimina il gruppo (5)');
    expect(rosso()).toBeDisabled();
    await avanza(1000);
    expect(rosso()).toHaveTextContent('Elimina il gruppo (4)');
    await avanza(3000);
    expect(rosso()).toHaveTextContent('Elimina il gruppo (1)');
    expect(rosso()).toBeDisabled();
    await avanza(1000);
    expect(rosso()).toHaveTextContent(/^Elimina il gruppo$/);
    expect(rosso()).toBeEnabled();
    await userEvent.click(rosso());
    expect(p.onconferma).toHaveBeenCalledTimes(1);
  });

  it('abilitato diventa un pulsante rosso pieno; mentre conta no', async () => {
    monta();
    expect(rosso()).not.toHaveClass('pieno');
    await avanza(5000);
    expect(rosso()).toHaveClass('pieno');
  });

  it('dice «Elimino…» e resta disabilitato mentre la promessa lavora', async () => {
    let fine: () => void = () => {};
    const p = monta({ onconferma: vi.fn(() => new Promise<void>((r) => (fine = r))) });
    await avanza(5000);
    await userEvent.click(rosso());
    expect(screen.getByRole('button', { name: 'Elimino…' })).toBeDisabled();
    fine();
    expect(p.onconferma).toHaveBeenCalledTimes(1);
  });

  it('se la promessa fallisce la finestra resta aperta e mostra il messaggio su due righe', async () => {
    monta({ onconferma: vi.fn(() => Promise.reject(new ErroreApi(409, 'Non si può eliminare: il gruppo è cambiato.\nRiapri la finestra: i numeri sono aggiornati.'))) });
    await avanza(5000);
    await userEvent.click(rosso());
    const msg = await screen.findByRole('alert');
    expect(msg.querySelectorAll('.riga-b, span')).toHaveLength(2);
    expect(msg.querySelectorAll('b')[0]).toHaveTextContent('Non si può eliminare:');
    expect(msg.querySelectorAll('b')[1]).toHaveTextContent('Riapri la finestra:');
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    expect(rosso()).toBeEnabled();
  });

  it('mentre elimina, Annulla, Esc e il velo non chiudono la finestra', async () => {
    const p = monta({ onconferma: vi.fn(() => new Promise<void>(() => {})) });
    await avanza(5000);
    await userEvent.click(rosso());
    expect(screen.getByRole('button', { name: 'Annulla' })).toBeDisabled();
    await userEvent.keyboard('{Escape}');
    await userEvent.click(document.querySelector('.velo')!);
    expect(p.onannulla).not.toHaveBeenCalled();
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });

  it('il conto riparte a ogni apertura e si può cambiare con `secondi`', async () => {
    monta({ secondi: 2 });
    expect(rosso()).toHaveTextContent('(2)');
    await avanza(2000);
    expect(rosso()).toBeEnabled();
    cleanup();
    monta({ secondi: 2 });
    expect(rosso()).toHaveTextContent('(2)');
  });

  it('Tab gira dentro la finestra', async () => {
    monta();
    await avanza(5000);
    expect(screen.getByRole('button', { name: 'Annulla' })).toHaveFocus();
    await userEvent.tab();
    expect(rosso()).toHaveFocus();
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Annulla' })).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(rosso()).toHaveFocus();
  });

  it('alla chiusura il fuoco torna a chi l\'aveva aperta', () => {
    const apri = document.createElement('button');
    document.body.append(apri);
    apri.focus();
    monta();
    expect(apri).not.toHaveFocus();
    cleanup();
    expect(apri).toHaveFocus();
    apri.remove();
  });
});
