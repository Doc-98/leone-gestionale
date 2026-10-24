<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Azione from '../components/Azione.svelte';
  import { api, messaggioErrore, type Impianto, type Modello, type PersonaBreve, type Postazione, type RigaModello } from '../lib/api';
  import { dataLunga, formatOre, GIORNI_ESTESI, ora, oreTra } from '../lib/date';
  import { COLONNE_SETTIMANA, daRiga } from '../lib/griglia';
  import { schermo } from '../lib/schermo.svelte';
  import Griglia from '../components/Griglia.svelte';
  import Foglio from '../components/Foglio.svelte';
  import RigaModelloForm from './RigaModelloForm.svelte';
  import ModelloForm from './ModelloForm.svelte';

  let modelli = $state<Modello[]>([]);
  let postazioni = $state<Postazione[]>([]);
  let personale = $state<PersonaBreve[]>([]);
  let impianti = $state<Impianto[]>([]);
  let sceltoId = $state('');
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let rigaAperta = $state<{ riga: RigaModello | null; giorno: number; postazione?: string } | null>(null);
  let modelloAperto = $state<{ modello: Modello | null } | null>(null);

  Promise.all([api.modelli(), api.postazioni(), api.personale(), api.impianti()])
    .then(([m, p, s, i]) => {
      modelli = m;
      postazioni = p;
      personale = s;
      impianti = i;
      sceltoId = m[0]?.id ?? '';
    })
    .catch((e) => (errore = messaggioErrore(e)))
    .finally(() => (caricamento = false));

  const scelto = $derived(modelli.find((m) => m.id === sceltoId) ?? null);
  const mansioni = $derived(new Map(postazioni.map((p) => [p.id, p.mansione])));
  const oreSettimana = $derived(scelto?.righe.reduce((s, r) => s + oreTra(r.inizio, r.fine), 0) ?? 0);
  const daAssegnare = $derived(scelto?.righe.filter((r) => !r.persona).length ?? 0);

  function aggiornato(m: Modello, testo: string) {
    const i = modelli.findIndex((x) => x.id === m.id);
    modelli = i >= 0 ? modelli.map((x) => (x.id === m.id ? m : x)) : [m, ...modelli];
    sceltoId = m.id;
    rigaAperta = null;
    modelloAperto = null;
    messaggio = testo;
  }

  function eliminato(id: string) {
    modelli = modelli.filter((m) => m.id !== id);
    sceltoId = modelli[0]?.id ?? '';
    modelloAperto = null;
    messaggio = 'Modello eliminato.';
  }

  function apriRiga(id: string) {
    const r = scelto?.righe.find((x) => x.id === id);
    if (r) {
      rigaAperta = { riga: r, giorno: r.giornoSettimana };
      messaggio = '';
    }
  }
</script>

<section class="page">
  <div class="barra">
    {#if modelli.length}
      <label class="field scelta"><span class="lbl">Modello</span>
        <select class="inp compatto" bind:value={sceltoId}>
          {#each modelli as m (m.id)}<option value={m.id}>{m.nome}</option>{/each}
        </select>
      </label>
    {/if}
    {#if scelto}
      <div class="info small">
        <span>Dal {dataLunga(scelto.validoDal)}{scelto.validoAl ? ` al ${dataLunga(scelto.validoAl)}` : ', senza scadenza'}</span>
        <span class="m muted">{scelto.righe.length} righe · {formatOre(oreSettimana)} h a settimana</span>
        {#if daAssegnare}<span class="tag t-warn">{daAssegnare} senza persona</span>{/if}
      </div>
      <button class="btn btn-secondary compatto" onclick={() => ((modelloAperto = { modello: scelto }), (messaggio = ''))}>Modifica…</button>
    {/if}
    <button class="btn btn-primary compatto" onclick={() => ((modelloAperto = { modello: null }), (messaggio = ''))}><Icona nome="piu" />Nuovo modello…</button>
  </div>

  <p class="muted small nomargin">La settimana tipo da cui si generano i turni del mese (da «Pianificazione» → «Genera un mese»). Ogni riga è una postazione in un giorno, con l'orario e chi di solito la copre.</p>
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  {#if scelto}
    {#if schermo.larga}
      {#if !caricamento}<Griglia colonne={COLONNE_SETTIMANA} {postazioni} voci={scelto.righe.map((r) => daRiga(r, scelto.impiantoId, mansioni))}
        etichetta="Settimana tipo per postazione" apri={apriRiga}
        nuovo={(postazione, giorno) => ((rigaAperta = { riga: null, giorno: Number(giorno), postazione }), (messaggio = ''))} />{/if}
    {:else}
      {#each [1, 2, 3, 4, 5, 6, 7] as g (g)}
        {@const delGiorno = scelto.righe.filter((r) => r.giornoSettimana === g)}
        <div class="giorno">
          <div class="intesta">
            <h2 class="lbl">{GIORNI_ESTESI[g]}</h2>
            <Azione icona="piu" tono="cloro" etichetta={`Aggiungi una riga il ${GIORNI_ESTESI[g].toLowerCase()}`} onclick={() => (rigaAperta = { riga: null, giorno: g })} />
          </div>
          {#each delGiorno as r (r.id)}
            <button class="card riga" class:scoperta={!r.persona} onclick={() => apriRiga(r.id)}>
              <span class="m">{ora(r.inizio)}–{ora(r.fine)}</span>
              <span class="dove"><span class="postazione">{r.postazione}</span><span class="muted small">{r.persona?.nome ?? 'Senza persona'}</span></span>
            </button>
          {:else}
            <p class="muted small nomargin">Nessuna riga</p>
          {/each}
        </div>
      {/each}
    {/if}
  {:else if !caricamento}
    <p class="muted">Nessun modello. Creane uno per generare i turni del mese.</p>
  {/if}
</section>

{#if rigaAperta && scelto}
  <Foglio titolo={rigaAperta.riga ? 'Riga del modello' : 'Nuova riga'} chiudi={() => (rigaAperta = null)}>
    <RigaModelloForm modello={scelto} riga={rigaAperta.riga} giorno={rigaAperta.giorno} postazione={rigaAperta.postazione}
      {postazioni} {personale} fatto={aggiornato} />
  </Foglio>
{/if}

{#if modelloAperto}
  <Foglio titolo={modelloAperto.modello ? 'Modifica modello' : 'Nuovo modello'} chiudi={() => (modelloAperto = null)}>
    <ModelloForm modello={modelloAperto.modello} {modelli} {impianti} fatto={aggiornato} {eliminato} />
  </Foglio>
{/if}

<style>
  .barra { display: flex; align-items: flex-end; gap: var(--space-12); flex-wrap: wrap; }
  .scelta { min-width: 220px; }
  .info { display: flex; flex-direction: column; gap: var(--space-2); flex: 1; min-width: 200px; padding-bottom: var(--space-2); }
  .info .tag { align-self: flex-start; }
  .compatto { height: 40px; font-size: var(--text-md); }
  .inp.compatto { padding: 0 var(--space-10); appearance: auto; }
  .btn.compatto { padding: 0 var(--space-14); }
  .giorno { display: flex; flex-direction: column; gap: var(--space-6); }
  .intesta { display: flex; justify-content: space-between; align-items: center; }
  .intesta h2 { margin: 0; }
  .riga { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-10) var(--space-14); min-height: 56px; width: 100%; text-align: left; color: var(--ink); font: inherit; cursor: pointer; }
  .dove { display: flex; flex-direction: column; min-width: 0; }
  .postazione { font-weight: 600; }
  .scoperta { border-style: dashed; border-color: var(--warn-line); background: var(--warn-bg); }
  .scoperta .small { color: var(--warn); font-weight: 600; }
</style>
