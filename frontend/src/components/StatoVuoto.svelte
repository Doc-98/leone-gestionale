<script lang="ts">
  import Icona from './Icona.svelte';
  import { CARICO } from '../lib/testi';

  /**
   * Elenco vuoto e caricamento (design system, «Stato vuoto e caricamento»): una riga grigia dentro la card.
   * - vuoto: «Nessun prezzo.» e, solo se chi guarda può creare e non c'è già una riga «Aggiungi», a destra il pulsante;
   * - `caricamento`: la stessa riga con «Carico…» (letto dallo screen reader), senza «Aggiungi» e senza rotelle.
   * Il componente non mette margini: li dà il contenitore. `come` è l'elemento: `li` dentro un elenco, `tr` dentro una
   * tabella (disegna `<tr><td colspan={colonne}>`), altrimenti `div`.
   */
  let {
    testo,
    caricamento = false,
    aggiungi,
    come = 'div',
    colonne = 1,
  }: {
    testo: string;
    caricamento?: boolean;
    aggiungi?: { testo: string; onclick: () => void };
    come?: 'div' | 'li' | 'tr';
    colonne?: number;
  } = $props();
</script>

{#snippet riga()}
  {#if caricamento}
    <span class="testo" role="status">{CARICO}</span>
  {:else}
    <span class="testo">{testo}</span>
    {#if aggiungi}
      <button type="button" class="btn btn-sm btn-quieto" onclick={aggiungi.onclick}><Icona nome="piu" />{aggiungi.testo}</button>
    {/if}
  {/if}
{/snippet}

{#if come === 'tr'}
  <tr class="stato-vuoto"><td colspan={colonne}><div class="riga">{@render riga()}</div></td></tr>
{:else if come === 'li'}
  <li class="stato-vuoto riga">{@render riga()}</li>
{:else}
  <div class="stato-vuoto riga">{@render riga()}</div>
{/if}

<style>
  .riga { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); min-height: 44px; }
  .stato-vuoto { background: var(--surface-sunken); color: var(--muted); font-size: var(--text-sm); }
  li.stato-vuoto, div.stato-vuoto { padding: var(--space-4) var(--space-14); }
  /* in tabella lo sfondo e il filo di separazione stanno sulla cella: la riga di una tabella non ha padding */
  td { padding: var(--space-4) var(--space-14); background: var(--surface-sunken); border-top: 1px solid var(--line-soft); }
  .testo { white-space: nowrap; }
</style>
