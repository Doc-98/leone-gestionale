<script lang="ts">
  import type { Snippet } from 'svelte';
  import Avatar from './Avatar.svelte';
  import { ambiente } from '../lib/ambiente.svelte';
  import { schermo } from '../lib/schermo.svelte';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * La barra di pagina (ADR 0028), disegnata dalla pagina: il titolo e, di solito, `<Periodo>`, `<SceltaVista>` e le
   * azioni della pagina (cerca, file, «⋯»).
   * Computer: il titolo a sinistra; a destra periodo, vista e azioni su una riga.
   * Sotto i 1024 px: in alto la società a sinistra e l'account (avatar e nome, apre il menu «Altro») a destra; poi il titolo
   * con a destra le azioni della pagina o, se non ne ha, la vista; sotto il periodo (e la vista, se ci sono le azioni).
   * `sopra` è la riga piccola sopra il titolo; senza, sul telefono c'è il nome della società.
   */
  let { titolo, sopra, periodo, vista, azioni }: {
    titolo: string; sopra?: string; periodo?: Snippet; vista?: Snippet; azioni?: Snippet;
  } = $props();

  const riga = $derived(sopra ?? ambiente.societa);
  const utente = $derived(sessione.utente);
  const nomeCompleto = $derived(utente ? `${utente.nome} ${utente.cognome}`.trim() : '');
  const iniziali = $derived(utente ? ((utente.nome[0] ?? '') + (utente.cognome[0] ?? '')).toUpperCase() : '');
</script>

<header class="barra" class:larga={schermo.larga}>
  {#if schermo.larga}
    <div class="tit">
      {#if sopra}<div class="lbl nowrap">{sopra}</div>{/if}
      <h1 class="page-title">{titolo}</h1>
    </div>
    <div class="comandi">
      {#if periodo}{@render periodo()}{/if}
      {#if vista}{@render vista()}{/if}
      {#if azioni}<span class="gruppo-azioni">{@render azioni()}</span>{/if}
    </div>
  {:else}
    <div class="alto">
      <span class="lbl societa">{riga}</span>
      {#if utente}
        <button type="button" class="chi tap44" title={nomeCompleto} aria-label="{nomeCompleto}: altro, cambia password ed esci" aria-expanded={ambiente.menuAltro} aria-controls="menu-altro"
                onclick={() => (ambiente.menuAltro = !ambiente.menuAltro)}>
          <Avatar {iniziali} /><span class="nome">{nomeCompleto}</span>
        </button>
      {/if}
    </div>
    <div class="riga-titolo">
      <h1 class="page-title">{titolo}</h1>
      {#if azioni}<span class="gruppo-azioni">{@render azioni()}</span>
      {:else if vista}<span class="v">{@render vista()}</span>{/if}
    </div>
    {#if periodo || (azioni && vista)}
      <div class="seconda">
        {#if periodo}<span class="p">{@render periodo()}</span>{/if}
        {#if azioni && vista}<span class="v">{@render vista()}</span>{/if}
      </div>
    {/if}
  {/if}
</header>

<style>
  .barra { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-8) var(--space-12); }
  .tit { flex: 1 1 auto; min-width: 0; }
  .barra :global(.page-title) { white-space: nowrap; }
  .comandi { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--space-8) var(--space-16); min-width: 0; }
  /* sotto i 1024 px la barra è a righe, una sotto l'altra */
  .barra:not(.larga) { flex-direction: column; align-items: stretch; flex-wrap: nowrap; gap: var(--space-4); }
  .alto { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); min-width: 0; min-height: 24px; margin-bottom: var(--space-8); } /* 4 + 8 = 12px: stacca il nome dal titolo e lascia all area da 44px dell avatar 10px liberi sotto */
  .societa { flex: 0 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .chi { flex: 0 0 auto; max-width: 60%; min-width: 0; margin-left: auto; display: inline-flex; align-items: center; gap: var(--space-6); min-height: 24px; padding: 0 var(--space-4) 0 0;
    border: none; background: transparent; color: var(--muted); font: inherit; font-size: var(--text-xs); font-weight: 600; cursor: pointer; }
  .nome { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .riga-titolo { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-8) var(--space-12); min-width: 0; }
  .seconda { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-8); margin-top: var(--space-4); min-width: 0; }
  .p { min-width: 0; display: inline-flex; }
  .v { display: inline-flex; margin-left: auto; }
</style>
