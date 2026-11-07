<script lang="ts">
  import BorsellinoCliente from '../components/BorsellinoCliente.svelte';
  import Icona from '../components/Icona.svelte';
  import LettoreTelefonoPc from '../components/LettoreTelefonoPc.svelte';
  import { onDestroy, tick } from 'svelte';
  import {
    api, ErroreApi, messaggioErrore,
    type Dispositivo, type EsitoMacchinaSimulata, type PersonaScheda, type RispostaMacchina, type SchedaCliente, type Servizio, type StatoCreditiBraccialetto, type Utilizzo,
  } from '../lib/api';
  import { lettoreChip, lettoreSimulato } from '../lib/chip.svelte';
  import { dataBreve, dataDi, iso, oraDi } from '../lib/date';
  import { daRicaricare, ESITI_MACCHINA, statoMacchina } from '../lib/macchine';
  import { lettoreTelefono } from '../lib/lettore.svelte';
  import { codiceValido, leggiConNfc, nfcDisponibile, normalizzaCodice } from '../lib/nfc';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Phon e docce al banco (variante B del mockup): si passa il braccialetto, o si cerca per nome, e si vede subito il
   * borsellino di quella persona, con la ricarica già aperta. A destra chi è rimasto senza crediti oggi e lo stato delle
   * macchine (solo con le macchine collegate: quelle non collegate non dicono niente al gestionale, ADR 0025) e, chiuso,
   * il simulatore per provare senza le macchine vere.
   */
  let chip = $state<StatoCreditiBraccialetto | null>(null);
  api.statoCreditiBraccialetto().then((c) => (chip = c)).catch(() => {});
  const sulBraccialetto = $derived(!!chip?.sulBraccialetto);
  const puoImpostare = $derived(sessione.puo('IMPOSTAZIONI'));

  // ── Chi c'è al banco ──────────────────────────────────────────────────
  let cerca = $state('');
  let trovate = $state<PersonaScheda[]>([]);
  let attivo = $state(0);
  let errore = $state('');
  let scelta = $state<SchedaCliente | null>(null);
  let codiceLetto = $state('');
  let versione = $state(0);
  let borsellino = $state<ReturnType<typeof BorsellinoCliente> | null>(null);
  let campo = $state<HTMLInputElement | null>(null);

  async function apri(personaId: string, codice = '') {
    errore = '';
    cerca = '';
    trovate = [];
    try {
      scelta = await api.schedaCliente(personaId);
      codiceLetto = codice;
      await tick();
      // al banco si viene quasi sempre per ricaricare: la ricarica è già aperta
      if (sessione.puo('BORSELLINO')) borsellino?.avviaRicarica();
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco ad aprire questo cliente.');
    }
  }

  /** Un codice dal lettore (da scrivania, NFC o telefono): di chi è? */
  async function braccialetto(grezzo: string): Promise<boolean> {
    const c = normalizzaCodice(grezzo);
    if (!/\d/.test(c) || !codiceValido(c)) return false;
    try {
      const r = await api.braccialettoDiChi(c);
      if (r.personaId) await apri(r.personaId, c);
      else {
        cerca = '';
        errore = `Il braccialetto ${c} non è di nessun cliente: si assegna dalla scheda del cliente.`;
      }
      return true;
    } catch {
      return false; // non era un braccialetto: si cerca per nome
    }
  }

  let ultima = 0;
  async function cercaPersone() {
    const q = cerca.trim();
    const n = ++ultima;
    attivo = 0;
    if (q.length < 2 || /^[0-9a-f:\- ]{8,}$/i.test(q)) return void (trovate = []);
    try {
      const r = await api.cercaPersone(q);
      if (n === ultima) trovate = r.slice(0, 8);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  async function tasti(e: KeyboardEvent) {
    if (e.key === 'ArrowDown' && trovate.length) { e.preventDefault(); attivo = (attivo + 1) % trovate.length; }
    else if (e.key === 'ArrowUp' && trovate.length) { e.preventDefault(); attivo = (attivo - 1 + trovate.length) % trovate.length; }
    else if (e.key === 'Escape') { cerca = ''; trovate = []; }
    else if (e.key === 'Enter') {
      e.preventDefault();
      if (await braccialetto(cerca)) return;
      if (trovate[attivo]) apri(trovate[attivo].id);
    }
  }

  // il telefono abbinato come lettore manda i codici qui
  onDestroy(lettoreTelefono.ascolta((c) => void braccialetto(c)));
  let nfc: AbortController | null = null;
  let nfcAttivo = $state(false);
  async function leggiNfc() {
    nfc?.abort();
    nfc = new AbortController();
    nfcAttivo = true;
    try {
      await leggiConNfc((c) => void braccialetto(c), nfc.signal);
    } catch {
      nfcAttivo = false;
      errore = "Non riesco ad avviare l'NFC.";
    }
  }
  onDestroy(() => nfc?.abort());
  $effect(() => {
    campo?.focus();
  });

  const tutore = $derived(scelta?.famiglia.tutori[0] ?? null);
  const chi = $derived(!scelta ? '' : [
    scelta.persona.eta == null ? null : scelta.persona.eta === 1 ? '1 anno' : `${scelta.persona.eta} anni`,
    tutore ? `${tutore.relazione.toLowerCase()} ${tutore.nome} ${tutore.cognome}` : null,
    codiceLetto ? `braccialetto ${codiceLetto}` : null,
  ].filter(Boolean).join(' · '));
  const iniziali = (nome: string, cognome: string) => `${nome[0] ?? ''}${cognome[0] ?? ''}`.toUpperCase();

  // ── Macchine collegate: utilizzi di oggi e stato ─────────────────────
  let dispositivi = $state<Dispositivo[]>([]);
  let utilizzi = $state<Utilizzo[]>([]);
  async function caricaMacchine() {
    try {
      [dispositivi, utilizzi] = await Promise.all([api.dispositivi(), api.utilizzi({ quanti: 300 })]);
      if (!sceltoSim) sceltoSim = dispositivi.find((d) => d.attivo)?.id ?? '';
    } catch {
      /* chi non può vedere le macchine vede solo il borsellino */
    }
  }
  caricaMacchine();
  const giro = setInterval(caricaMacchine, 15000);
  onDestroy(() => clearInterval(giro));
  let adesso = $state(Date.now());
  const orologio = setInterval(() => (adesso = Date.now()), 30000);
  onDestroy(() => clearInterval(orologio));

  const oggi = $derived(iso(new Date(adesso)));
  const coda = $derived(daRicaricare(utilizzi, oggi));
  const suoiOggi = $derived(!scelta ? [] : utilizzi.filter((u) => u.personaId === scelta!.persona.id && dataDi(u.istante) === oggi));
  const respinta = $derived(coda.find((c) => c.personaId === scelta?.persona.id) ?? null);

  // ── Simulatore (per provare senza le macchine vere) ───────────────────
  let sceltoSim = $state('');
  let codiceSim = $state('');
  let postazione = $state('');
  let risposta = $state<RispostaMacchina | null>(null);
  let attesa = $state(false);
  const dispositivo = $derived(dispositivi.find((d) => d.id === sceltoSim));
  async function prova(azione: 'USA' | 'SALDO') {
    errore = '';
    attesa = true;
    try {
      risposta = await api.provaDispositivo(sceltoSim, normalizzaCodice(codiceSim), azione, postazione || null);
      await caricaMacchine();
      versione++;
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      attesa = false;
    }
  }
  let servizi = $state<Servizio[]>([]);
  let servizioSim = $state('');
  let esitoSim = $state<EsitoMacchinaSimulata | null>(null);
  api.servizi().then((x) => { servizi = x.filter((v) => v.attivo); servizioSim = servizi[0]?.id ?? ''; }).catch(() => {});
  const lettoreSim = $derived(lettoreChip(chip));
  const nomeServizio = $derived(servizi.find((v) => v.id === servizioSim)?.nome ?? '');
  async function usaOffline() {
    errore = '';
    attesa = true;
    try {
      lettoreSimulato.appoggia(codiceSim);
      const memoria = await lettoreSimulato.leggi(chip!.accesso!);
      esitoSim = await api.macchinaSimulata(servizioSim, memoria);
      if (esitoSim.via) await lettoreSimulato.scrivi(memoria.uid, esitoSim.scrivi, chip!.accesso!);
    } catch (e) {
      errore = e instanceof ErroreApi || !(e instanceof Error) ? messaggioErrore(e) : e.message;
    } finally {
      attesa = false;
    }
  }
</script>

<div class="pagina">
  <div class="barra">
    <div class="cerca-c">
      <label class="campo-codice"><span class="sr">Braccialetto o cliente</span>
        <Icona nome="braccialetto" />
        <input bind:this={campo} class="inp" type="search" bind:value={cerca} oninput={cercaPersone} onkeydown={tasti} autocomplete="off"
          placeholder="Passa il braccialetto o scrivi un nome" role="combobox" aria-expanded={trovate.length > 0} aria-controls="trovate-pd" />
      </label>
      {#if trovate.length}
        <ul class="card trovate" id="trovate-pd" role="listbox" aria-label="Clienti trovati">
          {#each trovate as p, i (p.id)}
            <li role="option" aria-selected={i === attivo}><button type="button" class:on={i === attivo} onclick={() => apri(p.id)}>
              <span class="av" aria-hidden="true">{iniziali(p.nome, p.cognome)}</span><b>{p.cognome} {p.nome}</b>
              {#if p.dataNascita}<span class="muted small">{dataBreve(p.dataNascita)}</span>{/if}</button></li>
          {/each}
        </ul>
      {/if}
    </div>
    {#if nfcDisponibile()}
      <button type="button" class="btn btn-secondary" aria-pressed={nfcAttivo} onclick={leggiNfc}><Icona nome="nfc" />{nfcAttivo ? 'NFC in ascolto' : 'Leggi con NFC'}</button>
    {:else}
      <LettoreTelefonoPc />
    {/if}
  </div>
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  <div class="corpo">
    <section class="princ" aria-label="Il cliente al banco">
      {#if scelta}
        {@const p = scelta.persona}
        <div class="card chi-c">
          <span class="av grande" aria-hidden="true">{iniziali(p.nome, p.cognome)}</span>
          <span class="chi-t"><a class="nome-g" href={`#/clienti/${p.id}`}>{p.nome} {p.cognome}</a><span class="muted small">{chi}</span></span>
          <a class="btn btn-secondary btn-sm" href={`#/clienti/${p.id}`}>Scheda completa</a>
          <button type="button" class="mini-btn" aria-label="Chiudi questo cliente" onclick={() => { scelta = null; campo?.focus(); }}><Icona nome="chiudi" /></button>
        </div>
        {#if respinta}
          <p class="avviso-r" role="status"><Icona nome="avviso" />Alle {oraDi(respinta.ultimo)} {respinta.dove} ha detto «crediti finiti»{respinta.tentativi > 1 ? ` (${respinta.tentativi} tentativi)` : ''}.</p>
        {/if}
        {#key p.id}
          <BorsellinoCliente bind:this={borsellino} personaId={p.id} {versione} cambiato={caricaMacchine} />
        {/key}
        {#if !sulBraccialetto && suoiOggi.length}
          <p class="muted small nomargin">Oggi: {suoiOggi.length === 1 ? '1 passaggio' : `${suoiOggi.length} passaggi`} alle macchine,
            {suoiOggi.filter((u) => u.esito === 'GRATIS').length} gratis.</p>
        {/if}
      {:else}
        <div class="card vuoto">
          <Icona nome="braccialetto" />
          <b>Passa il braccialetto sul lettore</b>
          <span class="muted">Oppure scrivi il nome qui sopra. Si apre il borsellino del cliente, con la ricarica dei crediti di phon e docce già pronta.</span>
          {#if sulBraccialetto}<span class="muted small">I crediti stanno sul braccialetto: per ricaricare serve il lettore della memoria (lo trovi nel borsellino del cliente).</span>{/if}
        </div>
      {/if}
    </section>

    <aside class="lato-m">
      {#if !sulBraccialetto}
        <section class="blocco-l" aria-label="Da ricaricare">
          <h2 class="lbl arancio">Da ricaricare · crediti finiti oggi</h2>
          <ul class="coda">
            {#each coda as c (c.personaId ?? c.codice)}
              <li><button type="button" aria-pressed={c.personaId === scelta?.persona.id} disabled={!c.personaId} onclick={() => c.personaId && apri(c.personaId)}>
                <span class="av" aria-hidden="true">{c.nome ? c.nome.split(' ').map((x) => x[0]).slice(0, 2).join('') : '?'}</span>
                <span class="grow"><b>{c.nome ?? c.codice}</b><span class="muted small">{c.dove}{c.tentativi > 1 ? ` · ${c.tentativi} tentativi` : ''}</span></span>
                <span class="m muted small">{oraDi(c.ultimo)}</span></button></li>
            {:else}
              <li class="muted small nessuno">Nessuno, per ora.</li>
            {/each}
          </ul>
        </section>
      {/if}

      {#if dispositivi.length}
        <section class="card blocco-m" aria-label="Macchine">
          <div class="testa-m"><h2 class="lbl">Macchine</h2><span class="grow"></span>
            {#if puoImpostare}<a class="link-m" href="#/servizi">Impostazioni →</a>{/if}</div>
          {#if sulBraccialetto}<p class="muted small nomargin">Le macchine non sono collegate: qui si vedono solo quelle che chiamano il gestionale.</p>{/if}
          <ul class="macchine">
            {#each dispositivi as d (d.id)}
              {@const s = statoMacchina(d, adesso)}
              <li class:spenta={s.classe === 'spenta'}><span class="punto {s.classe}" aria-hidden="true"></span><span class="grow">{d.nome}</span><span class="muted small">{s.testo}</span></li>
            {/each}
          </ul>
        </section>
      {/if}

      <details class="card sim">
        <summary><Icona nome="macchine" />Prova una macchina (simulatore)</summary>
        {#if sulBraccialetto}
          {#if lettoreSim.lettore}
            <p class="muted small nomargin">Come una centralina vera: legge i crediti dal braccialetto (simulato su questo dispositivo), li scala e li riscrive. Al gestionale non arriva niente.</p>
            <label class="field"><span class="lbl">Servizio</span>
              <select class="inp" bind:value={servizioSim}>{#each servizi as v (v.id)}<option value={v.id}>{v.nome} · {v.crediti} cr.</option>{/each}</select></label>
            <form class="riga" onsubmit={(e) => { e.preventDefault(); usaOffline(); }}>
              <input class="inp m grow" bind:value={codiceSim} placeholder="Codice del braccialetto" aria-label="Codice del braccialetto da provare" autocomplete="off" />
              <button class="btn btn-primary" disabled={!servizioSim || normalizzaCodice(codiceSim).length < 4 || attesa}>Appoggia</button>
            </form>
            <div class="display" class:via={esitoSim?.via} class:no={esitoSim && !esitoSim.via} aria-live="polite">
              <span class="d1">{nomeServizio}</span><span class="d2">{esitoSim ? (esitoSim.via ? esitoSim.messaggio : `${esitoSim.messaggio} · ${esitoSim.crediti} cr.`) : 'Avvicinare card'}</span>
            </div>
          {:else}
            <p class="muted small nomargin">{lettoreSim.perche}</p>
          {/if}
        {:else}
          <p class="muted small nomargin">Fa quello che farebbe la macchina: scala davvero i crediti (l'utilizzo resta segnato come simulato).</p>
          <label class="field"><span class="lbl">Macchina</span>
            <select class="inp" bind:value={sceltoSim}>{#each dispositivi as d (d.id)}<option value={d.id}>{d.nome}{d.attivo ? '' : ' (spenta)'}</option>{/each}</select></label>
          <form class="riga" onsubmit={(e) => { e.preventDefault(); prova(dispositivo?.tipo === 'SALDO' ? 'SALDO' : 'USA'); }}>
            <input class="inp m grow" bind:value={codiceSim} placeholder="Codice del braccialetto" aria-label="Codice del braccialetto da provare" autocomplete="off" />
            {#if dispositivo?.tipo === 'SERVIZIO'}<input class="inp m post" bind:value={postazione} placeholder="N°" aria-label="Numero del phon o della doccia" maxlength="20" />{/if}
            <button class="btn btn-primary" disabled={!sceltoSim || normalizzaCodice(codiceSim).length < 4 || attesa}>{dispositivo?.tipo === 'SALDO' ? 'Saldo' : 'Appoggia'}</button>
          </form>
          <div class="display" class:via={risposta?.via} class:no={risposta && !risposta.via && risposta.esito !== 'SALDO'} aria-live="polite">
            <span class="d1">{risposta?.nome ? `Ciao ${risposta.nome}` : (dispositivo?.nome ?? '')}</span>
            <span class="d2">{risposta ? risposta.messaggio : 'Avvicinare il braccialetto'}</span>
            {#if risposta?.via && risposta.minuti}<span class="d3">Via per {risposta.minuti} minuti</span>{/if}
          </div>
          <ul class="ultimi">
            {#each utilizzi.slice(0, 6) as u (u.id)}
              <li><span class="m muted">{oraDi(u.istante)}</span><span class="grow">{u.persona ?? u.codice} · {u.dispositivo}</span><span class="tag {u.esito === 'OK' || u.esito === 'GRATIS' ? 't-soft' : 't-warn'}">{ESITI_MACCHINA[u.esito] ?? u.esito}</span></li>
            {/each}
          </ul>
        {/if}
      </details>
    </aside>
  </div>
</div>

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); }
  .barra { display: flex; gap: var(--space-10); align-items: flex-start; flex-wrap: wrap; }
  .cerca-c { position: relative; flex: 1 1 360px; max-width: 620px; }
  .campo-codice { display: flex; align-items: center; gap: var(--space-10); height: 52px; padding: 0 var(--space-14); border-radius: var(--radius-lg); border: 1.5px solid var(--line-strong);
    background: var(--surface); }
  .campo-codice:focus-within { border-color: var(--cloro); box-shadow: 0 0 0 3px var(--cloro-soft); }
  .campo-codice :global(.ic) { color: var(--muted); flex-shrink: 0; }
  .campo-codice .inp { flex: 1; border: none; background: none; height: 48px; padding: 0; font-size: var(--text-lg); box-shadow: none; outline: none; min-width: 0; }
  .trovate { position: absolute; z-index: 5; left: 0; right: 0; top: calc(100% + 6px); list-style: none; margin: 0; padding: var(--space-4); }
  .trovate button { width: 100%; display: flex; align-items: center; gap: var(--space-10); min-height: 44px; padding: 0 var(--space-10); border: none; border-radius: var(--radius-md);
    background: none; font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .trovate button.on, .trovate button:hover { background: var(--cloro-soft); }
  .trovate b { flex: 1; }
  .av { width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: var(--surface-sunken); color: var(--seg-fg); font-size: var(--text-xs);
    font-weight: 700; flex-shrink: 0; }
  .av.grande { width: 52px; height: 52px; font-size: var(--text-lg); background: var(--cloro-soft); color: var(--cloro-ink); }
  .corpo { display: flex; flex-wrap: wrap; gap: var(--space-20); align-items: flex-start; }
  .princ { flex: 7 1 560px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-12); }
  .lato-m { flex: 4 1 300px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-16); }
  .chi-c { display: flex; align-items: center; gap: var(--space-14); padding: var(--space-14) var(--space-18, 18px); flex-wrap: wrap; }
  .chi-t { flex: 1; min-width: 200px; display: flex; flex-direction: column; line-height: 1.25; }
  .nome-g { font-family: var(--font-display); font-size: var(--text-3xl, 26px); font-weight: 800; color: var(--ink); text-decoration: none; }
  .nome-g:hover { text-decoration: underline; }
  .chi-c a.btn { text-decoration: none; display: inline-flex; align-items: center; }
  .avviso-r { display: flex; align-items: center; gap: var(--space-10); margin: 0; padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--warn-bg);
    color: var(--warn); font-weight: 600; font-size: var(--text-md); }
  .vuoto { display: flex; flex-direction: column; align-items: center; text-align: center; gap: var(--space-8); padding: var(--space-32, 32px) var(--space-20); }
  .vuoto > :global(.ic) { width: 36px; height: 36px; color: var(--cloro); }
  .vuoto b { font-size: var(--text-lg); }
  .vuoto .muted { max-width: 440px; }
  .blocco-l { display: flex; flex-direction: column; gap: var(--space-8); }
  .blocco-l h2 { margin: 0; min-height: 28px; display: flex; align-items: center; }
  .lbl.arancio { color: var(--warn); }
  .coda { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-6); }
  .coda button { width: 100%; display: flex; align-items: center; gap: var(--space-10); padding: var(--space-10) var(--space-12); border: 1px solid var(--line); border-radius: var(--radius-md);
    background: var(--surface); font: inherit; text-align: left; cursor: pointer; color: var(--ink); }
  .coda button[aria-pressed='true'] { background: var(--cloro-soft); border-color: var(--cloro); box-shadow: inset 0 0 0 1px var(--cloro); }
  .coda button:disabled { cursor: default; }
  .coda .grow { display: flex; flex-direction: column; min-width: 0; line-height: 1.3; }
  .nessuno { padding: var(--space-6) 0; }
  .blocco-m { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-14) var(--space-16); }
  .testa-m { display: flex; align-items: center; }
  .testa-m h2 { margin: 0; }
  .link-m { font-size: var(--text-sm); font-weight: 600; color: var(--cloro); text-decoration: none; }
  .macchine { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-8); }
  .macchine li { display: flex; align-items: center; gap: var(--space-10); font-size: var(--text-md); font-weight: 600; }
  .macchine li.spenta .grow { opacity: 0.55; }
  .punto { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; background: var(--line-strong); }
  .punto.ok { background: var(--passa); }
  .punto.muta { background: var(--ferma); }
  .sim { padding: 0 var(--space-16); }
  .sim[open] { padding-bottom: var(--space-14); display: flex; flex-direction: column; gap: var(--space-10); }
  .sim summary { display: flex; align-items: center; gap: var(--space-8); min-height: 48px; cursor: pointer; font-weight: 700; list-style: none; }
  .sim summary::-webkit-details-marker { display: none; }
  .sim summary :global(.ic) { color: var(--muted); }
  .sim[open] summary { border-bottom: 1px solid var(--line-soft); margin-bottom: var(--space-4); }
  .riga { display: flex; gap: var(--space-8); align-items: center; flex-wrap: wrap; }
  .post { width: 64px; }
  select.inp { appearance: auto; }
  /* come il display verde della centralina */
  .display { display: flex; flex-direction: column; gap: 2px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: #1f2a1a;
    color: #b8f28a; font-family: var(--font-mono, monospace); min-height: 70px; justify-content: center; border: 3px solid #2f3d27; }
  .display.via { border-color: var(--passa); }
  .display.no { border-color: var(--ferma); color: #ffb4a8; }
  .d1 { font-size: var(--text-sm); opacity: 0.85; }
  .d2 { font-size: var(--text-lg); font-weight: 700; }
  .d3 { font-size: var(--text-sm); }
  .ultimi { list-style: none; margin: 0; padding: 0; font-size: var(--text-sm); }
  .ultimi li { display: flex; gap: var(--space-8); align-items: center; padding: var(--space-6) 0; border-top: 1px solid var(--line-soft); }
  .ultimi .grow { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
