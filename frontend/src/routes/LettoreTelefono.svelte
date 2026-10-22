<script lang="ts">
  import { onDestroy } from 'svelte';
  import { api, ErroreApi, messaggioErrore } from '../lib/api';
  import { leggiConNfc, motivoNfcAssente, nfcDisponibile } from '../lib/nfc';

  /**
   * Il telefono come lettore NFC di un PC (ADR 0017): si apre dal QR mostrato sul PC (#/lettore/<token>) o scrivendo il
   * codice di 6 cifre (#/lettore). Non serve accedere: il telefono legge i braccialetti e manda al PC solo i codici,
   * e qui non si vede nessun dato dei clienti.
   */
  let token = $state(leggiToken());
  let codice = $state('');
  let errore = $state('');
  let scaduto = $state(false);
  let leggendo = $state(false);
  let inviati = $state<{ codice: string; ok: boolean; quando: Date }[]>([]);
  let ferma: AbortController | null = null;
  let presenza: ReturnType<typeof setInterval> | null = null;

  function leggiToken(): string {
    return location.hash.replace(/^#\/lettore\/?/, '').split('/')[0] ?? '';
  }

  async function presentati() {
    if (!token) return;
    try {
      await api.telefonoPresente(token);
      scaduto = false;
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 404) chiudi();
    }
  }

  function chiudi() {
    scaduto = true;
    fermaLettura();
    if (presenza) clearInterval(presenza);
    presenza = null;
  }

  $effect(() => {
    if (!token) return;
    presentati();
    presenza = setInterval(presentati, 20_000);
    return () => {
      if (presenza) clearInterval(presenza);
    };
  });

  async function usaCodice(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    try {
      const r = await api.tokenLettore(codice.replace(/\s/g, ''));
      history.replaceState(null, '', `#/lettore/${r.token}`);
      token = r.token;
    } catch (err) {
      errore = messaggioErrore(err, 'Codice non valido.');
    }
  }

  async function invia(c: string) {
    try {
      await api.inviaLettura(token, c);
      inviati = [{ codice: c, ok: true, quando: new Date() }, ...inviati].slice(0, 8);
      navigator.vibrate?.(60);
    } catch (e) {
      inviati = [{ codice: c, ok: false, quando: new Date() }, ...inviati].slice(0, 8);
      navigator.vibrate?.([80, 60, 80]);
      if (e instanceof ErroreApi && e.status === 404) chiudi();
      else errore = messaggioErrore(e, 'Non riesco a mandare il codice al PC.');
    }
  }

  async function iniziaLettura() {
    errore = '';
    ferma = new AbortController();
    try {
      await leggiConNfc((c) => invia(c), ferma.signal);
      leggendo = true;
    } catch (e) {
      leggendo = false;
      errore = e instanceof DOMException && e.name === 'NotAllowedError'
        ? 'Serve il permesso per l\'NFC: consentilo quando Chrome lo chiede (o dalle impostazioni del sito).'
        : 'Non riesco ad avviare l\'NFC. Controlla che sia acceso nelle impostazioni del telefono.';
    }
  }

  function fermaLettura() {
    ferma?.abort();
    ferma = null;
    leggendo = false;
  }

  onDestroy(() => {
    fermaLettura();
    if (presenza) clearInterval(presenza);
  });

  const ora = (d: Date) => d.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
</script>

<div class="lettore">
  <div class="testa">
    <div class="lbl">Leone Gestionale</div>
    <h1 class="page-title">Lettore NFC</h1>
  </div>

  {#if !token}
    <form class="card blocco" onsubmit={usaCodice}>
      <p class="nomargin">Scrivi il codice di 6 cifre che vedi sul PC, oppure inquadra il suo QR con la fotocamera.</p>
      <input class="inp m codice" bind:value={codice} inputmode="numeric" autocomplete="one-time-code" maxlength="7" placeholder="123 456"
             aria-label="Codice dell'abbinamento" />
      <button class="btn btn-primary" disabled={codice.replace(/\s/g, '').length !== 6}>Collega al PC</button>
      {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
    </form>
  {:else if scaduto}
    <div class="card blocco">
      <p class="nomargin"><strong>Il collegamento con il PC è finito.</strong> Dal PC rifai «Collega un telefono» e inquadra il QR nuovo.</p>
      <a class="btn btn-secondary" href="#/lettore" onclick={() => { token = ''; scaduto = false; inviati = []; }}>Scrivi un codice</a>
    </div>
  {:else}
    <div class="card blocco">
      <div class="collegato"><span class="punto"></span>Collegato al PC</div>
      {#if !nfcDisponibile()}
        <p class="msg err nomargin">{motivoNfcAssente()}</p>
      {:else if leggendo}
        <div class="onda" role="status"><span></span>Avvicina il braccialetto al retro del telefono</div>
        <button class="btn btn-secondary" onclick={fermaLettura}>Ferma la lettura</button>
      {:else}
        <button class="btn btn-accent grande" onclick={iniziaLettura}>Inizia a leggere</button>
        <p class="muted small nomargin">Tieni questa pagina aperta e lo schermo acceso mentre leggi.</p>
      {/if}
      {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
    </div>

    {#if inviati.length}
      <ul class="card inviati" aria-label="Codici mandati al PC">
        {#each inviati as x, i (x.quando.getTime() + x.codice + i)}
          <li class:errore={!x.ok}><span class="m">{x.codice}</span><span class="grow"></span><span class="small">{x.ok ? 'mandato' : 'non mandato'} · {ora(x.quando)}</span></li>
        {/each}
      </ul>
    {/if}
    <p class="muted small nomargin">Il telefono manda al PC solo i codici dei braccialetti: qui non si vedono dati dei clienti.</p>
  {/if}
</div>

<style>
  .lettore { width: min(480px, 100%); margin: 0 auto; display: flex; flex-direction: column; gap: var(--space-14); }
  .blocco { display: flex; flex-direction: column; gap: var(--space-12); padding: var(--space-16); }
  .codice { font-size: 28px; letter-spacing: 0.12em; text-align: center; height: 60px; }
  .grande { min-height: 64px; font-size: var(--text-lg); }
  .collegato { display: flex; align-items: center; gap: var(--space-8); font-weight: 600; color: var(--cloro-ink); }
  .punto { width: 10px; height: 10px; border-radius: 50%; background: var(--cloro); box-shadow: 0 0 0 4px var(--cloro-soft); }
  .onda { display: flex; align-items: center; gap: var(--space-10); font-weight: 600; color: var(--cloro-ink); background: var(--cloro-soft);
    border-radius: var(--radius-md); padding: var(--space-14); }
  .onda span { width: 18px; height: 18px; border-radius: 50%; background: var(--cloro); animation: pulsa 1.4s ease-in-out infinite; }
  @keyframes pulsa { 0%, 100% { transform: scale(0.7); opacity: 0.6; } 50% { transform: scale(1.1); opacity: 1; } }
  @media (prefers-reduced-motion: reduce) { .onda span { animation: none; } }
  .inviati { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .inviati li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-10) var(--space-14); border-top: 1px solid var(--line-soft); }
  .inviati li:first-child { border-top: none; }
  .inviati li.errore { color: var(--danger); }
</style>
