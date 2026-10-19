<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Corso, type Lezione, type PersonaBreve, type SpazioPiano } from '../lib/api';
  import { GIORNI_ESTESI, iso, ora } from '../lib/date';
  import SceltaSpazi from '../components/SceltaSpazi.svelte';

  /**
   * Un turno del corso: giorno, orario, dove, posti. Dalla scheda del corso il corso è dato; dalla pianta della vasca
   * (corsia libera) si sceglie il corso e giorno, orario e corsia arrivano già compilati.
   */
  let { corso, corsi = [], lezione, spazi, personale, proposta, fatto }: {
    corso: Corso | null;
    corsi?: Corso[];
    lezione: Lezione | null;
    spazi: SpazioPiano[];
    personale: PersonaBreve[];
    proposta?: { giornoSettimana: number; inizio: string; fine: string; spazio: string; dal: string };
    fatto: (messaggio: string, avvisi: string[]) => void;
  } = $props();

  const l = untrack(() => $state.snapshot(lezione));
  const p = untrack(() => $state.snapshot(proposta));
  let corsoId = $state(untrack(() => corso?.id ?? ''));
  let giornoSettimana = $state(l?.giornoSettimana ?? p?.giornoSettimana ?? 1);
  let inizio = $state(l ? ora(l.inizio) : p?.inizio ?? '17:00');
  let fine = $state(l ? ora(l.fine) : p?.fine ?? '17:45');
  let validaDal = $state(l?.validaDal ?? p?.dal ?? iso(new Date()));
  let validaAl = $state(l?.validaAl ?? '');
  let scelti = $state<string[]>(l?.spazi ?? (p?.spazio ? [p.spazio] : []));
  let istruttoreId = $state(l?.istruttore?.id ?? '');
  let posti = $state<number | string | null>(l?.posti ?? null);
  let errore = $state('');
  let lavoro = $state(false);

  const corsoScelto = $derived(corso ?? corsi.find((c) => c.id === corsoId) ?? null);

  async function salva(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    if (!corsoScelto) return (errore = 'Scegli il corso.');
    if (!scelti.length) return (errore = 'Scegli almeno una vasca o una corsia.');
    lavoro = true;
    const dati = {
      giornoSettimana: Number(giornoSettimana), inizio, fine, validaDal, validaAl: validaAl || null, spazi: scelti,
      istruttoreId: istruttoreId || null, posti: posti === '' || posti == null ? null : Number(posti),
    };
    try {
      const esito = lezione ? await api.modificaLezione(lezione.id, dati) : await api.aggiungiLezione(corsoScelto.id, dati);
      fatto(lezione ? 'Turno del corso aggiornato.' : `${corsoScelto.nome}: turno aggiunto.`, esito.avvisi);
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
</script>

<form class="form" onsubmit={salva}>
  {#if !corso}
    <label class="field"><span class="lbl">Corso</span>
      <select class="inp" bind:value={corsoId} required>
        <option value="" disabled>Scegli il corso…</option>
        {#each corsi.filter((c) => c.attivo) as c (c.id)}<option value={c.id}>{c.nome}</option>{/each}
      </select>
    </label>
  {/if}
  <label class="field"><span class="lbl">Ogni</span>
    <select class="inp" bind:value={giornoSettimana}>
      {#each [1, 2, 3, 4, 5, 6, 7] as g (g)}<option value={g}>{GIORNI_ESTESI[g]}</option>{/each}
    </select>
  </label>
  <div class="due">
    <label class="field"><span class="lbl">Inizio</span><input class="inp m" type="time" bind:value={inizio} required /></label>
    <label class="field"><span class="lbl">Fine</span><input class="inp m" type="time" bind:value={fine} required /></label>
  </div>
  <div class="field"><span class="lbl">Dove</span><SceltaSpazi {spazi} bind:scelti /></div>
  <div class="due">
    <label class="field"><span class="lbl">Posti (facoltativo)</span><input class="inp m" type="number" min="1" max="500" bind:value={posti} placeholder="senza limite" /></label>
    <label class="field"><span class="lbl">Istruttore</span>
      <select class="inp" bind:value={istruttoreId}>
        <option value="">Del corso{corsoScelto?.istruttore ? ` (${corsoScelto.istruttore.nome})` : ''}</option>
        {#each personale as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
      </select>
    </label>
  </div>
  <div class="due">
    <label class="field"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={validaDal} required /></label>
    <label class="field"><span class="lbl">Fino al (facoltativo)</span><input class="inp" type="date" bind:value={validaAl} min={validaDal} /></label>
  </div>
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro}>{lezione ? 'Salva' : 'Aggiungi il turno'}</button>
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
</style>
