<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from './lib/api';
  import { sessione } from './lib/sessione.svelte';
  import { schermo } from './lib/schermo.svelte';
  import {
    cambiaServer, cercaAggiornamento, infoProgramma, installaAggiornamento, type AggiornamentoProgramma, type InfoProgramma,
  } from './lib/dispositivi';
  import LettoreTelefono from './routes/LettoreTelefono.svelte';
  import Listino from './routes/Listino.svelte';
  import Login from './routes/Login.svelte';
  import Turni from './routes/Turni.svelte';
  import Richieste from './routes/Richieste.svelte';
  import Ore from './routes/Ore.svelte';
  import Pianifica from './routes/Pianifica.svelte';
  import Approva from './routes/Approva.svelte';
  import Modelli from './routes/Modelli.svelte';
  import Postazioni from './routes/Postazioni.svelte';
  import PianoVasca from './routes/PianoVasca.svelte';
  import Corsi from './routes/Corsi.svelte';
  import Tornello from './routes/Tornello.svelte';
  import Clienti from './routes/Clienti.svelte';
  import Regole from './routes/Regole.svelte';
  import Segnalazioni from './components/Segnalazioni.svelte';
  import Foglio from './components/Foglio.svelte';
  import CambioPassword from './routes/CambioPassword.svelte';
  import Accesso from './components/Accesso.svelte';
  import Marchio from './components/Marchio.svelte';
  import ListaAttesa from './routes/ListaAttesa.svelte';
  import Scadenze from './routes/Scadenze.svelte';
  import Societa from './routes/Societa.svelte';
  import Calendario from './routes/Calendario.svelte';
  import Promemoria from './routes/Promemoria.svelte';
  import AccountRuoli from './routes/AccountRuoli.svelte';
  import Incassi from './routes/Incassi.svelte';
  import MetodiPagamento from './routes/MetodiPagamento.svelte';
  import ImpostazioniRicevute from './routes/ImpostazioniRicevute.svelte';
  import ImpostazioniCassa from './routes/ImpostazioniCassa.svelte';
  import Macchine from './routes/Macchine.svelte';
  import PhonDocce from './routes/PhonDocce.svelte';
  import RegolePrezzo from './routes/RegolePrezzo.svelte';
  import Aspetto from './routes/Aspetto.svelte';
  import CercaImpostazioni from './components/impostazioni/CercaImpostazioni.svelte';
  import PanoramicaImpostazioni from './components/impostazioni/PanoramicaImpostazioni.svelte';
  import type { Permesso } from './lib/api';
  import type { NomeIcona } from './lib/icone';
  import Icona from './components/Icona.svelte';
  import Azione from './components/Azione.svelte';

  type Scheda = 'turni' | 'richieste' | 'ore' | 'piano' | 'corsi' | 'tornello' | 'clienti' | 'pianifica' | 'approva' | 'modelli' | 'postazioni' | 'tariffe' | 'regole' | 'ruoli' | 'scadenze' | 'promemoria' | 'account' | 'attesa' | 'societa' | 'calendario' | 'listino' | 'metodi' | 'ricevute' | 'cassa' | 'incassi' | 'macchine' | 'servizi' | 'prezzi' | 'aspetto' | 'impostazioni';
  type AreaImpostazioni = 'La società' | 'Prezzi e pagamenti' | 'Clienti e accessi' | 'Staff' | 'Sistema';
  interface Sezione {
    id: Scheda;
    nome: string;
    /** Nome nella barra in basso del telefono; senza, la sezione non ci sta (si raggiunge da Pianificazione). */
    breve?: string;
    titolo: string;
    gruppo: 'Il mio lavoro' | 'Piscina' | 'Accessi' | 'Gestione' | 'Impostazioni';
    /** Le impostazioni stanno tutte sotto un'unica voce, divise per area (con una riga di spiegazione per l'elenco). */
    area?: AreaImpostazioni;
    spiega?: string;
    /** Visibile a chi ha almeno uno di questi permessi; senza, a tutti. */
    permessi?: Permesso[];
    larga?: boolean;
    icona: NomeIcona;
  }

  // Icone a tratto, 24×24
  const SEZIONI: Sezione[] = [
    { id: 'turni', nome: 'I miei turni', breve: 'Turni', titolo: 'I miei turni', gruppo: 'Il mio lavoro', larga: true, icona: 'turni' },
    { id: 'richieste', nome: 'Le mie richieste', breve: 'Le mie', titolo: 'Le mie richieste', gruppo: 'Il mio lavoro', icona: 'richieste' },
    { id: 'ore', nome: 'Ore e compensi', breve: 'Ore', titolo: 'Ore e compensi', gruppo: 'Il mio lavoro', larga: true, icona: 'ore' },
    { id: 'piano', nome: 'Piano vasca', breve: 'Piano', titolo: 'Piano vasca', gruppo: 'Piscina', larga: true, icona: 'piano' },
    { id: 'attesa', nome: 'Liste d\'attesa', breve: 'Attesa', titolo: 'Liste d\'attesa', gruppo: 'Piscina', permessi: ['ISCRIZIONI'], larga: true, icona: 'attesa' },
    { id: 'corsi', nome: 'Corsi', titolo: 'Corsi', gruppo: 'Piscina', permessi: ['PIANO_MODIFICA'], larga: true, icona: 'corsi' },
    { id: 'tornello', nome: 'Tornello', breve: 'Tornello', titolo: 'Tornello', gruppo: 'Accessi', larga: true, icona: 'tornello' },
    { id: 'clienti', nome: 'Clienti', breve: 'Clienti', titolo: 'Clienti', gruppo: 'Accessi', permessi: ['CLIENTI', 'BRACCIALETTI'], larga: true, icona: 'clienti' },
    { id: 'scadenze', nome: 'Scadenze', breve: 'Scadenze', titolo: 'Scadenze', gruppo: 'Accessi', permessi: ['CLIENTI'], larga: true, icona: 'scadenze' },
    { id: 'incassi', nome: 'Incassi', breve: 'Incassi', titolo: 'Incassi', gruppo: 'Accessi', permessi: ['PAGAMENTI'], larga: true, icona: 'incassi' },
    { id: 'macchine', nome: 'Phon e docce', breve: 'Phon', titolo: 'Phon e docce', gruppo: 'Accessi', permessi: ['BORSELLINO', 'IMPOSTAZIONI'], larga: true, icona: 'macchine' },
    { id: 'pianifica', nome: 'Pianificazione', breve: 'Pianifica', titolo: 'Pianificazione', gruppo: 'Gestione', permessi: ['PIANIFICAZIONE'], larga: true, icona: 'pianifica' },
    { id: 'approva', nome: 'Richieste', breve: 'Richieste', titolo: 'Richieste', gruppo: 'Gestione', permessi: ['APPROVAZIONI'], larga: true, icona: 'approva' },
    { id: 'modelli', nome: 'Settimana tipo', titolo: 'Settimana tipo', gruppo: 'Gestione', permessi: ['PIANIFICAZIONE'], larga: true, icona: 'modelli' },
    // ── Impostazioni: tutte sotto una voce, per area (ADR 0024) ──
    { id: 'societa', nome: 'Dati, stagioni e gruppi', titolo: 'Dati, stagioni e gruppi', gruppo: 'Impostazioni', area: 'La società', permessi: ['IMPOSTAZIONI'], larga: true, icona: 'societa',
      spiega: 'Chi è la società (compare su ricevute ed email), le sue stagioni con la quota associativa e i gruppi di clienti' },
    { id: 'calendario', nome: 'Calendario della stagione', titolo: 'Calendario della stagione', gruppo: 'Impostazioni', area: 'La società', permessi: ['IMPOSTAZIONI', 'PIANO_MODIFICA'], larga: true, icona: 'calendario',
      spiega: 'Giorni di chiusura e a orario ridotto: niente turni né lezioni in quei giorni' },
    { id: 'listino', nome: 'Listino', titolo: 'Listino', gruppo: 'Impostazioni', area: 'Prezzi e pagamenti', permessi: ['IMPOSTAZIONI', 'CLIENTI'], larga: true, icona: 'listino',
      spiega: 'Le offerte di ogni stagione con i loro prezzi' },
    { id: 'prezzi', nome: 'Regole di prezzo', titolo: 'Regole di prezzo', gruppo: 'Impostazioni', area: 'Prezzi e pagamenti', permessi: ['IMPOSTAZIONI', 'CLIENTI'], larga: true, icona: 'prezzi',
      spiega: 'Come cambia il prezzo durante la stagione: la riduzione per chi si iscrive tardi, le rate e quanto si rimborsa a chi recede' },
    { id: 'metodi', nome: 'Metodi di pagamento', titolo: 'Metodi di pagamento', gruppo: 'Impostazioni', area: 'Prezzi e pagamenti', permessi: ['IMPOSTAZIONI', 'PAGAMENTI'], larga: true, icona: 'metodi',
      spiega: 'Contanti, carta, bonifico…: quali si usano, quali si segnano incassati a mano, e come paga una famiglia' },
    { id: 'ricevute', nome: 'Ricevute', titolo: 'Ricevute', gruppo: 'Impostazioni', area: 'Prezzi e pagamenti', permessi: ['IMPOSTAZIONI', 'PAGAMENTI'], larga: true, icona: 'ricevuta',
      spiega: 'Come si numerano, cosa c\'è scritto e come arrivano a chi paga' },
    { id: 'cassa', nome: 'Cassa', titolo: 'Cassa', gruppo: 'Impostazioni', area: 'Prezzi e pagamenti', permessi: ['IMPOSTAZIONI', 'PAGAMENTI'], larga: true, icona: 'incassi',
      spiega: 'Una cassa o una per postazione, il fondo per il resto e quando si chiude' },
    { id: 'regole', nome: 'Regole del tornello', titolo: 'Regole del tornello', gruppo: 'Impostazioni', area: 'Clienti e accessi', permessi: ['REGOLE_TORNELLO', 'BRACCIALETTI'], larga: true, icona: 'regole',
      spiega: 'Cosa controlla il tornello a ogni passaggio' },
    { id: 'promemoria', nome: 'Promemoria email', titolo: 'Promemoria email', gruppo: 'Impostazioni', area: 'Clienti e accessi', permessi: ['CLIENTI'], larga: true, icona: 'promemoria',
      spiega: 'Le email che partono da sole prima (o dopo) una scadenza, e gli avvisi della lista d\'attesa' },
    { id: 'servizi', nome: 'Phon e docce', titolo: 'Phon e docce', gruppo: 'Impostazioni', area: 'Clienti e accessi', permessi: ['IMPOSTAZIONI'], larga: true, icona: 'macchine',
      spiega: 'Crediti e servizi, il borsellino, il braccialetto e le macchine' },
    { id: 'postazioni', nome: 'Mansioni, postazioni e tariffe', titolo: 'Mansioni, postazioni e tariffe', gruppo: 'Impostazioni', area: 'Staff', permessi: ['ANAGRAFICHE_STAFF'], larga: true, icona: 'postazioni',
      spiega: 'Ogni mansione è un ruolo pagato, con la sua tariffa; le postazioni sono le righe della pianificazione, divise per area' },
    { id: 'account', nome: 'Account e ruoli', titolo: 'Account e ruoli', gruppo: 'Impostazioni', area: 'Sistema', permessi: ['ACCOUNT', 'RUOLI'], larga: true, icona: 'ruoli',
      spiega: 'Chi accede al gestionale e cosa può fare' },
    // di chi usa il dispositivo, non della società: la vedono tutti
    { id: 'aspetto', nome: 'Aspetto', titolo: 'Aspetto', gruppo: 'Impostazioni', area: 'Sistema', larga: true, icona: 'aspetto',
      spiega: 'Tema chiaro, scuro o come il sistema, su questo dispositivo' },
  ];
  const ID = SEZIONI.map((s) => s.id);

  const AREE: AreaImpostazioni[] = ['La società', 'Prezzi e pagamenti', 'Clienti e accessi', 'Staff', 'Sistema'];
  /** Gli indirizzi di prima della riorganizzazione portano alla pagina che ora contiene quella impostazione. */
  const VECCHI: Record<string, Scheda> = { borsellino: 'servizi', tariffe: 'postazioni', ruoli: 'account' };

  const leggiScheda = (): Scheda => {
    const parte = location.hash.replace('#/', '').split('/')[0];
    const h = (VECCHI[parte] ?? parte) as Scheda;
    return ID.includes(h) || h === 'impostazioni' ? h : 'turni';
  };
  let scheda = $state<Scheda>(leggiScheda());
  // Il telefono come lettore NFC di un PC: pagina a sé, senza accesso (ADR 0017)
  let paginaLettore = $state(location.hash.startsWith('#/lettore'));
  let daApprovare = $state(0);
  /** Sulla voce Incassi: chiusure da controllare (per chi può controllare) e casse aperte da troppo. */
  let avvisiCassa = $state(0);
  async function contaAvvisiCassa() {
    if (!sessione.puo('PAGAMENTI')) return;
    try {
      const a = await api.avvisiCassa();
      avvisiCassa = (sessione.puo('PAGAMENTI_ANNULLA') ? a.daControllare : 0) + a.aperteDaTroppo.length;
    } catch {
      /* il menù resta senza numero */
    }
  }
  let programma = $state<InfoProgramma | null>(null);
  // Programma per Windows: versione nuova disponibile e stato del controllo («Cerca aggiornamenti»)
  let aggiornamento = $state<AggiornamentoProgramma | null>(null);
  let statoAggiornamento = $state('');

  async function controllaAggiornamento(aMano: boolean) {
    statoAggiornamento = aMano ? 'Controllo…' : '';
    try {
      aggiornamento = await cercaAggiornamento();
      if (aMano) statoAggiornamento = aggiornamento ? '' : 'Il programma è aggiornato.';
    } catch (e) {
      if (aMano) statoAggiornamento = String(e);
    }
  }

  async function aggiorna() {
    statoAggiornamento = 'Scarico e installo: il programma si chiuderà e si riaprirà da solo.';
    try {
      await installaAggiornamento();
    } catch (e) {
      statoAggiornamento = String(e);
    }
  }

  onMount(() => {
    sessione.carica();
    infoProgramma().then((i) => {
      programma = i;
      if (i) controllaAggiornamento(false);
    });
    const f = () => {
      scheda = leggiScheda();
      paginaLettore = location.hash.startsWith('#/lettore');
    };
    window.addEventListener('hashchange', f);
    return () => window.removeEventListener('hashchange', f);
  });

  const visibili = $derived(SEZIONI.filter((s) => !s.permessi || sessione.puo(...s.permessi)));
  // le sezioni di lavoro (nella barra) e le impostazioni (sotto un'unica voce)
  const lavoro = $derived(visibili.filter((s) => s.gruppo !== 'Impostazioni'));
  const impostazioni = $derived(visibili.filter((s) => s.gruppo === 'Impostazioni'));
  const areeImpostazioni = $derived(AREE.map((a) => ({ nome: a, voci: impostazioni.filter((s) => s.area === a) })).filter((a) => a.voci.length));
  /** «#/impostazioni»: l'elenco delle impostazioni (sul telefono); sul PC si apre la prima. */
  const elencoImpostazioni = $derived(scheda === 'impostazioni');
  const corrente = $derived(visibili.find((s) => s.id === scheda) ?? (elencoImpostazioni ? impostazioni[0] : undefined) ?? SEZIONI[0]);
  const inImpostazioni = $derived(elencoImpostazioni || corrente.gruppo === 'Impostazioni');
  // Sul PC la barra mostra le sezioni di lavoro o, scorrendo, le impostazioni: dipende dalla pagina aperta.
  // «← Impostazioni» torna all'ultima pagina di lavoro.
  let ultimaLavoro = $state<Scheda>('turni');
  $effect(() => {
    if (!inImpostazioni && lavoro.some((s) => s.id === corrente.id)) ultimaLavoro = corrente.id;
  });
  const tornaAlLavoro = () => (location.hash = `#/${lavoro.some((s) => s.id === ultimaLavoro) ? ultimaLavoro : (lavoro[0]?.id ?? 'turni')}`);
  // «#/societa/stagioni»: dopo una ricerca si scorre al riquadro dell'impostazione
  $effect(() => {
    void scheda;
    const ancora = location.hash.replace('#/', '').split('/')[1];
    if (!inImpostazioni || !ancora) return;
    let prove = 0;
    const cerca = () => {
      const el = document.getElementById(`imp-${ancora}`);
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      else if (prove++ < 20) setTimeout(cerca, 100);
    };
    setTimeout(cerca, 50);
  });
  const sezioniImpostazioni = $derived(impostazioni.map((s) => s.id));
  const gruppi = $derived(
    (['Il mio lavoro', 'Piscina', 'Accessi', 'Gestione'] as const)
      .map((g) => ({ nome: g, voci: lavoro.filter((s) => s.gruppo === g) }))
      .filter((g) => g.voci.length),
  );
  // il nome della società (il gestionale serve a qualsiasi società)
  let nomeSocieta = $state('');
  $effect(() => {
    if (sessione.utente) api.ambiente().then((d) => (nomeSocieta = d.societa)).catch(() => {});
  });
  const iniziali = $derived(((sessione.utente?.nome ?? ' ')[0] + (sessione.utente?.cognome ?? ' ')[0]).toUpperCase());
  // Telefono: al massimo cinque voci in basso, le prime per il ruolo; il resto in «Altro»
  // in ordine di importanza; lo staff senza gestione ha richieste e ore in basso
  const ORDINE_BASSO: Scheda[] = ['turni', 'piano', 'tornello', 'clienti', 'scadenze', 'approva', 'pianifica', 'richieste', 'ore'];
  let cambioPassword = $state(false);
  const prime = $derived(
    sessione.puo('CLIENTI', 'APPROVAZIONI', 'PIANIFICAZIONE')
      ? ORDINE_BASSO.filter((id) => visibili.some((s) => s.id === id)).slice(0, 5)
      : (['turni', 'richieste', 'ore', 'piano', 'tornello'] as Scheda[]),
  );
  const altre = $derived(lavoro.filter((s) => !prime.includes(s.id)));
  const inBasso = $derived(
    prime.map((id) => lavoro.find((s) => s.id === id)).filter((s): s is Sezione => !!s).slice(0, altre.length || impostazioni.length ? 4 : 5),
  );
  const nelMenu = $derived(lavoro.filter((s) => !inBasso.includes(s)));
  let menuAltro = $state(false);
  $effect(() => {
    void scheda;
    menuAltro = false;
  });
  const NOMI_RUOLI: Record<string, string> = { ADMIN: 'Admin', COORDINATORE: 'Coordinatore', SEGRETERIA: 'Segreteria', STAFF: 'Staff' };
  const ruolo = $derived(
    [...new Set((sessione.utente?.ruoli ?? []).map((r) => NOMI_RUOLI[r.ruolo] ?? r.ruolo.charAt(0) + r.ruolo.slice(1).toLowerCase().replaceAll('_', ' ')))].join(' · ') || 'Nessun ruolo',
  );

  // il numero sulla voce Incassi si aggiorna a ogni cambio di pagina (es. dopo aver controllato una chiusura)
  $effect(() => {
    void scheda;
    contaAvvisiCassa();
  });
  $effect(() => {
    if (sessione.puo('APPROVAZIONI')) api.richiesteDaGestire().then((r) => (daApprovare = r.filter((x) => x.stato === 'IN_ATTESA_APPROVAZIONE' || (x.tipo === 'SOSTITUZIONE' && !x.collega)).length)).catch(() => {});
  });

  // Sul PC: Alt + numero per passare da una sezione all'altra
  function tasto(e: KeyboardEvent) {
    if (!e.altKey || e.ctrlKey || e.metaKey) return;
    const n = Number(e.key);
    if (n >= 1 && n <= lavoro.length) {
      e.preventDefault();
      location.hash = `#/${lavoro[n - 1].id}`;
    }
  }
</script>

<svelte:window onkeydown={tasto} />

{#snippet icona(nome: NomeIcona)}
  <Icona {nome} />
{/snippet}

{#snippet pagina()}
  {#key corrente.id}
    {#if corrente.id === 'turni'}<Turni />
    {:else if corrente.id === 'richieste'}<Richieste />
    {:else if corrente.id === 'ore'}<Ore />
    {:else if corrente.id === 'pianifica'}<Pianifica />
    {:else if corrente.id === 'approva'}<Approva cambiate={(n) => (daApprovare = n)} />
    {:else if corrente.id === 'modelli'}<Modelli />
    {:else if corrente.id === 'postazioni'}<Postazioni />
    {:else if corrente.id === 'piano'}<PianoVasca />
    {:else if corrente.id === 'corsi'}<Corsi />
    {:else if corrente.id === 'tornello'}<Tornello />
    {:else if corrente.id === 'clienti'}<Clienti />
    {:else if corrente.id === 'regole'}<Regole />
    {:else if corrente.id === 'scadenze'}<Scadenze />
    {:else if corrente.id === 'attesa'}<ListaAttesa />
    {:else if corrente.id === 'societa'}<Societa />
    {:else if corrente.id === 'calendario'}<Calendario />
    {:else if corrente.id === 'listino'}<Listino />
    {:else if corrente.id === 'metodi'}<MetodiPagamento />
    {:else if corrente.id === 'ricevute'}<ImpostazioniRicevute />
    {:else if corrente.id === 'cassa'}<ImpostazioniCassa />
    {:else if corrente.id === 'incassi'}<Incassi />
    {:else if corrente.id === 'macchine'}<Macchine />
    {:else if corrente.id === 'servizi'}<PhonDocce />
    {:else if corrente.id === 'prezzi'}<RegolePrezzo />
    {:else if corrente.id === 'promemoria'}<Promemoria />
    {:else if corrente.id === 'account'}<AccountRuoli />
    {:else if corrente.id === 'aspetto'}<Aspetto />{/if}
  {/key}
{/snippet}

{#snippet badge(s: Sezione)}
  {#if s.id === 'approva' && daApprovare}<span class="badge" aria-label="{daApprovare} richieste da gestire">{daApprovare}</span>{/if}
  {#if s.id === 'incassi' && avvisiCassa}<span class="badge warn" aria-label="{avvisiCassa} avvisi della cassa">{avvisiCassa}</span>{/if}
{/snippet}

{#if paginaLettore}
  <LettoreTelefono />
{:else if !sessione.pronto}
  <p class="muted centro">Caricamento…</p>
{:else if !sessione.utente}
  <Login />
{:else if sessione.utente.deveCambiarePassword}
  <!-- primo accesso: la password temporanea va cambiata prima di tutto -->
  <Accesso societa={nomeSocieta} titolo="Ciao {sessione.utente.nome}. Prima di iniziare, una password tua."
    testo="Sei entrato con la password temporanea che ti ha dato la segreteria. Scegline una che conosci solo tu: serve una volta, poi si entra sempre con questa.">
    <h1 class="titolo-acc">Nuova password</h1>
    <CambioPassword obbligatorio />
  </Accesso>
{:else}
  <div class="guscio" class:desktop={schermo.larga}>
    {#if schermo.larga}
      <aside class="lato">
        <div class="marchio">
          <Marchio misura={34} />
          <span class="nome-m"><span class="logo">Leone</span><span class="lbl">{nomeSocieta || 'Gestionale'}</span></span>
        </div>
        <div class="scorrevole">
          <div class="traccia" class:imp={inImpostazioni}>
            <nav aria-label="Sezioni" class="pannello" inert={inImpostazioni}>
              {#each gruppi as g (g.nome)}
                <div class="gruppo-nav">
                  <div class="lbl">{g.nome}</div>
                  {#each g.voci as s (s.id)}
                    <a href={`#/${s.id}`} class="voce-nav" class:on={scheda === s.id} aria-current={scheda === s.id ? 'page' : undefined}
                       title="{s.nome} (Alt+{lavoro.indexOf(s) + 1})">
                      {@render icona(s.icona)}<span class="grow">{s.nome}</span>{@render badge(s)}
                    </a>
                  {/each}
                </div>
              {/each}
              <span class="grow"></span>
              {#if impostazioni.length}
                <a href="#/impostazioni" class="voce-nav impostazioni" aria-label="Apri le impostazioni">
                  {@render icona('impostazioni')}<span class="grow">Impostazioni</span>
                  <Icona nome="dx" class="freccia-nav" />
                </a>
              {/if}
            </nav>
            <nav aria-label="Impostazioni" class="pannello" inert={!inImpostazioni}>
              <button class="indietro-nav" onclick={tornaAlLavoro}>
                <Icona nome="sx" />
                <span class="grow">Torna al lavoro</span>
              </button>
              <div class="gruppo-nav">
                <a href="#/impostazioni" class="voce-nav" class:on={elencoImpostazioni} aria-current={elencoImpostazioni ? 'page' : undefined}>
                  {@render icona('impostazioni')}<span class="grow">Panoramica</span>
                </a>
              </div>
              {#each areeImpostazioni as a (a.nome)}
                <div class="gruppo-nav">
                  <div class="lbl">{a.nome}</div>
                  {#each a.voci as s (s.id)}
                    <a href={`#/${s.id}`} class="voce-nav" class:on={!elencoImpostazioni && corrente.id === s.id} aria-current={!elencoImpostazioni && corrente.id === s.id ? 'page' : undefined}>
                      {@render icona(s.icona)}<span class="grow">{s.nome}</span>
                    </a>
                  {/each}
                </div>
              {/each}
            </nav>
          </div>
        </div>
        <div class="io">
          <div class="io-riga">
            <span class="avatar" aria-hidden="true">{iniziali}</span>
            <span class="io-chi"><span class="io-nome">{sessione.utente.nome} {sessione.utente.cognome}</span><span class="muted small">{ruolo}</span></span>
          </div>
          <div class="io-azioni">
            <Azione icona="chiave" etichetta="Cambia la tua password" onclick={() => (cambioPassword = true)} />
            {#if programma}
              <Azione icona="server" etichetta="Cambia server" onclick={cambiaServer} />
              <Azione icona="aggiorna" etichetta="Cerca aggiornamenti del programma" onclick={() => controllaAggiornamento(true)} />
            {/if}
            <Azione icona="esci" etichetta="Esci" onclick={() => sessione.esci()} />
          </div>
          {#if programma}
            <div class="programma muted">Programma {programma.versione} · {location.host}</div>
            {#if statoAggiornamento && !aggiornamento}<div class="programma muted">{statoAggiornamento}</div>{/if}
          {/if}
        </div>
      </aside>
    {/if}

    <main class:larga={schermo.larga && corrente.larga} class:con-nav={!schermo.larga}>
      {#if aggiornamento}
        <div class="aggiornamento" role="status">
          <span class="grow">{statoAggiornamento || `È disponibile la versione ${aggiornamento.versione} del programma (ora hai la ${aggiornamento.attuale}).`}</span>
          <button class="btn btn-primary" onclick={aggiorna}>Aggiorna e riavvia</button>
          <button class="btn btn-secondary" onclick={() => (aggiornamento = null)}>Più tardi</button>
        </div>
      {/if}
      {#if inImpostazioni && schermo.larga}
        <!-- intestazione condivisa: la ricerca in cima a ogni pagina delle impostazioni, ferma mentre si scorre -->
        <div class="testa-cerca"><CercaImpostazioni sezioni={sezioniImpostazioni} /></div>
        {#if elencoImpostazioni}
          <header class="testa">
            <div>
              <div class="briciole lbl">{nomeSocieta || 'Impostazioni'}</div>
              <h1 class="page-title">Impostazioni</h1>
              <p class="muted nomargin spiega">Come lavora la società: prezzi, regole del tornello, promemoria, staff e accessi. Cerca un'impostazione dalla barra in alto, o apri un'area.</p>
            </div>
          </header>
          <PanoramicaImpostazioni aree={areeImpostazioni} />
        {:else}
          <header class="testa">
            <div>
              <div class="briciole lbl">Impostazioni · {corrente.area}</div>
              <h1 class="page-title">{corrente.titolo}</h1>
              {#if corrente.spiega}<p class="muted nomargin spiega">{corrente.spiega}.</p>{/if}
            </div>
          </header>
          {@render pagina()}
        {/if}
      {:else if elencoImpostazioni}
        <header class="testa"><div><div class="lbl">{nomeSocieta}</div><h1 class="page-title">Impostazioni</h1></div></header>
        <CercaImpostazioni sezioni={sezioniImpostazioni} telefono />
        <PanoramicaImpostazioni aree={areeImpostazioni} telefono />
        {#each areeImpostazioni as a (a.nome)}
          <section class="elenco-imp">
            <div class="lbl">{a.nome}</div>
            <div class="card voci-imp">
              {#each a.voci as s (s.id)}
                <a href={`#/${s.id}`}>{@render icona(s.icona)}<span class="grow"><span class="nome-imp">{s.nome}</span><span class="muted small">{s.spiega}</span></span><Icona nome="dx" class="chev" /></a>
              {/each}
            </div>
          </section>
        {/each}
      {:else}
        <header class="testa">
          <div>
            {#if !schermo.larga && inImpostazioni}<a class="indietro small" href="#/impostazioni"><Icona nome="sx" class="chev" />Impostazioni</a>
            {:else if !schermo.larga}<div class="lbl">{nomeSocieta}</div>{/if}
            <h1 class="page-title">{corrente.titolo}</h1>
          </div>
          {#if !schermo.larga}
            <div class="chi">
              <span class="muted small">{sessione.utente.nome}</span>
              <Azione icona="chiave" etichetta="Cambia la tua password" onclick={() => (cambioPassword = true)} />
              <Azione icona="esci" etichetta="Esci" onclick={() => sessione.esci()} />
            </div>
          {/if}
        </header>
        {@render pagina()}
      {/if}
    </main>

    {#if sessione.puo('CLIENTI', 'BRACCIALETTI')}<Segnalazioni />{/if}
    {#if cambioPassword}
      <Foglio titolo="Cambia password" chiudi={() => (cambioPassword = false)}>
        <CambioPassword fatto={() => (cambioPassword = false)} />
      </Foglio>
    {/if}

    {#if !schermo.larga}
      {#if menuAltro}
        <div class="altro card" id="menu-altro">
          {#each nelMenu as s (s.id)}
            <a href={`#/${s.id}`} class:on={scheda === s.id}>{@render icona(s.icona)}<span class="grow">{s.nome}</span>{@render badge(s)}</a>
          {/each}
          {#if impostazioni.length}
            <a href="#/impostazioni" class="sep" class:on={inImpostazioni}>{@render icona('impostazioni')}<span class="grow">Impostazioni</span></a>
          {/if}
        </div>
      {/if}
      <nav class="basso" aria-label="Sezioni" style="--n: {inBasso.length + (nelMenu.length || impostazioni.length ? 1 : 0)}">
        {#each inBasso as s (s.id)}
          <a href={`#/${s.id}`} class:on={scheda === s.id} aria-current={scheda === s.id ? 'page' : undefined}>
            {s.breve ?? s.nome}{@render badge(s)}
          </a>
        {/each}
        {#if nelMenu.length || impostazioni.length}
          <button class:on={menuAltro || inImpostazioni || nelMenu.some((s) => s.id === scheda)} aria-expanded={menuAltro} aria-controls="menu-altro"
                  onclick={() => (menuAltro = !menuAltro)}>Altro{#if nelMenu.some((s) => s.id === 'approva') && daApprovare}<span class="badge">{daApprovare}</span>{/if}{#if nelMenu.some((s) => s.id === 'incassi') && avvisiCassa}<span class="badge warn">{avvisiCassa}</span>{/if}</button>
        {/if}
      </nav>
    {/if}
  </div>
{/if}

<style>
  main {
    width: 100%;
    max-width: 640px;
    margin: 0 auto;
    padding: calc(env(safe-area-inset-top, 0px) + var(--space-20)) var(--space-16) calc(env(safe-area-inset-bottom, 0px) + var(--space-20));
    display: flex; flex-direction: column; gap: var(--space-16);
  }
  main.con-nav { padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 96px); }
  .centro { text-align: center; margin-top: 40vh; }
  .testa { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-12); }
  .chi { display: flex; align-items: center; gap: var(--space-4); }
  .chi > span { margin-right: var(--space-4); }

  /* ── Desktop ─────────────────────────────────────────────── */
  .guscio.desktop { display: grid; grid-template-columns: 248px minmax(0, 1fr); min-height: 100vh; }
  .desktop main { max-width: 760px; padding: 28px 40px 48px; margin: 0; }
  .desktop main.larga { max-width: 1440px; }
  .lato {
    position: sticky; top: 0; height: 100vh; display: flex; flex-direction: column; gap: var(--space-20);
    padding: var(--space-20) var(--space-12); background: var(--surface); border-right: 1px solid var(--line); overflow-y: auto;
  }
  .marchio { display: flex; align-items: center; gap: var(--space-10); padding: 0 var(--space-10); }
  .nome-m { display: flex; flex-direction: column; gap: var(--space-2); min-width: 0; }
  .nome-m .lbl { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .logo { font-family: var(--font-display); font-size: 24px; font-weight: 800; color: var(--ink); line-height: 1; letter-spacing: -0.01em; }
  .titolo-acc { margin: 0; font-family: var(--font-display); font-weight: 800; font-size: var(--text-4xl); line-height: 1.1; letter-spacing: -0.01em; }
  .gruppo-nav { display: flex; flex-direction: column; gap: var(--space-2); }
  .gruppo-nav > .lbl { padding: 0 var(--space-10) var(--space-4); font-size: var(--text-2xs); }
  .voce-nav {
    display: flex; align-items: center; gap: var(--space-10); min-height: 38px; padding: 0 var(--space-10);
    border-radius: var(--radius-sm); color: var(--ink); text-decoration: none; font-size: var(--text-md); font-weight: 500;
  }
  .voce-nav :global(.ic) { width: 18px; height: 18px; color: var(--muted); }
  .voce-nav:hover { background: var(--carta); }
  .voce-nav.on { background: var(--cloro-soft); color: var(--cloro-ink); font-weight: 600; }
  .voce-nav.on :global(.ic) { color: var(--cloro); }
  .voce-nav.impostazioni { border-top: 1px solid var(--line-soft); border-radius: 0; min-height: 46px; flex-shrink: 0; }
  .io { border-top: 1px solid var(--line-soft); padding: var(--space-12) var(--space-10) 0; display: flex; flex-direction: column; gap: var(--space-4); }
  .io-riga { display: flex; align-items: center; gap: var(--space-10); }
  .avatar { width: 34px; height: 34px; border-radius: 50%; background: var(--cloro-soft); color: var(--cloro-ink); display: inline-flex; align-items: center;
    justify-content: center; font-weight: 700; font-size: var(--text-sm); flex-shrink: 0; }
  .io-chi { display: flex; flex-direction: column; min-width: 0; line-height: 1.25; }
  .io-nome { font-weight: 600; font-size: var(--text-md); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .io-azioni { display: flex; gap: var(--space-4); margin-left: -10px; }

  /* ── Barra che scorre: sezioni di lavoro ⇄ impostazioni ── */
  .scorrevole { flex: 1; overflow: hidden; min-height: 0; }
  .traccia { display: flex; width: 200%; height: 100%; transition: transform 260ms cubic-bezier(0.2, 0.7, 0.2, 1); }
  .traccia.imp { transform: translateX(-50%); }
  @media (prefers-reduced-motion: reduce) { .traccia { transition: none; } }
  .pannello { width: 50%; height: 100%; display: flex; flex-direction: column; gap: var(--space-16); overflow-y: auto; box-sizing: border-box; }
  .voce-nav :global(.freccia-nav) { width: 16px; height: 16px; }
  .indietro-nav { display: flex; align-items: center; gap: var(--space-8); min-height: 44px; padding: 0 var(--space-8); border: none; border-radius: var(--radius-sm);
    background: var(--carta); color: var(--ink); font: inherit; font-size: var(--text-lg); font-weight: 700; cursor: pointer; text-align: left; flex-shrink: 0; }
  .indietro-nav :global(.ic) { width: 20px; height: 20px; color: var(--cloro); }
  .indietro-nav:hover { background: var(--surface-sunken); }
  .briciole { color: var(--cloro-ink); }
  /* la ricerca: fascia bianca con un filo sotto, così si stacca dal fondo carta della pagina */
  .testa-cerca { position: sticky; top: 0; z-index: 15; margin: -28px -40px 0; padding: 12px 40px; background: var(--surface); border-bottom: 1px solid var(--line); }
  .spiega { margin-top: var(--space-4); }

  /* ── Elenco delle impostazioni (telefono) ── */
  .elenco-imp { display: flex; flex-direction: column; gap: var(--space-6); }
  .voci-imp { display: flex; flex-direction: column; overflow: hidden; padding: 0; }
  .voci-imp a { display: flex; align-items: center; gap: var(--space-12); min-height: 60px; padding: var(--space-8) var(--space-14); color: var(--ink);
    text-decoration: none; border-top: 1px solid var(--line-soft); }
  .voci-imp a:first-child { border-top: none; }
  .voci-imp :global(.ic) { width: 20px; height: 20px; color: var(--cloro); flex-shrink: 0; }
  .voci-imp :global(.chev) { width: 18px; height: 18px; color: var(--chevron); }
  .voci-imp .grow { display: flex; flex-direction: column; min-width: 0; }
  .nome-imp { font-weight: 600; }
  .indietro { color: var(--cloro-ink); text-decoration: none; font-weight: 600; display: inline-flex; min-height: 32px; align-items: center; gap: var(--space-2); margin-left: -4px; }
  .indietro :global(.ic) { color: var(--cloro); }
  .altro a.sep { border-top: 1px solid var(--line-soft); border-radius: 0; margin-top: var(--space-4); }
  .programma { font-size: var(--text-2xs); margin-top: var(--space-6); }
  .aggiornamento { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; padding: var(--space-10) var(--space-14);
    border-radius: var(--radius-md); background: var(--cloro-soft); color: var(--cloro-ink); border: 1px solid var(--cloro); margin-bottom: var(--space-12); }

  /* ── Telefono ─────────────────────────────────────────────── */
  nav.basso {
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 20;
    display: grid; grid-template-columns: repeat(var(--n), minmax(0, 1fr));
    padding-bottom: env(safe-area-inset-bottom, 0px);
    background: var(--surface); border-top: 1px solid var(--line);
  }
  nav.basso a { position: relative; display: flex; align-items: center; justify-content: center; gap: 4px; min-height: 60px; padding: 0 4px; font-size: var(--text-xs); font-weight: 600; color: var(--muted); text-decoration: none; text-align: center; }
  nav.basso a.on, nav.basso button.on { color: var(--cloro); box-shadow: inset 0 3px 0 var(--cloro); }
  nav.basso button { position: relative; display: flex; align-items: center; justify-content: center; gap: 4px; min-height: 60px; padding: 0 4px;
    border: none; background: none; font: inherit; font-size: var(--text-xs); font-weight: 600; color: var(--muted); cursor: pointer; }
  .altro { position: fixed; right: var(--space-12); bottom: calc(env(safe-area-inset-bottom, 0px) + 68px); z-index: 21; width: min(300px, calc(100vw - 24px));
    display: flex; flex-direction: column; padding: var(--space-6); box-shadow: 0 8px 28px rgba(16, 33, 43, 0.18); }
  .altro a { display: flex; align-items: center; gap: var(--space-10); min-height: 46px; padding: 0 var(--space-10); border-radius: var(--radius-sm);
    color: var(--ink); text-decoration: none; font-weight: 500; }
  .altro a :global(.ic) { width: 18px; height: 18px; color: var(--muted); }
  .altro a.on { background: var(--cloro-soft); color: var(--cloro-ink); }
  .badge.warn { background: var(--warn-bg); color: var(--warn); }
  .badge { min-width: 18px; height: 18px; border-radius: 9px; background: var(--cloro); color: var(--on-cloro); font-size: 11px; display: inline-flex; align-items: center; justify-content: center; padding: 0 5px; }
</style>
