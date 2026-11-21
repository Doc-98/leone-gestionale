/**
 * Azione Svelte: porta il nodo fuori da dov'è (di solito in `<body>`) e, quando l'elemento sparisce, lo rimette
 * al suo posto. Serve alle bolle e ai pannelli che devono uscire da una card con `overflow: hidden` o da un foglio.
 *
 *   <div use:portale>…</div>
 *   <div use:portale={altroNodo}>…</div>
 */
export function portale(nodo: HTMLElement, dove: HTMLElement = document.body) {
  const origine = nodo.parentNode;
  const dopo = nodo.nextSibling;
  dove.appendChild(nodo);
  return {
    destroy() {
      // se Svelte ha già tolto il nodo dalla pagina (chiusura di un {#if}) non c'è niente da rimettere a posto
      if (nodo.parentNode !== dove) return;
      if (origine && origine.isConnected) {
        // di nuovo nel suo posto, davanti al fratello di prima se c'è ancora
        origine.insertBefore(nodo, dopo && dopo.parentNode === origine ? dopo : null);
      } else {
        // il posto di prima non c'è più: il nodo non resta comunque a vagare in <body>
        nodo.remove();
      }
    },
  };
}

/**
 * Come `portale`, ma alla fine il nodo sparisce del tutto invece di tornare al suo posto. È quella da usare per ciò
 * che nasce e muore dentro un `{#if}` (bolle, menu): quando si smonta tutto il componente Svelte toglie dalla pagina
 * solo ciò che sta dove l'aveva messo, e un nodo portato in <body> resterebbe lì.
 */
export function portaleEffimero(nodo: HTMLElement, dove: HTMLElement = document.body) {
  dove.appendChild(nodo);
  return { destroy: () => nodo.remove() };
}
