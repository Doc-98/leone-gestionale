<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Corso, type Lezione, type PersonaBreve, type SpazioPiano } from '../lib/api';
  import { GIORNI_ESTESI, iso, ora } from '../lib/date';
  import SceltaSpazi from '../components/SceltaSpazi.svelte';

  let { corso, lezione, spazi, personale, fatto }: {
    corso: Corso;
    lezione: Lezione | null;
    spazi: SpazioPiano[];
    personale: PersonaBreve[];
    fatto: (messaggio: string, avvisi: string[]) => void;
  } = $props();

  const l = untrack(() => $state.snapshot(lezione));
  let giornoSettimana = $state(l?.giornoSettimana ?? 1);
  let inizio = $state(l ? ora(l.inizio) : '17:00');
  let fine = $state(l ? ora(l.fine) : '18:00');
  let validaDal = $state(l?.validaDal ?? iso(new Date()));
  let validaAl = $state(l?.validaAl ?? '');
  let scelti = $state<string[]>(l?.spazi ?? []);
  let istruttoreId = $state(l?.istruttore?.id ?? '');
  let errore = $state('');
  let lavoro = $state(false);

  async function salva(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    if (!scelti.length) return (errore = 'Scegli almeno una vasca o una corsia.');
    lavoro = true;
    const dati = { giornoSettimana: Number(giornoSettimana), inizio, fine, validaDal, validaAl: validaAl || null, spazi: scelti, istruttoreId: istruttoreId || null };
    try {
      const esito = lezione ? await api.modificaLezione(lezione.id, dati) : await api.aggiungiLezione(corso.id, dati);
      fatto(lezione ? 'Lezione aggiornata.' : 'Lezione aggiunta.', esito.avvisi);
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
</script>

<form class="form" onsubmit={salva}>
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
    <label class="field"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={validaDal} required /></label>
    <label class="field"><span class="lbl">Fino al (facoltativo)</span><input class="inp" type="date" bind:value={validaAl} min={validaDal} /></label>
  </div>
  <label class="field"><span class="lbl">Istruttore</span>
    <select class="inp" bind:value={istruttoreId}>
      <option value="">Quello del corso{corso.istruttore ? ` (${corso.istruttore.nome})` : ''}</option>
      {#each personale as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
    </select>
  </label>
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro}>{lezione ? 'Salva' : 'Aggiungi la lezione'}</button>
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
</style>
