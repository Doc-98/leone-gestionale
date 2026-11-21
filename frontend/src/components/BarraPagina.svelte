<script lang="ts">
  import type { Snippet } from 'svelte';
  import { ambiente } from '../lib/ambiente.svelte';
  import { schermo } from '../lib/schermo.svelte';

  /**
   * La barra di pagina (ADR 0028), disegnata dalla pagina: il titolo e, di solito, `<Periodo>`, `<SceltaVista>` e le
   * azioni della pagina (cerca, file, «⋯»).
   * Computer: il titolo a sinistra; a destra periodo, vista e azioni su una riga.
   * Telefono e tablet: il titolo con le azioni a destra; sotto, su una riga, il periodo a sinistra e la vista a destra.
   * `sopra` è la riga piccola sopra il titolo; senza, sul telefono c'è il nome della società.
   */
  let { titolo, sopra, periodo, vista, azioni }: {
    titolo: string; sopra?: string; periodo?: Snippet; vista?: Snippet; azioni?: Snippet;
  } = $props();

  const riga = $derived(sopra ?? (schermo.larga ? '' : ambiente.societa));
</script>

<header class="barra" class:larga={schermo.larga}>
  <div class="tit">
    {#if riga}<div class="lbl nowrap">{riga}</div>{/if}
    <h1 class="page-title">{titolo}</h1>
  </div>
  {#if schermo.larga}
    <div class="comandi">
      {#if periodo}{@render periodo()}{/if}
      {#if vista}{@render vista()}{/if}
      {#if azioni}<span class="gruppo-azioni">{@render azioni()}</span>{/if}
    </div>
  {:else}
    {#if azioni}<span class="gruppo-azioni az">{@render azioni()}</span>{/if}
    {#if periodo || vista}
      <div class="seconda">
        <span class="p">{#if periodo}{@render periodo()}{/if}</span>
        {#if vista}<span class="v">{@render vista()}</span>{/if}
      </div>
    {/if}
  {/if}
</header>

<style>
  .barra { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-8) var(--space-12); }
  .tit { flex: 1 1 auto; min-width: 0; }
  .barra :global(.page-title) { white-space: nowrap; }
  .comandi { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--space-8) var(--space-16); min-width: 0; }
  .az { margin-left: auto; }
  .seconda { flex: 1 0 100%; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-8); min-width: 0; }
  .p { min-width: 0; display: inline-flex; }
  .v { display: inline-flex; margin-left: auto; }
</style>
