import { cleanup, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import BarraSalva from '../components/BarraSalva.svelte';

/**
 * Specifica (brief coerenza blocco 7, lotto 1, «Barra di salvataggio unica»): il componente compare solo quando ci
 * sono modifiche; dice «1 modifica da salvare» / «N modifiche da salvare»; ha «Annulla» e «Salva» (proprio «Salva», non
 * «Salva le modifiche»); «Salva» è disabilitato se la bozza non è valida.
 * IPOTESI sulle props (il brief non le fissa): `modifiche` (numero), `valida` (booleano), `onannulla`, `onsalva`.
 */
afterEach(cleanup);

const base = { modifiche: 1, valida: true, onannulla: () => {}, onsalva: () => {} };

describe('BarraSalva – quando compare', () => {
  it('senza modifiche (0) non compare: né testo né pulsanti', () => {
    render(BarraSalva, { ...base, modifiche: 0 });
    expect(screen.queryByText(/da salvare/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Annulla' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Salva' })).toBeNull();
  });

  it('con una modifica compare con i due pulsanti', () => {
    render(BarraSalva, { ...base, modifiche: 1 });
    expect(screen.getByRole('button', { name: 'Annulla' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Salva' })).toBeVisible();
  });
});

describe('BarraSalva – testo', () => {
  it('1 modifica: «1 modifica da salvare»', () => {
    render(BarraSalva, { ...base, modifiche: 1 });
    expect(screen.getByText('1 modifica da salvare')).toBeVisible();
  });

  it('3 modifiche: «3 modifiche da salvare»', () => {
    render(BarraSalva, { ...base, modifiche: 3 });
    expect(screen.getByText('3 modifiche da salvare')).toBeVisible();
    expect(screen.queryByText('3 modifica da salvare')).toBeNull();
  });

  it('il testo segue il numero quando cambia', async () => {
    const { rerender } = render(BarraSalva, { ...base, modifiche: 2 });
    expect(screen.getByText('2 modifiche da salvare')).toBeTruthy();
    await rerender({ ...base, modifiche: 1 });
    expect(screen.getByText('1 modifica da salvare')).toBeTruthy();
  });
});

describe('BarraSalva – pulsanti', () => {
  it('il pulsante si chiama esattamente «Salva» (non «Salva le modifiche»)', () => {
    render(BarraSalva, base);
    const salva = screen.getByRole('button', { name: 'Salva' });
    expect(salva.textContent?.trim()).toBe('Salva');
    expect(screen.queryByRole('button', { name: /Salva le modifiche/ })).toBeNull();
  });

  it('sono due soli pulsanti: «Annulla» e «Salva»', () => {
    render(BarraSalva, base);
    expect(screen.getAllByRole('button').map((b) => b.textContent?.trim())).toEqual(['Annulla', 'Salva']);
  });

  it('sono veri <button type="button">', () => {
    render(BarraSalva, base);
    for (const b of screen.getAllByRole('button')) {
      expect(b.tagName).toBe('BUTTON');
      expect(b.getAttribute('type')).toBe('button');
    }
  });

  it('«Annulla» chiama onannulla una volta e non onsalva', async () => {
    const onannulla = vi.fn();
    const onsalva = vi.fn();
    render(BarraSalva, { ...base, onannulla, onsalva });
    await userEvent.click(screen.getByRole('button', { name: 'Annulla' }));
    expect(onannulla).toHaveBeenCalledTimes(1);
    expect(onsalva).not.toHaveBeenCalled();
  });

  it('«Salva» chiama onsalva una volta e non onannulla', async () => {
    const onannulla = vi.fn();
    const onsalva = vi.fn();
    render(BarraSalva, { ...base, onannulla, onsalva });
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }));
    expect(onsalva).toHaveBeenCalledTimes(1);
    expect(onannulla).not.toHaveBeenCalled();
  });

  it('nulla viene chiamato prima del clic', () => {
    const onannulla = vi.fn();
    const onsalva = vi.fn();
    render(BarraSalva, { ...base, onannulla, onsalva });
    expect(onannulla).not.toHaveBeenCalled();
    expect(onsalva).not.toHaveBeenCalled();
  });
});

describe('BarraSalva – bozza non valida', () => {
  it('«Salva» è disabilitato', () => {
    render(BarraSalva, { ...base, valida: false });
    expect((screen.getByRole('button', { name: 'Salva' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('il clic su «Salva» disabilitato non chiama onsalva', async () => {
    const onsalva = vi.fn();
    render(BarraSalva, { ...base, valida: false, onsalva });
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }));
    expect(onsalva).not.toHaveBeenCalled();
  });

  it('«Annulla» resta attivo e funziona anche con la bozza non valida', async () => {
    const onannulla = vi.fn();
    render(BarraSalva, { ...base, valida: false, onannulla });
    const annulla = screen.getByRole('button', { name: 'Annulla' }) as HTMLButtonElement;
    expect(annulla.disabled).toBe(false);
    await userEvent.click(annulla);
    expect(onannulla).toHaveBeenCalledTimes(1);
  });

  it('con la bozza valida «Salva» è abilitato', () => {
    render(BarraSalva, { ...base, valida: true });
    expect((screen.getByRole('button', { name: 'Salva' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('senza modifiche e bozza non valida non compare comunque nulla', () => {
    render(BarraSalva, { ...base, modifiche: 0, valida: false });
    expect(screen.queryByRole('button')).toBeNull();
  });
});
