<script lang="ts">
  import type { Turno } from '../../lib/api';
  import { aggiungiGiorni, formatOre, giornoEsteso, ora } from '../../lib/date';
  import { areaDi, attivo, conMe, nomiCon, fasce, giornoBreve, orario, posizione } from '../../lib/turni';

  /**
   * La settimana a colonne (variante C del mockup, per il PC): i turni come blocchi sull'orario, la linea dell'ora
   * attuale; a destra il turno scelto con le richieste. Il resto della colonna lo riempie la pagina (snippet «lato»).
   */
  let { lun, turni, staff, oggi, adesso, nota, onchiedi, lato }: {
    lun: string;
    turni: Turno[];
    staff: Turno[];
    oggi: string;
    adesso: number;
    nota: (t: Turno) => string | null;
    onchiedi: (t: Turno, tipo: 'SOSTITUZIONE' | 'SCAMBIO' | 'CAMBIO_ORARIO') => void;
    lato?: import('svelte').Snippet;
  } = $props();

  const PX = 43;
  let sceltoId = $state<string | null>(null);
  const giorni = $derived(Array.from({ length: 7 }, (_, i) => aggiungiGiorni(lun, i)));
  const validi = $derived(turni.filter(attivo));
  const f = $derived(fasce(validi));
  const ore = $derived(Array.from({ length: f.a - f.da }, (_, i) => f.da + i));
  const altezza = $derived((f.a - f.da) * PX);
  const ordinati = $derived([...validi].sort((a, b) => a.data.localeCompare(b.data) || a.inizio.localeCompare(b.inizio)));
  const scelto = $derived(validi.find((t) => t.id === sceltoId)
    ?? ordinati.find((t) => t.data > oggi || (t.data === oggi && posizione(t.fine, 0, 60) > adesso)) ?? ordinati[0] ?? null);
  const adessoTop = $derived(((adesso - f.da * 60) / 60) * PX);
</script>

<div class="colonne">
  <section class="card griglia-c">
    <div class="g" style:--h="{altezza}px">
      <span></span>
      {#each giorni as g (g)}
        {@const b = giornoBreve(g)}
        <span class="testa" class:oggi={g === oggi}><span class="lbl">{b.l}</span><b>{b.n}</b></span>
      {/each}
      <span class="ore">
        {#each ore as h, i (h)}<span class="m muted" style:top="{i * PX}px">{String(h).padStart(2, '0')}</span>{/each}
      </span>
      {#each giorni as g (g)}
        <span class="col" class:oggi={g === oggi} style:background-size="100% {PX}px">
          {#each validi.filter((t) => t.data === g) as t (t.id)}
            <button type="button" class="blocco ar-{areaDi(t.postazione)}" class:scelto={scelto?.id === t.id} class:passato={g < oggi}
              style:top="{posizione(t.inizio, f.da, PX)}px" style:height="{posizione(t.fine, f.da, PX) - posizione(t.inizio, f.da, PX) - 4}px"
              onclick={() => (sceltoId = t.id)} aria-label={`${giornoEsteso(t.data)}, ${orario(t)}, ${t.postazione}`}>
              <b class="m">{ora(t.inizio)}</b><span class="m">fino alle {ora(t.fine)}</span><span>{t.postazione}</span><span class="h">{formatOre(Number(t.ore))} h</span>
            </button>
          {/each}
          {#if g === oggi && adessoTop >= 0 && adessoTop <= altezza}<span class="adesso" style:top="{adessoTop}px"></span>{/if}
        </span>
      {/each}
    </div>
  </section>

  <aside class="lato">
    {#if scelto}
      {@const con = conMe(scelto, staff)}
      {@const n = nota(scelto)}
      <section class="card dett ar-{areaDi(scelto.postazione)}">
        <span class="lbl">{giornoEsteso(scelto.data)}{scelto.data === oggi ? ' · oggi' : ''}</span>
        <span class="sopra"><span class="m orario">{orario(scelto)}</span><span class="muted">{formatOre(Number(scelto.ore))} h</span></span>
        <span class="dove"><span class="segno-area"></span><b>{scelto.postazione}</b></span>
        <span class="muted small">{nomiCon(con)}</span>
        {#if n}<span class="nota">{n}</span>{/if}
        {#if scelto.data >= oggi}
          <div class="chiedi">
            <span class="lbl">Chiedi</span>
            <div class="tre">
              <button class="btn btn-secondary btn-sm" onclick={() => onchiedi(scelto, 'SOSTITUZIONE')}>Sostituzione</button>
              <button class="btn btn-secondary btn-sm" onclick={() => onchiedi(scelto, 'SCAMBIO')}>Scambio</button>
              <button class="btn btn-secondary btn-sm" onclick={() => onchiedi(scelto, 'CAMBIO_ORARIO')}>Orario</button>
            </div>
          </div>
        {/if}
      </section>
    {:else}
      <section class="card dett"><span class="muted">Nessun turno questa settimana.</span></section>
    {/if}
    {@render lato?.()}
  </aside>
</div>

<style>
  .colonne { display: flex; flex-wrap: wrap; gap: var(--space-20); align-items: flex-start; }
  .griglia-c { flex: 7 1 620px; min-width: 0; padding: var(--space-12) var(--space-12) var(--space-16); }
  .lato { flex: 3 1 300px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-14); }
  .g { display: grid; grid-template-columns: 48px repeat(7, minmax(0, 1fr)); }
  .testa { display: flex; flex-direction: column; align-items: center; padding: var(--space-4) 0 var(--space-10); }
  .testa b { font-family: var(--font-display); font-size: 22px; font-weight: 800; line-height: 1.1; }
  .testa.oggi, .testa.oggi .lbl { color: var(--cloro-ink); }
  .ore { position: relative; height: var(--h); }
  .ore span { position: absolute; right: var(--space-8); font-size: 11px; transform: translateY(-50%); }
  .col { position: relative; height: var(--h); border-left: 1px solid var(--line-soft);
    background-image: linear-gradient(to bottom, var(--line-soft) 1px, transparent 1px); }
  .col.oggi { background-color: color-mix(in srgb, var(--cloro) 6%, transparent); }
  .blocco { position: absolute; left: 4px; right: 4px; border-radius: 10px; padding: var(--space-8) var(--space-10); box-sizing: border-box; border: none; font: inherit;
    text-align: left; cursor: pointer; background: var(--a-bg); color: var(--a-fg); display: flex; flex-direction: column; gap: 2px; overflow: hidden; }
  .blocco b { font-size: var(--text-sm); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .blocco span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .blocco span { font-size: var(--text-xs); }
  .blocco .h { opacity: 0.8; }
  .blocco.scelto { box-shadow: inset 0 0 0 2px var(--a-fg); }
  .blocco.passato { opacity: 0.55; }
  .adesso { position: absolute; left: 0; right: 0; height: 2px; background: var(--ferma); }
  .dett { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); }
  .sopra { display: flex; align-items: baseline; gap: var(--space-10); }
  .orario { font-size: 24px; font-weight: 600; white-space: nowrap; }
  .dove { display: flex; align-items: center; gap: var(--space-8); min-width: 0; }
  .dove b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .nota { font-size: var(--text-xs); font-weight: 700; color: var(--warn); }
  .chiedi { display: flex; flex-direction: column; gap: var(--space-6); padding-top: var(--space-6); border-top: 1px solid var(--line-soft); }
  .tre { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-6); }
</style>
