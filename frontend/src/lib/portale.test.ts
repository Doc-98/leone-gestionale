// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { portale, portaleEffimero } from './portale';

afterEach(() => (document.body.innerHTML = ''));

function inCard() {
  const card = document.createElement('div');
  const prima = document.createElement('i');
  const nodo = document.createElement('span');
  const dopo = document.createElement('b');
  card.append(prima, nodo, dopo);
  document.body.appendChild(card);
  return { card, nodo, prima, dopo };
}

describe('portale', () => {
  it('in destroy il nodo torna prima del suo fratello di un tempo', () => {
    const { card, nodo, dopo } = inCard();
    portale(nodo).destroy();
    expect(nodo.nextSibling).toBe(dopo);
    expect(nodo.parentElement).toBe(card);
  });

  it('se Svelte ha già tolto il nodo dalla pagina, destroy non lo rimette', () => {
    const { nodo } = inCard();
    const az = portale(nodo);
    nodo.remove();
    az.destroy();
    expect(nodo.isConnected).toBe(false);
  });
});

describe('portaleEffimero', () => {
  it('porta il nodo in <body> e in destroy lo toglie del tutto', () => {
    const { card, nodo } = inCard();
    const az = portaleEffimero(nodo);
    expect(nodo.parentElement).toBe(document.body);
    az.destroy();
    expect(nodo.isConnected).toBe(false);
    expect(card.contains(nodo)).toBe(false);
  });
});
