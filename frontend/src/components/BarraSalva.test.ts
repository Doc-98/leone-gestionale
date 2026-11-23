import { cleanup, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it } from 'vitest';
import BarraSalva from './BarraSalva.svelte';

afterEach(cleanup);
const base = { valida: true, onannulla: () => {}, onsalva: () => {} };

describe('BarraSalva – regione annunciata', () => {
  it('la regione status c\'è anche senza modifiche, vuota', () => {
    render(BarraSalva, { ...base, modifiche: 0 });
    expect(screen.getByRole('status')).toHaveTextContent('');
  });
  it('con le modifiche la regione ha il solo testo, i pulsanti sono fuori', () => {
    render(BarraSalva, { ...base, modifiche: 2 });
    const regione = screen.getByRole('status');
    expect(regione).toHaveTextContent('2 modifiche da salvare');
    expect(regione.querySelector('button')).toBeNull();
    expect(screen.getByRole('button', { name: 'Salva' }).closest('[role="status"]')).toBeNull();
  });
  it('è la stessa regione che passa da vuota a piena (così l\'annuncio parte)', async () => {
    const { rerender } = render(BarraSalva, { ...base, modifiche: 0 });
    const prima = screen.getByRole('status');
    await rerender({ ...base, modifiche: 1 });
    expect(screen.getByRole('status')).toBe(prima);
    expect(prima).toHaveTextContent('1 modifica da salvare');
  });
});
