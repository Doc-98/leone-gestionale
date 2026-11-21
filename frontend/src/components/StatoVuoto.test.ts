import { cleanup, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it } from 'vitest';
import StatoVuoto from './StatoVuoto.svelte';

afterEach(cleanup);

/** Il comportamento (testo, «Aggiungi», `come`, caricamento) lo prova `lib/statovuoto.specifica.test.ts`: qui le regole d'aspetto. */
describe('StatoVuoto – aspetto', () => {
  it('il testo non va a capo', () => {
    render(StatoVuoto, { props: { testo: 'Nessun prezzo.' } });
    const testo = screen.getByText('Nessun prezzo.');
    expect(testo).toHaveClass('testo');
  });

  it('dentro un <ul> con come="li" è un <li> e il caricamento ha role="status"', () => {
    const ul = document.createElement('ul');
    document.body.appendChild(ul);
    render(StatoVuoto, { props: { testo: 'Nessuno.', come: 'li', caricamento: true }, target: ul });
    expect(ul.querySelector('li')).not.toBeNull();
    expect(screen.getByRole('status')).toHaveTextContent('Carico…');
    ul.remove();
  });

  it('in tabella disegna <tr><td colspan> e il pulsante «Aggiungi» sta nella cella', () => {
    const corpo = document.createElement('tbody');
    const tab = document.createElement('table');
    tab.appendChild(corpo);
    document.body.appendChild(tab);
    render(StatoVuoto, { props: { testo: 'Nessun servizio.', come: 'tr', colonne: 5, aggiungi: { testo: 'Aggiungi un servizio', onclick: () => {} } }, target: corpo });
    const td = corpo.querySelector('tr > td');
    expect(td).toHaveAttribute('colspan', '5');
    expect(td?.querySelector('button')).toHaveTextContent('Aggiungi un servizio');
    tab.remove();
  });
});
