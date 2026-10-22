<script lang="ts">
  import { onDestroy, onMount, tick } from 'svelte';
  import { api, messaggioErrore, type EsitoTornello, type Passaggio, type Verso } from '../lib/api';
  import { oraDi } from '../lib/date';
  import { codiceValido, leggiConNfc, motivoNfcAssente, nfcDisponibile, normalizzaCodice } from '../lib/nfc';
  import { schermo } from '../lib/schermo.svelte';
  import { sessione } from '../lib/sessione.svelte';
  import EsitoTornelloView from '../components/EsitoTornello.svelte';

  /**
   * La postazione del tornello. Sul telefono Android si leggono i braccialetti con l'NFC; sul PC un lettore da
   * scrivania scrive il codice nel campo come una tastiera (seguito da Invio). Ogni lettura è un passaggio registrato.
   */
  let codice = $state('');
  let esito = $state<EsitoTornello | null>(null);
  let errore = $state('');
  let inCorso = $state(false);
  let storico = $state<Passaggio[]>([]);
  let nfcAttivo = $state(false);
  let campo = $state<HTMLInputElement | null>(null);
  let fermaNfc: AbortController | null = null;

  // Il lato del tornello di questa postazione (il lettore d'uscita è un secondo lettore): si ricorda sul dispositivo
  let lato = $state<Verso>(leggiLato());
  function leggiLato(): Verso {
    try {
      return localStorage.getItem('tornello.lato') === 'USCITA' ? 'USCITA' : 'ENTRATA';
    } catch {
      return 'ENTRATA';
    }
  }
  function cambiaLato(v: Verso) {
    lato = v;
    esito = null;
    try {
      localStorage.setItem('tornello.lato', v);
    } catch {
      /* resta per questa sessione */
    }
  }

  const nfc = nfcDisponibile();
  const vedeSchede = $derived(sessione.puo('CLIENTI', 'BRACCIALETTI'));

  async function caricaStorico() {
    try {
      storico = await api.passaggi(30);
    } catch {
      /* lo storico è un di più */
    }
  }

  async function leggi(grezzo: string) {
    const c = normalizzaCodice(grezzo);
    if (!codiceValido(c)) {
      errore = 'Codice non valido.';
      return;
    }
    errore = '';
    inCorso = true;
    try {
      esito = await api.passa(c, lato);
      if (navigator.vibrate) navigator.vibrate(esito.verde ? 60 : [80, 60, 80]);
      caricaStorico();
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a controllare il braccialetto.');
    } finally {
      inCorso = false;
      codice = '';
      await tick();
      if (schermo.larga) campo?.focus();
    }
  }

  async function avviaNfc() {
    errore = '';
    fermaNfc = new AbortController();
    try {
      await leggiConNfc((c) => leggi(c), fermaNfc.signal);
      nfcAttivo = true;
    } catch (e) {
      nfcAttivo = false;
      errore = e instanceof DOMException && e.name === 'NotAllowedError'
        ? "Permesso NFC negato: consentilo nelle impostazioni del sito."
        : "Non riesco ad avviare l'NFC. Controlla che sia acceso.";
    }
  }

  function fermaLettura() {
    fermaNfc?.abort();
    fermaNfc = null;
    nfcAttivo = false;
  }

  onMount(() => {
    caricaStorico();
    if (schermo.larga) campo?.focus();
  });
  onDestroy(fermaLettura);

  const href = (personaId: string | undefined) => (vedeSchede && personaId ? `#/clienti/${personaId}` : null);
</script>

<div class="tornello" class:largo={schermo.larga}>
  <div class="principale">
    <form class="card lettore" onsubmit={(e) => { e.preventDefault(); leggi(codice); }}>
      <div class="seg lati" role="group" aria-label="Lato del tornello">
        <button type="button" aria-pressed={lato === 'ENTRATA'} onclick={() => cambiaLato('ENTRATA')}>Entrata</button>
        <button type="button" aria-pressed={lato === 'USCITA'} onclick={() => cambiaLato('USCITA')}>Uscita</button>
      </div>
      {#if nfc}
        {#if nfcAttivo}
          <div class="nfc on" role="status"><span class="onda" aria-hidden="true"></span>Avvicina il braccialetto al telefono</div>
          <button type="button" class="btn btn-secondary" onclick={fermaLettura}>Ferma la lettura</button>
        {:else}
          <button type="button" class="btn btn-accent grande" onclick={avviaNfc}>Leggi i braccialetti con l'NFC</button>
        {/if}
      {:else if !schermo.larga || /Android/i.test(navigator.userAgent)}
        <p class="muted small nomargin">{motivoNfcAssente()}</p>
      {/if}
      <label class="field">
        <span class="lbl">{schermo.larga ? 'Passa il braccialetto sul lettore o scrivi il codice' : 'Codice del braccialetto'}</span>
        <div class="row">
          <input class="inp m grow" bind:this={campo} bind:value={codice} autocomplete="off" autocapitalize="characters" spellcheck="false"
                 placeholder="Es. D0000002" aria-label="Codice del braccialetto" />
          <button class="btn btn-primary" disabled={inCorso || !codice.trim()}>Controlla</button>
        </div>
      </label>
      {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
    </form>

    {#if esito}
      <EsitoTornelloView {esito} schedaHref={href(esito.persona?.id)} />
      <p class="muted small nomargin">Letto alle {oraDi(esito.istante)}.</p>
    {:else}
      <div class="attesa card">
        <svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4h10v16H7zM10 8h4M12 15v.01" /></svg>
        <span>In attesa di un braccialetto</span>
      </div>
    {/if}
  </div>

  <section class="storico">
    <div class="hdr-storico">
      <h2 class="lbl">Passaggi di oggi</h2>
      <button class="link small" onclick={caricaStorico}>Aggiorna</button>
    </div>
    <ul class="card lista">
      {#each storico.slice(0, schermo.larga ? 30 : 10) as p (p.id)}
        <li>
          <span class="punto" class:v={p.verde} aria-label={p.verde ? 'Verde' : 'Rosso'}></span>
          <span class="m ora">{oraDi(p.istante)}</span>
          <span class="chi">
            {#if p.persona}
              {#if href(p.persona.id)}<a href={href(p.persona.id)}>{p.persona.cognome} {p.persona.nome}</a>{:else}{p.persona.cognome} {p.persona.nome}{/if}
            {:else}<span class="m">{p.codice}</span>{/if}
            {#if p.verso === 'USCITA'}<span class="perche">Uscita</span>{/if}
            {#if !p.verde && p.motivi.length}<span class="perche">{p.motivi.filter((m) => m.blocca).map((m) => m.messaggio).join(' · ')}</span>{/if}
            {#if p.segnalato}<span class="perche oro">Sotto controllo: segnalato alla segreteria</span>{/if}
            {#if p.goldenPass}<span class="perche">Golden pass</span>{/if}
            {#if p.carnetScalato}<span class="perche">Carnet: scalato un ingresso</span>{/if}
          </span>
        </li>
      {:else}
        <li class="muted small">Nessun passaggio oggi.</li>
      {/each}
    </ul>
  </section>
</div>

<style>
  .tornello { display: flex; flex-direction: column; gap: var(--space-20); }
  .tornello.largo { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(320px, 1fr); align-items: start; gap: var(--space-24, 24px); }
  .principale { display: flex; flex-direction: column; gap: var(--space-14); }
  .lettore { display: flex; flex-direction: column; gap: var(--space-12); padding: var(--space-16); }
  .grande { min-height: 56px; font-size: var(--text-lg); }
  .nfc { display: flex; align-items: center; gap: var(--space-10); font-weight: 600; color: var(--cloro-ink); background: var(--cloro-soft);
    padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); }
  .onda { width: 14px; height: 14px; border-radius: 50%; background: var(--cloro); animation: pulsa 1.4s ease-out infinite; }
  @keyframes pulsa { 0% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--cloro) 60%, transparent); } 100% { box-shadow: 0 0 0 14px transparent; } }
  @media (prefers-reduced-motion: reduce) { .onda { animation: none; } }
  .lettore .inp { font-size: var(--text-lg); letter-spacing: 0.04em; }
  .attesa { display: flex; align-items: center; justify-content: center; gap: var(--space-10); min-height: 160px; color: var(--muted);
    border-style: dashed; border-color: var(--line-dashed); background: transparent; }
  .attesa .ic { width: 26px; height: 26px; }
  .storico { display: flex; flex-direction: column; gap: var(--space-8); }
  .hdr-storico { display: flex; justify-content: space-between; align-items: center; }
  .hdr-storico h2 { margin: 0; }
  .lista { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .lista li { display: flex; align-items: flex-start; gap: var(--space-10); padding: var(--space-10) var(--space-12); border-top: 1px solid var(--line-soft); }
  .lista li:first-child { border-top: none; }
  .punto { width: 10px; height: 10px; border-radius: 50%; background: var(--ferma); margin-top: 6px; flex-shrink: 0; }
  .punto.v { background: var(--passa); }
  .ora { color: var(--muted); font-size: var(--text-sm); padding-top: 1px; }
  .chi { display: flex; flex-direction: column; gap: 1px; min-width: 0; font-weight: 500; }
  .chi a { color: var(--ink); text-decoration: none; }
  .chi a:hover { text-decoration: underline; }
  .lati { grid-template-columns: 1fr 1fr; }
  .perche.oro { color: var(--oro-fg); font-weight: 600; }
  .perche { font-size: var(--text-sm); color: var(--muted); font-weight: 400; }
</style>
