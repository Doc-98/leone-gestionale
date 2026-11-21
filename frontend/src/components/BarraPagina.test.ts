import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { createRawSnippet } from 'svelte';
import { afterEach, describe, expect, it } from 'vitest';
import BarraPagina from './BarraPagina.svelte';
import Periodo from './Periodo.svelte';
import SceltaVista from './SceltaVista.svelte';

afterEach(() => {
  cleanup();
  localStorage.clear();
});

const OPZ = [
  { id: 'a', nome: 'Prima' },
  { id: 'b', nome: 'Seconda' },
  { id: 'c', nome: 'Terza' },
];

describe('SceltaVista', () => {
  it('fino a 3 opzioni è un segmentato di pulsanti premuti', async () => {
    render(SceltaVista, { props: { opzioni: OPZ, valore: 'a', etichetta: 'Come mostrare', preferenza: 'prova.vista' } });
    const gruppo = screen.getByRole('group', { name: 'Come mostrare' });
    expect(gruppo.querySelectorAll('button')).toHaveLength(3);
    expect(screen.getByRole('button', { name: 'Prima' })).toHaveAttribute('aria-pressed', 'true');
    await fireEvent.click(screen.getByRole('button', { name: 'Seconda' }));
    expect(screen.getByRole('button', { name: 'Seconda' })).toHaveAttribute('aria-pressed', 'true');
    expect(Object.values({ ...localStorage })).toContain('b');
  });

  it('oltre 3 opzioni è una tendina', () => {
    render(SceltaVista, { props: { opzioni: [...OPZ, { id: 'd', nome: 'Quarta' }], valore: 'd', etichetta: 'Vista' } });
    const tendina = screen.getByRole('combobox', { name: 'Vista' }) as HTMLSelectElement;
    expect(tendina.value).toBe('d');
    expect(screen.queryByRole('group')).toBeNull();
  });

  it('una scelta che non è tra le opzioni mostra la prima, senza cambiare quella salvata', () => {
    render(SceltaVista, { props: { opzioni: OPZ.slice(0, 2), valore: 'colonne', etichetta: 'Vista', preferenza: 'prova.vista' } });
    expect(screen.getByRole('button', { name: 'Prima' })).toHaveAttribute('aria-pressed', 'true');
    expect(localStorage.length).toBe(0);
  });
});

describe('Periodo', () => {
  it('le frecce spostano il periodo e «oggi» è spento se il periodo contiene già oggi', async () => {
    render(Periodo, { props: { inizio: '2026-11-16', passo: 'settimana', oggi: '2026-11-18' } });
    expect(screen.getByText('16–22 novembre')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Torna a questa settimana' })).toBeDisabled();
    await fireEvent.click(screen.getByRole('button', { name: 'Settimana successiva' }));
    expect(screen.getByText('23–29 novembre')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Torna a questa settimana' })).toBeEnabled();
    await fireEvent.click(screen.getByRole('button', { name: 'Torna a questa settimana' }));
    expect(screen.getByText('16–22 novembre')).toBeInTheDocument();
  });

  it('con max non si va oltre', () => {
    render(Periodo, { props: { inizio: '2026-11-01', passo: 'mese', oggi: '2026-11-18', max: '2026-11-18' } });
    expect(screen.getByRole('button', { name: 'Mese successivo' })).toBeDisabled();
  });

  it('un valore oltre max dal calendario torna a max', async () => {
    render(Periodo, { props: { inizio: '2026-11-01', passo: 'mese', oggi: '2026-11-18', max: '2026-11-18' } });
    await fireEvent.change(screen.getByLabelText('Scegli il mese', { selector: 'input' }), { target: { value: '2027-02-10' } });
    expect(screen.getByText('Novembre 2026')).toBeInTheDocument();
  });

  it('il calendario nativo cambia il periodo', async () => {
    render(Periodo, { props: { inizio: '2026-11-16', passo: 'settimana', oggi: '2026-11-18' } });
    const campo = screen.getByLabelText('Scegli la settimana', { selector: 'input' }) as HTMLInputElement;
    await fireEvent.change(campo, { target: { value: '2026-12-30' } });
    expect(screen.getByText('28 dicembre – 3 gennaio')).toBeInTheDocument();
  });
});

describe('BarraPagina', () => {
  it('il titolo è un h1; periodo, vista e azioni compaiono se ci sono', () => {
    const pezzo = (t: string) => createRawSnippet(() => ({ render: () => `<button>${t}</button>` }));
    render(BarraPagina, { props: { titolo: 'I miei turni', periodo: pezzo('P'), vista: pezzo('V'), azioni: pezzo('A') } });
    expect(screen.getByRole('heading', { level: 1, name: 'I miei turni' })).toBeInTheDocument();
    for (const n of ['P', 'V', 'A']) expect(screen.getByRole('button', { name: n })).toBeInTheDocument();
  });
});
