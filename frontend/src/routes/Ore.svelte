<script lang="ts">
  import Mese from '../components/Mese.svelte';
  import MieOre from '../components/ore/MieOre.svelte';
  import OreStaff from '../components/ore/OreStaff.svelte';
  import { api, messaggioErrore, type Riepilogo, type Turno } from '../lib/api';
  import { aggiungiGiorni, meseIso, spostaMese } from '../lib/date';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Ore e compensi (ADR 0022): «Le mie ore» per chiunque lavori; con il permesso «Ore dello staff» anche «Lo staff»,
   * con l'interruttore in alto. Lo staff si vede per gruppo (le mansioni che si coordinano) o, per chi li vede tutti,
   * anche tutti insieme.
   */
  const coordinatore = $derived(sessione.puo('ORE_STAFF'));
  let vista = $state<'mie' | 'staff'>(leggiPreferenza<'mie' | 'staff'>('ore.vista', 'mie', ['mie', 'staff']));
  const efficace = $derived(coordinatore ? vista : 'mie');
  let mese = $state(meseIso(new Date()));
  let errore = $state('');

  let mio = $state<Riepilogo | null>(null);
  let mioPrima = $state<Riepilogo | null>(null);
  let turni = $state<Turno[]>([]);
  let tutti = $state<Riepilogo | null>(null);
  let tuttiPrima = $state<Riepilogo | null>(null);
  let delGruppo = $state<Riepilogo | null>(null);
  let gruppo = $state<string | null>(null);

  $effect(() => {
    const m = mese;
    errore = '';
    if (efficace === 'mie') {
      const fine = aggiungiGiorni(`${spostaMese(m, 1)}-01`, -1);
      api.riepilogoMio(m).then((r) => (mio = r)).catch((e) => (errore = messaggioErrore(e)));
      api.riepilogoMio(spostaMese(m, -1)).then((r) => (mioPrima = r)).catch(() => (mioPrima = null));
      api.turniMiei(`${m}-01`, fine).then((t) => (turni = t)).catch(() => (turni = []));
    } else {
      api.riepilogo(m).then((r) => {
        tutti = r;
        if (gruppo && !r.gruppi.some((g) => g.id === gruppo)) gruppo = null;
      }).catch((e) => (errore = messaggioErrore(e)));
      api.riepilogo(spostaMese(m, -1)).then((r) => (tuttiPrima = r)).catch(() => (tuttiPrima = null));
    }
  });
  $effect(() => {
    const g = gruppo;
    const m = mese;
    delGruppo = null;
    if (efficace === 'staff' && g) api.riepilogo(m, g).then((r) => (delGruppo = r)).catch((e) => (errore = messaggioErrore(e)));
  });

  function cambia(v: 'mie' | 'staff') {
    vista = v;
    salvaPreferenza('ore.vista', v);
  }
</script>

<section class="page ore">
  <div class="testa">
    {#if coordinatore}
      <div class="seg vista" role="group" aria-label="Quali ore">
        <button type="button" aria-pressed={efficace === 'mie'} onclick={() => cambia('mie')}>Le mie ore</button>
        <button type="button" aria-pressed={efficace === 'staff'} onclick={() => cambia('staff')}>Lo staff</button>
      </div>
    {/if}
    <span class="grow"></span>
    <div class="mese"><Mese bind:mese /></div>
  </div>
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if efficace === 'mie'}
    <MieOre {mese} {mio} prima={mioPrima} {turni} />
  {:else if tutti}
    <OreStaff {mese} {tutti} {delGruppo} prima={tuttiPrima} bind:gruppo puoTariffe={sessione.puo('ANAGRAFICHE_STAFF')} />
  {:else if !errore}
    <p class="muted">Caricamento…</p>
  {/if}
</section>

<style>
  .ore { max-width: 1400px; }
  .testa { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-12); }
  .vista { grid-template-columns: 1fr 1fr; }
  .vista button { padding: 0 var(--space-16); white-space: nowrap; }
  .mese { min-width: 280px; }
  .nomargin { margin: 0; }
  @media (max-width: 600px) { .vista, .mese { width: 100%; } }
</style>
