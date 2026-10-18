<script lang="ts">
  import { untrack } from 'svelte';
  import { api, messaggioErrore, type Impianto, type Modello } from '../lib/api';
  import { iso } from '../lib/date';

  let { modello, modelli, impianti, fatto, eliminato }: {
    /** null = modello nuovo */
    modello: Modello | null;
    modelli: Modello[];
    impianti: Impianto[];
    fatto: (m: Modello, messaggio: string) => void;
    eliminato: (id: string) => void;
  } = $props();

  const iniziale = untrack(() => $state.snapshot({ modello, modelli, impianti }));
  let nome = $state(iniziale.modello?.nome ?? '');
  let impiantoId = $state(iniziale.modello?.impiantoId ?? iniziale.impianti[0]?.id ?? '');
  let validoDal = $state(iniziale.modello?.validoDal ?? iso(new Date()));
  let validoAl = $state(iniziale.modello?.validoAl ?? '');
  let copiaDa = $state(iniziale.modelli[0]?.id ?? '');
  let errore = $state('');
  let lavoro = $state(false);
  let confermaElimina = $state(false);

  const copiabili = $derived(modelli.filter((m) => m.impiantoId === impiantoId));

  async function salva(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    lavoro = true;
    const dati = { impiantoId, nome, validoDal, validoAl: validoAl || null, copiaDa: modello ? null : copiaDa || null };
    try {
      const m = modello ? await api.modificaModello(modello.id, dati) : await api.creaModello(dati);
      fatto(m, modello ? 'Modello aggiornato.' : 'Modello creato.');
    } catch (err) {
      errore = messaggioErrore(err);
    } finally {
      lavoro = false;
    }
  }

  async function elimina() {
    if (!modello) return;
    if (!confermaElimina) return (confermaElimina = true);
    lavoro = true;
    try {
      await api.eliminaModello(modello.id);
      eliminato(modello.id);
    } catch (err) {
      errore = messaggioErrore(err);
      confermaElimina = false;
    } finally {
      lavoro = false;
    }
  }
</script>

<form class="form" onsubmit={salva}>
  <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={nome} maxlength="120" required placeholder="Es. Stagione 2026-27" /></label>
  {#if !modello && impianti.length > 1}
    <label class="field"><span class="lbl">Impianto</span>
      <select class="inp" bind:value={impiantoId}>{#each impianti as i (i.id)}<option value={i.id}>{i.nome}</option>{/each}</select>
    </label>
  {/if}
  <div class="due">
    <label class="field"><span class="lbl">Valido dal</span><input class="inp" type="date" bind:value={validoDal} required /></label>
    <label class="field"><span class="lbl">Fino al (facoltativo)</span><input class="inp" type="date" bind:value={validoAl} /></label>
  </div>
  {#if !modello}
    <label class="field"><span class="lbl">Parti da</span>
      <select class="inp" bind:value={copiaDa}>
        <option value="">Un modello vuoto</option>
        {#each copiabili as m (m.id)}<option value={m.id}>Copia di «{m.nome}» ({m.righe.length} righe)</option>{/each}
      </select>
    </label>
  {/if}

  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={lavoro}>{modello ? 'Salva' : 'Crea il modello'}</button>
  {#if modello}
    <button type="button" class="btn btn-danger" disabled={lavoro} onclick={elimina}>{confermaElimina ? 'Tocca di nuovo per eliminare' : 'Elimina il modello'}</button>
    <p class="muted small nomargin">Si può eliminare solo un modello mai usato per generare turni. Per chiuderne uno, indica «Fino al».</p>
  {/if}
</form>

<style>
  .form { display: flex; flex-direction: column; gap: var(--space-14); }
  select.inp { appearance: auto; }
</style>
