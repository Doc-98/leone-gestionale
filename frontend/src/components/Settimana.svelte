<script lang="ts">
  import Azione from './Azione.svelte';
  import { aggiungiGiorni, iso, lunedi, titoloSettimana } from '../lib/date';
  import { schermo } from '../lib/schermo.svelte';

  let { lunediSettimana = $bindable(), sotto = '' }: { lunediSettimana: string; sotto?: string } = $props();
  const oggi = lunedi(iso(new Date()));
  // mesi a tre lettere quando sono due, solo sul telefono: lì il titolo non va a capo
  const titolo = $derived(titoloSettimana(lunediSettimana, schermo.telefono));
</script>

<div class="settimana">
  <div class="centro grow">
    <div class="d titolo">{titolo}</div>
    <div class="muted small m nowrap">{sotto}</div>
  </div>
  <span class="gruppo-azioni">
    <Azione cornice icona="oggi" etichetta="Torna a questa settimana" disabled={lunediSettimana === oggi} onclick={() => (lunediSettimana = oggi)} />
    <Azione cornice icona="sx" etichetta="Settimana precedente" onclick={() => (lunediSettimana = aggiungiGiorni(lunediSettimana, -7))} />
    <Azione cornice icona="dx" etichetta="Settimana successiva" onclick={() => (lunediSettimana = aggiungiGiorni(lunediSettimana, 7))} />
  </span>
</div>



<style>
  .settimana { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); }
  .centro { min-width: 0; }
  .titolo { font-size: var(--text-2xl); font-weight: 800; }
</style>
