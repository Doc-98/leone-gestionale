<script lang="ts">
  import { onMount } from 'svelte';
  import { sessione } from './lib/sessione.svelte';
  import Login from './routes/Login.svelte';
  import Turni from './routes/Turni.svelte';

  onMount(() => sessione.carica());
</script>

<main>
  {#if !sessione.pronto}
    <p class="muted centro">Caricamento…</p>
  {:else if !sessione.utente}
    <Login />
  {:else}
    <Turni />
  {/if}
</main>

<style>
  main {
    max-width: 640px;
    margin: 0 auto;
    padding: calc(env(safe-area-inset-top, 0px) + var(--space-20)) var(--space-16) calc(env(safe-area-inset-bottom, 0px) + var(--space-20));
  }
  .centro { text-align: center; margin-top: 40vh; }
</style>
