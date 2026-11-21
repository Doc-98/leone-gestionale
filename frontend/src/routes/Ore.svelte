<script lang="ts">
  import BarraPagina from '../components/BarraPagina.svelte';
  import MieOre from '../components/ore/MieOre.svelte';
  import OreStaff from '../components/ore/OreStaff.svelte';
  import Periodo from '../components/Periodo.svelte';
  import SceltaVista from '../components/SceltaVista.svelte';
  import { api, messaggioErrore, type Riepilogo, type Turno } from '../lib/api';
  import { aggiungiGiorni, meseIso, spostaMese } from '../lib/date';
  import { inizioPeriodo } from '../lib/periodo';
  import { leggiPreferenza } from '../lib/preferenze';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Ore e compensi (ADR 0022): «Le mie ore» per chiunque lavori; con il permesso «Ore dello staff» anche «Lo staff»,
   * con l'interruttore in alto. Lo staff si vede per gruppo (le mansioni che si coordinano) o, per chi li vede tutti,
   * anche tutti insieme.
   */
  const coordinatore = $derived(sessione.puo('ORE_STAFF'));
  let scelta = $state<'mie' | 'staff'>(leggiPreferenza<'mie' | 'staff'>('ore.vista', 'mie', ['mie', 'staff']));
  const efficace = $derived(coordinatore ? scelta : 'mie');
  const OPZIONI = [
    { id: 'mie', nome: 'Le mie ore' },
    { id: 'staff', nome: 'Lo staff' },
  ];
  let inizio = $state(inizioPeriodo(meseIso(new Date()) + '-01', 'mese'));
  const mese = $derived(inizio.slice(0, 7));
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
</script>

{#snippet periodo()}
  <Periodo bind:inizio passo="mese" />
{/snippet}
{#snippet vistaOre()}
  <SceltaVista opzioni={OPZIONI} bind:valore={scelta} etichetta="Quali ore" preferenza="ore.vista" />
{/snippet}

<section class="page ore">
  <BarraPagina titolo="Ore e compensi" {periodo} vista={coordinatore ? vistaOre : undefined} />
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
</style>
