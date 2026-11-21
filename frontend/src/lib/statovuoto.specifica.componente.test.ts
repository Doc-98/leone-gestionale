import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';
import StatoVuoto from '../components/StatoVuoto.svelte';
import { CARICO } from './testi';

/**
 * Specifica §2.1 e design system «Stato vuoto e caricamento»: una riga dentro la card, «Nessun prezzo.»; se chi guarda
 * può creare, a destra «Aggiungi un prezzo»; il caricamento è la stessa riga, «Carico…», senza rotelle né «Aggiungi».
 */
afterEach(cleanup);

describe('StatoVuoto – vuoto', () => {
  it('mostra il testo e nessun pulsante se chi guarda non può creare', () => {
    render(StatoVuoto, { testo: 'Nessun prezzo.' });
    expect(screen.getByText('Nessun prezzo.')).toBeTruthy();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('senza caricamento non ha ruolo «status»: lo screen reader non lo annuncia', () => {
    render(StatoVuoto, { testo: 'Nessun prezzo.' });
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('con `aggiungi` mostra il pulsante col suo testo e il clic chiama onclick una volta', async () => {
    const onclick = vi.fn();
    render(StatoVuoto, { testo: 'Nessun prezzo.', aggiungi: { testo: 'Aggiungi un prezzo', onclick } });
    const b = screen.getByRole('button', { name: /Aggiungi un prezzo/ });
    expect(onclick).not.toHaveBeenCalled();
    await fireEvent.click(b);
    expect(onclick).toHaveBeenCalledTimes(1);
  });

  it('il pulsante «Aggiungi» è un vero <button type="button"> (non manda moduli)', () => {
    render(StatoVuoto, { testo: 'Nessun X.', aggiungi: { testo: 'Aggiungi un X', onclick: () => {} } });
    const b = screen.getByRole('button');
    expect(b.tagName).toBe('BUTTON');
    expect(b.getAttribute('type')).toBe('button');
  });

  it('il pulsante è piccolo e quieto (.btn .btn-sm .btn-quieto), non un pulsante grande', () => {
    render(StatoVuoto, { testo: 'Nessun X.', aggiungi: { testo: 'Aggiungi un X', onclick: () => {} } });
    const b = screen.getByRole('button');
    expect(b.classList.contains('btn')).toBe(true);
    expect(b.classList.contains('btn-sm')).toBe(true);
    expect(b.classList.contains('btn-quieto')).toBe(true);
  });

  it('il testo dell\'«Aggiungi» e quello vuoto restano separati (il pulsante non contiene «Nessun…»)', () => {
    render(StatoVuoto, { testo: 'Nessun prezzo.', aggiungi: { testo: 'Aggiungi un prezzo', onclick: () => {} } });
    expect(screen.getByRole('button').textContent).not.toContain('Nessun prezzo.');
  });

  it('non disegna rotelle né icone grandi (nessun role="progressbar")', () => {
    render(StatoVuoto, { testo: 'Nessun prezzo.' });
    expect(screen.queryByRole('progressbar')).toBeNull();
  });
});

describe('StatoVuoto – caricamento', () => {
  it('mostra «Carico…» al posto del testo', () => {
    render(StatoVuoto, { testo: 'Nessun prezzo.', caricamento: true });
    expect(screen.getByText(CARICO)).toBeTruthy();
    expect(screen.queryByText('Nessun prezzo.')).toBeNull();
  });

  it('ha role="status" e il testo letto è «Carico…»', () => {
    render(StatoVuoto, { testo: 'Nessun prezzo.', caricamento: true });
    expect(screen.getByRole('status').textContent?.trim()).toBe('Carico…');
  });

  it('non mostra «Aggiungi» nemmeno se `aggiungi` è passato', () => {
    render(StatoVuoto, {
      testo: 'Nessun prezzo.',
      caricamento: true,
      aggiungi: { testo: 'Aggiungi un prezzo', onclick: () => {} },
    });
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByText('Aggiungi un prezzo')).toBeNull();
  });

  it('`caricamento = false` esplicito si comporta come il vuoto', () => {
    render(StatoVuoto, { testo: 'Nessuno.', caricamento: false });
    expect(screen.getByText('Nessuno.')).toBeTruthy();
    expect(screen.queryByText(CARICO)).toBeNull();
  });
});

describe('StatoVuoto – elemento radice (`come`)', () => {
  it('predefinito: un <div>', () => {
    const { container } = render(StatoVuoto, { testo: 'Nessun X.' });
    expect(container.querySelector('div')?.textContent).toContain('Nessun X.');
    expect(container.querySelector('li')).toBeNull();
    expect(container.querySelector('tr')).toBeNull();
  });

  it('come="li": un <li> (dentro un elenco)', () => {
    const ul = document.createElement('ul');
    document.body.appendChild(ul);
    render(StatoVuoto, { target: ul, props: { testo: 'Nessuno.', come: 'li' } });
    expect(ul.querySelector('li')?.textContent).toContain('Nessuno.');
    ul.remove();
  });

  it('come="tr": <tr><td colspan> con le colonne date', () => {
    const tab = document.createElement('table');
    const corpo = document.createElement('tbody');
    tab.appendChild(corpo);
    document.body.appendChild(tab);
    render(StatoVuoto, { target: corpo, props: { testo: 'Nessun servizio.', come: 'tr', colonne: 4 } });
    const td = corpo.querySelector('tr > td');
    expect(td).not.toBeNull();
    expect(td!.getAttribute('colspan')).toBe('4');
    expect(td!.textContent).toContain('Nessun servizio.');
    tab.remove();
  });

  it('come="tr" senza `colonne`: colspan 1', () => {
    const tab = document.createElement('table');
    const corpo = document.createElement('tbody');
    tab.appendChild(corpo);
    document.body.appendChild(tab);
    render(StatoVuoto, { target: corpo, props: { testo: 'Nessuna macchina.', come: 'tr' } });
    expect(corpo.querySelector('td')!.getAttribute('colspan')).toBe('1');
    tab.remove();
  });

  it('come="tr" in caricamento: ha comunque role="status" e «Carico…» dentro la cella', () => {
    const tab = document.createElement('table');
    const corpo = document.createElement('tbody');
    tab.appendChild(corpo);
    document.body.appendChild(tab);
    render(StatoVuoto, { target: corpo, props: { testo: 'x', come: 'tr', colonne: 3, caricamento: true } });
    expect(corpo.querySelector('[role="status"]')?.textContent?.trim()).toBe('Carico…');
    tab.remove();
  });

  it('come="tr" con «Aggiungi»: il pulsante sta dentro la cella e funziona', async () => {
    const tab = document.createElement('table');
    const corpo = document.createElement('tbody');
    tab.appendChild(corpo);
    document.body.appendChild(tab);
    const onclick = vi.fn();
    render(StatoVuoto, { target: corpo, props: { testo: 'x', come: 'tr', colonne: 2, aggiungi: { testo: 'Aggiungi una macchina', onclick } } });
    const b = corpo.querySelector('td button') as HTMLButtonElement;
    expect(b).not.toBeNull();
    await fireEvent.click(b);
    expect(onclick).toHaveBeenCalledTimes(1);
    tab.remove();
  });
});
