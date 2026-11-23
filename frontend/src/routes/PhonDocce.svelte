<script lang="ts">
  import BarraSalva from '../components/BarraSalva.svelte';
  import Icona from '../components/Icona.svelte';
  import Conferma from '../components/Conferma.svelte';
  import ElencoModificabile from '../components/ElencoModificabile.svelte';
  import ErroreCampo from '../components/ErroreCampo.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import RiquadroAcceso from '../components/RiquadroAcceso.svelte';
  import { api, ErroreApi, messaggioErrore, type Dispositivo, type RegoleBorsellino, type Servizio, type StatoCreditiBraccialetto } from '../lib/api';
  import { creaCoda } from '../lib/coda';
  import { statoMacchina } from '../lib/macchine';
  import { euro, importoNonValido, numeroDa, perCampo } from '../lib/numeri';
  import { crediti as nCrediti } from '../lib/plurali';
  import { contaModifiche } from '../lib/salvataggio';
  import { bozzaDiServizio, bozzaServizioVuota, datiServizio, erroriServizio, restaNelloStorico, riassuntoServizio, servizioValido, usiConRicarica, type BozzaServizio } from '../lib/servizi';
  import { impila } from '../lib/impila';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Macchine e crediti (ADR 0024, 0029, 0030): tutto quello che riguarda il braccialetto come borsellino, in una pagina.
   * Le impostazioni di pagina (dove sta il credito, quanto vale un credito, il credito in euro, il braccialetto) si
   * salvano con la barra; i servizi e le macchine hanno la loro riga e il loro foglio: interruttore, riordino e cestino
   * agiscono subito, il foglio ha il suo «Salva …». Servizi e macchine si eliminano sempre: lo storico tiene una copia.
   */
  let regole = $state<RegoleBorsellino | null>(null);
  let scade = $state(true);
  /** Gli importi come si scrivono nei campi: si convertono al salvataggio. */
  let testi = $state({ valoreCredito: '', ricaricaMinima: '', cauzione: '' });
  let servizi = $state<Servizio[]>([]);
  let dispositivi = $state<Dispositivo[]>([]);
  let originale = $state('');
  let errore = $state('');
  let messaggio = $state('');
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));
  /** Il formato dei crediti sul braccialetto (ADR 0025): lo decide il server, qui si mostra soltanto. */
  let chip = $state<StatoCreditiBraccialetto | null>(null);
  api.statoCreditiBraccialetto().then((c) => (chip = c)).catch(() => {});

  // la barra conta solo le impostazioni di pagina: i servizi e le macchine si salvano da sé
  const istantanea = () => JSON.stringify({ regole, scade, testi });
  async function carica() {
    try {
      const [r, s, d] = await Promise.all([api.regoleBorsellino(), api.servizi(), api.dispositivi()]);
      regole = r;
      testi = { valoreCredito: perCampo(r.valoreCredito), ricaricaMinima: perCampo(r.ricaricaMinima), cauzione: perCampo(r.cauzione) };
      scade = r.validitaEuroMesi != null;
      servizi = s;
      dispositivi = d;
      originale = istantanea();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  const valoreN = $derived(numeroDa(testi.valoreCredito));
  const minimaN = $derived(numeroDa(testi.ricaricaMinima));
  const erroreCredito = $derived(valoreN == null || valoreN <= 0 || importoNonValido(testi.valoreCredito) ? 'Scrivi quanto vale un credito in euro, es. 0,20' : '');
  const erroreMinima = $derived(minimaN == null || importoNonValido(testi.ricaricaMinima) ? 'Scrivi la ricarica minima in euro, es. 5' : '');
  const erroreCauzione = $derived(importoNonValido(testi.cauzione) ? 'Scrivi la cauzione in euro, es. 5 (vuoto = nessuna)' : '');
  const nonValido = $derived(!!(erroreCredito || erroreMinima || erroreCauzione));
  const sporco = $derived(!!regole && istantanea() !== originale);

  async function salva() {
    errore = '';
    messaggio = '';
    try {
      const r = regole!;
      await api.modificaRegoleBorsellino({ ...r, valoreCredito: valoreN!, ricaricaMinima: minimaN!, cauzione: numeroDa(testi.cauzione) ?? 0,
        validitaEuroMesi: scade ? Number(r.validitaEuroMesi ?? 12) : null });
      await carica();
      messaggio = 'Modifiche salvate.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  const ricaricaCrediti = $derived(regole && valoreN != null && valoreN > 0 && minimaN != null ? Math.floor(minimaN / valoreN) : 0);
  const usiRicarica = $derived(usiConRicarica(ricaricaCrediti, servizi));
  const testoUsi = $derived(
    `Con la ricarica minima di ${euro(minimaN ?? 0)}: ${nCrediti(ricaricaCrediti)}${usiRicarica.map((u) => `, ${u.usi} usi di ${u.nome.toLowerCase()}`).join('')}.`,
  );

  // Le chiamate che cambiano servizi e macchine partono una alla volta, nell'ordine dei tocchi.
  const inCoda = creaCoda();

  // ── Servizi ───────────────────────────────────────────────────────────
  const sostituisciServizio = (n: Servizio) => (servizi = servizi.map((x) => (x.id === n.id ? n : x)));

  /** Acceso o spento, dalla riga e dal foglio: subito, con i dati salvati del servizio. Dopo un errore si rilegge l'elenco. */
  async function accendiServizio(s: Servizio, attivo: boolean) {
    errore = '';
    messaggio = '';
    erroreFoglioS = '';
    sostituisciServizio({ ...s, attivo });
    try {
      const nuovo = await inCoda(() => api.modificaServizio(s.id, { ...datiServizio(bozzaDiServizio(s)), attivo }));
      sostituisciServizio(nuovo);
    } catch (e) {
      if (foglioS?.id === s.id) erroreFoglioS = messaggioErrore(e);
      else errore = messaggioErrore(e);
      servizi = await api.servizi().catch(() => servizi);
    }
  }

  /** Il nuovo ordine in una chiamata sola; se fallisce rilancia l'errore e l'elenco torna da sé a com'era. */
  async function riordinaServizi(ids: string[]) {
    errore = '';
    messaggio = '';
    try {
      const nuovi = await inCoda(() => api.ordinaServizi(ids));
      if (Array.isArray(nuovi)) servizi = nuovi;
    } catch (e) {
      errore = messaggioErrore(e);
      throw e;
    }
  }

  /** Elimina sempre (lo storico ne tiene una copia); si ferma solo se il server dice che c'è qualcosa in corso (409): il testo sta sotto la riga. */
  async function eliminaServizio(s: Servizio): Promise<string | undefined> {
    errore = '';
    messaggio = '';
    if (s.eliminabile === false) return s.motivoBlocco || 'Non si può eliminare.';
    try {
      const esito = await inCoda(() => api.eliminaServizio(s.id));
      // i numeri al momento dell'eliminazione; se il server non li manda, quelli dell'elenco
      const sue = esito?.macchine ?? s.macchine ?? dispositivi.filter((d) => d.servizioId === s.id).length;
      const usi = esito?.utilizzi ?? s.utilizzi;
      servizi = servizi.filter((x) => x.id !== s.id);
      messaggio = `«${s.nome}» eliminato${restaNelloStorico(usi)}.${sue ? ` ${sue === 1 ? 'La sua macchina resta' : `Le sue ${sue} macchine restano`} senza servizio.` : ''}`;
      if (sue) dispositivi = await api.dispositivi().catch(() => dispositivi);
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409) return e.message;
      errore = messaggioErrore(e);
    }
  }

  // il foglio di un servizio: «Nuovo servizio» e la modifica di uno esistente
  let foglioS = $state<{ id: string | null } | null>(null);
  let bozzaS = $state<BozzaServizio>(bozzaServizioVuota());
  let nuovoAccesoS = $state(true);
  let erroreFoglioS = $state('');
  let inSalvataggioS = $state(false);
  const apertoS = $derived(foglioS?.id ? servizi.find((s) => s.id === foglioS!.id) ?? null : null);
  const accesoFoglioS = $derived(apertoS ? apertoS.attivo : nuovoAccesoS);
  const erroriS = $derived(erroriServizio(bozzaS));
  const cambiatoS = $derived(!apertoS || JSON.stringify(bozzaS) !== JSON.stringify(bozzaDiServizio(apertoS)));

  // il servizio aperto è sparito dall'elenco (eliminato altrove): si chiude il foglio e lo si dice
  $effect(() => {
    if (foglioS?.id && !apertoS) {
      foglioS = null;
      messaggio = 'Il servizio non c\'è più.';
    }
  });

  function nuovoServizio() {
    bozzaS = bozzaServizioVuota();
    nuovoAccesoS = true;
    erroreFoglioS = '';
    foglioS = { id: null };
  }
  function apriServizio(s: Servizio) {
    bozzaS = bozzaDiServizio(s);
    erroreFoglioS = '';
    foglioS = { id: s.id };
  }
  const chiudiServizio = () => (foglioS = null);
  function accendiServizioDalFoglio(v: boolean) {
    if (apertoS) accendiServizio(apertoS, v);
    else nuovoAccesoS = v;
  }
  async function salvaServizio(e: SubmitEvent) {
    e.preventDefault();
    if (!servizioValido(bozzaS) || inSalvataggioS) return;
    inSalvataggioS = true;
    erroreFoglioS = '';
    errore = '';
    try {
      const dati = datiServizio(bozzaS);
      if (apertoS) {
        sostituisciServizio(await inCoda(() => api.modificaServizio(apertoS.id, { ...dati, attivo: apertoS.attivo })));
        messaggio = `«${dati.nome}» salvato.`;
      } else {
        await inCoda(() => api.creaServizio({ ...dati, attivo: nuovoAccesoS }));
        servizi = await api.servizi();
        messaggio = `Servizio «${dati.nome}» aggiunto.`;
      }
      chiudiServizio();
    } catch (err) {
      erroreFoglioS = messaggioErrore(err);
    } finally {
      inSalvataggioS = false;
    }
  }

  // ── Macchine ──────────────────────────────────────────────────────────
  let chiave = $state<{ nome: string; id: string; chiave: string } | null>(null);
  let foglioM = $state<{ id: string | null } | null>(null);
  let bozzaM = $state<{ nome: string; tipo: 'SERVIZIO' | 'SALDO'; servizioId: string }>({ nome: '', tipo: 'SERVIZIO', servizioId: '' });
  let nuovoAccesoM = $state(true);
  let erroreFoglioM = $state('');
  let inSalvataggioM = $state(false);
  const apertoM = $derived(foglioM?.id ? dispositivi.find((d) => d.id === foglioM!.id) ?? null : null);
  const accesoFoglioM = $derived(apertoM ? apertoM.attivo : nuovoAccesoM);
  const tipoM = $derived(apertoM ? apertoM.tipo : bozzaM.tipo);
  const cambiatoM = $derived(!apertoM || bozzaM.nome.trim() !== apertoM.nome || (apertoM.tipo === 'SERVIZIO' && bozzaM.servizioId !== (apertoM.servizioId ?? '')));
  /** Nella tendina: i servizi accesi e, se è spento, quello che la macchina ha adesso. */
  const serviziScelta = $derived(servizi.filter((s) => s.attivo || s.id === (apertoM?.servizioId ?? null)));
  const senzaServizio = (d: Dispositivo) => d.tipo === 'SERVIZIO' && !d.servizioId;

  const sostituisciMacchina = (n: Dispositivo) => (dispositivi = dispositivi.map((x) => (x.id === n.id ? n : x)));

  async function accendiMacchina(d: Dispositivo, attivo: boolean) {
    errore = '';
    messaggio = '';
    erroreFoglioM = '';
    sostituisciMacchina({ ...d, attivo });
    try {
      sostituisciMacchina(await inCoda(() => api.modificaDispositivo(d.id, { nome: d.nome, attivo })));
    } catch (e) {
      if (foglioM?.id === d.id) erroreFoglioM = messaggioErrore(e);
      else errore = messaggioErrore(e);
      dispositivi = await api.dispositivi().catch(() => dispositivi);
    }
  }

  /** Come i servizi: si elimina sempre, lo storico tiene il nome; il 409 (qualcosa in corso) sta sotto la riga. */
  async function eliminaMacchina(d: Dispositivo): Promise<string | undefined> {
    errore = '';
    messaggio = '';
    if (d.eliminabile === false) return d.motivoBlocco || 'Non si può eliminare.';
    try {
      const esito = await inCoda(() => api.eliminaDispositivo(d.id));
      const usi = esito?.utilizzi ?? d.utilizzi;
      dispositivi = dispositivi.filter((x) => x.id !== d.id);
      messaggio = d.tipo === 'SALDO' ? `«${d.nome}» eliminato.` : `«${d.nome}» eliminata${restaNelloStorico(usi)}.`;
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409) return e.message;
      errore = messaggioErrore(e);
    }
  }

  // la macchina aperta è sparita dall'elenco (eliminata altrove): si chiude il foglio e lo si dice
  $effect(() => {
    if (foglioM?.id && !apertoM) {
      foglioM = null;
      chiave = null;
      messaggio = 'La macchina non c\'è più.';
    }
  });

  function nuovaMacchina() {
    bozzaM = { nome: '', tipo: 'SERVIZIO', servizioId: servizi.find((s) => s.attivo)?.id ?? '' };
    nuovoAccesoM = true;
    erroreFoglioM = '';
    chiave = null;
    foglioM = { id: null };
  }
  function apriMacchina(d: Dispositivo) {
    bozzaM = { nome: d.nome, tipo: d.tipo, servizioId: d.servizioId ?? '' };
    erroreFoglioM = '';
    chiave = null;
    foglioM = { id: d.id };
  }
  function chiudiMacchina() {
    foglioM = null;
    chiave = null;
  }
  function accendiMacchinaDalFoglio(v: boolean) {
    if (apertoM) accendiMacchina(apertoM, v);
    else nuovoAccesoM = v;
  }
  async function salvaMacchina(e: SubmitEvent) {
    e.preventDefault();
    const nome = bozzaM.nome.trim();
    if (!nome || inSalvataggioM) return;
    if (!apertoM && bozzaM.tipo === 'SERVIZIO' && !bozzaM.servizioId) return;
    inSalvataggioM = true;
    erroreFoglioM = '';
    errore = '';
    try {
      if (apertoM) {
        const corpo = { nome, attivo: apertoM.attivo, ...(apertoM.tipo === 'SERVIZIO' && bozzaM.servizioId ? { servizioId: bozzaM.servizioId } : {}) };
        sostituisciMacchina(await inCoda(() => api.modificaDispositivo(apertoM.id, corpo)));
        messaggio = `«${nome}» salvata.`;
        chiudiMacchina();
      } else {
        const c = await inCoda(() => api.creaDispositivo({ nome, tipo: bozzaM.tipo, servizioId: bozzaM.tipo === 'SERVIZIO' ? bozzaM.servizioId : null, attivo: nuovoAccesoM }));
        dispositivi = await api.dispositivi();
        // il foglio resta aperto e diventa quello della macchina appena creata, con la chiave in cima
        chiave = { nome: c.dispositivo.nome, id: c.dispositivo.id, chiave: c.chiave };
        bozzaM = { nome: c.dispositivo.nome, tipo: c.dispositivo.tipo, servizioId: c.dispositivo.servizioId ?? '' };
        foglioM = { id: c.dispositivo.id };
        messaggio = `Macchina «${c.dispositivo.nome}» aggiunta.`;
      }
    } catch (err) {
      erroreFoglioM = messaggioErrore(err);
    } finally {
      inSalvataggioM = false;
    }
  }
  async function rigenera() {
    const d = apertoM;
    if (!d) return;
    erroreFoglioM = '';
    try {
      const c = await api.nuovaChiaveDispositivo(d.id);
      chiave = { nome: d.nome, id: d.id, chiave: c.chiave };
    } catch (e) {
      erroreFoglioM = messaggioErrore(e);
    }
  }

  const maiCollegate = $derived(regole?.creditiSu === 'BRACCIALETTO' ? 0 : dispositivi.filter((d) => d.attivo && !d.ultimoContatto).length);
  const sulServer = $derived(regole?.creditiSu !== 'BRACCIALETTO');
</script>

{#snippet spiega(corta: string, tema: string, lunga: string)}
  <span class="corta"><span class="t-c">{corta}</span><Fumetto {tema}>{lunga}</Fumetto></span>
{/snippet}

{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
{#if messaggio && !sporco}<p class="msg ok" role="status">{messaggio}</p>{/if}

{#if regole}
  <div class="colonne-p">
    <fieldset class="princ" disabled={!puo}>
      <section class="card sez" id="imp-dove" aria-label="Dove sta il credito">
        <div class="sez-t"><h2>Dove sta il credito</h2>
          <Fumetto tema="dove sta il credito">«Nel gestionale»: le macchine sono collegate, chiedono al gestionale e lui scala i crediti; si ricarica dalla scheda, anche senza il braccialetto in mano. «Sul braccialetto»: le macchine non sono collegate e leggono e scalano i crediti nella memoria del braccialetto; si ricarica leggendolo e scrivendolo, e il gestionale conosce l'ultimo saldo visto.</Fumetto></div>
        <div class="dove" role="radiogroup" aria-label="Dove sta il credito">
          <button type="button" class="dove-s" role="radio" aria-checked={regole.creditiSu !== 'BRACCIALETTO'} onclick={() => (regole!.creditiSu = 'SERVER')}>
            <span class="pallino-r" aria-hidden="true"></span><b>Nel gestionale</b>
            <span>Le macchine chiedono al gestionale</span></button>
          <button type="button" class="dove-s" role="radio" aria-checked={regole.creditiSu === 'BRACCIALETTO'} onclick={() => (regole!.creditiSu = 'BRACCIALETTO')}>
            <span class="pallino-r" aria-hidden="true"></span><b>Sul braccialetto</b>
            <span>Le macchine leggono il braccialetto</span></button>
        </div>
        {#if regole.creditiSu === 'BRACCIALETTO' && chip}
          <div class="riga-imp"><span class="t"><b class="lbl-i">Formato del braccialetto<Fumetto tema="il formato del braccialetto">{chip.nota}{#if !chip.pronto}{' '}Intanto si può scrivere a mano il saldo mostrato dal display delle macchine; le ricariche sul braccialetto restano bloccate. Il formato si imposta sul server, non da qui.{/if}</Fumetto></b></span>
            <span class="ctl"><span class="pill {chip.pronto ? (chip.formato === 'PROVA' ? 'warn' : 'passa') : 'ferma'}">{chip.nomeFormato}</span></span></div>
        {/if}
      </section>

      <section class="card sez" id="imp-crediti" aria-label="Crediti e servizi">
        <div class="sez-t"><h2 class="grow">Crediti e servizi</h2></div>
        <div class="riga-imp"><span class="t"><b>Un credito vale</b>{@render spiega('Si comprano al banco.', 'il credito', 'I crediti si comprano al banco o alla cassa automatica.')}</span>
          <span class="ctl"><input class="inp num" type="text" inputmode="decimal" bind:value={testi.valoreCredito} aria-invalid={!!erroreCredito} aria-label="Un credito vale" /><span class="unita">€</span></span>
          {#if erroreCredito}<span class="msg err" role="alert">{erroreCredito}</span>{/if}</div>
        <div class="riga-imp"><span class="t"><b>Ricarica minima</b>{#if ricaricaCrediti}{@render spiega(nCrediti(ricaricaCrediti), 'la ricarica minima', testoUsi)}{/if}</span>
          <span class="ctl"><input class="inp num" type="text" inputmode="decimal" bind:value={testi.ricaricaMinima} aria-invalid={!!erroreMinima} aria-label="Ricarica minima" /><span class="unita">€</span></span>
          {#if erroreMinima}<span class="msg err" role="alert">{erroreMinima}</span>{/if}</div>
        <div class="elenco" id="imp-servizi">
          <ElencoModificabile
            elementi={servizi}
            modificabile={puo}
            nome={(s) => s.nome}
            nomeElemento="servizio"
            riga={contenutoServizio}
            sotto={sottoServizio}
            acceso={(s) => s.attivo}
            onriordina={riordinaServizi}
            onaggiungi={nuovoServizio}
            onapri={apriServizio}
            onacceso={accendiServizio}
            onelimina={eliminaServizio}
          />
        </div>
      </section>

      <section class="card sez" id="imp-euro" aria-label="Credito in euro">
        <div class="sez-t"><h2 class="grow">Credito in euro</h2><Fumetto tema="il credito in euro">Rimborsi di recesso e sospensione, cauzioni restituite.</Fumetto></div>
        <div class="riga-imp"><span class="t"><b>Scade</b>{@render spiega('Dopo quanto il credito si perde.', 'la scadenza', 'Dopo quanto il credito non usato si perde. Si usa per pagare con il metodo «Borsellino».')}</span>
          <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={scade} aria-label="Il credito scade" onclick={() => (scade = !scade)}><span></span></button>
            <input class="inp num" type="number" min="1" max="120" bind:value={regole.validitaEuroMesi} disabled={!scade} aria-label="Mesi" /><span class="unita">mesi</span></span></div>
        <div class="riga-imp"><span class="t"><b>Si può trasferire a un familiare</b><span>Genitori, figli, fratelli.</span></span>
          <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={regole.trasferibile} aria-label="Trasferibile" onclick={() => (regole!.trasferibile = !regole!.trasferibile)}><span></span></button></span></div>
        <div class="riga-imp"><span class="t"><b>Si può restituire in denaro</b>{@render spiega('Serve un permesso in più.', 'la restituzione', 'Serve anche il permesso «Rimborsi e correzioni del borsellino».')}</span>
          <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={regole.restituibile} aria-label="Restituibile in denaro" onclick={() => (regole!.restituibile = !regole!.restituibile)}><span></span></button></span></div>
      </section>

      <section class="card sez" id="imp-braccialetto" aria-label="Braccialetto">
        <div class="sez-t"><h2 class="grow">Braccialetto</h2></div>
        <div class="riga-imp"><span class="t"><b>Cauzione alla consegna</b><span>0 = nessuna cauzione.</span></span>
          <span class="ctl"><input class="inp num" type="text" inputmode="decimal" bind:value={testi.cauzione} aria-invalid={!!erroreCauzione} aria-label="Cauzione" /><span class="unita">€</span></span>
          {#if erroreCauzione}<span class="msg err" role="alert">{erroreCauzione}</span>{/if}</div>
        <div class="riga-imp"><span class="t"><b>Alla riconsegna</b>{@render spiega('Cauzione e crediti tornano.', 'la riconsegna', 'Alla riconsegna, la cauzione e i crediti tornano alla persona nel modo scelto qui.')}</span>
          <span class="ctl"><span class="seg" style:--n={3} use:impila role="group" aria-label="Alla riconsegna">
            {#each [['DENARO', 'In denaro'], ['BORSELLINO', 'Nel borsellino'], ['A_SCELTA', 'A scelta']] as [v, t] (v)}<button type="button" aria-pressed={regole.cauzioneRiconsegna === v} onclick={() => (regole!.cauzioneRiconsegna = v as RegoleBorsellino['cauzioneRiconsegna'])}>{t}</button>{/each}
          </span></span></div>
        <div class="riga-imp"><span class="t"><b>Riconsegnando l'ultimo braccialetto</b>{@render spiega('Si propone il rimborso dei crediti.', 'il rimborso dei crediti', 'Riconsegnando l\'ultimo braccialetto si propone di rimborsare i crediti di phon e docce.')}</span>
          <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={regole.creditiRiconsegna} aria-label="Rimborso dei crediti alla riconsegna" onclick={() => (regole!.creditiRiconsegna = !regole!.creditiRiconsegna)}><span></span></button></span></div>
        <div class="riga-imp"><span class="t"><b>Se si smarrisce</b>{@render spiega('Cosa succede ai crediti.', 'lo smarrimento', 'Se il braccialetto si smarrisce, i crediti restano alla persona o si perdono. Il braccialetto nuovo si paga come una consegna.')}</span>
          <span class="ctl"><span class="seg" style:--n={2} use:impila role="group" aria-label="Se si smarrisce">
            {#each [['RESTANO', 'Restano alla persona'], ['PERSI', 'Si perdono']] as [v, t] (v)}<button type="button" aria-pressed={regole.creditiSmarrimento === v} onclick={() => (regole!.creditiSmarrimento = v as RegoleBorsellino['creditiSmarrimento'])}>{t}</button>{/each}
          </span></span></div>
      </section>
    </fieldset>

    <aside class="lato-p largo-p">
      <section class="card sez" id="imp-macchine" aria-label="Macchine">
        <div class="sez-t"><h2>Macchine</h2>
          <Fumetto tema="le macchine">Ogni macchina ha la sua chiave, che si mostra una volta sola: copiala nella macchina (le istruzioni per il tecnico sono nel manuale). Una chiave nuova spegne subito quella vecchia.{#if !sulServer}{' '}Con i crediti sul braccialetto le macchine non chiamano il gestionale: chiave e ultimo contatto servono solo se un giorno si collegano.{/if}</Fumetto></div>
        {#if maiCollegate}
          <div class="avvisa"><Icona nome="avviso" /><span class="corta"><span class="t-c">{maiCollegate === dispositivi.filter((d) => d.attivo).length ? 'Nessuna si è ancora collegata' : `${maiCollegate} non si sono ancora collegate`}</span>
            <Fumetto tema="le macchine non collegate">Ognuna va configurata con la sua chiave.</Fumetto></span></div>
        {/if}
        <div class="elenco">
          <ElencoModificabile
            elementi={dispositivi}
            modificabile={puo}
            riordinabile={false}
            nome={(d) => d.nome}
            nomeElemento="macchina"
            articolo="una"
            riga={contenutoMacchina}
            sotto={sottoMacchina}
            acceso={(d) => d.attivo}
            onaggiungi={nuovaMacchina}
            onapri={apriMacchina}
            onacceso={accendiMacchina}
            onelimina={eliminaMacchina}
          />
        </div>
      </section>
    </aside>
  </div>

  <BarraSalva modifiche={sporco && originale ? contaModifiche(JSON.parse(originale), JSON.parse(istantanea())) : 0} valida={!nonValido} onannulla={carica} onsalva={salva} />
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

{#snippet contenutoServizio(s: Servizio)}
  <strong class="nome" class:tenue={!s.attivo}>{s.nome}</strong>
{/snippet}
{#snippet sottoServizio(s: Servizio)}
  <!-- anche il riassunto apre il foglio (il pulsante vero è il nome, questo è un'area di tocco in più) -->
  <button type="button" class="sub apri-sub" tabindex="-1" aria-hidden="true" onclick={() => apriServizio(s)}>{riassuntoServizio(s)}</button>
{/snippet}

{#snippet contenutoMacchina(d: Dispositivo)}
  <strong class="nome" class:tenue={!d.attivo}>{d.nome}</strong>
{/snippet}
{#snippet sottoMacchina(d: Dispositivo)}
  <button type="button" class="sub apri-sub" tabindex="-1" aria-hidden="true" onclick={() => apriMacchina(d)}>
    {#if senzaServizio(d)}<span class="tag t-warn">Senza servizio</span>
    {:else}<span class="sv">{d.tipo === 'SALDO' ? 'mostra il saldo' : (d.servizio ?? '').toLowerCase()}</span>{/if}
    {#if sulServer}<span class="sv">· {statoMacchina(d, Date.now()).testo}</span>{/if}
  </button>
{/snippet}

{#snippet pillolaSpentoS()}
  {#if !accesoFoglioS}<span class="pill">spento</span>{/if}
{/snippet}
{#snippet pillolaSpentaM()}
  {#if !accesoFoglioM}<span class="pill">spenta</span>{/if}
{/snippet}

{#if foglioS && puo}
  <Foglio titolo={apertoS ? apertoS.nome : 'Nuovo servizio'} chiudi={chiudiServizio} troncaTitolo dopoTitolo={pillolaSpentoS}>
    <form class="modulo" onsubmit={salvaServizio}>
      <RiquadroAcceso acceso={accesoFoglioS} onacceso={accendiServizioDalFoglio} />
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={bozzaS.nome} maxlength="80" autocomplete="off" placeholder="Es. Phon" /></label>
      <label class="field"><span class="lbl">Crediti per ogni uso</span>
        <input class="inp m corto" type="text" inputmode="numeric" bind:value={bozzaS.crediti} aria-invalid={!!erroriS.crediti} aria-describedby={erroriS.crediti ? 'err-s-crediti' : undefined} />
        {#if erroriS.crediti}<ErroreCampo id="err-s-crediti">{erroriS.crediti}</ErroreCampo>{/if}</label>
      <label class="field"><span class="lbl">Usi gratuiti al giorno</span>
        <input class="inp m corto" type="text" inputmode="numeric" bind:value={bozzaS.gratuiti} aria-invalid={!!erroriS.gratuiti} aria-describedby={erroriS.gratuiti ? 'err-s-gratuiti' : undefined} />
        {#if erroriS.gratuiti}<ErroreCampo id="err-s-gratuiti">{erroriS.gratuiti}</ErroreCampo>{/if}</label>
      <div class="field"><span class="lbl lbl-i"><span id="lbl-s-minuti">Minuti sulla macchina</span>
        <Fumetto tema="i minuti">Quanti minuti dura un uso sulla macchina. Se lo lasci vuoto, i minuti non si mostrano.</Fumetto></span>
        <input class="inp m corto" type="text" inputmode="numeric" bind:value={bozzaS.minuti} placeholder="—" aria-labelledby="lbl-s-minuti" aria-invalid={!!erroriS.minuti} aria-describedby={erroriS.minuti ? 'err-s-minuti' : undefined} />
        {#if erroriS.minuti}<ErroreCampo id="err-s-minuti">{erroriS.minuti}</ErroreCampo>{/if}</div>

      {#if erroreFoglioS}<p class="msg err nomargin" role="alert">{erroreFoglioS}</p>{/if}

      <div class="azioni-foglio">
        <button type="button" class="btn btn-secondary" onclick={chiudiServizio}>Annulla</button>
        <button type="submit" class="btn btn-accent" disabled={!servizioValido(bozzaS) || !cambiatoS || inSalvataggioS}>
          {#if apertoS}<Icona nome="salva" />Salva il servizio{:else}<Icona nome="piu" />Aggiungi il servizio{/if}
        </button>
      </div>
    </form>
  </Foglio>
{/if}

{#if foglioM && puo}
  <Foglio titolo={apertoM ? apertoM.nome : 'Nuova macchina'} chiudi={chiudiMacchina} troncaTitolo dopoTitolo={pillolaSpentaM}>
    <form class="modulo" onsubmit={salvaMacchina}>
      {#if chiave}
        <div class="chiave" role="status">
          <strong>Chiave di «{chiave.nome}»</strong>
          <code class="m">{chiave.chiave}</code>
          <span class="small">Id: <code class="m">{chiave.id}</code></span>
          <span class="corta"><span class="t-c small">Copiala adesso nella macchina</span>
            <Fumetto tema="la chiave">Non si potrà più rivedere: se si perde, se ne crea una nuova.</Fumetto></span>
          <button type="button" class="btn btn-sm btn-secondary sx" onclick={() => (chiave = null)}><Icona nome="check" />L'ho copiata</button>
        </div>
      {/if}
      <RiquadroAcceso acceso={accesoFoglioM} onacceso={accendiMacchinaDalFoglio} />
      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={bozzaM.nome} maxlength="80" autocomplete="off" placeholder="Es. Phon spogliatoio maschile" /></label>
      {#if !apertoM}
        <div class="field"><span class="lbl" id="tipo-macchina">Tipo</span>
          <span class="seg" style:--n={2} use:impila role="group" aria-labelledby="tipo-macchina">
            <button type="button" aria-pressed={bozzaM.tipo === 'SERVIZIO'} onclick={() => (bozzaM.tipo = 'SERVIZIO')}>Macchina</button>
            <button type="button" aria-pressed={bozzaM.tipo === 'SALDO'} onclick={() => (bozzaM.tipo = 'SALDO')}>Lettore del saldo</button>
          </span>
        </div>
      {:else}
        <div class="field"><span class="lbl">Tipo</span><span class="valore">{apertoM.tipo === 'SALDO' ? 'Lettore del saldo' : 'Macchina'}</span></div>
      {/if}
      {#if tipoM === 'SERVIZIO'}
        <label class="field"><span class="lbl">Servizio</span>
          <select class="inp" bind:value={bozzaM.servizioId}>
            {#if !bozzaM.servizioId}<option value="" disabled={!apertoM}>Scegli un servizio</option>{/if}
            {#each serviziScelta as s (s.id)}<option value={s.id}>{s.nome}</option>{/each}
          </select></label>
      {/if}
      {#if apertoM}
        <div class="nuova-chiave">
          <Conferma class="btn btn-sm btn-secondary" tono="cloro" domanda="Nuova chiave?" onclick={rigenera}><Icona nome="chiave" />Nuova chiave</Conferma>
          <Fumetto tema="la chiave nuova">La vecchia smette subito di funzionare: la macchina va riconfigurata.</Fumetto>
        </div>
      {/if}

      {#if erroreFoglioM}<p class="msg err nomargin" role="alert">{erroreFoglioM}</p>{/if}

      <div class="azioni-foglio">
        <button type="button" class="btn btn-secondary" onclick={chiudiMacchina}>Annulla</button>
        <button type="submit" class="btn btn-accent"
          disabled={!bozzaM.nome.trim() || (!apertoM && bozzaM.tipo === 'SERVIZIO' && !bozzaM.servizioId) || !cambiatoM || inSalvataggioM}>
          {#if apertoM}<Icona nome="salva" />Salva la macchina{:else}<Icona nome="piu" />Aggiungi la macchina{/if}
        </button>
      </div>
    </form>
  </Foglio>
{/if}

<style>
  /* dove sta il credito: due scelte affiancate, con una riga corta (il resto sta nel fumetto del titolo) */
  .dove { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  .dove-s { display: grid; grid-template-columns: auto 1fr; gap: var(--space-4) var(--space-10); align-items: center; min-height: 44px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md);
    border: 1.5px solid var(--line); background: var(--surface); font: inherit; color: var(--ink); text-align: left; cursor: pointer; min-width: 0; }
  .dove-s > b, .dove-s > span:last-child { white-space: nowrap; }
  .dove-s > span:last-child { grid-column: 2; font-size: var(--text-sm); color: var(--muted); overflow: hidden; text-overflow: ellipsis; }
  .dove-s[aria-checked='true'] { border-color: var(--cloro); box-shadow: 0 0 0 3px color-mix(in srgb, var(--cloro) 25%, transparent); }
  .pallino-r { width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--line-strong); }
  .dove-s[aria-checked='true'] .pallino-r { border: 5px solid var(--cloro); }
  @media (max-width: 360px) { .dove { padding-inline: var(--space-10); } .dove-s { padding-inline: var(--space-10); } }
  /* i segmenti: stessa larghezza per tutte le voci, alti 44; se non ci stanno in fila vanno uno sotto l'altro */
  .seg { grid-template-columns: repeat(var(--n), var(--col, 1fr)); }
  .seg button { min-height: 44px; }
  .seg:global([data-impilato]) { grid-template-columns: 1fr; }
  /* quando i controlli vanno a capo sotto il testo, 20 px tra le righe: le aree di tocco non si sovrappongono */
  .princ :global(.riga-imp) { row-gap: var(--space-20); }
  fieldset { border: none; margin: 0; padding: 0; min-width: 0; }
  .largo-p { min-width: 0; flex-basis: min(380px, 100%) !important; max-width: 440px !important; }
  .grow { flex: 1 1 auto; min-width: 0; }
  .elenco { padding: var(--space-6) var(--space-16) var(--space-10); border-top: 1px solid var(--line-soft); }
  /* telefoni stretti: meno margine ai lati perché i nomi stiano interi */
  @media (max-width: 360px) { .elenco { padding-inline: var(--space-8); } }
  /* la riga corta: non va a capo, la «i» sta in fondo e non allarga la riga; a 320 px il testo si accorcia con i puntini */
  .corta { display: flex; align-items: center; gap: var(--space-4); white-space: nowrap; min-width: 0; }
  .t-c { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  .lbl-i { display: flex; align-items: center; gap: var(--space-4); white-space: nowrap; }
  .nome { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .nome.tenue { opacity: 0.7; }
  /* sul telefono stretto un nome lungo può usare due righe; i puntini restano l'ultima difesa (come i gruppi in Società) */
  @media (max-width: 479px) {
    .nome { white-space: normal; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow-wrap: anywhere; }
    .avvisa .corta { white-space: normal; }
    .avvisa .t-c { white-space: normal; overflow: visible; }
  }
  .sub { display: flex; align-items: center; gap: var(--space-6); min-width: 0; font-size: var(--text-xs); color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .apri-sub { position: relative; display: flex; width: 100%; background: none; border: none; padding: 0; font: inherit; font-size: var(--text-xs); text-align: left; cursor: pointer; }
  .apri-sub::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .elenco :global(.contenuto.apri) { min-height: 44px; }
  .sub .sv { overflow: hidden; text-overflow: ellipsis; min-width: 0; }
  .avvisa { display: flex; align-items: center; gap: var(--space-8); margin: var(--space-4) var(--space-16) var(--space-4); padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm);
    background: var(--warn-bg); color: var(--warn); font-size: var(--text-sm); font-weight: 600; min-width: 0; }
  .avvisa :global(.ic) { width: 16px; height: 16px; flex-shrink: 0; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-14); container-type: inline-size; }
  .modulo .inp { height: 44px; }
  .corto { width: 120px; }
  select.inp { appearance: auto; }
  .valore { min-height: 44px; display: flex; align-items: center; font-weight: 600; }
  .chiave { display: flex; flex-direction: column; gap: var(--space-6); padding: var(--space-12); border-radius: var(--radius-md); background: var(--cloro-soft); color: var(--cloro-ink); min-width: 0; }
  .chiave code { font-size: var(--text-lg); word-break: break-all; user-select: all; }
  .sx { align-self: flex-start; }
  .nuova-chiave { display: flex; align-items: center; gap: var(--space-6); flex-wrap: wrap; }
  .azioni-foglio :global(.btn) { white-space: nowrap; }
  /* foglio stretto: i due pulsanti uno sotto l'altro a tutta larghezza, il principale sopra */
  @container (max-width: 330px) { .azioni-foglio { flex-direction: column-reverse; } .azioni-foglio :global(.btn) { width: 100%; justify-content: center; } }
  .azioni-foglio { display: flex; justify-content: flex-end; gap: var(--space-10); flex-wrap: wrap; }
</style>
