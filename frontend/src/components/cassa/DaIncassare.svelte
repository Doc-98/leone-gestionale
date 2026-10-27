<script lang="ts">
  import Icona from '../Icona.svelte';
  import type { DaIncassare } from '../../lib/api';
  import { dataBreve } from '../../lib/date';
  import { euro } from '../../lib/listino';

  /**
   * I pagamenti con un metodo «da incassare» (bonifici) non ancora arrivati: si segnano quando arrivano sul conto.
   * Più vecchi sono, più si colorano: arancio dopo 3 giorni, rosso dopo 7.
   */
  let { aperti, oggi, onincassa }: { aperti: DaIncassare[]; oggi: string; onincassa: (x: DaIncassare) => void } = $props();

  const giorni = (d: string) => Math.round((new Date(oggi).getTime() - new Date(d).getTime()) / 86_400_000);
  let quanti = $state(5);
  const totale = $derived(aperti.reduce((t, x) => t + Math.round(x.importo * 100), 0) / 100);
</script>

<section class="blocco">
  <div class="testa"><h2 class="lbl">Da incassare{aperti.length ? ` · ${aperti.length}` : ''}</h2><span class="grow"></span>
    {#if aperti.length}<span class="m tot">{euro(totale)}</span>{/if}</div>
  <ul class="lista">
    {#each aperti.slice(0, quanti) as x (x.id)}
      {@const g = giorni(x.data)}
      <li class="card" class:vecchio={g >= 7} class:medio={g >= 3 && g < 7}>
        <span class="riga"><strong class="grow">{x.pagante}</strong><span class="m imp">{euro(x.importo)}</span></span>
        <span class="muted small">{x.metodo} del {dataBreve(x.data)}{x.riferimento ? ` · ${x.riferimento}` : ''}{x.per.length ? ` · ${x.per.join(' · ')}` : ''}</span>
        <span class="riga">
          <span class="quando">{g <= 0 ? 'registrato oggi' : g === 1 ? 'in attesa da ieri' : `in attesa da ${g} giorni`}</span><span class="grow"></span>
          <button class="btn btn-secondary btn-sm" onclick={() => onincassa(x)}><Icona nome="check" />È arrivato</button>
        </span>
      </li>
    {:else}
      <li class="vuoto muted small">Niente da incassare.</li>
    {/each}
  </ul>
  {#if aperti.length > quanti}<button class="btn btn-secondary btn-sm tutti" onclick={() => (quanti = aperti.length)}>Mostra tutti i {aperti.length}</button>{/if}
</section>

<style>
  .blocco { display: flex; flex-direction: column; gap: var(--space-8); }
  .testa { display: flex; align-items: center; min-height: 28px; }
  .testa h2 { margin: 0; }
  .tot { font-weight: 700; color: var(--warn); font-size: var(--text-md); }
  .lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-8); }
  .lista .card { padding: var(--space-12) var(--space-14); display: flex; flex-direction: column; gap: var(--space-6); box-shadow: inset 4px 0 0 var(--line); }
  .lista .card.medio { box-shadow: inset 4px 0 0 var(--warn); }
  .lista .card.vecchio { box-shadow: inset 4px 0 0 var(--ferma); }
  .riga { display: flex; align-items: center; gap: var(--space-8); }
  .imp { font-weight: 700; }
  .quando { font-size: var(--text-xs); font-weight: 700; color: var(--muted); }
  .medio .quando { color: var(--warn); }
  .vecchio .quando { color: var(--ferma); }
  .tutti { align-self: center; }
  .vuoto { padding: var(--space-12); border: 1px dashed var(--line-dashed); border-radius: var(--radius-md); }
</style>
