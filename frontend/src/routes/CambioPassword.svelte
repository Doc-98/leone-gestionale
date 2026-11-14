<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore } from '../lib/api';
  import { forzaPassword, requisitiPassword } from '../lib/password';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Cambio della propria password; obbligatorio al primo accesso con la password temporanea. I requisiti si spuntano
   * mentre si scrive e una barra dice se è corta, buona o ottima: meglio una frase facile da ricordare.
   */
  let { obbligatorio = false, fatto }: { obbligatorio?: boolean; fatto?: () => void } = $props();

  let attuale = $state('');
  let nuova = $state('');
  let ripeti = $state('');
  let mostra = $state(false);
  let errore = $state('');
  let inCorso = $state(false);

  const requisiti = $derived(requisitiPassword(attuale, nuova, ripeti, obbligatorio));
  const forza = $derived(forzaPassword(nuova));
  const pronta = $derived(!!attuale && requisiti.every((r) => r.ok));

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

<form class="cambio" class:card={obbligatorio} onsubmit={(e) => { e.preventDefault(); salva(); }}>
  <label class="field"><span class="lbl">{obbligatorio ? 'Password temporanea' : 'Password attuale'}</span>
    <input class="inp" type="password" bind:value={attuale} autocomplete="current-password" required />
  </label>
  <div class="field">
    <label class="lbl" for="nuova-password">Nuova password</label>
    <span class="pw">
      <input id="nuova-password" class="inp" type={mostra ? 'text' : 'password'} bind:value={nuova} autocomplete="new-password" minlength="10" required />
      <button type="button" class="occhio" aria-label={mostra ? 'Nascondi le password' : 'Mostra le password'} aria-pressed={mostra} onclick={() => (mostra = !mostra)}>
        <Icona nome={mostra ? 'occhio-no' : 'occhio'} /></button>
    </span>
    <div class="forza f{forza.livello}" aria-live="polite">
      <span class="barre" aria-hidden="true"><i></i><i></i><i></i></span>
      <span class="testo">{forza.testo}</span>
    </div>
  </div>
  <label class="field"><span class="lbl">Ripeti la nuova password</span>
    <input class="inp" type={mostra ? 'text' : 'password'} bind:value={ripeti} autocomplete="new-password" required />
  </label>
  <ul class="req" aria-label="Cosa serve">
    {#each requisiti as r (r.testo)}
      <li class:ok={r.ok}><span class="segno" aria-hidden="true">{#if r.ok}<svg viewBox="0 0 24 24"><path d="m5 12 5 5 9-10" /></svg>{/if}</span>
        {r.testo}<span class="sr">{r.ok ? ': fatto' : ': manca'}</span></li>
    {/each}
  </ul>
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
  <div class="azioni">
    {#if obbligatorio}<button type="button" class="btn btn-secondary" onclick={() => sessione.esci()}><Icona nome="esci" />Esci</button>{/if}
    <button class="btn btn-primary grow" disabled={inCorso || !pronta}>{inCorso ? 'Salvo…' : obbligatorio ? 'Salva e continua' : 'Salva la nuova password'}</button>
  </div>
</form>

<style>
  .cambio { display: flex; flex-direction: column; gap: var(--space-14); }
  .cambio.card { padding: var(--space-20); }
  .cambio .inp { height: 48px; }
  .pw { position: relative; display: block; }
  .pw .inp { width: 100%; box-sizing: border-box; padding-right: 52px; }
  .occhio { position: absolute; right: 2px; top: 2px; width: 44px; height: 44px; border: none; background: none; border-radius: var(--radius-md); color: var(--muted); cursor: pointer; display: grid; place-items: center; }
  .occhio:hover { color: var(--ink); }
  .forza { display: flex; flex-direction: column; gap: var(--space-6); margin-top: var(--space-4); }
  .barre { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-4); }
  .barre i { height: 6px; border-radius: 3px; background: var(--line); }
  .f1 .barre i:nth-child(1) { background: var(--warn); }
  .f2 .barre i:nth-child(-n + 2) { background: var(--cloro); }
  .f3 .barre i { background: var(--passa); }
  .forza .testo { font-size: var(--text-sm); color: var(--muted); }
  .req { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-md); }
  .req li { display: flex; align-items: center; gap: var(--space-8); color: var(--muted); }
  .segno { width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--line-strong); box-sizing: border-box; display: grid; place-items: center; flex-shrink: 0; }
  .req li.ok { color: var(--passa-fg); }
  .req li.ok .segno { border: none; background: var(--passa); }
  .segno svg { width: 12px; height: 12px; stroke: var(--on-passa); stroke-width: 3; fill: none; stroke-linecap: round; stroke-linejoin: round; }
  .azioni { display: flex; gap: var(--space-8); flex-wrap: wrap; }
</style>
