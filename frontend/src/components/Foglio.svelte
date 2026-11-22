<script lang="ts">
  import Azione from './Azione.svelte';
  import { onMount, type Snippet } from 'svelte';

  /**
   * Il foglio dei moduli: dal basso sul telefono, pannello a destra sul computer.
   * - `dalBasso`: il foglio sale dal basso, al centro, anche sul computer invece di aprirsi come pannello a destra
   *   (per i fogli corti, come quello del menu «⋯» sul telefono e sul tablet).
   * - `troncaTitolo`: il titolo sta su una riga e, se è lungo, finisce nei puntini (il nome intero resta nell'etichetta e nel `title`).
   * - Fuoco: all'apertura passa al foglio (che si annuncia con il suo titolo, senza far comparire la tastiera del telefono);
   *   alla chiusura torna a chi l'aveva, se c'è ancora.
   * - `dopoTitolo`: un pezzo accanto al titolo (la pillola «spento» di un elemento spento).
   */
  let { titolo, chiudi, children, dalBasso = false, dopoTitolo, troncaTitolo = false }: {
    titolo: string; chiudi: () => void; children: Snippet; dalBasso?: boolean; dopoTitolo?: Snippet; troncaTitolo?: boolean;
  } = $props();

  let foglio = $state<HTMLDivElement>();
  onMount(() => {
    const apriva = document.activeElement;
    foglio?.focus({ preventScroll: true });
    return () => {
      if (apriva instanceof HTMLElement && apriva !== document.body && document.contains(apriva)) apriva.focus({ preventScroll: true });
    };
  });

  function tasto(e: KeyboardEvent) {
    if (e.key === 'Escape') chiudi();
  }
</script>

<svelte:window onkeydown={tasto} />

<div class="velo" class:dal-basso={dalBasso} role="presentation" onclick={chiudi}></div>
<div class="foglio" class:dal-basso={dalBasso} role="dialog" aria-modal="true" aria-label={titolo} tabindex="-1" bind:this={foglio}>
  <header>
    <div class="tit"><h2 class:tronca={troncaTitolo} title={troncaTitolo ? titolo : undefined}>{titolo}</h2>{#if dopoTitolo}{@render dopoTitolo()}{/if}</div>
    <Azione cornice icona="chiudi" etichetta="Chiudi" onclick={chiudi} />
  </header>
  <div class="corpo">{@render children()}</div>
</div>

<style>
  .velo { position: fixed; inset: 0; background: rgba(16, 33, 43, 0.45); z-index: 40; }
  .foglio {
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 41; max-height: 88vh; overflow-y: auto;
    max-width: 640px; margin: 0 auto; background: var(--carta); border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    padding: var(--space-16) var(--space-16) calc(env(safe-area-inset-bottom, 0px) + var(--space-20));
  }
  .foglio:focus { outline: none; }
  /* Sul PC: pannello laterale a destra, la pagina resta visibile accanto */
  @media (min-width: 1024px) {
    .velo:not(.dal-basso) { background: rgba(16, 33, 43, 0.25); }
    .foglio:not(.dal-basso) {
      left: auto; top: 0; width: 440px; max-width: none; max-height: none; margin: 0;
      border-radius: 0; border-left: 1px solid var(--line); padding: var(--space-20) var(--space-20) var(--space-20);
      box-shadow: -12px 0 32px rgba(16, 33, 43, 0.12);
    }
  }
  header { display: flex; justify-content: space-between; align-items: center; gap: var(--space-12); margin-bottom: var(--space-12); }
  .tit { display: flex; align-items: center; gap: var(--space-8); min-width: 0; }
  h2 { margin: 0; font-size: var(--text-xl); }
  h2.tronca { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .corpo { display: flex; flex-direction: column; gap: var(--space-14); }
</style>
