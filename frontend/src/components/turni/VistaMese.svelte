<script lang="ts">
  import type { Turno } from '../../lib/api';
  import { formatOre, giornoEsteso } from '../../lib/date';
  import { areaDi, attivo, caselleMese, conMe, nomiCon, NOMI_AREA, orario, type Area } from '../../lib/turni';

  /**
   * Il mese (variante B del mockup): il calendario con una barretta colorata nei giorni di turno e le ore; toccando un
   * giorno compare il dettaglio con le richieste.
   */
  let { mese, turni, staff, oggi, nota, onchiedi }: {
    mese: string;
    turni: Turno[];
    staff: Turno[];
    oggi: string;
    nota: (t: Turno) => string | null;
    onchiedi: (t: Turno, tipo: 'SOSTITUZIONE' | 'SCAMBIO' | 'CAMBIO_ORARIO') => void;
  } = $props();

  let scelto = $state<string | null>(null);
  const caselle = $derived(caselleMese(mese));
  const delGiorno = (g: string) => turni.filter((t) => t.data === g && attivo(t));
  // il giorno scelto: quello toccato, altrimenti oggi se è nel mese, altrimenti il primo con un turno
  const giorno = $derived(scelto && scelto.slice(0, 7) === mese ? scelto
    : oggi.slice(0, 7) === mese ? oggi : (turni.filter(attivo).map((t) => t.data).sort()[0] ?? `${mese}-01`));
  const delScelto = $derived(delGiorno(giorno).sort((a, b) => a.inizio.localeCompare(b.inizio)));
  const aree: Area[] = ['m', 'f', 'p', 'a'];
</script>

<div class="card calendario">
  <div class="intest">{#each ['L', 'M', 'M', 'G', 'V', 'S', 'D'] as l, i (i)}<span class="lbl">{l}</span>{/each}</div>
  <div class="griglia">
    {#each caselle as g, i (g ?? `v${i}`)}
      {#if g}
        {@const suoi = delGiorno(g)}
        <button type="button" class="cella ar-{suoi[0] ? areaDi(suoi[0].postazione) : 'x'}" class:oggi={g === oggi} class:scelto={g === giorno}
          class:passato={g < oggi} class:vuoto={!suoi.length} aria-pressed={g === giorno} onclick={() => (scelto = g)}
          aria-label={`${giornoEsteso(g)}${suoi.length ? `, ${suoi.length} turni` : ', libero'}`}>
          <span class="n">{Number(g.slice(8))}</span><span class="b"></span>
          <span class="h">{suoi.length ? `${formatOre(suoi.reduce((s, t) => s + Number(t.ore), 0))} h` : ''}</span>
        </button>
      {:else}
        <span></span>
      {/if}
    {/each}
  </div>
  <div class="legenda">
    {#each aree as a (a)}<span class="ar-{a}"><i></i>{NOMI_AREA[a]}</span>{/each}
  </div>
</div>

<section class="giorno">
  <span class="lbl">{giornoEsteso(giorno)}{giorno === oggi ? ' · oggi' : ''}</span>
  {#each delScelto as t (t.id)}
    {@const con = conMe(t, staff)}
    {@const n = nota(t)}
    <div class="card dett ar-{areaDi(t.postazione)}">
      <span class="sopra"><span class="m orario">{orario(t)}</span><span class="muted">{formatOre(Number(t.ore))} h</span></span>
      <span class="dove"><span class="segno-area"></span><b>{t.postazione}</b></span>
      <span class="muted small">{nomiCon(con)}</span>
      {#if n}<span class="nota">{n}</span>{/if}
      {#if t.data >= oggi}
        <div class="azioni">
          <button class="btn btn-secondary btn-sm" onclick={() => onchiedi(t, 'SOSTITUZIONE')}>Sostituzione</button>
          <button class="btn btn-secondary btn-sm" onclick={() => onchiedi(t, 'SCAMBIO')}>Scambio</button>
          <button class="btn btn-secondary btn-sm" onclick={() => onchiedi(t, 'CAMBIO_ORARIO')}>Orario</button>
        </div>
      {/if}
    </div>
  {:else}
    <div class="card libero"><span class="muted">Giorno libero.</span></div>
  {/each}
</section>

<style>
  .calendario { padding: var(--space-8) var(--space-6); }
  .intest, .griglia { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); text-align: center; }
  .intest { padding-bottom: var(--space-4); }
  .griglia { gap: 2px; }
  .cella { display: flex; flex-direction: column; align-items: center; gap: 3px; min-height: 58px; padding: var(--space-6) 0 var(--space-4); border: none; border-radius: 10px;
    background: none; font: inherit; color: var(--ink); cursor: pointer; }
  .cella .n { font-size: var(--text-md); font-weight: 600; }
  .cella .b { width: 70%; height: 6px; border-radius: 3px; background: var(--a); }
  .cella.vuoto .b { background: transparent; }
  .cella .h { font-size: 10px; color: var(--muted); white-space: nowrap; }
  .cella.oggi { box-shadow: inset 0 0 0 1px var(--cloro); }
  .cella.oggi .n { color: var(--cloro-ink); }
  .cella.scelto { background: var(--cloro-soft); box-shadow: inset 0 0 0 2px var(--cloro); }
  .cella.passato { opacity: 0.5; }
  .legenda { display: flex; flex-wrap: wrap; gap: var(--space-6) var(--space-14); padding: var(--space-8) var(--space-8) 2px; font-size: var(--text-xs); color: var(--muted); }
  .legenda span { display: inline-flex; align-items: center; gap: 5px; }
  .legenda i { width: 10px; height: 6px; border-radius: 3px; background: var(--a); display: inline-block; }
  .giorno { display: flex; flex-direction: column; gap: var(--space-8); }
  .dett { padding: var(--space-14); display: flex; flex-direction: column; gap: var(--space-8); }
  .sopra { display: flex; align-items: baseline; gap: var(--space-10); }
  .orario { font-size: 22px; font-weight: 600; white-space: nowrap; }
  .dove { display: flex; align-items: center; gap: var(--space-8); min-width: 0; }
  .dove b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .nota { font-size: var(--text-xs); font-weight: 700; color: var(--warn); }
  .azioni { display: flex; gap: var(--space-8); }
  .azioni .btn { flex: 1; }
  .libero { padding: var(--space-14); }
</style>
