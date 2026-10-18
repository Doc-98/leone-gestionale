<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Modello, type PersonaBreve, type Postazione, type RigaModello } from '../lib/api';
  import { GIORNI_ESTESI, ora } from '../lib/date';

  let { modello, riga, giorno = 1, postazione = '', postazioni, personale, fatto }: {
    modello: Modello;
    riga: RigaModello | null;
    giorno?: number;
    postazione?: string;
    postazioni: Postazione[];
    personale: PersonaBreve[];
    fatto: (m: Modello, messaggio: string) => void;
  } = $props();

  const iniziale = untrack(() => $state.snapshot({ riga, giorno, postazione, postazioni }));
  let postazioneId = $state(iniziale.riga?.postazioneId ?? (iniziale.postazione || iniziale.postazioni[0]?.id) ?? '');
  let giornoSettimana = $state(iniziale.riga?.giornoSettimana ?? iniziale.giorno);
  let inizio = $state(iniziale.riga ? ora(iniziale.riga.inizio) : '15:00');
  let fine = $state(iniziale.riga ? ora(iniziale.riga.fine) : '19:00');
  let personaId = $state(iniziale.riga?.persona?.id ?? '');
  let errore = $state('');
  let lavoro = $state(false);
  let confermaTogli = $state(false);

  async function salva(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    lavoro = true;
    const dati = { postazioneId, giornoSettimana: Number(giornoSettimana), inizio, fine, personaId: personaId || null };
    try {
      const m = riga ? await api.modificaRiga(modello.id, riga.id, dati) : await api.aggiungiRiga(modello.id, dati);
      fatto(m, riga ? 'Riga aggiornata.' : 'Riga aggiunta.');
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }

  async function togli() {
    if (!riga) return;
    if (!confermaTogli) return (confermaTogli = true);
    lavoro = true;
    try {
      fatto(await api.togliRiga(modello.id, riga.id), 'Riga tolta dal modello.');
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
</script>

<form class="form" onsubmit={salva}>
  <label class="field"><span class="lbl">Postazione</span>
    <select class="inp" bind:value={postazioneId} required>
      {#each postazioni as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
    </select>
  </label>
  <label class="field"><span class="lbl">Giorno</span>
    <select class="inp" bind:value={giornoSettimana}>
      {#each [1, 2, 3, 4, 5, 6, 7] as g (g)}<option value={g}>{GIORNI_ESTESI[g]}</option>{/each}
    </select>
  </label>
  <div class="due">
    <label class="field"><span class="lbl">Inizio</span><input class="inp m" type="time" bind:value={inizio} required /></label>
    <label class="field"><span class="lbl">Fine</span><input class="inp m" type="time" bind:value={fine} required /></label>
  </div>
  <label class="field"><span class="lbl">Di solito la copre</span>
    <select class="inp" bind:value={personaId}>
      <option value="">Nessuno (il turno nasce da assegnare)</option>
      {#each personale as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
    </select>
  </label>
  <p class="muted small nomargin">Le modifiche valgono per i mesi generati da ora in poi: i turni già creati non cambiano.</p>

  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro}>{riga ? 'Salva' : 'Aggiungi al modello'}</button>
  {#if riga}
    <button type="button" class="btn btn-danger" disabled={lavoro} onclick={togli}>{confermaTogli ? 'Tocca di nuovo per togliere la riga' : 'Togli dal modello'}</button>
  {/if}
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
</style>
