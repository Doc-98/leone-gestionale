import { cleanup, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { createRawSnippet } from 'svelte';
import { afterEach, describe, expect, it } from 'vitest';
import type { Scheda } from '../lib/schede';
import Schede from './Schede.svelte';

afterEach(cleanup);

const SCHEDE: Scheda[] = [
  { id: 'corsi', nome: 'Corsi e abbonamenti', segno: { testo: '2' } },
  { id: 'pagamenti', nome: 'Pagamenti e borsellino', segno: { testo: 'da pagare', warn: true } },
  { id: 'accessi', nome: 'Accessi' },
];
const contenuto = createRawSnippet((s: () => string) => ({ render: () => `<p>Pannello ${s()}</p>` }));
const monta = (attiva = 'corsi', schede = SCHEDE) =>
  render(Schede, { props: { schede, attiva, etichetta: 'Sezioni della scheda', id: 'prova', children: contenuto } });
const tab = (nome: string) => screen.getByRole('tab', { name: new RegExp(nome) });

describe('Schede – tablist ARIA', () => {
  it('ha il tablist con la sua etichetta, un tab per scheda e il pannello collegato', () => {
    monta();
    expect(screen.getByRole('tablist', { name: 'Sezioni della scheda' })).toBeInTheDocument();
    expect(screen.getAllByRole('tab')).toHaveLength(3);
    const pannello = screen.getByRole('tabpanel');
    expect(pannello).toHaveTextContent('Pannello corsi');
    expect(pannello.id).toBe('prova-pannello-corsi');
    expect(pannello).toHaveAttribute('aria-labelledby', 'prova-tab-corsi');
    expect(tab('Corsi')).toHaveAttribute('aria-controls', 'prova-pannello-corsi');
    expect(tab('Corsi')).toHaveAttribute('aria-selected', 'true');
    expect(tab('Accessi')).toHaveAttribute('aria-selected', 'false');
  });

  it('il segno sta accanto al nome (ambra con warn)', () => {
    monta();
    expect(tab('Corsi')).toHaveTextContent('2');
    expect(tab('Pagamenti').querySelector('.sc-segno')).toHaveClass('warn');
    expect(tab('Accessi').querySelector('.sc-segno')).toBeNull();
  });

  it('nel giro del Tab c\'è solo la scheda attiva', () => {
    monta('pagamenti');
    expect(tab('Pagamenti')).toHaveAttribute('tabindex', '0');
    expect(tab('Corsi')).toHaveAttribute('tabindex', '-1');
    expect(tab('Accessi')).toHaveAttribute('tabindex', '-1');
  });

  it('un tocco apre la scheda e cambia il pannello', async () => {
    monta();
    await userEvent.click(tab('Accessi'));
    expect(tab('Accessi')).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel').id).toBe('prova-pannello-accessi');
    expect(screen.getByRole('tabpanel')).toHaveAttribute('aria-labelledby', 'prova-tab-accessi');
  });
});

describe('Schede – tastiera', () => {
  it('le frecce attivano spostandosi e il focus segue, a giro', async () => {
    monta();
    tab('Corsi').focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(tab('Pagamenti')).toHaveFocus();
    expect(tab('Pagamenti')).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel').id).toBe('prova-pannello-pagamenti');
    await userEvent.keyboard('{ArrowRight}{ArrowRight}');
    expect(tab('Corsi')).toHaveFocus();
    await userEvent.keyboard('{ArrowLeft}');
    expect(tab('Accessi')).toHaveFocus();
    expect(tab('Accessi')).toHaveAttribute('aria-selected', 'true');
  });

  it('Inizio e Fine', async () => {
    monta('pagamenti');
    tab('Pagamenti').focus();
    await userEvent.keyboard('{End}');
    expect(tab('Accessi')).toHaveFocus();
    await userEvent.keyboard('{Home}');
    expect(tab('Corsi')).toHaveFocus();
    expect(tab('Corsi')).toHaveAttribute('aria-selected', 'true');
  });

  it('con due sole schede (Account e Ruoli) funziona uguale', async () => {
    monta('account', [{ id: 'account', nome: 'Account' }, { id: 'ruoli', nome: 'Ruoli' }]);
    tab('Account').focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(tab('Ruoli')).toHaveFocus();
    expect(screen.getByRole('tabpanel').id).toBe('prova-pannello-ruoli');
  });
});

describe('Schede – frecce di scorrimento', () => {
  it('se le schede ci stanno non ci sono frecce (e non hanno barra di scorrimento visibile)', () => {
    monta();
    expect(screen.queryByRole('button', { name: 'Schede precedenti' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Schede successive' })).toBeNull();
  });
});
