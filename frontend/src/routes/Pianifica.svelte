<script lang="ts">
  import { api, messaggioErrore, type EsitoGenerazione, type Modello, type PersonaBreve, type Postazione, type Turno } from '../lib/api';
  import { aggiungiGiorni, formatOre, giornoEsteso, iso, lunedi, meseIso, nomeMese, spostaMese } from '../lib/date';
  import Settimana from '../components/Settimana.svelte';
  import RigaTurno from '../components/RigaTurno.svelte';
  import Foglio from '../components/Foglio.svelte';
  import TurnoForm from './TurnoForm.svelte';

  let settimana = $state(lunedi(iso(new Date())));
  let turni = $state<Turno[]>([]);
  let postazioni = $state<Postazione[]>([]);
  let personale = $state<PersonaBreve[]>([]);
  let modelli = $state<Modello[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let modifica = $state<{ turno: Turno | null; data: string } | null>(null);

  // Generazione del mese
  let generazione = $state(false);
  let modelloId = $state('');
  let meseDaGenerare = $state(spostaMese(meseIso(new Date()), 1));
  let esito = $state<EsitoGenerazione | null>(null);
  let generando = $state(false);

  Promise.all([api.postazioni(), api.personale(), api.modelli()])
    .then(([p, s, m]) => {
      postazioni = p;
      personale = s;
      modelli = m;
      modelloId = m[0]?.id ?? '';
    })
    .catch((e) => (errore = messaggioErrore(e)));

  function carica() {
    caricamento = true;
    api.turniTutti(settimana, aggiungiGiorni(settimana, 6))
      .then((t) => (turni = t))
      .catch((e) => (errore = messaggioErrore(e, 'Non riesco a caricare i turni.')))
      .finally(() => (caricamento = false));
  }
  $effect(() => {
    void settimana;
    carica();
  });

  const giorni = $derived(Array.from({ length: 7 }, (_, i) => aggiungiGiorni(settimana, i)));
  const attivi = $derived(turni.filter((t) => t.stato === 'PIANIFICATO'));
  const scoperti = $derived(attivi.filter((t) => !t.persona).length);
  const ore = $derived(attivi.reduce((s, t) => s + Number(t.ore), 0));

  function fatto(m: string) {
    modifica = null;
    messaggio = m;
    carica();
  }

  async function genera() {
    errore = '';
    esito = null;
    generando = true;
    try {
      esito = await api.generaMese(modelloId, meseDaGenerare);
      carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      generando = false;
    }
  }
</script>

<section class="page">
  <Settimana bind:lunediSettimana={settimana} sotto={caricamento ? '…' : `${attivi.length} turni · ${formatOre(ore)} h${scoperti ? ` · ${scoperti} da assegnare` : ''}`} />
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  <div class="row">
    <button class="btn btn-secondary grow" onclick={() => (generazione = true)}>Genera un mese dal modello</button>
  </div>

  {#each giorni as g (g)}
    {@const delGiorno = turni.filter((t) => t.data === g)}
    <div class="giorno">
      <div class="intesta">
        <h2 class="lbl">{giornoEsteso(g)}</h2>
        <button class="link small" onclick={() => (modifica = { turno: null, data: g })}>+ Turno</button>
      </div>
      {#each delGiorno as t (t.id)}
        <RigaTurno turno={t} persona onclick={t.stato === 'PIANIFICATO' ? () => ((modifica = { turno: t, data: g }), (messaggio = '')) : undefined} />
      {:else}
        <p class="muted small nomargin">{caricamento ? '' : 'Nessun turno'}</p>
      {/each}
    </div>
  {/each}
</section>

{#if modifica}
  <Foglio titolo={modifica.turno ? 'Modifica turno' : 'Nuovo turno'} chiudi={() => (modifica = null)}>
    <TurnoForm turno={modifica.turno} data={modifica.data} {postazioni} {personale} {fatto} />
  </Foglio>
{/if}

{#if generazione}
  <Foglio titolo="Genera un mese" chiudi={() => ((generazione = false), (esito = null))}>
    <p class="muted small nomargin">Crea i turni del mese dalla settimana tipo. Si può rilanciare: i turni già presenti non vengono duplicati.</p>
    <label class="field"><span class="lbl">Modello</span>
      <select class="inp" bind:value={modelloId}>
        {#each modelli as m (m.id)}<option value={m.id}>{m.nome} · {m.righe.length} righe</option>{/each}
      </select>
    </label>
    <label class="field"><span class="lbl">Mese</span><input class="inp" type="month" bind:value={meseDaGenerare} /></label>
    {#if esito}
      <div class="msg ok" role="status">
        {nomeMese(meseDaGenerare)}: {esito.creati} turni creati{esito.scoperti ? `, ${esito.scoperti} da assegnare` : ''}{esito.giaPresenti ? `, ${esito.giaPresenti} c'erano già` : ''}.
      </div>
      {#each esito.avvisi as a (a)}<p class="msg err nomargin">{a}</p>{/each}
    {/if}
    <button class="btn btn-primary" disabled={!modelloId || !meseDaGenerare || generando} onclick={genera}>{generando ? 'Genero…' : 'Genera i turni'}</button>
  </Foglio>
{/if}

<style>
  .giorno { display: flex; flex-direction: column; gap: var(--space-6); }
  .intesta { display: flex; justify-content: space-between; align-items: center; }
  .intesta h2 { margin: 0; }
  .intesta .link { min-height: 36px; }
  select.inp { appearance: auto; }
</style>
