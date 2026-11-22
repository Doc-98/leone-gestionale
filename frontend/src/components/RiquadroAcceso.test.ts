import { cleanup, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import RiquadroAcceso from './RiquadroAcceso.svelte';

afterEach(cleanup);

describe('RiquadroAcceso', () => {
  it('acceso dice «Acceso», spento «Spento»; l\'interruttore ha nome e stato', () => {
    render(RiquadroAcceso, { props: { acceso: false, onacceso: () => {}, nome: 'Carta' } });
    expect(screen.getByText('Spento')).toBeTruthy();
    expect(screen.getByRole('switch', { name: 'Acceso: «Carta»' })).toHaveAttribute('aria-checked', 'false');
  });

  it('tutto il riquadro si tocca, e un tocco cambia lo stato una volta sola', async () => {
    const onacceso = vi.fn();
    render(RiquadroAcceso, { props: { acceso: true, onacceso, nome: 'Carta' } });
    await userEvent.click(screen.getByText('Acceso'));
    expect(onacceso).toHaveBeenCalledTimes(1);
    expect(onacceso).toHaveBeenCalledWith(false);
    await userEvent.click(screen.getByRole('switch'));
    expect(onacceso).toHaveBeenCalledTimes(2);
  });

  it('la scritta a vista non si aggiunge al nome letto dallo screen reader', () => {
    render(RiquadroAcceso, { props: { acceso: true, onacceso: () => {}, nome: 'Carta' } });
    expect(screen.getByText('Acceso')).toHaveAttribute('aria-hidden', 'true');
  });
});
