<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type PersonaBreve, type Postazione, type Turno } from '../lib/api';
  import { ora } from '../lib/date';

  let { turno, data, postazioni, personale, fatto }: {
    turno: Turno | null;
    data: string;
    postazioni: Postazione[];
    personale: PersonaBreve[];
    fatto: (messaggio: string) => void;
  } = $props();

  // Il form parte dai valori del turno (o da quelli di un turno nuovo) e poi vive di vita propria
  const iniziale = untrack(() => $state.snapshot({ turno, data, postazioni }));
  let postazioneId = $state(iniziale.turno?.postazioneId ?? iniziale.postazioni[0]?.id ?? '');
  let giorno = $state(iniziale.turno?.data ?? iniziale.data);
  let inizio = $state(iniziale.turno ? ora(iniziale.turno.inizio) : '15:00');
  let fine = $state(iniziale.turno ? ora(iniziale.turno.fine) : '19:00');
  let personaId = $state(iniziale.turno?.persona?.id ?? '');
  let note = $state(iniziale.turno?.note ?? '');
  let errore = $state('');
  let lavoro = $state(false);
  let confermaAnnulla = $state(false);

  async function salva(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    lavoro = true;
    const dati = { postazioneId, data: giorno, inizio, fine, personaId: personaId || null, note: note || null };
    try {
      if (turno) await api.modificaTurno(turno.id, dati);
      else await api.creaTurno(dati);
      fatto(turno ? 'Turno aggiornato.' : 'Turno creato.');
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }

  async function annulla() {
    if (!turno) return;
    if (!confermaAnnulla) return (confermaAnnulla = true);
    lavoro = true;
    try {
      await api.annullaTurno(turno.id);
      fatto('Turno annullato.');
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
  <label class="field"><span class="lbl">Giorno</span><input class="inp" type="date" bind:value={giorno} required /></label>
  <div class="due">
    <label class="field"><span class="lbl">Inizio</span><input class="inp m" type="time" bind:value={inizio} required /></label>
    <label class="field"><span class="lbl">Fine</span><input class="inp m" type="time" bind:value={fine} required /></label>
  </div>
  <label class="field"><span class="lbl">Chi</span>
    <select class="inp" bind:value={personaId}>
      <option value="">Da assegnare</option>
      {#each personale as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
    </select>
  </label>
  <label class="field"><span class="lbl">Note (facoltative)</span><input class="inp" bind:value={note} maxlength="500" /></label>

  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro}>{turno ? 'Salva modifiche' : 'Crea turno'}</button>
  {#if turno}
    <button type="button" class="btn btn-danger" disabled={lavoro} onclick={annulla}>{confermaAnnulla ? 'Tocca di nuovo per annullare il turno' : 'Annulla turno'}</button>
  {/if}
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
</style>
