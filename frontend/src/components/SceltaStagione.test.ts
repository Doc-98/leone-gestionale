import { cleanup, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import SceltaStagione from './SceltaStagione.svelte';
import type { Stagione } from '../lib/api';

afterEach(cleanup);

const s = (id: string, nome: string, dal: string, al: string): Stagione => ({ id, nome, dal, al, quota: null, quotaRimborsabile: false });
const stagioni = [s('b', '2026/27', '2026-09-01', '2027-08-31'), s('a', '2025/26', '2025-09-01', '2026-08-31'), s('c', '2027/28', '2027-09-01', '2028-08-31')];

describe('SceltaStagione', () => {
  it('solo il nome nelle opzioni, in ordine, con la pillola dello stato', () => {
    render(SceltaStagione, { props: { stagioni, valore: 'b', oggi: '2026-11-23' } });
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['2025/26', '2026/27', '2027/28']);
    expect(screen.getByText('in corso')).toHaveClass('pill', 'passa');
  });

  it('cambiando la scelta chiama onchange e la pillola segue', async () => {
    const onchange = vi.fn();
    render(SceltaStagione, { props: { stagioni, valore: 'b', oggi: '2026-11-23', onchange } });
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Stagione' }), 'c');
    expect(onchange).toHaveBeenCalledWith('c');
    expect(screen.getByText('futura')).toHaveClass('pill', 'futura');
  });

  it('senza scelta sceglie quella iniziale; senza stagioni non disegna niente', () => {
    const onchange = vi.fn();
    render(SceltaStagione, { props: { stagioni, oggi: '2026-11-23', onchange } });
    expect(onchange).toHaveBeenCalledWith('b');
    cleanup();
    const { container } = render(SceltaStagione, { props: { stagioni: [], oggi: '2026-11-23' } });
    expect(container.textContent?.trim()).toBe('');
  });
});
