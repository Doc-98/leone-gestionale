<script lang="ts">
  import type { GiornataBanco } from '../../lib/api';
  import { posizioneLinea } from '../../lib/banco';

  /**
   * La linea della giornata, dalle 7 alle 22: un segno per ogni passaggio, i rossi più alti e cliccabili (riaprono
   * l'esito). Il segno «adesso» dice dove siamo.
   */
  let { linea, adesso, onapri }: { linea: GiornataBanco['linea']; adesso: Date; onapri: (id: string) => void } = $props();

  const ORE = Array.from({ length: 15 }, (_, i) => i + 7);
  const verdi = $derived(linea.filter((t) => t.verde));
  const rossi = $derived(linea.filter((t) => !t.verde));
</script>

<div class="linea" role="group" aria-label="Passaggi della giornata dalle 7 alle 22">
  <div class="ore" aria-hidden="true">{#each ORE as o (o)}<span>{o}</span>{/each}</div>
  {#each verdi as t (t.id)}<span class="tacca" class:u={t.verso === 'USCITA'} style:left="{posizioneLinea(t.istante)}%" aria-hidden="true"></span>{/each}
  {#each rossi as t (t.id)}
    <button type="button" class="tacca r" style:left="{posizioneLinea(t.istante)}%" title="{t.chi}: {t.motivo ?? 'rosso'}"
            aria-label="Riapri l'esito: {t.chi}, {t.motivo ?? 'rosso'}" onclick={() => onapri(t.id)}></button>
  {/each}
  <span class="adesso" style:left="{posizioneLinea(adesso.toISOString())}%" aria-hidden="true"></span>
</div>

<style>
  .linea { position: relative; height: 54px; border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line); overflow: hidden; }
  .ore { position: absolute; inset: auto 0 4px 0; display: flex; justify-content: space-between; padding: 0 6px; font-size: 11px; color: var(--muted);
    font-family: var(--font-mono); pointer-events: none; }
  .tacca { position: absolute; top: 14px; width: 2px; height: 16px; margin-left: -1px; border-radius: 1px; background: color-mix(in srgb, var(--passa) 55%, transparent); }
  .tacca.u { background: var(--line-strong); }
  .tacca.r { top: 8px; width: 6px; height: 24px; margin-left: -3px; border: none; padding: 0; border-radius: 3px; background: var(--ferma); cursor: pointer; }
  .tacca.r:hover, .tacca.r:focus-visible { outline: 2px solid var(--ferma); outline-offset: 2px; }
  .adesso { position: absolute; top: 4px; bottom: 18px; width: 2px; margin-left: -1px; background: var(--ink); opacity: 0.55; }
</style>
