import { cleanup, render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { createRawSnippet } from 'svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ElencoModificabile from '../components/ElencoModificabile.svelte';

/**
 * Specifica (brief coerenza 7, lotto 3, A1 «ElencoModificabile: cestinoApre»): nuova prop
 * `cestinoApre?: (el: T) => boolean` (default `() => false`). Per le righe per cui è `true` il cestino non chiede
 * «Elimina?»: l'etichetta finisce con i puntini («Elimina «Agonisti»…») e il primo tocco chiama subito `onelimina`
 * (che apre la finestra pesante); se `onelimina` restituisce un testo compare sotto la riga, come oggi. Il resto non
 * cambia: per le altre righe i due tocchi di sempre («Elimina «X»» poi «Elimina?»).
 */
afterEach(cleanup);

interface Gruppo { id: string; nome: string; pesante: boolean }
const gruppi = (): Gruppo[] => [
  { id: 'g1', nome: 'Agonisti', pesante: true },
  { id: 'g2', nome: 'Over 60', pesante: false },
  { id: 'g3', nome: 'Staff', pesante: true },
];
const riga = createRawSnippet((el: () => Gruppo) => ({ render: () => `<strong>${el().nome}</strong>` }));

function monta(extra: Record<string, unknown> = {}) {
  const onelimina = vi.fn();
  const props = {
    elementi: gruppi(),
    modificabile: true,
    nome: (g: Gruppo) => g.nome,
    nomeElemento: 'gruppo',
    riga,
    onelimina,
    cestinoApre: (g: Gruppo) => g.pesante,
    ...extra,
  };
  render(ElencoModificabile, { props: props as never });
  return { onelimina: props.onelimina as ReturnType<typeof vi.fn> };
}
const riDi = (nome: string) => screen.getByText(nome).closest('li') as HTMLElement;

describe('ElencoModificabile – cestinoApre: la riga che apre', () => {
  it('l\'etichetta del cestino finisce con i puntini', () => {
    monta();
    expect(screen.getByRole('button', { name: 'Elimina «Agonisti»…' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Elimina «Staff»…' })).toBeInTheDocument();
  });

  it('il primo tocco chiama subito `onelimina` con la riga, senza «Elimina?»', async () => {
    const { onelimina } = monta();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»…' }));
    expect(onelimina).toHaveBeenCalledTimes(1);
    expect(onelimina.mock.calls[0][0]).toMatchObject({ id: 'g1', nome: 'Agonisti' });
    expect(screen.queryByRole('button', { name: /Elimina\?/ })).toBeNull();
  });

  it('dopo il tocco il cestino non resta armato: l\'etichetta è ancora con i puntini', async () => {
    monta();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»…' }));
    expect(screen.getByRole('button', { name: 'Elimina «Agonisti»…' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Elimina\?/ })).toBeNull();
  });

  it('non chiede conferma nemmeno con `conferma` personalizzata («Rimuovi?»)', async () => {
    const { onelimina } = monta({ conferma: 'Rimuovi?' });
    await userEvent.click(screen.getByRole('button', { name: 'Rimuovi «Agonisti»…' }));
    expect(onelimina).toHaveBeenCalledTimes(1);
  });
});

describe('ElencoModificabile – cestinoApre: le altre righe', () => {
  it('il cestino ha l\'etichetta di sempre, senza puntini', () => {
    monta();
    expect(screen.getByRole('button', { name: 'Elimina «Over 60»' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Elimina «Over 60»…' })).toBeNull();
  });

  it('il primo tocco arma («Elimina?») e non chiama `onelimina`; il secondo elimina', async () => {
    const { onelimina } = monta();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Over 60»' }));
    expect(onelimina).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    expect(onelimina).toHaveBeenCalledTimes(1);
    expect(onelimina.mock.calls[0][0]).toMatchObject({ id: 'g2' });
  });
});

describe('ElencoModificabile – senza cestinoApre', () => {
  it('il default è «nessuna riga apre»: i due tocchi di sempre per tutte', async () => {
    const onelimina = vi.fn();
    render(ElencoModificabile, {
      props: { elementi: gruppi(), modificabile: true, nome: (g: Gruppo) => g.nome, nomeElemento: 'gruppo', riga, onelimina } as never,
    });
    expect(screen.getByRole('button', { name: 'Elimina «Agonisti»' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /…$/ })).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»' }));
    expect(onelimina).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    expect(onelimina).toHaveBeenCalledTimes(1);
  });
});

describe('ElencoModificabile – cestinoApre: il testo restituito da onelimina', () => {
  it('un testo restituito compare sotto la riga che ha aperto', async () => {
    monta({ onelimina: vi.fn(() => 'Il gruppo ha 38 persone e non si può eliminare adesso.') });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»…' }));
    const testo = await screen.findByText(/Il gruppo ha 38 persone/);
    expect(riDi('Agonisti').contains(testo)).toBe(true);
    expect(riDi('Staff').contains(testo)).toBe(false);
  });

  it('anche se il testo arriva da una promessa', async () => {
    monta({ onelimina: vi.fn(() => Promise.resolve('Il gruppo è in uso.')) });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Staff»…' }));
    const testo = await screen.findByText(/Il gruppo è in uso\./);
    expect(riDi('Staff').contains(testo)).toBe(true);
  });

  it('il testo su più righe porta l\'attacco in grassetto', async () => {
    monta({ onelimina: vi.fn(() => 'Non si può eliminare: «Agonisti» è in uso.\nPuoi spegnerlo: resta nello storico.') });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»…' }));
    await screen.findByText(/«Agonisti» è in uso\./);
    const grassetti = [...riDi('Agonisti').querySelectorAll('b')].map((b) => b.textContent);
    expect(grassetti).toEqual(['Non si può eliminare:', 'Puoi spegnerlo:']);
  });

  it('senza testo restituito (finestra aperta) non compare nessun messaggio', async () => {
    monta({ onelimina: vi.fn(() => undefined) });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Agonisti»…' }));
    await waitFor(() => expect(within(riDi('Agonisti')).queryByRole('alert')).toBeNull());
  });

  it('anche per le altre righe un testo restituito compare sotto la riga, come oggi', async () => {
    monta({ onelimina: vi.fn(() => 'Non si può eliminare adesso.') });
    await userEvent.click(screen.getByRole('button', { name: 'Elimina «Over 60»' }));
    await userEvent.click(screen.getByRole('button', { name: /Elimina\?/ }));
    const testo = await screen.findByText(/Non si può eliminare adesso/);
    expect(riDi('Over 60').contains(testo)).toBe(true);
  });
});
