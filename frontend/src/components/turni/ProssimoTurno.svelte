<script lang="ts">
  import type { Turno } from '../../lib/api';
  import { areaDi, conMe, nomiCon, orario, traQuanto } from '../../lib/turni';
  import { formatOre } from '../../lib/date';

  /** Il prossimo turno in evidenza: tra quanto, dove, con chi, e il bottone per chiedere un cambio. */
  let { turno, staff, oggi, adesso, nota, onchiedi }: {
    turno: Turno;
    staff: Turno[];
    oggi: string;
    adesso: number;
    nota: string | null;
    onchiedi: () => void;
  } = $props();

  let conChi = $state(false);
  const colleghi = $derived(conMe(turno, staff));
</script>

<section class="prossimo">
  <span class="riga"><span class="lbl">Il prossimo turno</span><span class="grow"></span><span class="quando">{traQuanto(turno, oggi, adesso)}</span></span>
  <span class="riga base"><span class="m orario">{orario(turno)}</span><span class="ore">{formatOre(Number(turno.ore))} h</span></span>
  <span class="riga ar-{areaDi(turno.postazione)}"><span class="segno-area"></span><b>{turno.postazione}</b></span>
  {#if nota}<span class="nota">{nota}</span>{/if}
  {#if conChi}
    <span class="con">{nomiCon(colleghi, 6)}</span>
  {/if}
  <span class="bottoni">
    <button type="button" class="pieno" onclick={onchiedi}>Chiedi un cambio</button>
    <button type="button" class="vuoto" aria-pressed={conChi} onclick={() => (conChi = !conChi)}>Chi c'è con me</button>
  </span>
</section>

<style>
  .prossimo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-16); border-radius: 16px; background: var(--hero); color: var(--on-fill); }
  .riga { display: flex; align-items: center; gap: var(--space-8); min-width: 0; }
  .riga b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .base { align-items: baseline; gap: var(--space-10); }
  .lbl { color: color-mix(in srgb, var(--on-fill) 70%, transparent); }
  .quando { font-size: var(--text-xs); font-weight: 700; padding: 3px 9px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--on-fill) 14%, transparent); white-space: nowrap; }
  .orario { font-size: 30px; font-weight: 600; letter-spacing: -0.02em; white-space: nowrap; }
  .ore { opacity: 0.7; font-size: var(--text-md); white-space: nowrap; }
  .nota, .con { font-size: var(--text-sm); opacity: 0.85; }
  .bottoni { display: flex; gap: var(--space-8); }
  .bottoni button { flex: 1; min-height: 44px; border-radius: var(--radius-md); font: inherit; font-size: var(--text-md); cursor: pointer; }
  .pieno { border: none; background: #fff; color: var(--ink); font-weight: 700; }
  .vuoto { border: 1px solid color-mix(in srgb, var(--on-fill) 35%, transparent); background: transparent; color: var(--on-fill); font-weight: 600; }
  .vuoto[aria-pressed='true'] { background: color-mix(in srgb, var(--on-fill) 14%, transparent); }
</style>
