<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import { onDestroy, onMount, tick } from 'svelte';
  import { api, messaggioErrore, type EsitoTornello, type GiornataBanco, type Passaggio, type Verso } from '../lib/api';
  import { esitoDi, VISTE_PASSAGGI, VISTE_SISTEMARE, type VistaPassaggi, type VistaSistemare } from '../lib/banco';
  import { oraDi } from '../lib/date';
  import { codiceValido, leggiConNfc, motivoNfcAssente, nfcDisponibile, normalizzaCodice } from '../lib/nfc';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { schermo } from '../lib/schermo.svelte';
  import { sessione } from '../lib/sessione.svelte';
  import EsitoTornelloView from '../components/EsitoTornello.svelte';
  import LettoreTelefonoPc from '../components/LettoreTelefonoPc.svelte';
  import LineaGiornata from '../components/banco/LineaGiornata.svelte';
  import PassaggiOggi from '../components/banco/PassaggiOggi.svelte';
  import DaSistemare from '../components/banco/DaSistemare.svelte';
  import RiepilogoCliente from '../components/banco/RiepilogoCliente.svelte';
  import { lettoreTelefono } from '../lib/lettore.svelte';

  /**
   * Il banco del tornello. Sul telefono Android si leggono i braccialetti con l'NFC; sul PC un lettore da scrivania
   * scrive il codice nel campo come una tastiera (seguito da Invio). Ogni lettura è un passaggio registrato.
   *
   * In alto la linea della giornata (si può nascondere); a sinistra l'esito dell'ultimo letto (o del passaggio
   * riaperto), il riepilogo del cliente e chi è da sistemare; a destra i numeri della giornata e i passaggi di oggi.
   * Come mostrare i passaggi e i «da sistemare» lo sceglie ognuno (preferenze dell'account, su questo dispositivo).
   */
  let codice = $state('');
  let esito = $state<EsitoTornello | null>(null);
  let scelto = $state<string | null>(null);
  let errore = $state('');
  let inCorso = $state(false);
  let giornata = $state<GiornataBanco | null>(null);
  let quanti = $state(60);
  let nfcAttivo = $state(false);
  let campo = $state<HTMLInputElement | null>(null);
  let fermaNfc: AbortController | null = null;
  let adesso = $state(new Date());

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
    scelto = null;
    try {
      localStorage.setItem('tornello.lato', v);
    } catch {
      /* resta per questa sessione */
    }
  }

  // ── le viste a scelta ──
  let vistaPassaggi = $state<VistaPassaggi>(leggiPreferenza<VistaPassaggi>('tornello.passaggi', 'tessere', VISTE_PASSAGGI.map((v) => v.id)));
  let vistaSistemare = $state<VistaSistemare>(leggiPreferenza<VistaSistemare>('tornello.sistemare', 'schede', VISTE_SISTEMARE.map((v) => v.id)));
  let lineaAperta = $state(leggiPreferenza<'aperta' | 'chiusa'>('tornello.linea', 'aperta', ['aperta', 'chiusa']) === 'aperta');
  let scegliViste = $state(false);
  $effect(() => salvaPreferenza('tornello.passaggi', vistaPassaggi));
  $effect(() => salvaPreferenza('tornello.sistemare', vistaSistemare));
  $effect(() => salvaPreferenza('tornello.linea', lineaAperta ? 'aperta' : 'chiusa'));

  const nfc = nfcDisponibile();
  const vedeSchede = $derived(sessione.puo('CLIENTI', 'BRACCIALETTI'));

  async function caricaGiornata() {
    try {
      giornata = await api.giornataBanco(quanti);
      adesso = new Date();
    } catch {
      /* la giornata è un di più: il tornello funziona anche senza */
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
      scelto = esito.passaggioId;
      if (navigator.vibrate) navigator.vibrate(esito.verde ? 60 : [80, 60, 80]);
      caricaGiornata();
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a controllare il braccialetto.');
    } finally {
      inCorso = false;
      codice = '';
      await tick();
      if (schermo.larga) campo?.focus();
    }
  }

  /** Riapre l'esito di un passaggio della giornata (dai passaggi, dalla linea, dai «da sistemare»). */
  function apri(p: Passaggio) {
    esito = esitoDi(p);
    scelto = p.id;
  }
  async function apriId(id: string) {
    const trova = () => giornata?.passaggi.find((p) => p.id === id);
    let p = trova();
    if (!p && giornata) {
      const d = giornata.daSistemare.find((x) => x.passaggioId === id);
      if (d) {
        esito = { passaggioId: id, istante: d.istante, codice: d.codice, verso: 'ENTRATA', verde: false, persona: d.persona, statoBraccialetto: null, goldenPass: false, motivi: d.motivi, carnet: null };
        scelto = id;
        return;
      }
      quanti = 200; // più indietro nella giornata
      await caricaGiornata();
      p = trova();
    }
    if (p) apri(p);
  }

  // I braccialetti letti dal telefono abbinato arrivano qui come da un lettore da scrivania
  onMount(() => lettoreTelefono.ascolta((c) => leggi(c)));

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
    caricaGiornata();
    if (schermo.larga) campo?.focus();
    // la giornata si aggiorna da sola: altre postazioni leggono braccialetti, la segreteria sistema
    const t = setInterval(caricaGiornata, 30_000);
    return () => clearInterval(t);
  });
  onDestroy(fermaLettura);

  const href = (personaId: string | undefined) => (vedeSchede && personaId ? `#/clienti/${personaId}` : null);
  const conti = $derived(giornata?.conteggi);
</script>

<div class="banco" class:largo={schermo.larga}>
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
    <div class="campo-codice">
      <Icona nome="braccialetto" />
      <input class="inp m grow" bind:this={campo} bind:value={codice} autocomplete="off" autocapitalize="characters" spellcheck="false"
             placeholder={schermo.larga ? 'Passa il braccialetto sul lettore o scrivi il codice' : 'Codice del braccialetto'} aria-label="Codice del braccialetto" />
      <button class="btn btn-primary" disabled={inCorso || !codice.trim()}>Controlla</button>
    </div>
    <span class="impost"><Azione icona="impostazioni" etichetta="Scegli come mostrare il banco" premuto={scegliViste} onclick={() => (scegliViste = !scegliViste)} /></span>
  </form>
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  {#if !nfc}<LettoreTelefonoPc />{/if}

  {#if scegliViste}
    <section class="card viste" aria-label="Come mostrare il banco">
      <div class="vista-riga">
        <span class="lbl">Passaggi di oggi</span>
        <div class="chips">{#each VISTE_PASSAGGI as v (v.id)}<button type="button" class="chip" aria-pressed={vistaPassaggi === v.id} onclick={() => (vistaPassaggi = v.id)}>{v.nome}</button>{/each}</div>
      </div>
      <div class="vista-riga">
        <span class="lbl">Da sistemare</span>
        <div class="chips">{#each VISTE_SISTEMARE as v (v.id)}<button type="button" class="chip" aria-pressed={vistaSistemare === v.id} onclick={() => (vistaSistemare = v.id)}>{v.nome}</button>{/each}</div>
      </div>
      <p class="muted small nomargin">La scelta resta per il tuo account su questo dispositivo.</p>
    </section>
  {/if}

  {#if giornata && schermo.larga}
    <section class="blocco">
      <div class="blocco-testa">
        <h2 class="lbl titolo-linea">La giornata · {conti?.entrati} entrati · {conti?.fermati} rossi
          <Azione icona={lineaAperta ? 'occhio' : 'occhio-no'} etichetta={lineaAperta ? 'Nascondi la linea della giornata' : 'Mostra la linea della giornata'}
                  onclick={() => (lineaAperta = !lineaAperta)} /></h2>
        {#if lineaAperta && conti?.fermati}<span class="muted small">un clic su un segno rosso ne riapre l'esito</span>{/if}
      </div>
      {#if lineaAperta}<LineaGiornata linea={giornata.linea} {adesso} onapri={apriId} />{/if}
    </section>
  {/if}

  <div class="colonne">
    <div class="sx">
      {#if esito}
        <EsitoTornelloView {esito} schedaHref={href(esito.persona?.id)} />
        {#if esito.persona && vedeSchede}<RiepilogoCliente personaId={esito.persona.id} turni={giornata?.turni[esito.persona.id] ?? []} />{/if}
        <p class="muted small nomargin">{esito.passaggioId === scelto && scelto ? `Letto alle ${oraDi(esito.istante)}.` : ''} Resta l'ultimo letto finché non ne passa un altro; un clic su un passaggio ne riapre l'esito.</p>
      {:else}
        <div class="attesa card">
          <Icona nome="telefono" />
          <span>In attesa di un braccialetto</span>
        </div>
      {/if}

      {#if giornata}
        <section class="blocco">
          <div class="blocco-testa"><h2 class="lbl">Da sistemare · {giornata.daSistemare.length ? `${giornata.daSistemare.length} rimasti fuori` : 'nessuno'}</h2></div>
          <DaSistemare fuori={giornata.daSistemare} sistemati={giornata.sistemati} vista={vistaSistemare} {scelto} {adesso} onapri={apriId} schede={vedeSchede} />
        </section>
      {/if}
    </div>

    <aside class="dx">
      {#if conti}
        <div class="kpi-oggi">
          <div class="k"><span class="lbl">Entrati oggi</span><span class="v">{conti.entrati}</span><span class="n">{conti.conCarnet ? `di cui ${conti.conCarnet} con il carnet` : 'persone diverse'}</span></div>
          <div class="k"><span class="lbl">Dentro ora</span><span class="v">{conti.dentro ?? '–'}</span><span class="n">{conti.dentro == null ? 'nessuna uscita registrata' : 'entrati e non usciti'}</span></div>
          <div class="k" class:warn={giornata!.daSistemare.length > 0}><span class="lbl">Da sistemare</span><span class="v">{giornata!.daSistemare.length}</span><span class="n">rimasti fuori oggi</span></div>
        </div>
      {/if}
      <section class="blocco">
        <div class="blocco-testa">
          <h2 class="lbl">Passaggi di oggi · i più recenti per primi</h2>
          <Azione icona="aggiorna" etichetta="Aggiorna i passaggi" onclick={caricaGiornata} />
        </div>
        {#if giornata}
          <PassaggiOggi {giornata} vista={vistaPassaggi} {scelto} {adesso} onapri={apri}
                        altri={giornata.passaggi.length >= quanti && quanti < 200 ? () => { quanti = 200; caricaGiornata(); } : null} />
        {:else}
          <p class="muted small">Carico i passaggi…</p>
        {/if}
      </section>
    </aside>
  </div>
</div>

<style>
  .banco { display: flex; flex-direction: column; gap: var(--space-16); }
  .lettore { display: flex; flex-direction: column; gap: var(--space-12); padding: var(--space-14) var(--space-16); }
  .largo .lettore { flex-direction: row; align-items: center; flex-wrap: wrap; }
  .lati { grid-template-columns: 1fr 1fr; min-width: 200px; }
  .banco:not(.largo) .impost { position: absolute; top: var(--space-10); right: var(--space-10); }
  .banco:not(.largo) .lettore { position: relative; }
  .banco:not(.largo) .lati { margin-right: 48px; }
  .campo-codice { display: flex; align-items: center; gap: var(--space-10); min-width: 0; }
  .largo .campo-codice { flex: 1 1 320px; }
  .campo-codice .inp { min-width: 0; }
  .campo-codice > :global(.ic) { color: var(--muted); flex-shrink: 0; }
  .campo-codice .inp { font-size: var(--text-lg); letter-spacing: 0.04em; }
  .grande { min-height: 56px; font-size: var(--text-lg); }
  .nfc { display: flex; align-items: center; gap: var(--space-10); font-weight: 600; color: var(--cloro-ink); background: var(--cloro-soft);
    padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); }
  .onda { width: 14px; height: 14px; border-radius: 50%; background: var(--cloro); animation: pulsa 1.4s ease-out infinite; }
  @keyframes pulsa { 0% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--cloro) 60%, transparent); } 100% { box-shadow: 0 0 0 14px transparent; } }
  @media (prefers-reduced-motion: reduce) { .onda { animation: none; } }

  .viste { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  .vista-riga { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; }
  .vista-riga .lbl { min-width: 130px; }
  .chips { display: flex; gap: var(--space-6); flex-wrap: wrap; }

  .blocco { display: flex; flex-direction: column; gap: var(--space-8); }
  .blocco-testa { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); min-height: 36px; }
  .blocco-testa h2 { margin: 0; }
  .titolo-linea { display: flex; align-items: center; gap: var(--space-6); }

  .colonne { display: flex; flex-direction: column; gap: var(--space-20); }
  .largo .colonne { display: grid; grid-template-columns: minmax(0, 7fr) minmax(340px, 5fr); align-items: start; gap: var(--space-20); }
  .sx, .dx { display: flex; flex-direction: column; gap: var(--space-14); min-width: 0; }
  .attesa { display: flex; align-items: center; justify-content: center; gap: var(--space-10); min-height: 160px; color: var(--muted);
    border-style: dashed; border-color: var(--line-dashed); background: transparent; }
  .attesa :global(.ic) { width: 26px; height: 26px; }

  .kpi-oggi { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-8); }
  .k { display: flex; flex-direction: column; gap: 2px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line); min-width: 0; }
  .k.warn { background: var(--warn-bg); border-color: transparent; }
  .k .v { font-family: var(--font-display); font-size: 28px; font-weight: 800; line-height: 1.1; }
  .k.warn .v { color: var(--warn); }
  .k .n { font-size: var(--text-xs); color: var(--muted); }
</style>
