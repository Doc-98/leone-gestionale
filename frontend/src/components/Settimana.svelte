<script lang="ts">
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
  <button class="icon-btn" aria-label="Settimana precedente" onclick={() => (lunediSettimana = aggiungiGiorni(lunediSettimana, -7))}>‹</button>
  <div class="centro">
    <div class="d titolo">{titolo}</div>
    <div class="muted small m">{sotto}</div>
  </div>
  <button class="icon-btn" aria-label="Settimana successiva" onclick={() => (lunediSettimana = aggiungiGiorni(lunediSettimana, 7))}>›</button>
</div>
{#if lunediSettimana !== oggi}
  <button class="link small torna" onclick={() => (lunediSettimana = oggi)}>Torna a questa settimana</button>
{/if}

<style>
  .settimana { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); }
  .icon-btn { font-size: 22px; }
  .centro { text-align: center; }
  .titolo { font-size: var(--text-2xl); font-weight: 800; }
  .torna { align-self: center; margin-top: calc(-1 * var(--space-8)); }
</style>
