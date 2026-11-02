<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import { onDestroy } from 'svelte';
  import { api, ErroreApi, messaggioErrore, type Dispositivo, type EsitoMacchinaSimulata, type RispostaMacchina, type Servizio, type StatoCreditiBraccialetto, type Utilizzo } from '../lib/api';
  import { lettoreChip, lettoreSimulato } from '../lib/chip.svelte';
  import { dataBreve, dataDi, oraDi } from '../lib/date';
  import { lettoreTelefono } from '../lib/lettore.svelte';
  import { leggiConNfc, nfcDisponibile, normalizzaCodice } from '../lib/nfc';
  import LettoreTelefonoPc from '../components/LettoreTelefonoPc.svelte';

  /**
   * Phon e docce: come stanno le macchine, gli ultimi utilizzi e un simulatore che fa esattamente quello che fa una
   * macchina vera (scala i crediti), per provare il borsellino senza le macchine. Gli utilizzi simulati restano segnati.
   */
  let dispositivi = $state<Dispositivo[]>([]);
  let utilizzi = $state<Utilizzo[]>([]);
  let errore = $state('');
  let scelto = $state('');
  let codice = $state('');
  let postazione = $state('');
  let risposta = $state<RispostaMacchina | null>(null);
  let attesa = $state(false);

  async function carica() {
    try {
      [dispositivi, utilizzi] = await Promise.all([api.dispositivi(), api.utilizzi({ quanti: 30 })]);
      if (!scelto) scelto = dispositivi.find((d) => d.attivo)?.id ?? '';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();
  const giro = setInterval(carica, 15000);
  onDestroy(() => clearInterval(giro));

  const dispositivo = $derived(dispositivi.find((d) => d.id === scelto));

  async function prova(azione: 'USA' | 'SALDO') {
    errore = '';
    attesa = true;
    try {
      risposta = await api.provaDispositivo(scelto, normalizzaCodice(codice), azione, postazione || null);
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      attesa = false;
    }
  }

  // il codice si scrive, si legge con l'NFC del telefono, o arriva dal telefono abbinato come lettore
  let nfc: AbortController | null = null;
  async function leggi() {
    nfc = new AbortController();
    try {
      await leggiConNfc((c) => {
        codice = c;
        nfc?.abort();
        if (dispositivo) prova(dispositivo.tipo === 'SALDO' ? 'SALDO' : 'USA');
      }, nfc.signal);
    } catch {
      errore = "Non riesco ad avviare l'NFC.";
    }
  }
  onDestroy(() => nfc?.abort());
  onDestroy(lettoreTelefono.ascolta((c) => {
    codice = c;
    if (dispositivo) prova(dispositivo.tipo === 'SALDO' ? 'SALDO' : 'USA');
  }));

  // ── Macchine non collegate (ADR 0025): il simulatore scala i crediti nella memoria del braccialetto simulato ──
  let chip = $state<StatoCreditiBraccialetto | null>(null);
  let servizi = $state<Servizio[]>([]);
  let servizioSim = $state('');
  let esitoSim = $state<EsitoMacchinaSimulata | null>(null);
  api.statoCreditiBraccialetto().then((c) => (chip = c)).catch(() => {});
  api.servizi().then((x) => { servizi = x.filter((v) => v.attivo); servizioSim = servizi[0]?.id ?? ''; }).catch(() => {});
  const lettoreSim = $derived(lettoreChip(chip));
  const nomeServizio = $derived(servizi.find((v) => v.id === servizioSim)?.nome ?? '');
  async function usaOffline() {
    errore = '';
    attesa = true;
    try {
      lettoreSimulato.appoggia(codice);
      const memoria = await lettoreSimulato.leggi(chip!.accesso!);
      esitoSim = await api.macchinaSimulata(servizioSim, memoria);
      if (esitoSim.via) await lettoreSimulato.scrivi(memoria.uid, esitoSim.scrivi, chip!.accesso!);
    } catch (e) {
      errore = e instanceof ErroreApi || !(e instanceof Error) ? messaggioErrore(e) : e.message;
    } finally {
      attesa = false;
    }
  }

  const ESITI: Record<string, string> = { OK: 'Usato', GRATIS: 'Gratis', CREDITI_FINITI: 'Crediti finiti', SCONOSCIUTO: 'Sconosciuto', NON_ATTIVO: 'Non attivo', SERVIZIO_SPENTO: 'Spento' };
  const recente = (i: string | null) => !!i && Date.now() - new Date(i).getTime() < 10 * 60 * 1000;
</script>

<div class="pagina">
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if chip?.sulBraccialetto}
    <p class="hint nomargin">Le macchine non sono collegate: leggono e scalano i crediti sul braccialetto senza dirlo al gestionale. Contatti e utilizzi qui sotto
      si vedono solo per le macchine che chiamano il gestionale; i crediti consumati compaiono nel borsellino di ognuno quando si legge il suo braccialetto.</p>
  {/if}
  <div class="griglia">
    {#if chip?.sulBraccialetto}
      <section class="card blocco">
        <h3>Macchina non collegata (simulata)</h3>
        {#if lettoreSim.lettore}
          <p class="muted small nomargin">Come una centralina vera: legge i crediti dal braccialetto (simulato su questo dispositivo), li scala e li riscrive. Al gestionale non arriva niente; non conta gli usi gratuiti.</p>
          <label class="field"><span class="lbl">Servizio</span>
            <select class="inp" bind:value={servizioSim}>{#each servizi as v (v.id)}<option value={v.id}>{v.nome} · {v.crediti} cr.</option>{/each}</select></label>
          <form class="riga" onsubmit={(e) => { e.preventDefault(); usaOffline(); }}>
            <input class="inp m grow" bind:value={codice} placeholder="Codice del braccialetto" aria-label="Codice del braccialetto" autocomplete="off" />
            <button class="btn btn-primary" disabled={!servizioSim || normalizzaCodice(codice).length < 4 || attesa}>Appoggia</button>
          </form>
          <div class="display" class:via={esitoSim?.via} class:no={esitoSim && !esitoSim.via} aria-live="polite">
            {#if esitoSim}<span class="d1">{nomeServizio}</span><span class="d2">{esitoSim.via ? esitoSim.messaggio : `${esitoSim.messaggio} · ${esitoSim.crediti} cr.`}</span>
            {:else}<span class="d1">{nomeServizio}</span><span class="d2">Avvicinare card</span>{/if}
          </div>
        {:else}
          <p class="muted small nomargin">{lettoreSim.perche}</p>
        {/if}
      </section>
    {:else}
    <section class="card blocco">
      <h3>Simulatore</h3>
      <p class="muted small nomargin">Fa quello che farebbe la macchina: scala davvero i crediti (l'utilizzo resta segnato come simulato).</p>
      <label class="field"><span class="lbl">Macchina</span>
        <select class="inp" bind:value={scelto}>
          {#each dispositivi as d (d.id)}<option value={d.id}>{d.nome}{d.attivo ? '' : ' (spento)'}</option>{/each}
        </select>
      </label>
      <form class="riga" onsubmit={(e) => { e.preventDefault(); prova(dispositivo?.tipo === 'SALDO' ? 'SALDO' : 'USA'); }}>
        <input class="inp m grow" bind:value={codice} placeholder="Codice del braccialetto" aria-label="Codice del braccialetto" autocomplete="off" />
        {#if dispositivo?.tipo === 'SERVIZIO'}<input class="inp m post" bind:value={postazione} placeholder="N°" aria-label="Numero del phon o della doccia" maxlength="20" />{/if}
        {#if nfcDisponibile()}<button type="button" class="btn btn-secondary" onclick={leggi}><Icona nome="nfc" />NFC</button>{/if}
        <button class="btn btn-primary" disabled={!scelto || normalizzaCodice(codice).length < 4 || attesa}>{dispositivo?.tipo === 'SALDO' ? 'Leggi il saldo' : 'Appoggia'}</button>
      </form>
      {#if dispositivo?.tipo === 'SERVIZIO'}
        <button class="btn btn-quieto btn-sm sx" disabled={!scelto || normalizzaCodice(codice).length < 4} onclick={() => prova('SALDO')}><Icona nome="occhio" />Solo il saldo, senza usare</button>
      {/if}
      <div class="display" class:via={risposta?.via} class:no={risposta && !risposta.via && risposta.esito !== 'SALDO'} aria-live="polite">
        {#if risposta}
          <span class="d1">{risposta.nome ? `Ciao ${risposta.nome}` : (dispositivo?.nome ?? '')}</span>
          <span class="d2">{risposta.messaggio}</span>
          {#if risposta.via && risposta.minuti}<span class="d3">Via per {risposta.minuti} minuti</span>{/if}
        {:else}
          <span class="d1">{dispositivo?.nome ?? ''}</span>
          <span class="d2">Avvicinare il braccialetto</span>
        {/if}
      </div>
      {#if !nfcDisponibile()}<LettoreTelefonoPc compatto />{/if}
    </section>
    {/if}

    <section class="card blocco">
      <h3>Macchine</h3>
      <ul class="righe">
        {#each dispositivi as d (d.id)}
          <li class:spento={!d.attivo}>
            <span class="punto" class:on={recente(d.ultimoContatto)} title={recente(d.ultimoContatto) ? 'Si è fatta sentire negli ultimi 10 minuti' : 'Nessun contatto recente'}></span>
            <span class="grow"><strong>{d.nome}</strong><span class="small muted riga">{d.tipo === 'SALDO' ? 'Saldo' : d.servizio} · ultimo contatto {d.ultimoContatto ? `${dataBreve(dataDi(d.ultimoContatto))} ${oraDi(d.ultimoContatto)}` : 'mai'}</span></span>
            <span class="small">{d.utilizziOggi} oggi</span>
          </li>
        {:else}
          <li class="muted small">Nessun dispositivo: si aggiungono in «Impostazioni → Phon, docce e lettori».</li>
        {/each}
      </ul>
    </section>
  </div>

  <section class="card blocco">
    <h3>Ultimi utilizzi</h3>
    <ul class="righe">
      {#each utilizzi as u (u.id)}
        <li>
          <span class="m small ora">{dataBreve(dataDi(u.istante))} {oraDi(u.istante)}</span>
          <span class="grow small"><strong>{u.persona ?? u.codice}</strong> · {u.dispositivo}{u.postazione ? ` ${u.postazione}` : ''}{u.simulato ? ' · simulato' : ''}</span>
          <span class="tag {u.esito === 'OK' || u.esito === 'GRATIS' ? 't-soft' : 't-warn'}">{ESITI[u.esito] ?? u.esito}{u.crediti ? ` · ${u.crediti} cr.` : ''}</span>
        </li>
      {:else}
        <li class="muted small">Nessun utilizzo.</li>
      {/each}
    </ul>
  </section>
</div>

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); }
  .griglia { display: grid; grid-template-columns: 1fr; gap: var(--space-14); }
  @media (min-width: 1100px) { .griglia { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); } }
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .riga { display: flex; gap: var(--space-8); align-items: center; flex-wrap: wrap; }
  .post { width: 70px; }
  select.inp { appearance: auto; }
  .sx { align-self: flex-start; }
  /* come il display verde della centralina */
  .display { display: flex; flex-direction: column; gap: 2px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: #1f2a1a;
    color: #b8f28a; font-family: var(--font-mono, monospace); min-height: 76px; justify-content: center; border: 3px solid #2f3d27; }
  .display.via { border-color: var(--passa); }
  .display.no { border-color: var(--ferma); color: #ffb4a8; }
  .d1 { font-size: var(--text-sm); opacity: 0.85; }
  .d2 { font-size: var(--text-lg); font-weight: 700; }
  .d3 { font-size: var(--text-sm); }
  .righe { list-style: none; margin: 0; padding: 0; }
  .righe li { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); flex-wrap: wrap; }
  .righe li:first-child { border-top: none; }
  .righe .riga { display: block; }
  .spento { opacity: 0.6; }
  .punto { width: 10px; height: 10px; border-radius: 50%; background: var(--line); flex-shrink: 0; }
  .punto.on { background: var(--passa); }
  .ora { min-width: 110px; color: var(--muted); }
</style>
