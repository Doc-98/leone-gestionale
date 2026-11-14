<script lang="ts">
  import { onDestroy } from 'svelte';
  import Icona from '../components/Icona.svelte';
  import Marchio from '../components/Marchio.svelte';
  import { api, ErroreApi, messaggioErrore } from '../lib/api';
  import { leggiConNfc, motivoNfcAssente, nfcDisponibile } from '../lib/nfc';

  /**
   * Il telefono come lettore NFC di un PC (ADR 0017): si apre dal QR mostrato sul PC (#/lettore/<token>) o scrivendo il
   * codice di 6 cifre (#/lettore). Non serve accedere: il telefono legge i braccialetti e manda al PC solo i codici,
   * e qui non si vede nessun dato dei clienti.
   */
  let token = $state(leggiToken());
  let cifre = $state(['', '', '', '', '', '']);
  let caselle: HTMLInputElement[] = $state([]);
  let errore = $state('');
  let scaduto = $state(false);
  let leggendo = $state(false);
  let nfcSpento = $state(false);
  let collegatoDalle = $state<Date | null>(null);
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
      collegatoDalle ??= new Date();
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

  /** Una cifra per casella; incollando il codice intero si riempiono tutte. */
  function scriviCifra(i: number, valore: string) {
    const solo = valore.replace(/\D/g, '');
    if (solo.length > 1) {
      cifre = [...solo.slice(0, 6).padEnd(6, ' ')].map((c) => c.trim());
      caselle[Math.min(solo.length, 6) - 1]?.focus();
      return;
    }
    cifre[i] = solo;
    if (solo && i < 5) caselle[i + 1]?.focus();
  }
  function tastoCifra(i: number, e: KeyboardEvent) {
    if (e.key === 'Backspace' && !cifre[i] && i > 0) caselle[i - 1]?.focus();
  }
  const codice = $derived(cifre.join(''));

  async function usaCodice(e: SubmitEvent) {
    e.preventDefault();
    errore = '';
    try {
      const r = await api.tokenLettore(codice);
      history.replaceState(null, '', `#/lettore/${r.token}`);
      token = r.token;
    } catch (err) {
      errore = messaggioErrore(err, 'Codice non valido.');
    }
  }

  async function invia(c: string) {
    try {
      await api.inviaLettura(token, c);
      inviati = [{ codice: c, ok: true, quando: new Date() }, ...inviati].slice(0, 4);
      navigator.vibrate?.(60);
    } catch (e) {
      inviati = [{ codice: c, ok: false, quando: new Date() }, ...inviati].slice(0, 4);
      navigator.vibrate?.([80, 60, 80]);
      if (e instanceof ErroreApi && e.status === 404) chiudi();
      else errore = messaggioErrore(e, 'Non riesco a mandare il codice al PC.');
    }
  }

  async function iniziaLettura() {
    errore = '';
    nfcSpento = false;
    ferma = new AbortController();
    try {
      await leggiConNfc((c) => invia(c), ferma.signal);
      leggendo = true;
    } catch (e) {
      leggendo = false;
      if (e instanceof DOMException && e.name === 'NotAllowedError')
        errore = 'Serve il permesso per l\'NFC: consentilo quando Chrome lo chiede (o dalle impostazioni del sito).';
      else nfcSpento = true;
    }
  }

  function fermaLettura() {
    ferma?.abort();
    ferma = null;
    leggendo = false;
  }

  function nuovoCodice() {
    token = '';
    scaduto = false;
    inviati = [];
    cifre = ['', '', '', '', '', ''];
    collegatoDalle = null;
    history.replaceState(null, '', '#/lettore');
  }

  onDestroy(() => {
    fermaLettura();
    if (presenza) clearInterval(presenza);
  });

  const ora = (d: Date) => d.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const ultimo = $derived(inviati.find((x) => x.ok));
  const mandati = $derived(inviati.filter((x) => x.ok).length);
  const stato = $derived(!token ? { c: '', t: 'Non collegato' } : scaduto ? { c: 'no', t: 'Finito' } : { c: 'on', t: 'Collegato' });
</script>

<div class="tel">
  <header class="testa">
    <Marchio misura={34} />
    <span class="titolo"><b>Lettore NFC</b><span>per il PC della segreteria</span></span>
    <span class="stato-c {stato.c}" role="status"><i></i>{stato.t}</span>
  </header>

  <div class="corpo">
    {#if !token}
      <form class="card blocco" onsubmit={usaCodice}>
        <h1 class="h">Collega al PC</h1>
        <p class="nomargin">Inquadra il QR che vedi sul PC con la fotocamera, oppure scrivi qui il codice di 6 cifre.</p>
        <div class="codice6" role="group" aria-label="Codice di 6 cifre">
          {#each cifre as c, i (i)}
            <input class="inp" bind:this={caselle[i]} value={c} inputmode="numeric" autocomplete={i === 0 ? 'one-time-code' : 'off'} aria-label="Cifra {i + 1} di 6"
              oninput={(e) => scriviCifra(i, e.currentTarget.value)} onkeydown={(e) => tastoCifra(i, e)} />
          {/each}
        </div>
        <button class="btn btn-primary" disabled={codice.length !== 6}>Collega al PC</button>
        {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
      </form>
      <div class="card blocco">
        <span class="lbl">Dove trovo il codice</span>
        <ol class="passi">
          <li>Sul PC apri <b>Tornello</b> o <b>Clienti</b>.</li>
          <li>In alto, accanto al campo del braccialetto, apri i lettori e tocca <b>Collega un telefono</b>.</li>
          <li>Inquadra il QR o copia le 6 cifre: valgono per qualche minuto.</li>
        </ol>
      </div>
    {:else if scaduto}
      <div class="card blocco">
        <h1 class="h">Il collegamento è finito</h1>
        <p class="nomargin">Il PC ha scollegato il telefono, oppure il collegamento è scaduto. Dal PC tocca di nuovo «Collega un telefono» e inquadra il QR nuovo.</p>
        <button class="btn btn-primary" onclick={nuovoCodice}>Scrivi un codice</button>
      </div>
    {:else if !nfcDisponibile()}
      <div class="card blocco"><p class="msg err nomargin">{motivoNfcAssente()}</p></div>
    {:else if nfcSpento}
      <div class="card blocco">
        <div class="avviso-l"><Icona nome="avviso" /><span><b>L'NFC del telefono è spento.</b>Senza NFC il telefono non legge i braccialetti.</span></div>
        <ol class="passi">
          <li>Apri le <b>Impostazioni</b> del telefono.</li>
          <li>Cerca <b>NFC</b> (di solito in «Dispositivi connessi») e accendilo.</li>
          <li>Torna qui e tocca «Riprova».</li>
        </ol>
        <button class="btn btn-primary" onclick={iniziaLettura}>Riprova</button>
      </div>
    {:else if leggendo}
      <div class="card grande">
        <div class="anello" aria-hidden="true"><i></i><i></i><i></i><span class="centro"><Icona nome="nfc" /></span></div>
        <b class="invito">Avvicina il braccialetto al retro del telefono</b>
        {#if ultimo}
          <div class="letto-ora" role="status">
            <span class="cerchio"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg></span>
            <span class="col"><b class="m">{ultimo.codice}</b><span class="small">mandato al PC alle {ora(ultimo.quando).slice(0, 5)}</span></span>
          </div>
        {/if}
      </div>
      {#if inviati.length}
        <div class="card elenco">
          <div class="elenco-t"><span class="lbl grow">Mandati al PC</span><span class="small muted">{mandati === 1 ? '1 braccialetto' : `${mandati} braccialetti`}</span></div>
          <ul class="inviati" aria-label="Codici mandati al PC">
            {#each inviati as x, i (x.quando.getTime() + x.codice + i)}
              <li><span class="m cod">{x.codice}</span><span class={x.ok ? 'ok-s' : 'no-s'}>{x.ok ? 'mandato' : 'non mandato, rileggilo'}</span><span class="q m">{ora(x.quando)}</span></li>
            {/each}
          </ul>
        </div>
      {/if}
      {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
      <button class="btn btn-secondary" onclick={fermaLettura}>Ferma la lettura</button>
    {:else}
      <div class="card grande">
        <button class="tondo" onclick={iniziaLettura}><Icona nome="nfc" />Inizia a leggere</button>
        <span class="muted small">Tieni questa pagina aperta e lo schermo acceso: il telefono legge finché non tocchi «Ferma».</span>
        {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}
      </div>
      <div class="card collegato">
        <Icona nome="server" />
        <span class="col grow"><b>Collegato al PC</b>{#if collegatoDalle}<span class="small muted">dalle {ora(collegatoDalle).slice(0, 5)}</span>{/if}</span>
      </div>
    {/if}

    <p class="muted small nomargin piede">Il telefono manda al PC solo i codici dei braccialetti: qui non si vedono dati dei clienti.</p>
  </div>
</div>

<style>
  .tel { min-height: 100vh; min-height: 100dvh; display: flex; flex-direction: column; background: var(--carta); }
  .testa { display: flex; align-items: center; gap: var(--space-12); padding: calc(env(safe-area-inset-top, 0px) + 14px) var(--space-20) 14px; background: var(--surface); border-bottom: 1px solid var(--line); }
  .titolo { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .titolo b { font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 800; line-height: 1.1; }
  .titolo span { font-size: var(--text-xs); color: var(--muted); }
  .stato-c { display: inline-flex; align-items: center; gap: var(--space-6); height: 28px; padding: 0 var(--space-10); border-radius: var(--radius-pill); font-size: var(--text-xs); font-weight: 700; background: var(--surface-sunken); color: var(--seg-fg); white-space: nowrap; }
  .stato-c i { width: 8px; height: 8px; border-radius: 50%; background: var(--line-strong); }
  .stato-c.on { background: var(--passa-bg); color: var(--passa-fg); }
  .stato-c.on i { background: var(--passa); }
  .stato-c.no { background: var(--ferma-bg); color: var(--ferma-fg); }
  .stato-c.no i { background: var(--ferma); }
  .corpo { flex: 1; width: min(480px, 100%); margin: 0 auto; box-sizing: border-box; padding: var(--space-20) var(--space-16) calc(env(safe-area-inset-bottom, 0px) + var(--space-20)); display: flex; flex-direction: column; gap: var(--space-14); }
  .blocco { display: flex; flex-direction: column; gap: var(--space-14); padding: var(--space-16) 18px; }
  .h { margin: 0; font-family: var(--font-display); font-size: 22px; font-weight: 800; }
  .codice6 { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: var(--space-8); }
  .codice6 .inp { height: 64px; padding: 0; text-align: center; font-family: var(--font-mono); font-size: var(--text-3xl); }
  .passi { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-md); color: var(--seg-fg); }
  .grande { display: flex; flex-direction: column; align-items: center; gap: 18px; padding: 22px var(--space-16); text-align: center; }
  .tondo { width: 188px; height: 188px; border-radius: 50%; border: none; background: var(--cloro); color: var(--on-cloro); font: inherit; font-size: 19px; font-weight: 700; cursor: pointer;
    display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--space-10); box-shadow: 0 0 0 10px var(--cloro-soft); }
  .tondo :global(.ic) { width: 44px; height: 44px; stroke-width: 1.6; }
  .anello { position: relative; width: 190px; height: 190px; display: grid; place-items: center; color: var(--cloro); }
  .anello i { position: absolute; inset: 0; border-radius: 50%; border: 3px solid var(--cloro); opacity: 0; animation: onda 2.4s ease-out infinite; }
  .anello i:nth-child(2) { animation-delay: 0.8s; }
  .anello i:nth-child(3) { animation-delay: 1.6s; }
  .centro { width: 108px; height: 108px; border-radius: 50%; background: var(--cloro-soft); display: grid; place-items: center; }
  .centro :global(.ic) { width: 52px; height: 52px; stroke-width: 1.6; }
  @keyframes onda { 0% { transform: scale(0.55); opacity: 0.9; } 100% { transform: scale(1); opacity: 0; } }
  @media (prefers-reduced-motion: reduce) { .anello i { animation: none; opacity: 0.3; } }
  .invito { font-size: var(--text-xl); }
  .letto-ora { width: 100%; box-sizing: border-box; display: flex; align-items: center; gap: var(--space-12); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--passa-bg); color: var(--passa-fg); text-align: left; }
  .cerchio { width: 36px; height: 36px; border-radius: 50%; background: var(--passa); display: grid; place-items: center; flex-shrink: 0; }
  .cerchio svg { width: 20px; height: 20px; stroke: var(--on-passa); stroke-width: 3; fill: none; stroke-linecap: round; stroke-linejoin: round; }
  .letto-ora b { font-size: 18px; color: var(--ink); }
  .col { display: flex; flex-direction: column; }
  .elenco { padding: var(--space-4) 0; }
  .elenco-t { display: flex; align-items: center; padding: var(--space-10) var(--space-14) var(--space-4); }
  .inviati { list-style: none; margin: 0; padding: 0 var(--space-14); }
  .inviati li { display: flex; align-items: center; gap: var(--space-10); min-height: 44px; border-top: 1px solid var(--line-soft); font-size: var(--text-md); }
  .inviati li:first-child { border-top: none; }
  .cod { font-weight: 500; }
  .q { margin-left: auto; font-size: var(--text-xs); color: var(--muted); }
  .ok-s { color: var(--passa-fg); font-weight: 600; }
  .no-s { color: var(--ferma); font-weight: 600; }
  .collegato { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-14) var(--space-16); }
  .collegato :global(.ic) { color: var(--cloro); }
  .collegato b { font-size: var(--text-base); }
  .avviso-l { display: flex; gap: var(--space-10); align-items: flex-start; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn); font-size: var(--text-md); }
  .avviso-l b { display: block; }
  .piede { margin-top: auto; }
</style>
