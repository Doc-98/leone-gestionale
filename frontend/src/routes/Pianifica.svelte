<script lang="ts">
  import { api, messaggioErrore, type EsitoGenerazione, type Modello, type PersonaBreve, type Postazione, type Turno } from '../lib/api';
  import { aggiungiGiorni, formatOre, giornoEsteso, iso, lunedi, meseIso, nomeMese, spostaMese } from '../lib/date';
  import Settimana from '../components/Settimana.svelte';
  import RigaTurno from '../components/RigaTurno.svelte';
  import Foglio from '../components/Foglio.svelte';
  import TurnoForm from './TurnoForm.svelte';
  import Griglia from '../components/Griglia.svelte';
  import { colonneGiorni, daTurno } from '../lib/griglia';
  import { schermo } from '../lib/schermo.svelte';

  let settimana = $state(lunedi(iso(new Date())));
  let turni = $state<Turno[]>([]);
  let postazioni = $state<Postazione[]>([]);
  let personale = $state<PersonaBreve[]>([]);
  let modelli = $state<Modello[]>([]);
  let caricamento = $state(true);
  let pronte = $state(false);
  let errore = $state('');
  let messaggio = $state('');
  let modifica = $state<{ turno: Turno | null; data: string; postazione?: string } | null>(null);
  let evidenzia = $state('');

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
      pronte = true;
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
  // Ore della settimana per persona, per bilanciare i turni
  const perPersona = $derived.by(() => {
    const m = new Map<string, { nome: string; turni: number; ore: number }>();
    for (const t of attivi) {
      if (!t.persona) continue;
      const r = m.get(t.persona.id) ?? { nome: t.persona.nome, turni: 0, ore: 0 };
      r.turni++;
      r.ore += Number(t.ore);
      m.set(t.persona.id, r);
    }
    return [...m.entries()].sort((a, b) => b[1].ore - a[1].ore);
  });

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
  {#if schermo.larga}
    <div class="barra">
      <div class="sett"><Settimana bind:lunediSettimana={settimana} /></div>
      <div class="riassunto m muted small">{caricamento ? '…' : `${attivi.length} turni · ${formatOre(ore)} h`}{#if scoperti}<span class="tag t-warn">{scoperti} da assegnare</span>{/if}</div>
      <label class="evid"><span class="lbl">Evidenzia</span>
        <select class="inp compatto" bind:value={evidenzia}>
          <option value="">Nessuno</option>
          {#each personale as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
        </select>
      </label>
      <button class="btn btn-secondary compatto" onclick={() => (generazione = true)}>Genera un mese…</button>
    </div>
    {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
    {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
    {#if pronte}<Griglia colonne={colonneGiorni(giorni)} {postazioni} voci={turni.map(daTurno)} {evidenzia} etichetta="Turni della settimana per postazione"
      apri={(id) => { const t = turni.find((x) => x.id === id); if (t) { modifica = { turno: t, data: t.data }; messaggio = ''; } }}
      nuovo={(postazione, data) => ((modifica = { turno: null, data, postazione }), (messaggio = ''))} />{/if}
    {#if perPersona.length}
      <div class="card persone">
        <div class="lbl">Ore della settimana per persona</div>
        <div class="elenco-persone">
          {#each perPersona as [id, r] (id)}
            <button class="persona" class:on={evidenzia === id} onclick={() => (evidenzia = evidenzia === id ? '' : id)}>
              <span>{r.nome}</span><span class="m muted">{r.turni} · {formatOre(r.ore)} h</span>
            </button>
          {/each}
        </div>
      </div>
    {/if}
  {:else}
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

    <div class="card voci altre">
      <a class="voce" href="#/modelli"><span class="voce-txt"><span class="voce-nome">Settimana tipo</span></span><span class="chev">›</span></a>
      <a class="voce" href="#/postazioni"><span class="voce-txt"><span class="voce-nome">Mansioni e postazioni</span></span><span class="chev">›</span></a>
      <a class="voce" href="#/tariffe"><span class="voce-txt"><span class="voce-nome">Tariffe</span></span><span class="chev">›</span></a>
    </div>
  {/if}
</section>

{#if modifica}
  <Foglio titolo={modifica.turno ? 'Modifica turno' : 'Nuovo turno'} chiudi={() => (modifica = null)}>
    <TurnoForm turno={modifica.turno} data={modifica.data} postazione={modifica.postazione} {postazioni} {personale} {fatto} />
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
  .barra { display: flex; align-items: center; gap: var(--space-16); flex-wrap: wrap; }
  .sett { min-width: 300px; }
  .riassunto { display: flex; align-items: center; gap: var(--space-8); flex: 1; }
  .evid { display: flex; align-items: center; gap: var(--space-8); }
  .compatto { height: 38px; font-size: var(--text-md); }
  .inp.compatto { width: 200px; padding: 0 var(--space-10); }
  .btn.compatto { padding: 0 var(--space-14); }
  .persone { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-8); }
  .elenco-persone { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--space-4) var(--space-16); }
  .persona { display: flex; justify-content: space-between; gap: var(--space-8); padding: var(--space-6) var(--space-8); border: none; border-radius: var(--radius-xs); background: none; color: var(--ink); font: inherit; font-size: var(--text-sm); cursor: pointer; text-align: left; }
  .persona:hover { background: var(--carta); }
  .persona.on { background: var(--cloro-soft); color: var(--cloro-ink); }
  .altre { margin-top: var(--space-8); }
</style>
