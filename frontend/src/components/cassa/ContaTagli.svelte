<script lang="ts">
  import { sommaTagli, TAGLI } from '../../lib/cassa';
  import { euro } from '../../lib/listino';

  /** Contare il cassetto per tagli: quanti pezzi di ogni banconota e moneta; il totale va al campo «Contati». */
  let { totale }: { totale: (t: number) => void } = $props();
  let pezzi = $state<Record<string, number | null>>({});
  const somma = $derived(sommaTagli(pezzi));
  $effect(() => totale(somma));
  const nome = (v: number) => (v >= 1 ? `${v} €` : `${Math.round(v * 100)} cent`);
</script>

<div class="tagli" role="group" aria-label="Conta per tagli">
  {#each TAGLI as v (v)}
    <label class="taglio" class:moneta={v < 5}>
      <span class="v m">{nome(v)}</span>
      <span class="x">×</span>
      <input class="inp m" type="number" min="0" step="1" inputmode="numeric" bind:value={pezzi[String(v)]} placeholder="0" aria-label={`Pezzi da ${nome(v)}`} />
    </label>
  {/each}
  <span class="tot"><span>Totale contato</span><b class="m">{euro(somma)}</b></span>
</div>

<style>
  .tagli { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: var(--space-6) var(--space-10); padding: var(--space-10) var(--space-12);
    border-radius: var(--radius-md); background: var(--surface-sunken); }
  .taglio { display: grid; grid-template-columns: 56px 12px minmax(0, 1fr); align-items: center; gap: var(--space-4); font-size: var(--text-sm); }
  .v { font-weight: 700; text-align: right; }
  .moneta .v { color: var(--seg-fg); font-weight: 600; }
  .x { color: var(--muted); text-align: center; }
  .taglio .inp { height: 36px; padding: 0 var(--space-8); text-align: right; min-width: 0; appearance: textfield; -moz-appearance: textfield; }
  .taglio .inp::-webkit-inner-spin-button, .taglio .inp::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }
  .tot { grid-column: 1 / -1; display: flex; justify-content: space-between; padding-top: var(--space-6); border-top: 1px solid var(--line); font-size: var(--text-md); }
</style>
