<script lang="ts">
  import Accesso from '../components/Accesso.svelte';
  import Icona from '../components/Icona.svelte';
  import { api, ErroreApi } from '../lib/api';
  import { cambiaServer, infoProgramma, nelProgramma } from '../lib/dispositivi';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * L'accesso: a sinistra la società, a destra email e password. Gli errori dicono cosa fare; la password dimenticata
   * la rimette chi gestisce gli account (non c'è il recupero per email). Gli account di prova si vedono solo nella demo.
   */
  let email = $state('');
  let password = $state('');
  let mostra = $state(false);
  let maiuscole = $state(false);
  let dimenticata = $state(false);
  let errore = $state<'' | 'credenziali' | 'server' | string>('');
  let lavoro = $state(false);
  let ambiente = $state<{ societa: string; demo: boolean } | null>(null);
  let serverSpento = $state(false);
  let versione = $state('');
  let demoScelto = $state('');

  api.ambiente().then((a) => (ambiente = a)).catch(() => (serverSpento = true));
  if (nelProgramma()) infoProgramma().then((i) => (versione = i?.versione ?? ''));

  const ora = new Date().getHours();
  const saluto = ora < 13 ? 'Buongiorno.' : ora < 18 ? 'Buon pomeriggio.' : 'Buonasera.';
  const DEMO = [
    { nome: 'Segreteria', email: 'segreteria@demo.leone' },
    { nome: 'Coordinatore', email: 'coordinatore@demo.leone' },
    { nome: 'Staff', email: 'staff@demo.leone' },
    { nome: 'Admin', email: 'admin@demo.leone' },
  ];

  async function entra(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    lavoro = true;
    try {
      await sessione.entra(email.trim(), password);
    } catch (err) {
      if (err instanceof ErroreApi && err.status === 401) errore = 'credenziali';
      else if (err instanceof ErroreApi && err.status === 0) errore = 'server';
      else errore = err instanceof ErroreApi ? err.message : 'Accesso non riuscito.';
    } finally {
      lavoro = false;
    }
  }

  function tasti(e: KeyboardEvent) {
    maiuscole = e.getModifierState?.('CapsLock') ?? false;
  }
</script>

<Accesso societa={ambiente?.societa} titolo={saluto} testo="Piscina, accessi, staff e pagamenti, nello stesso posto. Entra con l'email e la password che ti ha dato la segreteria."
  piede={versione ? `Leone Gestionale · programma per Windows, versione ${versione}` : 'Leone Gestionale'}>
  <h1 class="titolo">Accedi</h1>
  <form class="card modulo" onsubmit={entra}>
    {#if errore === 'credenziali'}
      <div class="avviso-a" role="alert"><Icona nome="avviso" /><span><b>Email o password non corrette.</b>Controlla di scriverle come te le hanno date; se non ricordi la password, guarda qui sotto.</span></div>
    {:else if errore === 'server'}
      <div class="avviso-a" role="alert"><Icona nome="server" /><span><b>Il server non risponde.</b>Controlla che il dispositivo sia collegato alla rete della società; se il problema resta, avvisa chi gestisce il server.</span></div>
    {:else if errore}
      <div class="avviso-a" role="alert"><Icona nome="avviso" /><span>{errore}</span></div>
    {/if}
    <label class="field"><span class="lbl">Email</span>
      <input class="inp" type="email" bind:value={email} autocomplete="username" required /></label>
    <div class="field">
      <label class="lbl" for="password">Password</label>
      <span class="pw">
        <input id="password" class="inp" type={mostra ? 'text' : 'password'} bind:value={password} autocomplete="current-password" required onkeydown={tasti} onkeyup={tasti} />
        <button type="button" class="occhio" aria-label={mostra ? 'Nascondi la password' : 'Mostra la password'} aria-pressed={mostra} onclick={() => (mostra = !mostra)}>
          <Icona nome={mostra ? 'occhio-no' : 'occhio'} /></button>
      </span>
      {#if maiuscole}<span class="caps" role="status"><Icona nome="su" />Il blocco delle maiuscole è attivo</span>{/if}
    </div>
    <button class="btn btn-primary" disabled={lavoro}>{lavoro ? 'Accesso…' : 'Entra'}</button>
    <button type="button" class="btn-quieto-a" aria-expanded={dimenticata} onclick={() => (dimenticata = !dimenticata)}>
      Ho dimenticato la password<Icona nome={dimenticata ? 'su' : 'giu'} /></button>
    {#if dimenticata}
      <p class="spiega-a">La password non arriva per email: te la rimette chi gestisce gli account (in segreteria o un admin). Ti darà una password temporanea, da cambiare la prima volta che entri.</p>
    {/if}
  </form>

  {#if nelProgramma()}
    <div class="server" class:no={serverSpento}>
      <span class="punto"></span>
      <span>Server {location.host}: {serverSpento ? 'non risponde' : 'raggiungibile'}</span>
      <button type="button" class="link-a" onclick={() => cambiaServer()}>Cambia</button>
    </div>
  {/if}

  {#if ambiente?.demo}
    <div class="demo">
      <span class="lbl">Solo nella demo · persone inventate</span>
      <div class="chi-demo">
        {#each DEMO as d (d.email)}
          <button type="button" aria-pressed={demoScelto === d.email} onclick={() => { email = d.email; password = 'demo1234'; demoScelto = d.email; errore = ''; }}>{d.nome}</button>
        {/each}
      </div>
      <span class="muted small">Tocca un ruolo per riempire email e password (demo1234). Fuori dalla demo questo riquadro non c'è.</span>
    </div>
  {/if}
</Accesso>

<style>
  .titolo { margin: 0; font-family: var(--font-display); font-weight: 800; font-size: var(--text-4xl); line-height: 1.1; letter-spacing: -0.01em; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-14); padding: var(--space-20); }
  .modulo .inp { height: 48px; }
  .pw { position: relative; display: block; }
  .pw .inp { width: 100%; box-sizing: border-box; padding-right: 52px; }
  .occhio { position: absolute; right: 2px; top: 2px; width: 44px; height: 44px; border: none; background: none; border-radius: var(--radius-md); color: var(--muted); cursor: pointer; display: grid; place-items: center; }
  .occhio:hover { color: var(--ink); }
  .caps { display: flex; align-items: center; gap: var(--space-6); font-size: var(--text-sm); font-weight: 600; color: var(--warn); }
  .caps :global(.ic) { width: 16px; height: 16px; }
  .avviso-a { display: flex; gap: var(--space-10); align-items: flex-start; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn); font-size: var(--text-md); }
  .avviso-a b { display: block; }
  .avviso-a :global(.ic) { margin-top: 1px; }
  .btn-quieto-a { align-self: flex-start; min-height: 44px; padding: 0; border: none; background: none; color: var(--cloro); font: inherit; font-size: var(--text-md); font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; gap: var(--space-6); }
  .btn-quieto-a :global(.ic) { width: 16px; height: 16px; }
  .spiega-a { margin: calc(-1 * var(--space-6)) 0 0; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--cloro-soft); color: var(--cloro-ink); font-size: var(--text-md); }
  .server { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-sm); color: var(--muted); flex-wrap: wrap; }
  .server .punto { width: 8px; height: 8px; border-radius: 50%; background: var(--passa); box-shadow: 0 0 0 3px var(--passa-bg); }
  .server.no .punto { background: var(--ferma); box-shadow: 0 0 0 3px var(--ferma-bg); }
  .link-a { min-height: 44px; padding: 0 var(--space-4); border: none; background: none; color: var(--cloro); font: inherit; font-weight: 600; cursor: pointer; }
  .demo { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); border: 1.5px dashed var(--line-dashed); border-radius: var(--radius); }
  .demo .lbl { color: var(--muted); }
  .chi-demo { display: flex; flex-wrap: wrap; gap: var(--space-8); }
  .chi-demo button { min-height: 36px; padding: 0 var(--space-12); border-radius: var(--radius-pill); border: 1px solid var(--line); background: var(--surface); font: inherit; font-size: var(--text-sm); font-weight: 600; color: var(--ink); cursor: pointer; position: relative; }
  .chi-demo button::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .chi-demo button[aria-pressed='true'] { background: var(--fill); border-color: var(--fill); color: var(--on-fill); }
</style>
