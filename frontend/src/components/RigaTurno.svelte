<script lang="ts">
  import type { Turno } from '../lib/api';
  import { formatOre, ora } from '../lib/date';

  let { turno, persona = false, onclick, extra }: { turno: Turno; persona?: boolean; onclick?: () => void; extra?: string } = $props();
  const scoperto = $derived(persona && !turno.persona && turno.stato === 'PIANIFICATO');
</script>

<svelte:element this={onclick ? 'button' : 'div'} class="card turno" class:annullato={turno.stato === 'ANNULLATO'} class:scoperto role={onclick ? undefined : 'listitem'} {onclick}>
  <span class="m orario">{ora(turno.inizio)}–{ora(turno.fine)}</span>
  <span class="dove">
    <span class="postazione">{turno.postazione}</span>
    <span class="muted small">
      {#if persona}{scoperto ? 'Da assegnare' : turno.persona?.nome ?? '—'}{:else}{turno.mansione}{/if}{extra ? ` · ${extra}` : ''}
    </span>
  </span>
  <span class="m muted ore">{turno.stato === 'ANNULLATO' ? 'annullato' : `${formatOre(Number(turno.ore))} h`}</span>
</svelte:element>

<style>
  .turno { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-10) var(--space-14); min-height: 56px; width: 100%; text-align: left; color: var(--ink); font: inherit; }
  button.turno { cursor: pointer; }
  .orario { font-size: var(--text-base); flex-shrink: 0; }
  .dove { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .postazione { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .ore { font-size: var(--text-sm); white-space: nowrap; }
  .annullato .orario, .annullato .postazione { text-decoration: line-through; color: var(--muted); }
  /* Scoperto: bordo tratteggiato e testo d'avviso, riconoscibile anche senza colore */
  .scoperto { border-style: dashed; border-color: var(--warn-line); background: var(--warn-bg); }
  .scoperto .small { color: var(--warn); font-weight: 600; }
</style>
