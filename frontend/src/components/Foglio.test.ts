import { cleanup, render, screen } from '@testing-library/svelte';
import { createRawSnippet } from 'svelte';
import { afterEach, describe, expect, it } from 'vitest';
import Foglio from './Foglio.svelte';

afterEach(cleanup);
const figli = createRawSnippet(() => ({ render: () => '<p>Corpo</p>' }));

describe('Foglio – basi del lotto 1', () => {
  it('predefinito: nessuna classe dal-basso', () => {
    render(Foglio, { props: { titolo: 'Prova', chiudi: () => {}, children: figli } });
    expect(screen.getByRole('dialog')).not.toHaveClass('dal-basso');
  });

  it('dalBasso mette la classe sul foglio e sul velo', () => {
    render(Foglio, { props: { titolo: 'Prova', chiudi: () => {}, children: figli, dalBasso: true } });
    expect(screen.getByRole('dialog')).toHaveClass('dal-basso');
    expect(document.querySelector('.velo')).toHaveClass('dal-basso');
  });

  it('dopoTitolo compare accanto al titolo, dentro l\'intestazione', () => {
    const pillola = createRawSnippet(() => ({ render: () => '<span class="pill">spento</span>' }));
    render(Foglio, { props: { titolo: 'Bonifico', chiudi: () => {}, children: figli, dopoTitolo: pillola } });
    const titolo = screen.getByRole('heading', { name: 'Bonifico' });
    expect(titolo.parentElement).toHaveTextContent('spento');
  });
});
