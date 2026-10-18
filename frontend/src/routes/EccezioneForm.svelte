<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Occorrenza } from '../lib/api';
  import { giornoEsteso, ora } from '../lib/date';

  /** Salta o sposta una sola data di una lezione ricorrente. */
  let { occorrenza, fatto }: { occorrenza: Occorrenza; fatto: (messaggio: string, avvisi: string[]) => void } = $props();

  const o = untrack(() => $state.snapshot(occorrenza));
  let tipo = $state<'SALTATA' | 'SPOSTATA'>('SALTATA');
  let nuovaData = $state(o.data);
  let nuovoInizio = $state(ora(o.inizio));
  let nuovoFine = $state(ora(o.fine));
  let motivo = $state('');
  let errore = $state('');
  let lavoro = $state(false);

  async function salva(e: SubmitEvent) {
    e.preventDefault();
    if (!o.lezioneId) return;
    errore = '';
    lavoro = true;
    try {
      const esito = await api.aggiungiEccezione(o.lezioneId, {
        data: o.data, tipo, motivo: motivo || null,
        ...(tipo === 'SPOSTATA' ? { nuovaData, nuovoInizio, nuovoFine } : {}),
      });
      fatto(tipo === 'SALTATA' ? `${o.titolo}: ${giornoEsteso(o.data)} non si tiene.` : `${o.titolo} spostata a ${giornoEsteso(nuovaData)} alle ${nuovoInizio}.`, esito.avvisi);
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
</script>

<form class="form" onsubmit={salva}>
  <p class="muted small nomargin">Solo per {giornoEsteso(o.data)}: le altre settimane restano come sono.</p>
  <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Cosa succede">
    <button type="button" aria-pressed={tipo === 'SALTATA'} onclick={() => (tipo = 'SALTATA')}>Non si tiene</button>
    <button type="button" aria-pressed={tipo === 'SPOSTATA'} onclick={() => (tipo = 'SPOSTATA')}>Si sposta</button>
  </div>
  {#if tipo === 'SPOSTATA'}
    <label class="field"><span class="lbl">Nuovo giorno</span><input class="inp" type="date" bind:value={nuovaData} required /></label>
    <div class="due">
      <label class="field"><span class="lbl">Inizio</span><input class="inp m" type="time" bind:value={nuovoInizio} required /></label>
      <label class="field"><span class="lbl">Fine</span><input class="inp m" type="time" bind:value={nuovoFine} required /></label>
    </div>
  {/if}
  <label class="field"><span class="lbl">Motivo (facoltativo)</span><input class="inp" bind:value={motivo} maxlength="300" placeholder="Es. gara, assenza dell'istruttore" /></label>
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro}>{tipo === 'SALTATA' ? 'Segna come non tenuta' : 'Sposta la lezione'}</button>
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
</style>
