<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type PersonaBreve, type TipoRichiesta, type Turno } from '../lib/api';
  import { aggiungiGiorni, iso, ora } from '../lib/date';
  import { quando } from '../lib/richieste';

  let { turno, fatto }: { turno: Turno; fatto: (messaggio: string) => void } = $props();

  let tipo = $state<TipoRichiesta>('SOSTITUZIONE');
  // il form parte dall'orario attuale del turno e poi vive di vita propria
  let nuovoInizio = $state(untrack(() => ora(turno.inizio)));
  let nuovoFine = $state(untrack(() => ora(turno.fine)));
  let collegaId = $state(''); // vuoto = sostituzione aperta a tutti
  let turnoCollegaId = $state('');
  let messaggio = $state('');
  let errore = $state('');
  let lavoro = $state(false);

  let colleghi = $state<PersonaBreve[]>([]);
  let turniColleghi = $state<Turno[]>([]);
  api.colleghi().then((c) => (colleghi = c)).catch(() => {});
  $effect(() => {
    if (tipo !== 'SCAMBIO' || turniColleghi.length) return;
    const da = iso(new Date());
    api.turniStaff(da, aggiungiGiorni(da, 45))
      .then((t) => (turniColleghi = t))
      .catch(() => {});
  });
  // Solo i turni dei colleghi (l'elenco dello staff comprende anche i miei)
  const scambiabili = $derived(turniColleghi.filter((t) => colleghi.some((c) => c.id === t.persona?.id)));

  async function invia(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    lavoro = true;
    try {
      await api.creaRichiesta({
        tipo,
        turnoId: turno.id,
        nuovoInizio: tipo === 'CAMBIO_ORARIO' ? nuovoInizio : null,
        nuovoFine: tipo === 'CAMBIO_ORARIO' ? nuovoFine : null,
        collegaId: tipo === 'SOSTITUZIONE' && collegaId ? collegaId : null,
        turnoCollegaId: tipo === 'SCAMBIO' ? turnoCollegaId : null,
        messaggio: messaggio || null,
      });
      fatto(
        tipo === 'CAMBIO_ORARIO' ? 'Richiesta inviata: ora la deve approvare il coordinatore.'
        : tipo === 'SCAMBIO' ? 'Proposta di scambio inviata al collega.'
        : collegaId ? 'Richiesta inviata al collega.' : 'Richiesta pubblicata: chiunque dello staff può offrirsi.',
      );
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }
</script>

<form class="form" onsubmit={invia}>
  <div class="card riepilogo">
    <div class="postazione">{turno.postazione}</div>
    <div class="muted small">{quando(turno)}</div>
  </div>

  <div class="seg tre" role="group" aria-label="Tipo di richiesta">
    <button type="button" aria-pressed={tipo === 'SOSTITUZIONE'} onclick={() => (tipo = 'SOSTITUZIONE')}>Sostituzione</button>
    <button type="button" aria-pressed={tipo === 'SCAMBIO'} onclick={() => (tipo = 'SCAMBIO')}>Scambio</button>
    <button type="button" aria-pressed={tipo === 'CAMBIO_ORARIO'} onclick={() => (tipo = 'CAMBIO_ORARIO')}>Orario</button>
  </div>

  {#if tipo === 'SOSTITUZIONE'}
    <label class="field"><span class="lbl">Chi ti sostituisce</span>
      <select class="inp" bind:value={collegaId}>
        <option value="">Chiunque sia libero (richiesta aperta)</option>
        {#each colleghi as c (c.id)}<option value={c.id}>{c.nome}</option>{/each}
      </select>
    </label>
  {:else if tipo === 'SCAMBIO'}
    <label class="field"><span class="lbl">Con quale turno</span>
      <select class="inp" bind:value={turnoCollegaId} required>
        <option value="" disabled>Scegli il turno di un collega</option>
        {#each scambiabili as t (t.id)}<option value={t.id}>{t.persona?.nome} · {quando(t)} · {t.postazione}</option>{/each}
      </select>
    </label>
  {:else}
    <div class="due">
      <label class="field"><span class="lbl">Nuovo inizio</span><input class="inp m" type="time" bind:value={nuovoInizio} required /></label>
      <label class="field"><span class="lbl">Nuova fine</span><input class="inp m" type="time" bind:value={nuovoFine} required /></label>
    </div>
  {/if}

  <label class="field"><span class="lbl">Messaggio (facoltativo)</span>
    <input class="inp" bind:value={messaggio} maxlength="500" placeholder="es. ho un esame" /></label>

  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro || (tipo === 'SCAMBIO' && !turnoCollegaId)}>{lavoro ? 'Invio…' : 'Invia richiesta'}</button>
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  .riepilogo { padding: var(--space-12) var(--space-14); }
  .postazione { font-weight: 600; }
  .tre { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  select.inp { appearance: auto; }
</style>
