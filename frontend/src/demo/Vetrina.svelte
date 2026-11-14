<script lang="ts">
  import { onMount } from 'svelte';
  import QRCode from 'qrcode';
  import { api } from '../lib/api';
  import { indirizzoPerTelefono } from '../lib/lettore.svelte';
  import Marchio from '../components/Marchio.svelte';
  import Icona from '../components/Icona.svelte';
  import { NOMI_APP, VERSIONI, misuraCornice, scala, versione, type App, type Versione } from './versioni';

  /** Account demo per l'accesso rapido (password di tutti: demo1234, vedi db/demo). */
  const ACCOUNT: Record<App, { email: string; nome: string }[]> = {
    gestionale: [
      { email: 'segreteria@demo.leone', nome: 'Segreteria' },
      { email: 'coordinatore@demo.leone', nome: 'Coordinatore' },
      { email: 'admin@demo.leone', nome: 'Amministratore' },
      { email: 'staff@demo.leone', nome: 'Staff spogliatoi (Luca)' },
      { email: 'federico.mauri@demo.leone', nome: 'Istruttore (Federico)' },
    ],
    famiglie: [],
  };
  const PASSWORD_DEMO = 'demo1234';

  let scelta = $state<Versione | undefined>(versione(new URLSearchParams(location.search).get('v')));
  let orizzontale = $state(new URLSearchParams(location.search).get('o') === '1');
  let demo = $state(false);
  let chi = $state('');
  let giro = $state(0);
  let qr = $state<{ img: string; url: string } | null>(null);
  let msg = $state('');
  let palco = $state<HTMLElement | undefined>();
  let spazio = $state({ larghezza: 0, altezza: 0 });

  const cornice = $derived(scelta ? misuraCornice(scelta, orizzontale) : undefined);
  const fattore = $derived(cornice ? scala(cornice, spazio) : 1);

  onMount(() => {
    api.ambiente().then((a) => (demo = a.demo)).catch(() => {});
    api.me().then((m) => (chi = m.email)).catch(() => {});
    const torna = () => {
      const p = new URLSearchParams(location.search);
      scelta = versione(p.get('v'));
      orizzontale = p.get('o') === '1';
      qr = null;
    };
    addEventListener('popstate', torna);
    return () => removeEventListener('popstate', torna);
  });

  // lo spazio del palco, per rimpicciolire la cornice quando la finestra è piccola
  $effect(() => {
    if (!palco) return;
    const ro = new ResizeObserver(([e]) => (spazio = { larghezza: e.contentRect.width, altezza: e.contentRect.height }));
    ro.observe(palco);
    return () => ro.disconnect();
  });

  function scrivi(v: Versione | undefined, oriz = orizzontale) {
    const u = new URL(location.href);
    u.search = '';
    if (v) u.searchParams.set('v', v.id);
    if (v?.ruotabile && oriz) u.searchParams.set('o', '1');
    history.pushState(null, '', u);
  }

  function apri(v: Versione) {
    if (!v.pronta) return;
    scelta = v;
    qr = null;
    msg = '';
    scrivi(v);
  }

  function home() {
    scelta = undefined;
    qr = null;
    scrivi(undefined);
  }

  function ruota() {
    orizzontale = !orizzontale;
    scrivi(scelta, orizzontale);
  }

  async function mostraQr() {
    if (!scelta) return;
    if (qr) return void (qr = null);
    const { url, localhost } = indirizzoPerTelefono();
    const completo = url + scelta.indirizzo;
    qr = { img: await QRCode.toDataURL(completo, { margin: 1, width: 220 }), url: completo };
    msg = localhost ? 'Il PC non ha un indirizzo nella rete locale: il telefono non lo raggiunge. Avvia con avvia.cmd.' : '';
  }

  async function entraCome(email: string) {
    if (!email) return;
    msg = '';
    try {
      if (chi) await api.logout().catch(() => {});
      const me = await api.login(email, PASSWORD_DEMO);
      chi = me.email;
      giro++;
    } catch {
      msg = 'Accesso non riuscito: il backend è acceso con il profilo demo?';
    }
  }

  async function esci() {
    await api.logout().catch(() => {});
    chi = '';
    giro++;
  }

  const gruppi: App[] = ['gestionale', 'famiglie'];
</script>

{#if !scelta}
  <main class="home">
    <header class="h-testa">
      <Marchio misura={64} />
      <div>
        <h1>Leone Gestionale</h1>
        <p>Scegli cosa provare. Da ogni versione puoi passare alle altre con la barra in alto.</p>
      </div>
    </header>
    {#each gruppi as app (app)}
      <section class="gruppo-v">
        <h2>{NOMI_APP[app]}</h2>
        <div class="schede">
          {#each VERSIONI.filter((v) => v.app === app) as v (v.id)}
            <button type="button" class="scheda" disabled={!v.pronta} onclick={() => apri(v)}>
              <span class="disegno {v.dispositivo}" aria-hidden="true"><i></i></span>
              <span class="s-testo">
                <b>{v.nome}</b>
                <span>{v.descrizione}</span>
              </span>
              {#if v.pronta}<span class="s-apri">Apri <Icona nome="dx" /></span>{:else}<span class="pill in-arrivo">in arrivo</span>{/if}
            </button>
          {/each}
        </div>
      </section>
    {/each}
    <p class="nota">Dati inventati, password degli account demo: <code>{PASSWORD_DEMO}</code>.</p>
  </main>
{:else}
  <div class="vista">
    <header class="barra-v">
      <button type="button" class="logo-b" onclick={home} aria-label="Tutte le versioni" title="Tutte le versioni"><Marchio misura={32} /></button>
      <nav class="scelte" aria-label="Versione">
        {#each gruppi as app (app)}
          <span class="sc-gruppo">
            <span class="sc-nome">{NOMI_APP[app]}</span>
            <span class="seg sc-seg" style="grid-template-columns: repeat({VERSIONI.filter((v) => v.app === app).length}, auto)">
              {#each VERSIONI.filter((v) => v.app === app) as v (v.id)}
                <button type="button" aria-pressed={scelta.id === v.id} disabled={!v.pronta} title={v.pronta ? '' : 'In arrivo'} onclick={() => apri(v)}>{v.nome}</button>
              {/each}
            </span>
          </span>
        {/each}
      </nav>
      <span class="strumenti">
        {#if scelta.ruotabile}
          <button type="button" class="btn btn-secondary btn-sm" onclick={ruota}><Icona nome="scambio" />{orizzontale ? 'Verticale' : 'Orizzontale'}</button>
        {/if}
        {#if demo && ACCOUNT[scelta.app].length}
          <label class="entra">
            <span class="sr">Entra come</span>
            <select class="inp" value="" onchange={(e) => entraCome((e.currentTarget as HTMLSelectElement).value)}>
              <option value="" disabled selected>{chi ? `Collegato: ${chi.split('@')[0]}` : 'Entra come…'}</option>
              {#each ACCOUNT[scelta.app] as a (a.email)}<option value={a.email}>{a.nome}</option>{/each}
            </select>
          </label>
          {#if chi}<button type="button" class="btn btn-secondary btn-sm" onclick={esci}><Icona nome="esci" />Esci</button>{/if}
        {/if}
        {#if scelta.dispositivo !== 'computer'}
          <button type="button" class="btn btn-secondary btn-sm" aria-pressed={!!qr} onclick={mostraQr}><Icona nome="qr" />Sul telefono</button>
        {/if}
        <a class="btn btn-secondary btn-sm" href={scelta.indirizzo} target="_blank" rel="noopener"><Icona nome="freccia" />Apri da sola</a>
      </span>
    </header>
    {#if msg}<p class="msg-v" role="status">{msg}</p>{/if}

    <div class="palco {scelta.dispositivo}" bind:this={palco}>
      {#key `${scelta.indirizzo}#${giro}`}
        {#if cornice}
          <div class="cornice {scelta.dispositivo}" style="width: {cornice.larghezza}px; height: {cornice.altezza}px; transform: scale({fattore})">
            <iframe src={scelta.indirizzo} title="{NOMI_APP[scelta.app]} · {scelta.nome}"></iframe>
          </div>
        {:else}
          <iframe class="intera" src={scelta.indirizzo} title="{NOMI_APP[scelta.app]} · {scelta.nome}"></iframe>
        {/if}
      {/key}
      {#if qr}
        <aside class="qr-pann" aria-label="Apri sul telefono">
          <img src={qr.img} alt="Codice QR" width="220" height="220" />
          <b>Inquadralo con il telefono</b>
          <span>Telefono e PC nella stessa Wi-Fi.</span>
          <code>{qr.url}</code>
          <button type="button" class="btn btn-secondary btn-sm" onclick={() => (qr = null)}>Chiudi</button>
        </aside>
      {/if}
    </div>
  </div>
{/if}

<style>
  :global(body) { margin: 0; background: var(--carta); }
  .home { max-width: 1080px; margin: 0 auto; padding: 48px 24px 64px; display: flex; flex-direction: column; gap: 32px; }
  .h-testa { display: flex; align-items: center; gap: 18px; }
  .h-testa h1 { margin: 0; font-family: var(--font-display); font-size: 34px; font-weight: 800; }
  .h-testa p { margin: 4px 0 0; color: var(--muted); }
  .gruppo-v { display: flex; flex-direction: column; gap: 14px; }
  .gruppo-v h2 { margin: 0; padding: 10px 16px; font-size: 18px; font-weight: 800; border-radius: var(--radius-md);
    background: #eef6f6; box-shadow: inset 4px 0 0 var(--cloro); }
  .schede { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
  .scheda { display: grid; grid-template-columns: 92px minmax(0, 1fr); grid-template-rows: 1fr auto; gap: 10px 16px; align-items: center;
    padding: 18px; border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); color: var(--ink);
    font: inherit; text-align: left; cursor: pointer; }
  .scheda:hover:not(:disabled) { border-color: var(--cloro); box-shadow: 0 6px 18px rgba(16, 33, 43, 0.08); }
  .scheda:disabled { cursor: default; opacity: 0.7; }
  .s-testo { display: flex; flex-direction: column; gap: 4px; font-size: 14px; color: var(--muted); }
  .s-testo b { font-size: 18px; color: var(--ink); }
  .s-apri { grid-column: 2; justify-self: end; display: inline-flex; align-items: center; gap: 4px; font-weight: 700; color: var(--cloro); }
  .s-apri :global(.ic) { width: 18px; height: 18px; }
  .in-arrivo { grid-column: 2; justify-self: end; }
  .disegno { grid-row: span 2; display: grid; place-items: center; height: 96px; }
  .disegno i { display: block; border: 3px solid var(--ink); border-radius: 8px; background: linear-gradient(160deg, #0b6e78, #14919b); }
  .disegno.computer i { width: 84px; height: 54px; border-radius: 6px; box-shadow: 0 10px 0 -6px var(--ink); }
  .disegno.telefono i { width: 40px; height: 78px; border-radius: 10px; }
  .disegno.tablet i { width: 62px; height: 82px; border-radius: 10px; }
  .nota { margin: 0; font-size: 13px; color: var(--muted); }

  .vista { height: 100vh; display: flex; flex-direction: column; }
  .barra-v { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 8px 16px; background: var(--surface);
    border-bottom: 1px solid var(--line); }
  .logo-b { border: none; background: none; padding: 4px; border-radius: 10px; cursor: pointer; display: flex; }
  .logo-b:hover { background: var(--cloro-soft); }
  .scelte { display: flex; gap: 18px; flex-wrap: wrap; flex: 1; }
  .sc-gruppo { display: flex; align-items: center; gap: 8px; }
  .sc-nome { font-size: 13px; font-weight: 700; color: var(--muted); white-space: nowrap; }
  .sc-seg button { min-height: 34px; padding: 0 12px; white-space: nowrap; }
  .sc-seg button:disabled { opacity: 0.45; cursor: default; }
  .strumenti { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  .strumenti a { text-decoration: none; }
  .entra select { height: 36px; min-width: 190px; padding: 0 10px; font-size: 14px; }
  .msg-v { margin: 0; padding: 8px 16px; background: var(--warn-bg); color: var(--warn); font-size: 14px; }
  .palco { position: relative; flex: 1; min-height: 0; display: grid; place-items: center; overflow: hidden;
    background: radial-gradient(circle at 50% 40%, #e9e4da, #d9d2c4); }
  .palco.computer { display: block; background: var(--carta); }
  .intera { width: 100%; height: 100%; border: 0; display: block; background: var(--carta); }
  .cornice { box-sizing: content-box; padding: 14px; border-radius: 46px; background: #10212b; box-shadow: 0 30px 60px rgba(16, 33, 43, 0.35);
    flex-shrink: 0; transform-origin: center center; }
  .cornice.tablet { border-radius: 34px; }
  .cornice iframe { width: 100%; height: 100%; border: 0; display: block; border-radius: 32px; background: var(--carta); }
  .cornice.tablet iframe { border-radius: 20px; }
  .qr-pann { position: absolute; right: 20px; top: 20px; z-index: 5; width: 260px; display: flex; flex-direction: column; align-items: center;
    gap: 8px; padding: 18px; border-radius: var(--radius-lg); background: var(--surface); box-shadow: 0 14px 34px rgba(16, 33, 43, 0.22);
    text-align: center; font-size: 14px; }
  .qr-pann code { font-size: 12px; overflow-wrap: anywhere; color: var(--muted); }
</style>
