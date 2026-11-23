/**
 * Una coda di chiamate: partono una alla volta, nell'ordine dei tocchi, così le risposte non si scavalcano e il server
 * finisce come l'ultimo tocco. Un errore di una chiamata non ferma le successive (chi ha chiamato lo riceve).
 */
export function creaCoda(): <R>(chiamata: () => Promise<R>) => Promise<R> {
  let coda: Promise<unknown> = Promise.resolve();
  return <R>(chiamata: () => Promise<R>) => {
    const p = coda.then(chiamata);
    coda = p.catch(() => {});
    return p;
  };
}
