<script lang="ts">
  import { api, messaggioErrore, NonAutenticato, type Turno } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';
  import { aggiungiGiorni, formatOre, giornoEsteso, iso, lunedi } from '../lib/date';
  import Settimana from '../components/Settimana.svelte';
  import RigaTurno from '../components/RigaTurno.svelte';
  import Foglio from '../components/Foglio.svelte';
  import RichiestaForm from './RichiestaForm.svelte';

  let settimana = $state(lunedi(iso(new Date())));
  let turni = $state<Turno[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let scelto = $state<Turno | null>(null);
  let messaggio = $state('');
  const oggi = iso(new Date());

  function carica() {
    caricamento = true;
    errore = '';
    api.turniMiei(settimana, aggiungiGiorni(settimana, 6))
      .then((t) => (turni = t))
      .catch((e) => (e instanceof NonAutenticato ? (sessione.utente = null) : (errore = messaggioErrore(e, 'Non riesco a caricare i turni.'))))
      .finally(() => (caricamento = false));
  }
  $effect(() => {
    void settimana;
    carica();
  });

  const giorni = $derived(Array.from({ length: 7 }, (_, i) => aggiungiGiorni(settimana, i)));
  const ore = $derived(turni.filter((t) => t.stato !== 'ANNULLATO').reduce((s, t) => s + Number(t.ore), 0));
  const modificabile = (t: Turno) => t.stato === 'PIANIFICATO' && t.data >= oggi;
</script>

<section class="page">
  <Settimana bind:lunediSettimana={settimana} sotto={caricamento ? '…' : `${turni.length} turni · ${formatOre(ore)} h`} />
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  {#each giorni as g (g)}
    {@const delGiorno = turni.filter((t) => t.data === g)}
    <div class="giorno" class:oggi={g === oggi}>
      <h2 class="lbl">{giornoEsteso(g)}{g === oggi ? ' · oggi' : ''}</h2>
      {#each delGiorno as t (t.id)}
        <RigaTurno turno={t} onclick={modificabile(t) ? () => ((scelto = t), (messaggio = '')) : undefined} />
      {:else}
        <p class="muted small nomargin">{caricamento ? '' : 'Libero'}</p>
      {/each}
    </div>
  {/each}
  <p class="muted small">Tocca un turno per chiedere un cambio orario, una sostituzione o uno scambio.</p>
</section>

{#if scelto}
  <Foglio titolo="Richiesta sul turno" chiudi={() => (scelto = null)}>
    <RichiestaForm turno={scelto} fatto={(m) => ((scelto = null), (messaggio = m))} />
  </Foglio>
{/if}

<style>
  .giorno { display: flex; flex-direction: column; gap: var(--space-6); }
  .giorno h2 { margin: 0; }
  .giorno.oggi h2 { color: var(--cloro); }
</style>
