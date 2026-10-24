<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';

  /** Cambio della propria password; obbligatorio al primo accesso con la password temporanea. */
  let { obbligatorio = false, fatto }: { obbligatorio?: boolean; fatto?: () => void } = $props();

  let attuale = $state('');
  let nuova = $state('');
  let ripeti = $state('');
  let errore = $state('');
  let inCorso = $state(false);

  const problema = $derived(
    nuova.length > 0 && nuova.length < 10 ? 'Almeno 10 caratteri.' : ripeti && nuova !== ripeti ? 'Le due password non coincidono.' : '',
  );

  async function salva() {
    errore = '';
    inCorso = true;
    try {
      await api.cambiaPassword(attuale, nuova);
      await sessione.aggiorna();
      attuale = nuova = ripeti = '';
      fatto?.();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      inCorso = false;
    }
  }
</script>

<form class="cambio" onsubmit={(e) => { e.preventDefault(); salva(); }}>
  {#if obbligatorio}
    <p class="hint nomargin">Stai usando una password temporanea: scegline una tua per continuare.</p>
  {/if}
  <label class="field"><span class="lbl">{obbligatorio ? 'Password temporanea' : 'Password attuale'}</span>
    <input class="inp" type="password" bind:value={attuale} autocomplete="current-password" required />
  </label>
  <label class="field"><span class="lbl">Nuova password</span>
    <input class="inp" type="password" bind:value={nuova} autocomplete="new-password" minlength="10" required />
  </label>
  <label class="field"><span class="lbl">Ripeti la nuova password</span>
    <input class="inp" type="password" bind:value={ripeti} autocomplete="new-password" required />
  </label>
  <p class="muted small nomargin">Almeno 10 caratteri; meglio una frase che ricordi facilmente. Non usare email o cognome.</p>
  {#if problema}<p class="msg err" role="alert">{problema}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  <button class="btn btn-primary" disabled={inCorso || !attuale || nuova.length < 10 || nuova !== ripeti}>Salva la nuova password</button>
  {#if obbligatorio}<button type="button" class="btn btn-secondary" onclick={() => sessione.esci()}><Icona nome="esci" />Esci</button>{/if}
</form>

<style>
  .cambio { display: flex; flex-direction: column; gap: var(--space-12); }
</style>
