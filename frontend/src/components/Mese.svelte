<script lang="ts">
  import { nomeMese, spostaMese } from '../lib/date';

  let { mese = $bindable() }: { mese: string } = $props();
</script>

<div class="mese">
  <button class="icon-btn" aria-label="Mese precedente" onclick={() => (mese = spostaMese(mese, -1))}>‹</button>
  <div class="d titolo">{nomeMese(mese)}</div>
  <button class="icon-btn" aria-label="Mese successivo" onclick={() => (mese = spostaMese(mese, 1))}>›</button>
</div>

<style>
  .mese { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); }
  .icon-btn { font-size: 22px; }
  .titolo { font-size: var(--text-2xl); font-weight: 800; text-transform: capitalize; }
</style>
