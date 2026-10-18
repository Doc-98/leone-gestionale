<script lang="ts">
  import type { Snippet } from 'svelte';

  let { titolo, chiudi, children }: { titolo: string; chiudi: () => void; children: Snippet } = $props();

  function tasto(e: KeyboardEvent) {
    if (e.key === 'Escape') chiudi();
  }
</script>

<svelte:window onkeydown={tasto} />

<div class="velo" role="presentation" onclick={chiudi}></div>
<div class="foglio" role="dialog" aria-modal="true" aria-label={titolo}>
  <header>
    <h2>{titolo}</h2>
    <button class="link" onclick={chiudi}>Chiudi</button>
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
  header { display: flex; justify-content: space-between; align-items: center; gap: var(--space-12); margin-bottom: var(--space-12); }
  h2 { margin: 0; font-size: var(--text-xl); }
  .corpo { display: flex; flex-direction: column; gap: var(--space-14); }
</style>
