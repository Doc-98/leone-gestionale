<script lang="ts">
  import Azione from './Azione.svelte';
  import { aggiungiGiorni, daIso, iso, lunedi, MESI } from '../lib/date';

  let { lunediSettimana = $bindable(), sotto = '' }: { lunediSettimana: string; sotto?: string } = $props();
  const oggi = lunedi(iso(new Date()));
  const titolo = $derived.by(() => {
    const a = daIso(lunediSettimana);
    const b = daIso(aggiungiGiorni(lunediSettimana, 6));
    return a.getMonth() === b.getMonth()
      ? `${a.getDate()}–${b.getDate()} ${MESI[a.getMonth()]}`
      : `${a.getDate()} ${MESI[a.getMonth()]} – ${b.getDate()} ${MESI[b.getMonth()]}`;
  });
</script>

<div class="settimana">
  <div class="centro grow">
    <div class="d titolo">{titolo}</div>
    <div class="muted small m">{sotto}</div>
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
