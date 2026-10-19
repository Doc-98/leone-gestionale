<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type FasciaOraria, type SpazioPiano } from '../lib/api';
  import { GIORNI_BREVI, GIORNI_ESTESI, iso, ora } from '../lib/date';

  /**
   * Le fasce di un giorno della settimana, vasca per vasca: orari liberi, assetto delle corsie, nuove fasce anche in
   * più giorni insieme. Cambiare l'orario di una fascia sposta con lei i turni che avevano lo stesso orario.
   */
  let { giornoSettimana, fasce, spazi, cambiato }: {
    giornoSettimana: number;
    fasce: FasciaOraria[];
    spazi: SpazioPiano[];
    cambiato: (messaggio: string) => void;
  } = $props();

  const oggi = iso(new Date());
  const vasche = $derived(spazi.filter((s) => !s.padreId && s.tipo === 'VASCA'));
  const assettiDi = (vascaId: string) => [...new Set(spazi.filter((s) => s.padreId === vascaId && s.assetto != null).map((s) => s.assetto!))].sort((a, b) => b - a);
  const delGiorno = $derived(
    fasce.filter((f) => f.giornoSettimana === giornoSettimana && (!f.validaAl || f.validaAl >= oggi)).sort((a, b) => a.inizio.localeCompare(b.inizio)),
  );

  let errore = $state('');
  let inModifica = $state<{ id: string; inizio: string; fine: string } | null>(null);
  let conferma = $state('');

  // nuova fascia
  let nuova = $state(untrack(() => ({ vascaId: '', giorni: [giornoSettimana], inizio: '17:00', fine: '17:45', assetto: 0 })));
  $effect(() => {
    if (!nuova.vascaId && vasche.length) nuova.vascaId = vasche[0].id;
  });
  const assettiNuova = $derived(assettiDi(nuova.vascaId));

  async function esegui(azione: () => Promise<string>) {
    errore = '';
    try {
      cambiato(await azione());
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  const salvaOrario = () =>
    esegui(async () => {
      const f = fasce.find((x) => x.id === inModifica!.id)!;
      const r = await api.modificaFascia(f.id, { inizio: inModifica!.inizio, fine: inModifica!.fine, validaDal: f.validaDal, validaAl: f.validaAl });
      inModifica = null;
      return `Fascia spostata alle ${ora(r.fascia.inizio)}${r.turniAggiornati ? `: ${r.turniAggiornati} turni l'hanno seguita` : ''}.`;
    });

  const assetto = (f: FasciaOraria, a: number) =>
    esegui(async () => {
      const r = await api.cambiaAssetto(f.id, a);
      return `${ora(f.inizio)}–${ora(f.fine)} ora a ${a} corsie${r.turniAggiornati ? ` (${r.turniAggiornati} corsi riportati sulle corsie con lo stesso numero)` : ''}.`;
    });

  function elimina(f: FasciaOraria) {
    if (conferma !== f.id) return (conferma = f.id);
    conferma = '';
    esegui(async () => {
      await api.eliminaFascia(f.id);
      return `Fascia ${ora(f.inizio)}–${ora(f.fine)} eliminata.`;
    });
  }

  const crea = () =>
    esegui(async () => {
      const r = await api.creaFasce({
        vascaId: nuova.vascaId, giorni: nuova.giorni, inizio: nuova.inizio, fine: nuova.fine,
        assetto: assettiNuova.length ? nuova.assetto || assettiNuova[0] : null, validaDal: oggi, validaAl: null,
      });
      return `Fascia ${nuova.inizio}–${nuova.fine} aggiunta in ${r.length === 1 ? '1 giorno' : `${r.length} giorni`}.`;
    });

  function alternaGiorno(g: number) {
    nuova.giorni = nuova.giorni.includes(g) ? nuova.giorni.filter((x) => x !== g) : [...nuova.giorni, g].sort();
  }
</script>

<p class="muted small nomargin">Le fasce di ogni {GIORNI_ESTESI[giornoSettimana]}, vasca per vasca. Gli orari sono liberi; l'assetto dice in quante corsie è divisa la vasca.</p>
{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

{#each vasche as v (v.id)}
  {@const sue = delGiorno.filter((f) => f.vascaId === v.id)}
  <section class="vasca">
    <h3>{v.nome}</h3>
    {#each sue as f (f.id)}
      <div class="fascia card">
        {#if inModifica?.id === f.id}
          <input class="inp m piccolo" type="time" bind:value={inModifica.inizio} aria-label="Inizio" />
          <span>–</span>
          <input class="inp m piccolo" type="time" bind:value={inModifica.fine} aria-label="Fine" />
          <button class="link small" onclick={salvaOrario}>Salva</button>
          <button class="link small muted" onclick={() => (inModifica = null)}>Annulla</button>
        {:else}
          <button class="orario m" title="Cambia orario" onclick={() => (inModifica = { id: f.id, inizio: ora(f.inizio), fine: ora(f.fine) })}>{ora(f.inizio)}–{ora(f.fine)}</button>
          {#if assettiDi(v.id).length > 1}
            <span class="seg assetto" style="grid-template-columns: repeat({assettiDi(v.id).length}, auto)" role="group" aria-label="Corsie">
              {#each assettiDi(v.id) as a (a)}<button aria-pressed={f.assetto === a} onclick={() => f.assetto !== a && assetto(f, a)}>{a}</button>{/each}
            </span>
          {/if}
          <span class="grow"></span>
          <button class="link small rosso" onclick={() => elimina(f)}>{conferma === f.id ? 'Conferma' : 'Elimina'}</button>
        {/if}
      </div>
    {:else}
      <p class="muted small nomargin">Nessuna fascia: il piano raggruppa da solo le lezioni per orario.</p>
    {/each}
  </section>
{/each}

<section class="card nuova">
  <h3>Nuova fascia</h3>
  <label class="field"><span class="lbl">Vasca</span>
    <select class="inp" bind:value={nuova.vascaId}>{#each vasche as v (v.id)}<option value={v.id}>{v.nome}</option>{/each}</select>
  </label>
  <div class="field">
    <span class="lbl">Giorni</span>
    <div class="chips">
      {#each [1, 2, 3, 4, 5, 6, 7] as g (g)}
        <button type="button" class="chip" aria-pressed={nuova.giorni.includes(g)} onclick={() => alternaGiorno(g)}>{GIORNI_BREVI[g]}</button>
      {/each}
    </div>
  </div>
  <div class="due">
    <label class="field"><span class="lbl">Inizio</span><input class="inp m" type="time" bind:value={nuova.inizio} /></label>
    <label class="field"><span class="lbl">Fine</span><input class="inp m" type="time" bind:value={nuova.fine} /></label>
  </div>
  {#if assettiNuova.length > 1}
    <div class="field">
      <span class="lbl">Corsie</span>
      <div class="seg" style="grid-template-columns: repeat({assettiNuova.length}, 1fr)">
        {#each assettiNuova as a (a)}<button type="button" aria-pressed={(nuova.assetto || assettiNuova[0]) === a} onclick={() => (nuova.assetto = a)}>{a} corsie</button>{/each}
      </div>
    </div>
  {/if}
  <button class="btn btn-primary" disabled={!nuova.giorni.length} onclick={crea}>Aggiungi la fascia</button>
</section>

<style>
  .vasca { display: flex; flex-direction: column; gap: var(--space-6); }
  h3 { margin: var(--space-6) 0 0; font-size: var(--text-lg); }
  .fascia { display: flex; align-items: center; gap: var(--space-8); padding: var(--space-6) var(--space-10); min-height: 48px; }
  .orario { border: none; background: none; font: inherit; font-weight: 600; color: var(--ink); cursor: pointer; padding: var(--space-4); border-radius: var(--radius-xs); }
  .orario:hover { background: var(--carta); }
  .piccolo { width: 110px; height: 36px; font-size: var(--text-md); }
  .assetto { padding: 2px; }
  .assetto button { min-height: 28px; padding: 0 var(--space-10); font-size: var(--text-xs); }
  .rosso { color: var(--danger); min-height: 32px; }
  .nuova { display: flex; flex-direction: column; gap: var(--space-12); padding: var(--space-14); }
  .nuova h3 { margin: 0; }
  select.inp { appearance: auto; }
</style>
