// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { portale } from './portale';

/** Specifica §2.2: azione Svelte che sposta il nodo (di solito in <body>) e lo rimette a posto in `destroy`. */
afterEach(() => {
  document.body.innerHTML = '';
});

function nodoInCard() {
  const card = document.createElement('div');
  card.style.overflow = 'hidden';
  const nodo = document.createElement('span');
  card.appendChild(nodo);
  document.body.appendChild(card);
  return { card, nodo };
}

describe('portale', () => {
  it('sposta il nodo in <body>', () => {
    const { nodo } = nodoInCard();
    portale(nodo);
    expect(nodo.parentElement).toBe(document.body);
  });

  it('con una destinazione sposta il nodo lì', () => {
    const { nodo } = nodoInCard();
    const dove = document.createElement('section');
    document.body.appendChild(dove);
    portale(nodo, dove);
    expect(nodo.parentElement).toBe(dove);
  });

  it('restituisce un\'azione con `destroy` (altrimenti Svelte non può pulire)', () => {
    const { nodo } = nodoInCard();
    const az = portale(nodo);
    expect(az).toBeDefined();
    expect(typeof (az as { destroy?: unknown }).destroy).toBe('function');
  });

  it('in `destroy` il nodo lascia <body> e torna dov\'era', () => {
    const { card, nodo } = nodoInCard();
    const az = portale(nodo) as { destroy: () => void };
    az.destroy();
    expect(nodo.parentElement).toBe(card);
  });

  it('`destroy` non lascia il nodo in <body> nemmeno se il posto di prima è sparito', () => {
    const { card, nodo } = nodoInCard();
    const az = portale(nodo) as { destroy: () => void };
    card.remove();
    expect(() => az.destroy()).not.toThrow();
    expect(nodo.parentElement).not.toBe(document.body);
  });

  it('il contenuto del nodo si porta dietro', () => {
    const { nodo } = nodoInCard();
    nodo.textContent = 'Spiegazione';
    portale(nodo);
    expect(document.body.textContent).toContain('Spiegazione');
  });
});
