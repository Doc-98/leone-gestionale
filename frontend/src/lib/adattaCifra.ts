/**
 * Le cifre dei riquadri stanno su una riga sola: se non ci stanno si rimpiccioliscono (mai a capo, mai puntini).
 * `corpoCheEntra` è il calcolo, `adattaCifra` l'azione Svelte (`use:adattaCifra`) che lo applica e lo rifà quando
 * cambiano il testo o la larghezza del riquadro.
 */

/** Il corpo (px) con cui un testo largo `larghezzaTesto` entra in `larghezzaDisponibile`, mai sotto `minimo`. */
export function corpoCheEntra(corpo: number, larghezzaTesto: number, larghezzaDisponibile: number, minimo: number): number {
  if (larghezzaTesto <= 0 || larghezzaDisponibile <= 0 || larghezzaTesto <= larghezzaDisponibile) return corpo;
  return Math.max(minimo, Math.floor((corpo * larghezzaDisponibile * 10) / larghezzaTesto) / 10);
}

const MINIMO = 11;

export function adattaCifra(nodo: HTMLElement) {
  let inCorso = false;
  const adatta = () => {
    if (inCorso) return;
    inCorso = true;
    nodo.style.fontSize = '';
    const corpo = parseFloat(getComputedStyle(nodo).fontSize);
    if (nodo.clientWidth > 0 && nodo.scrollWidth > nodo.clientWidth) {
      nodo.style.fontSize = `${corpoCheEntra(corpo, nodo.scrollWidth, nodo.clientWidth, MINIMO)}px`;
    }
    inCorso = false;
  };
  adatta();
  const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(adatta) : null;
  ro?.observe(nodo);
  const mo = typeof MutationObserver !== 'undefined' ? new MutationObserver(adatta) : null;
  mo?.observe(nodo, { childList: true, characterData: true, subtree: true });
  return { destroy() { ro?.disconnect(); mo?.disconnect(); } };
}
