<script lang="ts">
  import { sessione } from '../lib/sessione.svelte';
  import { ErroreApi } from '../lib/api';

  let email = $state('');
  let password = $state('');
  let errore = $state('');
  let lavoro = $state(false);

  async function entra(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    lavoro = true;
    try {
      await sessione.entra(email, password);
    } catch (err) {
      errore = err instanceof ErroreApi ? err.message : 'Accesso non riuscito.';
    } finally {
      lavoro = false;
    }
  }
</script>

<section class="page">
  <div>
    <div class="lbl">Leone XIII Sport</div>
    <h1 class="page-title">Accedi</h1>
  </div>

  <form class="card box" onsubmit={entra}>
    <label class="field"><span class="lbl">Email</span>
      <input class="inp" type="email" bind:value={email} autocomplete="username" required /></label>
    <label class="field"><span class="lbl">Password</span>
      <input class="inp" type="password" bind:value={password} autocomplete="current-password" required /></label>
    {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
    <button class="btn btn-primary" disabled={lavoro}>{lavoro ? 'Accesso…' : 'Entra'}</button>
  </form>

  <p class="muted small">Ambiente di sviluppo: account demo <span class="m">staff@demo.leone</span>, <span class="m">coordinatore@demo.leone</span>, <span class="m">admin@demo.leone</span> · password <span class="m">demo1234</span>.</p>
</section>

<style>
  .box { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-14); }
</style>
