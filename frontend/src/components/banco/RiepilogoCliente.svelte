<script lang="ts">
  import Icona from '../Icona.svelte';
  import { api, type Conto, type SchedaAccessi, type SchedaBorsellino, type SchedaCliente, type TurnoAlBanco } from '../../lib/api';
  import { etichettaTurno } from '../../lib/banco';
  import { euro } from '../../lib/listino';
  import { colore } from '../../lib/piano';

  /**
   * Sotto l'esito, il riepilogo di chi è appena passato: tesseramento e certificato, quanto deve, credito e crediti,
   * braccialetto, i turni di oggi; e il link alla scheda completa, dove si sistema tutto.
   */
  let { personaId, turni }: { personaId: string; turni: TurnoAlBanco[] } = $props();

  let cliente = $state<SchedaCliente | null>(null);
  let accessi = $state<SchedaAccessi | null>(null);
  let conto = $state<Conto | null>(null);
  let borsellino = $state<SchedaBorsellino | null>(null);

  $effect(() => {
    const id = personaId;
    cliente = accessi = conto = borsellino = null;
    api.schedaCliente(id).then((c) => { if (id === personaId) cliente = c; }).catch(() => {});
    api.schedaAccessi(id).then((a) => { if (id === personaId) accessi = a; }).catch(() => {});
    api.conto(id).then((c) => { if (id === personaId) conto = c; }).catch(() => {});
    api.borsellino(id).then((b) => { if (id === personaId) borsellino = b; }).catch(() => {});
  });

  const tesserato = $derived(cliente?.tesseramenti.find((t) => t.valido));
  const certificato = $derived(cliente?.certificati[0]);
  const golden = $derived(accessi?.goldenPass.some((g) => g.attivo));
  const braccialetto = $derived(accessi?.braccialetti.find((b) => b.stato === 'ATTIVO') ?? accessi?.braccialetti[0]);
  const daPagare = $derived(conto?.totali.daPagare ?? 0);
</script>

<section class="card riepilogo" aria-label="Riepilogo del cliente">
  <div class="testa">
    <div class="grow">
      <span class="lbl">Riepilogo del cliente</span>
      <div class="tag-riga">
        {#if cliente}
          {#if tesserato}<span class="tag t-soft">Tesserato {tesserato.stagione}</span>{:else}<span class="tag t-warn">Non tesserato</span>{/if}
          {#if !certificato}<span class="tag t-warn">Senza certificato</span>{:else if !certificato.valido}<span class="tag t-warn">Certificato scaduto</span>{/if}
          {#if golden}<span class="tag oro">Golden pass</span>{/if}
        {:else}<span class="muted small">…</span>{/if}
      </div>
    </div>
    <a class="btn btn-secondary btn-sm" href={`#/clienti/${personaId}`}><Icona nome="freccia" />Scheda completa</a>
  </div>

  <div class="kpis">
    <div class="kpi" class:warn={daPagare > 0}><span class="lbl">Da pagare</span><span class="v m">{conto ? euro(daPagare) : '…'}</span>
      <span class="n">{!conto ? '' : daPagare > 0 ? 'da registrare' : 'in regola'}</span></div>
    <div class="kpi"><span class="lbl">Credito</span><span class="v m">{borsellino ? euro(borsellino.saldo.euro) : '…'}</span><span class="n">nel borsellino</span></div>
    <div class="kpi"><span class="lbl">Phon e docce</span><span class="v m">{borsellino ? `${borsellino.saldo.crediti} crediti` : '…'}</span><span class="n">{borsellino ? `= ${euro(borsellino.saldo.valoreCrediti)}` : ''}</span></div>
    <div class="kpi" class:warn={accessi && braccialetto?.stato !== 'ATTIVO'}><span class="lbl">Braccialetto</span><span class="v m">{accessi ? braccialetto?.codice ?? 'Nessuno' : '…'}</span>
      <span class="n">{braccialetto && braccialetto.stato !== 'ATTIVO' ? braccialetto.stato.toLowerCase() : ''}</span></div>
  </div>

  <div class="oggi">
    <span class="lbl">Oggi</span>
    {#each turni as t (t.lezioneId + t.inizio)}
      <div class="turno {colore(t.tipo ?? 'ALTRO')}"><span class="liv" style:background={t.colore ?? 'var(--c-line)'} aria-hidden="true"></span>
        <b>{etichettaTurno(t)}</b><span class="muted">–{t.fine.slice(0, 5)} · {t.iscritti} iscritti</span></div>
    {:else}
      <span class="muted small">Nessun corso oggi.</span>
    {/each}
  </div>
</section>

<style>
  .riepilogo { display: flex; flex-direction: column; gap: var(--space-14); padding: var(--space-16); }
  .testa { display: flex; align-items: center; gap: var(--space-10); }
  .tag-riga { display: flex; gap: var(--space-6); flex-wrap: wrap; margin-top: 4px; }
  .oro { background: var(--oro-bg); color: var(--oro-fg); }
  .kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-8); }
  .kpi { display: flex; flex-direction: column; gap: 2px; padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--carta); min-width: 0; }
  .kpi.warn { background: var(--warn-bg); }
  .kpi .v { font-size: var(--text-md, 15px); font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .kpi .n { font-size: var(--text-xs); color: var(--muted); min-height: 1em; }
  .oggi { display: flex; flex-direction: column; gap: var(--space-6); padding-top: var(--space-12); border-top: 1px solid var(--line-soft); }
  .turno { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-sm); }
  .liv { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
  @media (max-width: 640px) { .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
