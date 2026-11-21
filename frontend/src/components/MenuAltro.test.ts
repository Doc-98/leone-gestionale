import { cleanup, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { VoceMenu } from '../lib/menu';
import MenuAltro from './MenuAltro.svelte';

/** Il layout lo decide `schermo.larga`: qui un oggetto che si cambia da un test all'altro (pannello sul computer, foglio altrove). */
const schermo = vi.hoisted(() => ({ larga: false }));
vi.mock('../lib/schermo.svelte', () => ({ schermo }));

const pulsante = () => screen.getByRole('button', { name: 'Altre azioni' });
const vociVisibili = () => screen.queryAllByRole('menuitem');

function crea(extra: Partial<VoceMenu>[] = []) {
  const clic = { modifica: vi.fn(), nuovo: vi.fn(), banna: vi.fn(), elimina: vi.fn() };
  const voci: VoceMenu[] = [
    { testo: "Modifica l'anagrafica", icona: 'matita', onclick: clic.modifica },
    { testo: 'Nuovo certificato…', icona: 'piu', onclick: clic.nuovo, separatore: true },
    { testo: 'Banna la persona…', icona: 'divieto', rossa: true, onclick: clic.banna },
    ...extra.map((e) => ({ testo: 'x', icona: 'piu', onclick: () => {}, ...e }) as VoceMenu),
  ];
  return { voci, clic };
}

beforeEach(() => {
  schermo.larga = false;
});
afterEach(cleanup);

describe('MenuAltro – tastiera e accessibilità (foglio dal basso, sotto i 1024 px)', () => {
  it('il pulsante dichiara il menu: haspopup, expanded e controls', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    expect(pulsante()).toHaveAttribute('aria-haspopup', 'menu');
    expect(pulsante()).toHaveAttribute('aria-expanded', 'false');
    expect(pulsante()).not.toHaveAttribute('aria-controls');
    await userEvent.click(pulsante());
    expect(pulsante()).toHaveAttribute('aria-expanded', 'true');
    expect(pulsante().getAttribute('aria-controls')).toBe(screen.getByRole('menu').id);
  });

  it('si apre in un foglio con il titolo dell\'etichetta, e il focus va alla prima voce', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci, etichetta: 'Altre azioni' } });
    await userEvent.click(pulsante());
    expect(screen.getByRole('dialog', { name: 'Altre azioni' })).toHaveClass('dal-basso');
    await vi.waitFor(() => expect(screen.getByRole('menuitem', { name: "Modifica l'anagrafica" })).toHaveFocus());
  });

  it('frecce su e giù girano tra le voci, Inizio e Fine saltano agli estremi', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    await vi.waitFor(() => expect(vociVisibili()[0]).toHaveFocus());
    await userEvent.keyboard('{ArrowDown}');
    expect(vociVisibili()[1]).toHaveFocus();
    await userEvent.keyboard('{ArrowDown}{ArrowDown}');
    expect(vociVisibili()[0]).toHaveFocus();
    await userEvent.keyboard('{ArrowUp}');
    expect(vociVisibili()[2]).toHaveFocus();
    await userEvent.keyboard('{Home}');
    expect(vociVisibili()[0]).toHaveFocus();
    await userEvent.keyboard('{End}');
    expect(vociVisibili()[2]).toHaveFocus();
  });

  it('salta le voci spente', async () => {
    const { voci } = crea();
    voci[1].disabled = true;
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    await vi.waitFor(() => expect(vociVisibili()[0]).toHaveFocus());
    await userEvent.keyboard('{ArrowDown}');
    expect(vociVisibili()[2]).toHaveFocus();
  });

  it('Esc chiude e rimette il focus sul pulsante', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    await vi.waitFor(() => expect(vociVisibili()[0]).toHaveFocus());
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).toBeNull();
    expect(pulsante()).toHaveFocus();
    expect(pulsante()).toHaveAttribute('aria-expanded', 'false');
  });

  it('Tab chiude il menu e il focus passa dal pulsante', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    await vi.waitFor(() => expect(vociVisibili()[0]).toHaveFocus());
    await userEvent.keyboard('{Tab}');
    expect(screen.queryByRole('menu')).toBeNull();
  });

  it('scegliere una voce chiude il menu, rimette il focus sul pulsante ed esegue', async () => {
    const { voci, clic } = crea();
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    await userEvent.click(screen.getByRole('menuitem', { name: 'Nuovo certificato…' }));
    expect(clic.nuovo).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('menu')).toBeNull();
    expect(pulsante()).toHaveFocus();
  });

  it('la voce rossa che apre un modulo sta ultima ed esegue al primo tocco', async () => {
    const { voci, clic } = crea([{ testo: 'Ultima normale' }]);
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    const nomi = vociVisibili().map((v) => v.textContent?.trim());
    expect(nomi.at(-1)).toBe('Banna la persona…');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Banna la persona…' }));
    expect(clic.banna).toHaveBeenCalledTimes(1);
  });

  it('il separatore sta tra le voci e non è una voce', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    expect(screen.getAllByRole('separator')).toHaveLength(1);
    expect(vociVisibili()).toHaveLength(3);
  });

  it('più di sette voci è un errore di chi scrive la pagina', () => {
    const { voci } = crea(Array.from({ length: 5 }, (_, i) => ({ testo: `v${i}` })));
    expect(() => render(MenuAltro, { props: { voci } })).toThrow(/al massimo 7/);
  });
});

describe('MenuAltro – la voce che elimina: due tocchi dentro la voce', () => {
  it('è l\'ultima, il primo tocco arma («Elimina?») e non esegue, il secondo esegue e chiude', async () => {
    const { voci, clic } = crea([{ testo: 'Elimina il cliente', elimina: 'Elimina?', onclick: undefined }]);
    voci[voci.length - 1].onclick = clic.elimina;
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    const ultima = vociVisibili().at(-1)!;
    expect(ultima).toHaveTextContent('Elimina il cliente');
    await userEvent.click(ultima);
    expect(clic.elimina).not.toHaveBeenCalled();
    expect(screen.getByRole('menu')).toBeInTheDocument();
    expect(vociVisibili().at(-1)).toHaveTextContent('Elimina?');
    await userEvent.click(vociVisibili().at(-1)!);
    expect(clic.elimina).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('menu')).toBeNull();
  });

  it('con Invio da tastiera vale uguale: arma, poi esegue', async () => {
    const { voci, clic } = crea([{ testo: 'Rimuovi dalla lista', elimina: 'Rimuovi?' }]);
    voci[voci.length - 1].onclick = clic.elimina;
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    await vi.waitFor(() => expect(vociVisibili()[0]).toHaveFocus());
    await userEvent.keyboard('{End}{Enter}');
    expect(clic.elimina).not.toHaveBeenCalled();
    await userEvent.keyboard('{Enter}');
    expect(clic.elimina).toHaveBeenCalledTimes(1);
  });
});

describe('MenuAltro – sul computer (pannello sotto il pulsante)', () => {
  beforeEach(() => {
    schermo.larga = true;
  });

  it('il pannello sta in <body>, senza foglio, e il focus va alla prima voce', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    expect(screen.queryByRole('dialog')).toBeNull();
    const pannello = screen.getByRole('menu').closest('.mn-pannello')!;
    expect(pannello.parentElement).toBe(document.body);
    await vi.waitFor(() => expect(vociVisibili()[0]).toHaveFocus());
  });

  it('Esc chiude, il pannello sparisce da <body> e il focus torna al pulsante', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    await vi.waitFor(() => expect(vociVisibili()[0]).toHaveFocus());
    await userEvent.keyboard('{Escape}');
    expect(document.querySelector('.mn-pannello')).toBeNull();
    expect(pulsante()).toHaveFocus();
  });

  it('un tocco fuori chiude (senza rubare il focus a ciò che si è toccato)', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    const fuori = document.createElement('button');
    document.body.append(fuori);
    await userEvent.click(pulsante());
    await userEvent.click(fuori);
    expect(document.querySelector('.mn-pannello')).toBeNull();
    expect(fuori).toHaveFocus();
    fuori.remove();
  });

  it('un tocco dentro il pannello (sul separatore) non lo chiude; il pulsante lo chiude', async () => {
    const { voci } = crea();
    render(MenuAltro, { props: { voci } });
    await userEvent.click(pulsante());
    await userEvent.click(screen.getByRole('separator'));
    expect(screen.getByRole('menu')).toBeInTheDocument();
    await userEvent.click(pulsante());
    expect(screen.queryByRole('menu')).toBeNull();
  });
});
