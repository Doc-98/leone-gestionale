import { cleanup, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import CercaImpostazioni from './CercaImpostazioni.svelte';

afterEach(cleanup);
const sezioni = ['societa', 'prezzi', 'servizi'];

describe('CercaImpostazioni', () => {
  it('cambiando pagina il testo cercato si svuota e i risultati si chiudono', async () => {
    render(CercaImpostazioni, { sezioni });
    const campo = screen.getByRole('combobox') as HTMLInputElement;
    await userEvent.type(campo, 'cauzione');
    expect(campo.value).toBe('cauzione');
    expect(screen.getByRole('listbox')).toBeTruthy();
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    await Promise.resolve();
    await new Promise((r) => setTimeout(r, 0));
    expect(campo.value).toBe('');
    expect(screen.queryByRole('listbox')).toBeNull();
  });
});
