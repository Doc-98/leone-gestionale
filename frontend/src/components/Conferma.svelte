<script lang="ts">
  import { onDestroy, type Snippet } from 'svelte';
  import { creaConferma, etichettaArmata } from '../lib/conferma';

  /**
   * Pulsante **con testo** per le azioni che non si annullano: la stessa conferma in due tocchi di `Azione` (design
   * system, «Azioni»). Il primo tocco arma il pulsante, che al posto del contenuto mostra la domanda («Elimina?»); il
   * secondo esegue. Si disarma da solo dopo 5 secondi o uscendo dal pulsante.
   * - `class`: le classi di pulsante di sempre (`btn btn-danger`, `mini-btn rosso`, `x-btn`…): a riposo l'aspetto non cambia.
   * - `domanda`: breve, in forma di domanda («Elimina?», «Rimuovi?», «Annulla?»).
   * - `tono`: il colore da armato, come in `Azione`: `danger` (di norma: eliminare, togliere, annullare) o `cloro` per le
   *   azioni che non distruggono niente ma non si annullano («Nuova chiave?»).
   * - `etichetta`: il nome per lo screen reader a riposo, per i pulsanti senza testo a vista (la X).
   * Armato si capisce dal testo e dal bordo pieno (`.conf-armata` in app.css), non solo dal colore. A riposo il pulsante è
   * quello di sempre; armato tiene almeno la stessa larghezza, così non «salta».
   */
  let {
    domanda,
    onclick,
    children,
    class: classe = '',
    etichetta,
    title,
    disabled = false,
    type = 'button',
    role,
    tono = 'danger',
  }: {
    domanda: string;
    onclick: () => unknown;
    children: Snippet;
    class?: string;
    etichetta?: string;
    title?: string;
    disabled?: boolean;
    type?: 'button' | 'submit';
    role?: 'menuitem';
    tono?: 'danger' | 'cloro';
  } = $props();

  let armata = $state(false);
  const due = creaConferma((v) => (armata = v));
  onDestroy(due.disarma);

  function clic(e: MouseEvent) {
    if (!due.tocca()) {
      e.preventDefault();
      return;
    }
    onclick();
  }
</script>

<button
  {type}
  {role}
  class="{classe} conf"
  class:conf-armata={armata}
  class:conf-cloro={tono === 'cloro'}
  aria-label={armata ? etichettaArmata(domanda) : etichetta}
  title={armata ? undefined : title}
  {disabled}
  onclick={clic}
  onblur={due.disarma}
>
  <!-- armato, la domanda sta nella stessa cella del contenuto (che resta, invisibile): a riposo il pulsante è quello di sempre, armato non diventa mai più stretto -->
  <span class="conf-w">
    <span class="conf-c" class:conf-nascosto={armata} aria-hidden={armata}>{@render children()}</span>
    {#if armata}<span class="conf-c">{domanda}</span>{/if}
  </span>
</button>
