<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import { onDestroy, tick, untrack } from 'svelte';
  import {
    api, messaggioErrore,
    type Abbonamento, type Braccialetto, type Conto, type Corso, type GruppoClienti, type MetodoPagamento, type ModoRestituzione, type RegoleBorsellino,
    type SchedaAccessi, type SchedaBorsellino, type SchedaCliente, type Sesso,
  } from '../lib/api';
  import { dataBreve, dataDi, iso, oraDi } from '../lib/date';
  import { euro } from '../lib/listino';
  import { leggiConNfc, motivoNfcAssente, nfcDisponibile, normalizzaCodice } from '../lib/nfc';
  import { sessione } from '../lib/sessione.svelte';
  import ConsensiCliente from '../components/ConsensiCliente.svelte';
  import BorsellinoCliente from '../components/BorsellinoCliente.svelte';
  import ContoCliente from '../components/ContoCliente.svelte';
  import EsitoTornello from '../components/EsitoTornello.svelte';
  import FamigliaCliente from '../components/FamigliaCliente.svelte';
  import TurniCliente from '../components/TurniCliente.svelte';
  import VenditaListino from '../components/VenditaListino.svelte';
  import LettoreTelefonoPc from '../components/LettoreTelefonoPc.svelte';
  import { lettoreTelefono } from '../lib/lettore.svelte';

  /**
   * La scheda di un cliente per la segreteria. In alto il riassunto, sempre visibile: chi è, come andrebbe adesso al
   * tornello, quanto deve, credito e crediti, braccialetto, e le azioni di tutti i giorni (pagamento, abbonamento,
   * ricarica; le rare in «Altre azioni»). Sotto, cinque schede: corsi e abbonamenti, pagamenti e borsellino, accessi,
   * anagrafica e famiglia, documenti; ognuna segnala se c'è qualcosa da fare. Ogni apertura si registra.
   */
  let { personaId }: { personaId: string } = $props();
  const id = untrack(() => personaId);
  const oggi = iso(new Date());

  let cliente = $state<SchedaCliente | null>(null);
  let accessi = $state<SchedaAccessi | null>(null);
  let corsi = $state<Corso[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  let conferma = $state('');
  /** Cresce a ogni registrazione: il conto dei pagamenti si ricarica (un abbonamento venduto crea i suoi addebiti). */
  let versione = $state(0);

  const daiGolden = $derived(sessione.puo('GOLDEN_PASS'));

  async function carica() {
    try {
      [cliente, accessi] = await Promise.all([api.schedaCliente(id), api.schedaAccessi(id)]);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco ad aprire la scheda.');
    }
  }
  carica();
  api.corsi().then((c) => (corsi = c.filter((x) => x.attivo))).catch(() => {});

  // ── Gruppi di clienti ─────────────────────────────────────────────────
  let gruppi = $state<GruppoClienti[]>([]);
  let nuovoGruppo = $state('');
  api.gruppi().then((g) => (gruppi = g)).catch(() => {});
  const gruppiLiberi = $derived(gruppi.filter((g) => g.attivo && !cliente?.gruppi.some((x) => x.gruppoId === g.id)));
  async function aggiungiGruppo() {
    const g = gruppi.find((x) => x.id === nuovoGruppo);
    if (!g) return;
    if (await esegui(() => api.aggiungiAlGruppo(id, g.id), `Aggiunto al gruppo «${g.nome}».`)) nuovoGruppo = '';
  }

  async function esegui(azione: () => Promise<unknown>, fatto: string) {
    errore = '';
    messaggio = '';
    conferma = '';
    try {
      await azione();
      await carica();
      versione++;
      messaggio = fatto;
      return true;
    } catch (e) {
      errore = messaggioErrore(e);
      return false;
    }
  }

  /** Le azioni pericolose chiedono un secondo clic. */
  function confermato(chiave: string) {
    if (conferma === chiave) return true;
    conferma = chiave;
    return false;
  }

  // ── Braccialetti ──────────────────────────────────────────────────────
  let nuovoCodice = $state('');
  let banBraccialetto = $state<{ id: string; motivo: string } | null>(null);
  let nfcAttivo = $state(false);
  let fermaNfc: AbortController | null = null;

  async function leggiNfc() {
    fermaNfc = new AbortController();
    try {
      await leggiConNfc((c) => {
        nuovoCodice = c;
        fermaNfc?.abort();
        nfcAttivo = false;
      }, fermaNfc.signal);
      nfcAttivo = true;
    } catch {
      errore = "Non riesco ad avviare l'NFC.";
    }
  }
  onDestroy(() => fermaNfc?.abort());
  // con il telefono abbinato come lettore, il codice letto va nel campo «Assegna un braccialetto»
  onDestroy(lettoreTelefono.ascolta((c) => (nuovoCodice = c)));

  // regole del borsellino (cauzione, crediti alla riconsegna e allo smarrimento) e metodi per restituire i soldi
  let regole = $state<RegoleBorsellino | null>(null);
  let metodi = $state<MetodoPagamento[]>([]);
  api.regoleBorsellino().then((r) => (regole = r)).catch(() => {});
  api.metodiPagamento().then((m) => (metodi = m.filter((x) => x.attivo && !x.borsellino && !x.daIncassare))).catch(() => {});
  let conCauzione = $state(true);

  const assegna = async () => {
    const c = normalizzaCodice(nuovoCodice);
    const cauzione = !!regole && regole.cauzione > 0 && conCauzione;
    if (await esegui(() => api.assegnaBraccialetto(id, c, undefined, cauzione), `Braccialetto ${c} assegnato${cauzione ? `: cauzione di ${euro(regole!.cauzione)} tra i pagamenti` : ''}.`)) {
      nuovoCodice = '';
      conCauzione = true;
    }
  };

  async function smarrito(b: Braccialetto) {
    let persi = 0;
    const ok = await esegui(async () => (persi = (await api.statoBraccialetto(b.id, 'SMARRITO')).creditiPersi), `${b.codice} segnato come smarrito: al tornello darà rosso.`);
    if (ok && persi > 0) messaggio = `${b.codice} segnato come smarrito: al tornello darà rosso. ${persi} crediti di phon e docce persi (regola della società).`;
  }

  // ── Riconsegna ────────────────────────────────────────────────────────
  type Riconsegna = { id: string; cauzione: ModoRestituzione | 'TRATTIENI'; crediti: boolean; modoCrediti: ModoRestituzione; metodoId: string };
  let riconsegna = $state<Riconsegna | null>(null);
  const ultimoAttivo = (b: Braccialetto) => !accessi?.braccialetti.some((x) => x.id !== b.id && x.stato === 'ATTIVO');
  function apriRiconsegna(b: Braccialetto) {
    const regola = regole?.cauzioneRiconsegna ?? 'A_SCELTA';
    const modo: ModoRestituzione = regola === 'A_SCELTA' ? 'DENARO' : regola;
    riconsegna = { id: b.id, cauzione: modo, crediti: (regole?.creditiRiconsegna ?? true) && ultimoAttivo(b), modoCrediti: regola === 'A_SCELTA' ? 'BORSELLINO' : regola,
      metodoId: metodi[0]?.id ?? '' };
  }
  async function confermaRiconsegna(b: Braccialetto) {
    const r = riconsegna!;
    let esito: Awaited<ReturnType<typeof api.riconsegnaBraccialetto>> | undefined;
    const ok = await esegui(async () => (esito = await api.riconsegnaBraccialetto(b.id, { cauzione: r.cauzione, crediti: r.crediti ? r.modoCrediti : null,
      metodoId: r.cauzione === 'DENARO' || (r.crediti && r.modoCrediti === 'DENARO') ? r.metodoId || null : null })), `${b.codice} riconsegnato.`);
    if (ok && esito) {
      const dove = (m: string | null) => (m === 'DENARO' ? 'in denaro' : 'nel borsellino');
      const parti = [esito.cauzione > 0 ? `cauzione di ${euro(esito.cauzione)} restituita ${dove(esito.modoCauzione)}` : esito.modoCauzione === 'TRATTIENI' ? 'cauzione trattenuta' : '',
        esito.crediti > 0 ? `${esito.crediti} crediti rimborsati ${dove(esito.modoCrediti)} (${euro(esito.valoreCrediti)})` : ''].filter(Boolean);
      messaggio = `${b.codice} riconsegnato${parti.length ? ': ' + parti.join(', ') : ''}.`;
      riconsegna = null;
    }
  }

  const STATI: Record<Braccialetto['stato'], { nome: string; classe: string }> = {
    ATTIVO: { nome: 'Attivo', classe: 't-soft' },
    SMARRITO: { nome: 'Smarrito', classe: 't-warn' },
    DISATTIVATO: { nome: 'Disattivato', classe: 't-altro' },
    RESTITUITO: { nome: 'Riconsegnato', classe: 't-altro' },
  };

  // ── Anagrafica e contatti ─────────────────────────────────────────────
  type Bozza = { nome: string; cognome: string; dataNascita: string; sesso: Sesso | ''; luogoNascita: string; codiceFiscale: string; indirizzo: string;
    cap: string; citta: string; provincia: string; nazione: string; note: string; email: string; telefono: string };
  let anag = $state<Bozza | null>(null);

  function modificaAnagrafica() {
    const a = cliente!.anagrafica;
    anag = { nome: a.nome, cognome: a.cognome, dataNascita: a.dataNascita ?? '', sesso: a.sesso ?? '', luogoNascita: a.luogoNascita ?? '', codiceFiscale: a.codiceFiscale ?? '',
      indirizzo: a.indirizzo ?? '', cap: a.cap ?? '', citta: a.citta ?? '', provincia: a.provincia ?? '', nazione: a.nazione ?? '', note: a.note ?? '',
      email: cliente!.contatti?.email ?? '', telefono: cliente!.contatti?.telefono ?? '' };
  }

  const vuoto = (v: string) => (v.trim() ? v.trim() : null);
  const salvaAnagrafica = async () => {
    const b = anag!;
    const ok = await esegui(async () => {
      await api.aggiornaAnagrafica(id, { nome: b.nome.trim(), cognome: b.cognome.trim(), dataNascita: b.dataNascita || null, sesso: b.sesso || null,
        luogoNascita: vuoto(b.luogoNascita), codiceFiscale: vuoto(b.codiceFiscale), indirizzo: vuoto(b.indirizzo), cap: vuoto(b.cap), citta: vuoto(b.citta),
        provincia: vuoto(b.provincia), nazione: vuoto(b.nazione), note: vuoto(b.note) });
      await api.contatti(id, { email: vuoto(b.email), telefono: vuoto(b.telefono) });
    }, 'Anagrafica salvata.');
    if (ok) anag = null;
  };

  const indirizzo = $derived.by(() => {
    const a = cliente?.anagrafica;
    if (!a) return '';
    const luogo = [a.cap, a.citta].filter(Boolean).join(' ') + (a.provincia ? ` (${a.provincia})` : '');
    return [a.indirizzo, luogo.trim(), a.nazione && a.nazione !== 'Italia' ? a.nazione : ''].filter(Boolean).join(', ');
  });

  // ── Ban e golden pass ─────────────────────────────────────────────────
  let banPersona = $state<{ tipo: 'BAN' | 'SORVEGLIANZA'; motivo: string; al: string } | null>(null);
  let permesso = $state<{ tipo: 'USCITA_AUTONOMA' | 'ACCOMPAGNATORE'; firmataDa: string; firmataIl: string; al: string; note: string } | null>(null);
  let sospensione = $state<{ abbonamentoId: string; dal: string; al: string; motivo: string } | null>(null);
  let golden = $state<{ motivo: string } | null>(null);

  // ── Certificato ───────────────────────────────────────────────────────
  let certificato = $state<{ tipo: 'NON_AGONISTICO' | 'AGONISTICO' | 'PROVVISORIO'; rilasciatoIl: string; scadenza: string; firmatoDa: string } | null>(null);
  const tra = (giorni: number) => iso(new Date(Date.now() + giorni * 86400000));
  const TIPI_CERT = { NON_AGONISTICO: 'Non agonistico', AGONISTICO: 'Agonistico', PROVVISORIO: 'Provvisorio' } as const;

  // ── Abbonamenti ───────────────────────────────────────────────────────
  let abbonamento = $state<{ tipo: 'CORSO' | 'CARNET'; corsoId: string; volte: number; ingressi: number | string; aOrario: boolean; dal: string; al: string; importo: number | string | null } | null>(null);

  // vendita dal listino (di base) o a mano
  let vendita = $state(false);
  let rimborsi = $state<Record<string, string>>({});
  /** Con il recesso (di base sì): le rate non pagate non sono più dovute e il rimborso diventa un credito. */
  let recesso = $state(true);
  /** Al primo clic su «Termina oggi»: quanto si rimborserebbe secondo la regola di recesso della stagione. */
  async function terminaOggi(a: Abbonamento) {
    if (!confermato('fin' + a.id)) {
      recesso = true;
      try {
        const r = await api.recesso(a.id, oggi);
        rimborsi[a.id] = r ? `Rimborso del ${r.percentuale}% di ${euro(r.base)} pagati = ${euro(r.importo)}${r.nelBorsellino ? ', come credito nel borsellino' : ''}.` : '';
      } catch {
        rimborsi[a.id] = '';
      }
      return;
    }
    const conRecesso = recesso;
    let esito: Awaited<ReturnType<typeof api.terminaAbbonamento>> | undefined;
    await esegui(async () => (esito = await api.terminaAbbonamento(a.id, oggi, conRecesso)), 'Abbonamento terminato oggi.');
    if (esito && conRecesso) {
      const parti = [esito.annullato > 0 ? `${euro(esito.annullato)} di rate non più dovute` : '', esito.rimborso && esito.rimborso.importo > 0 ? `${euro(esito.rimborso.importo)} di credito` : ''].filter(Boolean);
      if (parti.length) messaggio = `Abbonamento terminato oggi con il recesso: ${parti.join(' e ')}.`;
    }
  }

  function nuovoAbbonamento() {
    abbonamento = { tipo: 'CORSO', corsoId: cliente?.turni[0]?.corsoId ?? '', volte: Math.max(1, Math.min(3, cliente?.turni.length ?? 1)), ingressi: 10, aOrario: false,
      dal: oggi, al: cliente?.stagioneProposta?.al ?? '', importo: null };
  }

  const numero = (v: number | string | null) => (v === null || v === '' ? null : Number(v));

  const salvaAbbonamento = async () => {
    const a = abbonamento!;
    const ok = await esegui(() => api.nuovoAbbonamento(id, a.tipo === 'CORSO'
      ? { tipo: 'CORSO', corsoId: a.corsoId, volteASettimana: a.volte, dal: a.dal, al: a.al || null, importo: numero(a.importo) }
      : { tipo: 'CARNET', ingressi: numero(a.ingressi), corsoId: a.aOrario ? a.corsoId : null, dal: a.dal, al: a.al || null, importo: numero(a.importo) }), 'Abbonamento registrato.');
    if (ok) abbonamento = null;
  };

  function statoAbbonamento(a: Abbonamento): { nome: string; classe: string } {
    if (a.sospesoOggi) return { nome: 'Sospeso', classe: 't-warn' };
    if (a.dal > oggi) return { nome: 'Non iniziato', classe: 't-altro' };
    if ((a.alEffettivo ?? a.al) && (a.alEffettivo ?? a.al)! < oggi) return { nome: 'Scaduto', classe: 't-warn' };
    if (a.tipo === 'CARNET' && (a.residui ?? 0) <= 0) return { nome: 'Esaurito', classe: 't-warn' };
    return { nome: 'Valido', classe: 't-soft' };
  }

  const attivi = $derived(accessi?.blocchi.filter((b) => b.attivo) ?? []);
  const gp = $derived(accessi?.goldenPass.find((g) => g.attivo));
  const eta = (n: number | null) => (n == null ? '' : n === 1 ? '1 anno' : `${n} anni`);
  // le stagioni proposte per cui manca ancora il tesseramento
  const daTesserare = $derived(cliente?.stagioniTesseramento.filter((s) => !cliente!.tesseramenti.some((t) => t.stagione === s.stagione)) ?? []);

  // ── Riassunto ─────────────────────────────────────────────────────────
  let conto = $state<Conto | null>(null);
  let borsellino = $state<SchedaBorsellino | null>(null);
  async function caricaRiassunto() {
    const [c, b] = await Promise.all([api.conto(id).catch(() => null), api.borsellino(id).catch(() => null)]);
    conto = c;
    borsellino = b;
  }
  $effect(() => {
    void versione;
    untrack(caricaRiassunto);
  });
  const puoPagare = $derived(sessione.puo('PAGAMENTI'));
  const puoRicaricare = $derived(sessione.puo('BORSELLINO'));
  const daPagare = $derived(conto?.totali.daPagare ?? 0);
  const qualcosaDaPagare = $derived(!!conto && (conto.addebiti.some((a) => a.stato === 'DA_PAGARE' || a.stato === 'SCADUTO') || conto.famiglia.some((f) => f.aperti.length > 0)));
  const braccialetto = $derived(accessi?.braccialetti.find((b) => b.stato === 'ATTIVO') ?? accessi?.braccialetti.find((b) => b.stato === 'SMARRITO'));
  const tesseramentoValido = $derived(cliente?.tesseramenti.find((t) => t.valido));

  // i pannelli di pagamento e ricarica stanno nella scheda «Pagamenti e borsellino»: ci si va e si aprono
  let contoEl = $state<{ avviaPagamento: () => void }>();
  let borsEl = $state<{ avviaRicarica: () => void }>();
  async function registraPagamento() {
    vai('pagamenti');
    await tick();
    contoEl?.avviaPagamento();
  }
  async function ricaricaCrediti() {
    vai('pagamenti');
    await tick();
    borsEl?.avviaRicarica();
  }

  // ── Schede ────────────────────────────────────────────────────────────
  type Scheda = 'corsi' | 'pagamenti' | 'accessi' | 'anagrafica' | 'documenti';
  const SCHEDE: { id: Scheda; nome: string }[] = [
    { id: 'corsi', nome: 'Corsi e abbonamenti' },
    { id: 'pagamenti', nome: 'Pagamenti e borsellino' },
    { id: 'accessi', nome: 'Accessi' },
    { id: 'anagrafica', nome: 'Anagrafica e famiglia' },
    { id: 'documenti', nome: 'Documenti' },
  ];
  // la scheda aperta resta la stessa passando da un cliente all'altro (solo in questa finestra)
  function schedaSalvata(): Scheda {
    try {
      const v = sessionStorage.getItem('scheda-cliente');
      if (SCHEDE.some((x) => x.id === v)) return v as Scheda;
    } catch { /* senza storage si parte dalla prima */ }
    return 'corsi';
  }
  let scheda = $state<Scheda>(schedaSalvata());
  function vai(s: Scheda) {
    scheda = s;
    try { sessionStorage.setItem('scheda-cliente', s); } catch { /* niente */ }
  }
  /** Frecce, Inizio e Fine si spostano tra le schede, come in ogni elenco di schede. */
  async function tastiSchede(e: KeyboardEvent) {
    const i = SCHEDE.findIndex((x) => x.id === scheda);
    const j = e.key === 'ArrowRight' ? (i + 1) % SCHEDE.length : e.key === 'ArrowLeft' ? (i - 1 + SCHEDE.length) % SCHEDE.length
      : e.key === 'Home' ? 0 : e.key === 'End' ? SCHEDE.length - 1 : -1;
    if (j < 0) return;
    e.preventDefault();
    vai(SCHEDE[j].id);
    await tick();
    document.getElementById(`tab-${SCHEDE[j].id}`)?.focus();
  }

  /** Il segno accanto al nome della scheda: quante cose ci sono, o cosa manca. */
  const segni = $derived.by(() => {
    const r: Partial<Record<Scheda, { testo: string; warn: boolean }>> = {};
    if (!cliente || !accessi) return r;
    const corsiAttivi = cliente.abbonamenti.filter((a) => statoAbbonamento(a).classe === 't-soft').length + cliente.turni.filter((t) => !t.al || t.al >= oggi).length;
    if (corsiAttivi) r.corsi = { testo: String(corsiAttivi), warn: false };
    if (daPagare > 0) r.pagamenti = { testo: 'da pagare', warn: true };
    const ban = attivi.filter((b) => b.tipo === 'BAN').length;
    if (ban) r.accessi = { testo: ban === 1 ? '1 ban' : `${ban} ban`, warn: true };
    else if (braccialetto?.stato === 'SMARRITO') r.accessi = { testo: 'smarrito', warn: true };
    if (cliente.persona.eta != null && cliente.persona.eta < 18 && !cliente.famiglia.tutori.length) r.anagrafica = { testo: 'manca un tutore', warn: true };
    const daFare = (cliente.certificati[0]?.valido ? 0 : 1) + daTesserare.length + (cliente.consensi.some((c) => c.tipo === 'TRATTAMENTO' && c.valido) ? 0 : 1);
    if (daFare) r.documenti = { testo: `${daFare} da fare`, warn: true };
    return r;
  });

  // ── Altre azioni ──────────────────────────────────────────────────────
  let menu = $state(false);
  let menuEl = $state<HTMLElement>();
  const chiudiMenu = (e: MouseEvent) => {
    if (menu && menuEl && !menuEl.contains(e.target as Node)) menu = false;
  };
  function altra(s: Scheda, fai: () => void) {
    menu = false;
    vai(s);
    fai();
  }
  const nuovoPermesso = () => (permesso = { tipo: 'USCITA_AUTONOMA', firmataDa: '', firmataIl: oggi, al: '', note: '' });
  const nuovoCertificato = () => (certificato = { tipo: 'NON_AGONISTICO', rilasciatoIl: oggi, scadenza: '', firmatoDa: '' });
</script>

{#if cliente && accessi}
  {@const p = cliente.persona}
  <!-- Riassunto: chi è, se entra, cosa deve, e le azioni di tutti i giorni -->
  <section class="card riassunto">
    <div class="r-testa">
      <div class="r-chi">
        <h2 class="d nome">{p.nome} {p.cognome}</h2>
        <div class="muted">{eta(p.eta)}{p.dataNascita ? ` · nat${cliente.anagrafica.sesso === 'F' ? 'a' : 'o'} il ${dataBreve(p.dataNascita)}` : ''}{cliente.anagrafica.luogoNascita ? ` a ${cliente.anagrafica.luogoNascita}` : ''}</div>
        <div class="r-tag">
          {#if tesseramentoValido}<span class="tag t-soft">Tesserato {tesseramentoValido.stagione}</span>{:else}<span class="tag t-warn">Non tesserato</span>{/if}
          {#if gp}<span class="tag oro-t">Golden pass</span>{/if}
          {#each cliente.gruppi as g (g.appartenenzaId)}<span class="tag t-altro">{g.nome}</span>{/each}
        </div>
      </div>
      <div class="r-esito">
        <EsitoTornello esito={accessi.adesso} simulato />
      </div>
    </div>

    <div class="kpis">
      <div class="kpi" class:warn={daPagare > 0}>
        <span class="lbl">Da pagare</span>
        <span class="val m">{conto ? euro(daPagare) : '…'}</span>
        <span class="kpi-nota">{!conto ? '' : conto.totali.scaduto > 0 ? `di cui ${euro(conto.totali.scaduto)} scaduti` : daPagare > 0 ? 'Da registrare' : 'In regola'}</span>
      </div>
      <div class="kpi">
        <span class="lbl">Credito</span>
        <span class="val m">{borsellino ? euro(borsellino.saldo.euro) : '…'}</span>
        <span class="kpi-nota">{borsellino?.saldo.inScadenza && borsellino.saldo.prossimaScadenza ? `${euro(borsellino.saldo.inScadenza)} scadono il ${dataBreve(borsellino.saldo.prossimaScadenza)}` : 'Nel borsellino'}</span>
      </div>
      <div class="kpi">
        <span class="lbl">Phon e docce</span>
        <span class="val m">{borsellino ? `${borsellino.saldo.crediti} crediti` : '…'}</span>
        <span class="kpi-nota">{borsellino ? `= ${euro(borsellino.saldo.valoreCrediti)}` : ''}</span>
      </div>
      <div class="kpi" class:warn={!braccialetto || braccialetto.stato !== 'ATTIVO'}>
        <span class="lbl">Braccialetto</span>
        <span class="val m">{braccialetto?.codice ?? 'Nessuno'}</span>
        <span class="kpi-nota">{braccialetto ? `${STATI[braccialetto.stato].nome} dal ${dataBreve(dataDi(braccialetto.statoDal))}` : 'Da assegnare'}</span>
      </div>
    </div>

    <div class="r-azioni">
      {#if puoPagare}
        <button class="btn btn-primary" disabled={!conto || !qualcosaDaPagare} title={conto && !qualcosaDaPagare ? 'Niente da pagare' : undefined} onclick={registraPagamento}><Icona nome="euro" />Registra pagamento…</button>
      {/if}
      <button class="btn btn-secondary" onclick={() => { vai('corsi'); abbonamento = null; vendita = true; }}><Icona nome="piu" />Nuovo abbonamento…</button>
      {#if puoRicaricare}<button class="btn btn-secondary" onclick={ricaricaCrediti}><Icona nome="macchine" />Ricarica crediti…</button>{/if}
      <span class="grow"></span>
      <div class="menu-wrap" bind:this={menuEl}>
        <Azione cornice icona="altro" etichetta="Altre azioni" premuto={menu} onclick={() => (menu = !menu)} />
        {#if menu}
          <div class="menu-azioni" role="menu" aria-label="Altre azioni">
            <button role="menuitem" onclick={() => altra('anagrafica', modificaAnagrafica)}><Icona nome="matita" />Modifica l'anagrafica</button>
            <button role="menuitem" onclick={() => altra('documenti', nuovoCertificato)}><Icona nome="piu" />Nuovo certificato…</button>
            <button role="menuitem" onclick={() => altra('documenti', nuovoPermesso)}><Icona nome="firma" />Nuovo permesso firmato…</button>
            <div class="sep" role="separator"></div>
            {#if daiGolden && !gp}<button role="menuitem" onclick={() => altra('accessi', () => (golden = { motivo: '' }))}><Icona nome="golden" />Golden pass…</button>{/if}
            <button role="menuitem" onclick={() => altra('accessi', () => (banPersona = { tipo: 'SORVEGLIANZA', motivo: '', al: '' }))}><Icona nome="occhio" />Sotto controllo…</button>
            <button role="menuitem" class="danger" onclick={() => altra('accessi', () => (banPersona = { tipo: 'BAN', motivo: '', al: '' }))}><Icona nome="divieto" />Banna la persona…</button>
          </div>
        {/if}
      </div>
    </div>
  </section>

  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  <!-- Schede -->
  <div class="schede" role="tablist" aria-label="Sezioni della scheda" tabindex="-1" onkeydown={tastiSchede}>
    {#each SCHEDE as t (t.id)}
      {@const segno = segni[t.id]}
      <button id={`tab-${t.id}`} class="scheda-tab" role="tab" aria-selected={scheda === t.id} aria-controls={`pannello-${t.id}`} tabindex={scheda === t.id ? 0 : -1} onclick={() => vai(t.id)}>
        {t.nome}{#if segno}<span class="conta" class:warn={segno.warn}>{segno.testo}</span>{/if}
      </button>
    {/each}
  </div>

  <div class="pannello" id={`pannello-${scheda}`} role="tabpanel" aria-labelledby={`tab-${scheda}`}>
    {#if scheda === 'corsi'}
      {@render sAbbonamenti(cliente, accessi)}
      <TurniCliente {cliente} {corsi} {esegui} />
    {:else if scheda === 'pagamenti'}
      <div class="largo-g"><ContoCliente bind:this={contoEl} personaId={id} {versione} cambiato={() => { carica(); caricaRiassunto(); }} /></div>
      <div class="largo-g"><BorsellinoCliente bind:this={borsEl} personaId={id} {versione} cambiato={() => versione++} /></div>
    {:else if scheda === 'accessi'}
      {@render sBraccialetti(cliente, accessi)}
      {@render sBan(cliente, accessi)}
      {@render sPassaggi(cliente, accessi)}
    {:else if scheda === 'anagrafica'}
      {@render sAnagrafica(cliente, accessi)}
      <FamigliaCliente {cliente} {esegui} />
    {:else}
      {@render sCertificato(cliente, accessi)}
      {@render sTesseramento(cliente, accessi)}
      <ConsensiCliente {cliente} {esegui} />
      {@render sPermessi(cliente, accessi)}
    {/if}
  </div>
{:else if errore}
  <p class="msg err" role="alert">{errore}</p>
{:else}
  <p class="muted">Caricamento…</p>
{/if}

<svelte:window onclick={chiudiMenu} onkeydown={(e) => e.key === 'Escape' && (menu = false)} />

{#snippet sAnagrafica(cliente: SchedaCliente, accessi: SchedaAccessi)}
    <!-- Anagrafica e contatti -->
    <section class="card blocco">
      <div class="testa-card"><h3>Anagrafica e contatti</h3>
        {#if !anag}<Azione icona="matita" etichetta="Modifica l'anagrafica" onclick={modificaAnagrafica} />{/if}</div>
      {#if anag}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaAnagrafica(); }}>
          <div class="due">
            <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={anag.nome} /></label>
            <label class="field"><span class="lbl">Cognome</span><input class="inp" bind:value={anag.cognome} /></label>
          </div>
          <div class="due">
            <label class="field"><span class="lbl">Data di nascita</span><input class="inp" type="date" bind:value={anag.dataNascita} max={oggi} /></label>
            <div class="field"><span class="lbl">Sesso</span>
              <div class="seg" style="grid-template-columns: 1fr 1fr 1fr" role="group" aria-label="Sesso">
                <button type="button" aria-pressed={anag.sesso === 'F'} onclick={() => (anag!.sesso = 'F')}>F</button>
                <button type="button" aria-pressed={anag.sesso === 'M'} onclick={() => (anag!.sesso = 'M')}>M</button>
                <button type="button" aria-pressed={anag.sesso === ''} onclick={() => (anag!.sesso = '')}>—</button>
              </div>
            </div>
          </div>
          <div class="due stretto">
            <label class="field"><span class="lbl">Luogo di nascita</span><input class="inp" bind:value={anag.luogoNascita} /></label>
            <label class="field"><span class="lbl">Codice fiscale</span><input class="inp m" bind:value={anag.codiceFiscale} maxlength="16" autocapitalize="characters" /></label>
          </div>
          <label class="field"><span class="lbl">Indirizzo</span><input class="inp" bind:value={anag.indirizzo} placeholder="Via e numero civico" /></label>
          <div class="tre">
            <label class="field"><span class="lbl">CAP</span><input class="inp m" bind:value={anag.cap} inputmode="numeric" maxlength="10" /></label>
            <label class="field"><span class="lbl">Città</span><input class="inp" bind:value={anag.citta} /></label>
            <label class="field"><span class="lbl">Prov.</span><input class="inp m" bind:value={anag.provincia} maxlength="2" autocapitalize="characters" /></label>
          </div>
          <label class="field"><span class="lbl">Nazione</span><input class="inp" bind:value={anag.nazione} placeholder="Italia" /></label>
          <div class="due stretto">
            <label class="field"><span class="lbl">Email</span><input class="inp" type="email" bind:value={anag.email} placeholder="Per i minori, meglio quella del tutore" /></label>
            <label class="field"><span class="lbl">Telefono</span><input class="inp" type="tel" bind:value={anag.telefono} /></label>
          </div>
          <label class="field"><span class="lbl">Note</span><textarea class="inp note" rows="3" bind:value={anag.note} maxlength="1000"></textarea></label>
          <p class="muted small nomargin">Solo ciò che serve: niente documenti d'identità o dati sanitari (il certificato va nel suo riquadro).</p>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (anag = null)}>Annulla</button>
            <button class="btn btn-primary grow" disabled={!anag.nome.trim() || !anag.cognome.trim()}>Salva</button>
          </div>
        </form>
      {:else}
        {@const a = cliente.anagrafica}
        <dl class="dati">
          <dt>Codice fiscale</dt><dd class="m">{a.codiceFiscale ?? '—'}</dd>
          <dt>Indirizzo</dt><dd>{indirizzo || '—'}</dd>
          <dt>Email</dt><dd>{#if cliente.contatti?.email}{cliente.contatti.email}{:else}<span class="muted">—</span>{/if}</dd>
          <dt>Telefono</dt><dd>{cliente.contatti?.telefono ?? '—'}</dd>
          {#if a.note}<dt>Note</dt><dd class="a-capo-t">{a.note}</dd>{/if}
          <dt>Gruppi</dt>
          <dd class="gruppi">
            {#each cliente.gruppi as g (g.appartenenzaId)}
              <span class="tag t-soft gruppo">{g.nome}
                <button class="togli" aria-label={`Togli dal gruppo ${g.nome}`} title="Togli dal gruppo"
                        onclick={() => confermato('gr' + g.appartenenzaId) && esegui(() => api.togliDalGruppo(g.appartenenzaId), `Tolto dal gruppo «${g.nome}».`)}>{#if conferma === 'gr' + g.appartenenzaId}conferma{:else}<Icona nome="chiudi" />{/if}</button>
              </span>
            {:else}
              <span class="muted">—</span>
            {/each}
            {#if gruppiLiberi.length}
              <select class="inp scegli-gruppo" bind:value={nuovoGruppo} onchange={aggiungiGruppo} aria-label="Aggiungi a un gruppo">
                <option value="">+ gruppo</option>
                {#each gruppiLiberi as g (g.id)}<option value={g.id}>{g.nome}</option>{/each}
              </select>
            {/if}
          </dd>
        </dl>
      {/if}
      {#if cliente.email.length}
        <div class="sotto">
          <span class="lbl">Email inviate</span>
          {#each cliente.email as e (e.id)}
            <div class="small"><span class="muted">{dataBreve(dataDi(e.creataIl))}</span> · {e.oggetto}
              {#if e.stato !== 'INVIATA'}<span class="muted"> · {e.stato === 'SENZA_EMAIL' ? 'senza indirizzo' : e.stato === 'NON_INVIATA' ? 'non partita (posta non configurata)' : 'errore'}</span>{/if}</div>
          {/each}
        </div>
      {/if}
    </section>
{/snippet}

{#snippet sBraccialetti(cliente: SchedaCliente, accessi: SchedaAccessi)}
    <!-- Braccialetti -->
    <section class="card blocco">
      <h3>Braccialetti</h3>
      <ul class="righe">
        {#each accessi.braccialetti as b (b.id)}
          <li class="riga-b" class:spento={b.stato === 'DISATTIVATO' || b.stato === 'RESTITUITO'}>
            <div class="riga-testa">
              <span class="m codice">{b.codice}</span>
              {#if b.decimale}<span class="m muted small" title="Lo stesso codice in decimale, come nel gestionale attuale">{b.decimale}</span>{/if}
              <span class="tag {STATI[b.stato].classe}">{STATI[b.stato].nome}</span>
              {#if b.bannato}<span class="tag bannato">Bannato</span>{/if}
              <span class="muted small">dal {dataBreve(dataDi(b.statoDal))}</span>
            </div>
            {#if banBraccialetto?.id === b.id}
              <form class="row" onsubmit={(e) => { e.preventDefault(); esegui(() => api.banna({ braccialettoId: b.id, motivo: banBraccialetto!.motivo, al: null }), `Braccialetto ${b.codice} bannato.`).then((ok) => ok && (banBraccialetto = null)); }}>
                <input class="inp grow" bind:value={banBraccialetto.motivo} placeholder="Motivo del ban" aria-label="Motivo del ban" />
                <button class="btn btn-danger" disabled={!banBraccialetto.motivo.trim()}>Banna</button>
                <Azione icona="chiudi" etichetta="Annulla" onclick={() => (banBraccialetto = null)} />
              </form>
            {:else if riconsegna?.id === b.id}
              <form class="riconsegna" onsubmit={(e) => { e.preventDefault(); confermaRiconsegna(b); }}>
                <div class="field">
                  <span class="lbl">La cauzione{regole?.cauzione ? ` (${euro(regole.cauzione)}, se pagata)` : ''}</span>
                  <div class="chips">
                    {#if regole?.cauzioneRiconsegna !== 'BORSELLINO'}<button type="button" class="chip" aria-pressed={riconsegna.cauzione === 'DENARO'} onclick={() => (riconsegna!.cauzione = 'DENARO')}>Restituita in denaro</button>{/if}
                    {#if regole?.cauzioneRiconsegna !== 'DENARO'}<button type="button" class="chip" aria-pressed={riconsegna.cauzione === 'BORSELLINO'} onclick={() => (riconsegna!.cauzione = 'BORSELLINO')}>Nel borsellino</button>{/if}
                    <button type="button" class="chip" aria-pressed={riconsegna.cauzione === 'TRATTIENI'} onclick={() => (riconsegna!.cauzione = 'TRATTIENI')}>Trattenuta</button>
                  </div>
                </div>
                {#if ultimoAttivo(b)}
                  <label class="small spunta"><input type="checkbox" bind:checked={riconsegna.crediti} /> Rimborsa i crediti di phon e docce</label>
                  {#if riconsegna.crediti && regole?.cauzioneRiconsegna === 'A_SCELTA'}
                    <div class="chips">
                      <button type="button" class="chip" aria-pressed={riconsegna.modoCrediti === 'BORSELLINO'} onclick={() => (riconsegna!.modoCrediti = 'BORSELLINO')}>Crediti nel borsellino (in euro)</button>
                      <button type="button" class="chip" aria-pressed={riconsegna.modoCrediti === 'DENARO'} onclick={() => (riconsegna!.modoCrediti = 'DENARO')}>Crediti in denaro</button>
                    </div>
                  {/if}
                {:else}
                  <p class="muted small nomargin">Ha altri braccialetti attivi: i crediti di phon e docce restano a lui.</p>
                {/if}
                {#if riconsegna.cauzione === 'DENARO' || (riconsegna.crediti && riconsegna.modoCrediti === 'DENARO')}
                  <label class="field"><span class="lbl">Soldi restituiti con</span>
                    <select class="inp" bind:value={riconsegna.metodoId}>{#each metodi as m (m.id)}<option value={m.id}>{m.nome}</option>{/each}</select>
                  </label>
                {/if}
                <div class="row">
                  <button type="button" class="btn btn-secondary grow" onclick={() => (riconsegna = null)}>Annulla</button>
                  <button class="btn btn-primary grow">Riconsegnato</button>
                </div>
              </form>
            {:else if b.stato !== 'DISATTIVATO' && b.stato !== 'RESTITUITO'}
              <div class="azioni">
                {#if b.stato === 'ATTIVO'}
                  <Azione icona="smarrito" etichetta={`Segna ${b.codice} come smarrito`} onclick={() => smarrito(b)} />
                  <Azione icona="riconsegna" etichetta={`Riconsegna ${b.codice}`} onclick={() => apriRiconsegna(b)} />
                  {#if !b.bannato}<Azione icona="divieto" etichetta={`Banna ${b.codice}`} onclick={() => (banBraccialetto = { id: b.id, motivo: '' })} />{/if}
                {:else}
                  <Azione icona="ritrovato" tono="cloro" etichetta={`${b.codice} ritrovato: riattivalo`} onclick={() => esegui(() => api.statoBraccialetto(b.id, 'ATTIVO'), `${b.codice} ritrovato: di nuovo attivo.`)} />
                {/if}
                <Azione icona="spegni" tono="danger" etichetta={`Disattiva ${b.codice}`} conferma="Disattiva?" onclick={() => esegui(() => api.statoBraccialetto(b.id, 'DISATTIVATO'), `${b.codice} disattivato.`)} />
              </div>
            {/if}
          </li>
        {:else}
          <li class="muted small">Nessun braccialetto.</li>
        {/each}
      </ul>
      <form class="aggiungi" onsubmit={(e) => { e.preventDefault(); assegna(); }}>
        <span class="lbl">Assegna un braccialetto</span>
        <div class="row">
          <input class="inp m grow" bind:value={nuovoCodice} placeholder="Codice o lettore" aria-label="Codice del nuovo braccialetto" autocomplete="off" />
          {#if nfcDisponibile()}
            <button type="button" class="btn btn-secondary" onclick={leggiNfc} disabled={nfcAttivo}>{nfcAttivo ? 'Avvicina…' : 'NFC'}</button>
          {/if}
          <button class="btn btn-primary" disabled={normalizzaCodice(nuovoCodice).length < 4}>Assegna</button>
        </div>
        {#if regole && regole.cauzione > 0}
          <label class="small spunta"><input type="checkbox" bind:checked={conCauzione} /> Addebita la cauzione ({euro(regole.cauzione)}): si restituisce alla riconsegna</label>
        {/if}
        <p class="muted small nomargin">Se uno è smarrito resta suo: se si ritrova basta segnarlo come ritrovato.{regole?.creditiSmarrimento === 'PERSI' ? ' Segnandolo smarrito i crediti di phon e docce si perdono (regola della società), se non ha altri braccialetti attivi.' : ''}</p>
        {#if !nfcDisponibile() && /Android/i.test(navigator.userAgent)}<p class="muted small nomargin">{motivoNfcAssente()}</p>{/if}
      </form>
      {#if !nfcDisponibile()}<LettoreTelefonoPc compatto />{/if}
    </section>
{/snippet}

{#snippet sBan(cliente: SchedaCliente, accessi: SchedaAccessi)}
    <!-- Ban e golden pass -->
    <section class="card blocco">
      <h3>Ban e golden pass</h3>
      {#if gp}
        <div class="oro">
          <span><strong>Golden pass</strong> dal {dataBreve(gp.dal)}{gp.al ? ` al ${dataBreve(gp.al)}` : ''}{gp.motivo ? ` · ${gp.motivo}` : ''}</span>
          {#if daiGolden}<Azione icona="revoca" tono="danger" etichetta="Revoca il golden pass" conferma="Revoca?" onclick={() => esegui(() => api.revocaGoldenPass(gp!.id), 'Golden pass revocato.')} />{/if}
        </div>
        <p class="muted small nomargin">Salta certificato, tesseramento, abbonamento e orario. Braccialetto smarrito e ban valgono lo stesso.</p>
      {/if}
      <ul class="righe">
        {#each attivi as b (b.id)}
          <li class="ban">
            <span>
              {#if b.tipo === 'SORVEGLIANZA'}<span class="tag oro-t">Sotto controllo</span>{/if}
              <strong>{b.braccialettoId ? `Braccialetto ${b.codice}` : 'Persona'}</strong>{b.tipo === 'BAN' ? (b.braccialettoId ? ' bannato' : ' bannata') : ''}
              dal {dataBreve(b.dal)}{b.al ? ` al ${dataBreve(b.al)}` : ''}: {b.motivo}</span>
            <Azione icona="revoca" tono="danger" etichetta={b.tipo === 'BAN' ? 'Revoca il ban' : 'Togli la sorveglianza'} conferma="Revoca?" onclick={() => esegui(() => api.revocaBan(b.id), b.tipo === 'BAN' ? 'Ban revocato.' : 'Non è più sotto controllo.')} />
          </li>
        {:else}
          <li class="muted small">Nessun ban, nessuna segnalazione.{#if !banPersona && !golden} Ban, sorveglianza e golden pass si danno da «Altre azioni», in alto.{/if}</li>
        {/each}
      </ul>
      {#if banPersona}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.banna({ tipo: banPersona!.tipo, personaId: id, motivo: banPersona!.motivo, al: banPersona!.al || null }), banPersona!.tipo === 'BAN' ? 'Persona bannata: tutti i suoi braccialetti danno rosso.' : 'Sotto controllo: a ogni passaggio la segreteria riceve un avviso.').then((ok) => ok && (banPersona = null)); }}>
          {#if banPersona.tipo === 'SORVEGLIANZA'}<p class="muted small nomargin">Entra normalmente, ma il tornello lo segnala e la segreteria riceve un avviso a ogni passaggio.</p>{/if}
          <input class="inp" bind:value={banPersona.motivo} placeholder="Motivo" aria-label="Motivo del ban" />
          <label class="field"><span class="lbl">Fino al (vuoto: finché non si revoca)</span><input class="inp" type="date" bind:value={banPersona.al} min={oggi} /></label>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (banPersona = null)}>Annulla</button>
            <button class="btn {banPersona.tipo === 'BAN' ? 'btn-danger' : 'btn-primary'} grow" disabled={!banPersona.motivo.trim()}>{banPersona.tipo === 'BAN' ? 'Banna la persona' : 'Metti sotto controllo'}</button>
          </div>
        </form>
      {:else if golden}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.goldenPass(id, golden!.motivo, null), 'Golden pass attivato.').then((ok) => ok && (golden = null)); }}>
          <input class="inp" bind:value={golden.motivo} placeholder="Motivo (es. tornellista)" aria-label="Motivo del golden pass" />
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (golden = null)}>Annulla</button>
            <button class="btn btn-primary grow">Attiva il golden pass</button>
          </div>
        </form>
      {/if}
    </section>
{/snippet}

{#snippet sPermessi(cliente: SchedaCliente, accessi: SchedaAccessi)}
    <!-- Permessi firmati -->
    <section class="card blocco">
      <div class="testa-card"><h3>Permessi firmati</h3>
        {#if !permesso}<Azione icona="piu" tono="cloro" etichetta="Nuovo permesso firmato" onclick={nuovoPermesso} />{/if}</div>
      <ul class="righe">
        {#each accessi.autorizzazioni.filter((a) => a.attiva) as a (a.id)}
          <li class="ban">
            <span><strong>{a.tipo === 'USCITA_AUTONOMA' ? 'Uscita autonoma' : 'Accompagnatore'}</strong>
              {a.firmataDa ? ` · firmato da ${a.firmataDa}` : ''}{a.firmataIl ? ` il ${dataBreve(a.firmataIl)}` : ''}
              <span class="muted small"> · dal {dataBreve(a.dal)}{a.al ? ` al ${dataBreve(a.al)}` : ''}</span></span>
            <Azione icona="revoca" tono="danger" etichetta="Revoca il permesso" conferma="Revoca?" onclick={() => esegui(() => api.revocaAutorizzazione(a.id), 'Permesso revocato.')} />
          </li>
        {:else}
          <li class="muted small">Nessun permesso: uscita autonoma e accompagnatore si registrano col «+».</li>
        {/each}
      </ul>
      {#if permesso}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.autorizza(id, { tipo: permesso!.tipo, firmataDa: permesso!.firmataDa || null, firmataIl: permesso!.firmataIl || null, dal: null, al: permesso!.al || null, note: permesso!.note || null }), 'Permesso registrato.').then((ok) => ok && (permesso = null)); }}>
          <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Tipo di permesso">
            <button type="button" aria-pressed={permesso.tipo === 'USCITA_AUTONOMA'} onclick={() => (permesso!.tipo = 'USCITA_AUTONOMA')}>Uscita autonoma</button>
            <button type="button" aria-pressed={permesso.tipo === 'ACCOMPAGNATORE'} onclick={() => (permesso!.tipo = 'ACCOMPAGNATORE')}>Accompagnatore</button>
          </div>
          <p class="muted small nomargin">{permesso.tipo === 'USCITA_AUTONOMA'
            ? 'Il minorenne può uscire da solo passando il braccialetto sul lato d\'uscita (se il controllo dell\'uscita è acceso).'
            : 'Può ripassare il braccialetto subito, per far entrare chi accompagna (se il cooldown è acceso).'}</p>
          <div class="due">
            <input class="inp" bind:value={permesso.firmataDa} placeholder={permesso.tipo === 'USCITA_AUTONOMA' ? 'Firmato da (genitore)' : 'Firmato da (facoltativo)'} aria-label="Firmato da" />
            <input class="inp" type="date" bind:value={permesso.firmataIl} max={oggi} aria-label="Firmato il" />
          </div>
          <label class="field"><span class="lbl">Valido fino al (vuoto: finché non si revoca)</span><input class="inp" type="date" bind:value={permesso.al} min={oggi} /></label>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (permesso = null)}>Annulla</button>
            <button class="btn btn-primary grow" disabled={permesso.tipo === 'USCITA_AUTONOMA' && !permesso.firmataDa.trim()}>Registra</button>
          </div>
        </form>
      {/if}
    </section>
{/snippet}

{#snippet sCertificato(cliente: SchedaCliente, accessi: SchedaAccessi)}
    <!-- Certificato medico -->
    <section class="card blocco">
      <div class="testa-card"><h3>Certificato medico</h3>
        {#if !certificato}<Azione icona="piu" tono="cloro" etichetta="Nuovo certificato" onclick={nuovoCertificato} />{/if}</div>
      <ul class="righe">
        {#each cliente.certificati as c, i (c.id)}
          <li class="voce-l" class:spento={i > 0}>
            <span class="grow">{TIPI_CERT[c.tipo]} · scade il <strong>{dataBreve(c.scadenza)}</strong>{c.firmatoDa ? ` · firmato da ${c.firmatoDa}` : ''}</span>
            {#if i === 0}<span class="tag {c.valido ? 't-soft' : 't-warn'}">{c.valido ? 'Valido' : 'Scaduto'}</span>{/if}
            <Azione icona="annulla" etichetta="Annulla il certificato (registrato per errore)" conferma="Annulla?" onclick={() => esegui(() => api.annullaVoce('certificati', c.id), 'Certificato annullato.')} />
          </li>
        {:else}
          <li class="muted small">Nessun certificato: al tornello darà rosso.</li>
        {/each}
      </ul>
      {#if certificato}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); esegui(() => api.nuovoCertificato(id, { tipo: certificato!.tipo, rilasciatoIl: certificato!.rilasciatoIl || null, scadenza: certificato!.scadenza, firmatoDa: certificato!.tipo === 'PROVVISORIO' ? certificato!.firmatoDa : null }), 'Certificato registrato.').then((ok) => ok && (certificato = null)); }}>
          <div class="seg" style="grid-template-columns: 1fr 1fr 1fr" role="group" aria-label="Tipo">
            <button type="button" aria-pressed={certificato.tipo === 'NON_AGONISTICO'} onclick={() => (certificato!.tipo = 'NON_AGONISTICO')}>Non agonistico</button>
            <button type="button" aria-pressed={certificato.tipo === 'AGONISTICO'} onclick={() => (certificato!.tipo = 'AGONISTICO')}>Agonistico</button>
            <button type="button" aria-pressed={certificato.tipo === 'PROVVISORIO'} onclick={() => { certificato!.tipo = 'PROVVISORIO'; certificato!.scadenza = tra(14); }}>Provvisorio</button>
          </div>
          {#if certificato.tipo === 'PROVVISORIO'}
            <input class="inp" bind:value={certificato.firmatoDa} placeholder="Firmato da (l'interessato o un genitore)" aria-label="Firmato da" />
            <p class="muted small nomargin">Dichiarazione firmata in attesa del certificato: al tornello vale pochi giorni (vedi le regole) e passa con un avviso.</p>
          {/if}
          <div class="due">
            <label class="field"><span class="lbl">Rilasciato il</span><input class="inp" type="date" bind:value={certificato.rilasciatoIl} max={oggi} /></label>
            <label class="field"><span class="lbl">Scade il</span><input class="inp" type="date" bind:value={certificato.scadenza} /></label>
          </div>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (certificato = null)}>Annulla</button>
            <button class="btn btn-primary grow" disabled={!certificato.scadenza}>Registra</button>
          </div>
        </form>
      {/if}
    </section>
{/snippet}

{#snippet sTesseramento(cliente: SchedaCliente, accessi: SchedaAccessi)}
    <!-- Tesseramento -->
    <section class="card blocco">
      <h3>Tesseramento</h3>
      <ul class="righe">
        {#each cliente.tesseramenti as t (t.id)}
          <li class="voce-l">
            <span class="grow"><strong>{t.stagione}</strong> · {dataBreve(t.dal)} – {dataBreve(t.al)}{t.importo != null ? ` · ${t.importo} €` : ''}</span>
            {#if t.importo}<span class="tag {t.pagatoIl ? 't-soft' : 't-warn'}">{t.pagatoIl ? `Quota pagata il ${dataBreve(t.pagatoIl)}` : 'Quota da pagare'}</span>{/if}
            <span class="tag {t.valido ? 't-soft' : 't-altro'}">{t.valido ? 'Valido' : t.dal > oggi ? 'Non iniziato' : 'Scaduto'}</span>
            <Azione icona="annulla" etichetta={`Annulla il tesseramento ${t.stagione} (registrato per errore)`} conferma="Annulla?" onclick={() => esegui(() => api.annullaVoce('tesseramenti', t.id), 'Tesseramento annullato.')} />
          </li>
        {:else}
          <li class="muted small">Nessun tesseramento.</li>
        {/each}
      </ul>
      {#each daTesserare as s (s.stagione)}
        <div class="tess">
          <button class="btn btn-secondary btn-sm sx" onclick={() => esegui(() => api.nuovoTesseramento(id, { stagione: s.stagione }), `Tesseramento ${s.stagione} registrato.`)}>
            <Icona nome="piu" />Registra il tesseramento {s.stagione}{s.importo != null ? ` · ${s.importo} €` : ''}
          </button>
          <p class="muted small nomargin">Vale dal {dataBreve(s.dal)} al {dataBreve(s.al)}{s.importo ? '; la quota va tra i pagamenti' : ''}.</p>
        </div>
      {/each}
      {#if !cliente.stagioniTesseramento.length}
        <p class="muted small nomargin">Nessuna stagione in corso: le stagioni si impostano in «Impostazioni della società».</p>
      {/if}
    </section>
{/snippet}

{#snippet sAbbonamenti(cliente: SchedaCliente, accessi: SchedaAccessi)}
    <!-- Abbonamenti -->
    <section class="card blocco">
      <div class="testa-card"><h3>Abbonamenti</h3>
        {#if !vendita && !abbonamento}<Azione icona="piu" tono="cloro" etichetta="Nuovo abbonamento o carnet" onclick={() => (vendita = true)} />{/if}</div>
      <ul class="righe">
        {#each cliente.abbonamenti as a (a.id)}
          {@const st = statoAbbonamento(a)}
          <li class="voce-l">
            <span class="grow">
              {#if a.tipo === 'CORSO'}<strong>{a.corso}</strong> · {a.volteASettimana === 1 ? 'una volta' : `${a.volteASettimana} volte`} a settimana
              {:else}<strong>Carnet {a.ingressi} ingressi</strong> · {a.corso ? `negli orari di ${a.corso}` : 'entrata libera'} · {a.residui} rimasti{a.ultimoIngresso ? `, ultimo il ${dataBreve(dataDi(a.ultimoIngresso))}` : ''}{/if}
              <span class="muted small"> · dal {dataBreve(a.dal)}{a.al ? ` al ${dataBreve(a.alEffettivo ?? a.al)}` : ''}{a.alEffettivo && a.al && a.alEffettivo !== a.al ? ` (allungato per le sospensioni)` : ''}{a.importo != null ? ` · ${euro(a.importo)}` : ''}</span>
              {#if a.motivoImporto}<span class="small muted riga-info">Proposto {euro(a.importoListino)} · {a.motivoImporto}</span>{/if}
              {#if conferma === 'fin' + a.id}
                <span class="termina">
                  <label class="small recesso"><input type="checkbox" bind:checked={recesso} />
                    Recesso: le rate non ancora pagate non sono più dovute{rimborsi[a.id] ? ` · ${rimborsi[a.id]}` : ''}</label>
                  <span class="row">
                    <button class="btn btn-danger btn-sm" onclick={() => terminaOggi(a)}><Icona nome="termina" />Termina oggi</button>
                    <button class="btn btn-quieto btn-sm" onclick={() => (conferma = '')}>Lascia attivo</button>
                  </span>
                </span>
              {/if}
              {#each a.sospensioni as s (s.id)}
                <span class="sosp small">Sospeso dal {dataBreve(s.dal)} al {dataBreve(s.al)}{s.motivo ? ` · ${s.motivo}` : ''}
                  <Azione icona="annulla" etichetta="Annulla la sospensione" conferma="Annulla?" onclick={() => esegui(() => api.annullaSospensione(s.id), 'Sospensione annullata.')} /></span>
              {/each}
              {#if sospensione?.abbonamentoId === a.id}
                <form class="sospendi" onsubmit={(e) => { e.preventDefault(); esegui(() => api.sospendi(a.id, { dal: sospensione!.dal, al: sospensione!.al, motivo: sospensione!.motivo || null }), 'Abbonamento sospeso: la scadenza si allunga di altrettanti giorni.').then((ok) => ok && (sospensione = null)); }}>
                  <input class="inp" type="date" bind:value={sospensione.dal} aria-label="Sospeso dal" />
                  <input class="inp" type="date" bind:value={sospensione.al} min={sospensione.dal} aria-label="Sospeso fino al" />
                  <input class="inp" bind:value={sospensione.motivo} placeholder="Motivo (es. infortunio)" aria-label="Motivo" />
                  <button class="btn btn-primary" disabled={!sospensione.dal || !sospensione.al}><Icona nome="pausa" />Sospendi</button>
                  <Azione icona="chiudi" etichetta="Annulla" onclick={() => (sospensione = null)} />
                </form>
              {/if}
            </span>
            <span class="tag {st.classe}">{st.nome}</span>
            <span class="gruppo-azioni">
              {#if !sospensione}<Azione icona="pausa" etichetta="Sospendi l'abbonamento" onclick={() => (sospensione = { abbonamentoId: a.id, dal: oggi, al: tra(13), motivo: '' })} />{/if}
              {#if (!a.al || a.al > oggi) && conferma !== 'fin' + a.id}
                <Azione icona="termina" etichetta="Termina oggi l'abbonamento" onclick={() => terminaOggi(a)} />
              {/if}
              <Azione icona="annulla" etichetta="Annulla l'abbonamento (registrato per errore)" conferma="Annulla?" onclick={() => esegui(() => api.annullaVoce('abbonamenti', a.id), 'Abbonamento annullato.')} />
            </span>
          </li>
        {:else}
          <li class="muted small">Nessun abbonamento.</li>
        {/each}
      </ul>
      {#if vendita}
        <VenditaListino {cliente} {corsi} {esegui} chiudi={() => (vendita = false)} aMano={() => { vendita = false; nuovoAbbonamento(); }} />
      {:else if abbonamento}
        <form class="modulo" onsubmit={(e) => { e.preventDefault(); salvaAbbonamento(); }}>
          <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Tipo di abbonamento">
            <button type="button" aria-pressed={abbonamento.tipo === 'CORSO'} onclick={() => { abbonamento!.tipo = 'CORSO'; abbonamento!.al = cliente!.stagioneProposta?.al ?? ''; }}>Corso</button>
            <button type="button" aria-pressed={abbonamento.tipo === 'CARNET'} onclick={() => { abbonamento!.tipo = 'CARNET'; abbonamento!.al = ''; }}>Carnet</button>
          </div>
          {#if abbonamento.tipo === 'CORSO'}
            <label class="field"><span class="lbl">Corso</span>
              <select class="inp" bind:value={abbonamento.corsoId}>
                <option value="" disabled>Scegli il corso</option>
                {#each corsi as c (c.id)}<option value={c.id}>{c.nome}</option>{/each}
              </select>
            </label>
            <div class="field">
              <span class="lbl">Volte a settimana</span>
              <div class="chips">
                {#each [1, 2, 3] as v (v)}<button type="button" class="chip" aria-pressed={abbonamento.volte === v} onclick={() => (abbonamento!.volte = v)}>{v === 1 ? 'Mono' : v === 2 ? 'Bi' : 'Tri'}settimanale</button>{/each}
              </div>
            </div>
          {:else}
            <label class="field"><span class="lbl">Ingressi</span><input class="inp" type="number" min="1" max="500" bind:value={abbonamento.ingressi} /></label>
            <div class="field">
              <span class="lbl">Quando si entra</span>
              <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Quando si entra">
                <button type="button" aria-pressed={!abbonamento.aOrario} onclick={() => (abbonamento!.aOrario = false)}>Entrata libera</button>
                <button type="button" aria-pressed={abbonamento.aOrario} onclick={() => (abbonamento!.aOrario = true)}>Negli orari di un corso</button>
              </div>
            </div>
            {#if abbonamento.aOrario}
              <label class="field"><span class="lbl">Corso (es. Nuoto libero)</span>
                <select class="inp" bind:value={abbonamento.corsoId}>
                  <option value="" disabled>Scegli il corso</option>
                  {#each corsi as c (c.id)}<option value={c.id}>{c.nome}</option>{/each}
                </select>
              </label>
              <p class="muted small nomargin">Al tornello vale solo nella fascia dei turni di quel corso, come un abbonamento.</p>
            {/if}
          {/if}
          <div class="due">
            <label class="field"><span class="lbl">Dal</span><input class="inp" type="date" bind:value={abbonamento.dal} /></label>
            <label class="field"><span class="lbl">Al {abbonamento.tipo === 'CARNET' ? '(facoltativo)' : ''}</span><input class="inp" type="date" bind:value={abbonamento.al} min={abbonamento.dal} /></label>
          </div>
          <label class="field"><span class="lbl">Importo (€, facoltativo)</span><input class="inp" type="number" min="0" step="0.01" bind:value={abbonamento.importo} /></label>
          <div class="row">
            <button type="button" class="btn btn-secondary grow" onclick={() => (abbonamento = null)}>Annulla</button>
            <button class="btn btn-primary grow" disabled={abbonamento.tipo === 'CORSO' || abbonamento.aOrario ? !abbonamento.corsoId : !numero(abbonamento.ingressi)}>Registra</button>
          </div>
        </form>
      {/if}

    </section>
{/snippet}

{#snippet sPassaggi(cliente: SchedaCliente, accessi: SchedaAccessi)}
    <!-- Passaggi -->
    <section class="card blocco">
      <h3>Ultimi passaggi</h3>
      <ul class="righe">
        {#each accessi.passaggi as x (x.id)}
          <li class="voce-l">
            <span class="punto" class:v={x.verde}></span>
            <span class="m small">{dataBreve(dataDi(x.istante))} {oraDi(x.istante)}</span>
            <span class="grow small">{x.verso === 'USCITA' ? (x.verde ? 'Uscito' : 'Uscita negata: ') : ''}{x.verde ? (x.verso === 'USCITA' ? '' : x.goldenPass ? 'Golden pass' : x.carnetScalato ? 'Entrato · carnet scalato' : 'Entrato') : x.motivi.filter((m) => m.blocca).map((m) => m.messaggio).join(' · ')}{x.segnalato ? ' · segnalato alla segreteria' : ''}</span>
            <span class="m small muted">{x.codice}</span>
          </li>
        {:else}
          <li class="muted small">Nessun passaggio.</li>
        {/each}
      </ul>
    </section>
{/snippet}

<style>
  .testa { display: flex; justify-content: space-between; align-items: flex-end; gap: var(--space-12); }
  .nome { margin: 0; font-size: 28px; font-weight: 800; line-height: 1.1; }
  /* Riassunto */
  .riassunto { display: flex; flex-direction: column; gap: var(--space-16); padding: var(--space-16) var(--space-20) var(--space-20); }
  .r-testa { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: var(--space-16); }
  .r-chi { display: flex; flex-direction: column; gap: var(--space-4); min-width: 0; flex: 1 1 260px; }
  .r-tag { display: flex; flex-wrap: wrap; gap: var(--space-6); margin-top: var(--space-4); }
  .r-esito { flex: 0 1 340px; min-width: 260px; }
  .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: var(--space-10); }
  .kpi { display: flex; flex-direction: column; gap: var(--space-2); padding: var(--space-10) var(--space-14); border-radius: var(--radius-md); background: var(--carta); min-width: 0; }
  .kpi .val { font-size: var(--text-2xl); overflow-wrap: anywhere; }
  .kpi-nota { font-size: var(--text-xs); color: var(--muted); }
  .kpi.warn { background: var(--warn-bg); }
  .kpi.warn .val, .kpi.warn .kpi-nota, .kpi.warn .lbl { color: var(--warn); }
  .r-azioni { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-8); }
  .menu-wrap { position: relative; }
  @media (max-width: 600px) { .r-azioni > .btn { flex: 1 1 100%; } .r-esito { flex-basis: 100%; min-width: 0; } }
  .menu-azioni { position: absolute; right: 0; top: calc(100% + var(--space-6)); z-index: 10; width: 270px; padding: var(--space-6); display: flex; flex-direction: column;
    background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); box-shadow: 0 6px 20px rgba(16, 33, 43, 0.14); }
  .menu-azioni button { display: flex; align-items: center; gap: var(--space-10); min-height: 44px; padding: 0 var(--space-10); border: none; background: none;
    border-radius: var(--radius-sm); font: inherit; font-size: var(--text-md); color: var(--ink); cursor: pointer; text-align: left; }
  .menu-azioni button:hover { background: var(--carta); }
  .menu-azioni button :global(.ic) { width: 18px; height: 18px; color: var(--muted); }
  .menu-azioni button.danger, .menu-azioni button.danger :global(.ic) { color: var(--danger); }
  .menu-azioni .sep { height: 1px; background: var(--line-soft); margin: var(--space-4) var(--space-6); }

  /* Schede */
  .schede { display: flex; gap: var(--space-4); border-bottom: 1px solid var(--line); overflow-x: auto; margin-bottom: calc(-1 * var(--space-4)); }
  .scheda-tab { position: relative; display: inline-flex; align-items: center; gap: var(--space-8); min-height: 46px; padding: 0 var(--space-14); border: none; background: none;
    color: var(--muted); font: inherit; font-size: var(--text-md); font-weight: 600; cursor: pointer; white-space: nowrap; border-radius: var(--radius-sm) var(--radius-sm) 0 0; }
  .scheda-tab:hover { color: var(--ink); background: var(--surface-sunken); }
  .scheda-tab[aria-selected='true'] { color: var(--cloro-ink); }
  .scheda-tab[aria-selected='true']::after { content: ''; position: absolute; left: var(--space-10); right: var(--space-10); bottom: 0; height: 3px; border-radius: 3px 3px 0 0; background: var(--cloro); }
  .scheda-tab:focus-visible { outline-offset: -2px; }
  .conta { min-width: 20px; height: 20px; padding: 0 var(--space-6); border-radius: var(--radius-pill); background: var(--surface-sunken); color: var(--ink);
    font-size: var(--text-2xs); font-weight: 700; display: inline-flex; align-items: center; justify-content: center; }
  .conta.warn { background: var(--warn-bg); color: var(--warn); }
  .pannello { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 440px), 1fr)); gap: var(--space-14); align-items: start; }
  .largo-g { grid-column: 1 / -1; }
  .testa-card { display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); min-height: 40px; margin: calc(-1 * var(--space-6)) 0; }
  .blocco { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  h3 { margin: 0; font-size: var(--text-xl); }
  .righe { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .righe > li { padding: var(--space-8) 0; border-top: 1px solid var(--line-soft); }
  .righe > li:first-child { border-top: none; }
  .riga-b { display: flex; flex-direction: column; gap: var(--space-6); }
  .riga-testa { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
  .codice { font-size: var(--text-lg); }
  .spento { opacity: 0.6; }
  .bannato { background: var(--ferma-bg); color: var(--ferma-fg); }
  .azioni { display: flex; gap: var(--space-4); flex-wrap: wrap; }
  .rosso { color: var(--danger); }
  .aggiungi { display: flex; flex-direction: column; gap: var(--space-6); padding-top: var(--space-6); border-top: 1px solid var(--line); }
  .voce-l { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
  .ban { display: flex; align-items: center; justify-content: space-between; gap: var(--space-10); }
  .oro { display: flex; align-items: center; justify-content: space-between; gap: var(--space-10); padding: var(--space-8) var(--space-12);
    border-radius: var(--radius-md); background: var(--oro-bg); color: var(--oro-fg); border: 1px solid var(--oro); }
  .oro-t { background: var(--oro-bg); color: var(--oro-fg); margin-right: var(--space-4); }
  .sosp { display: flex; align-items: center; gap: var(--space-8); color: var(--warn); margin-top: 2px; }
  .sospendi { display: flex; flex-wrap: wrap; gap: var(--space-6); align-items: center; margin-top: var(--space-6); }
  .sospendi .inp { width: auto; min-width: 140px; }
  .dati { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: var(--space-4) var(--space-14); margin: 0; }
  .dati dt { color: var(--muted); font-size: var(--text-sm); align-self: center; }
  .dati dd { margin: 0; overflow-wrap: anywhere; }
  .a-capo-t { white-space: pre-line; }
  .sx { align-self: flex-start; }
  .tess { display: flex; flex-direction: column; gap: var(--space-4); }
  .riga-info { display: block; margin-top: 2px; }
  .riconsegna { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-10); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .spunta { display: flex; align-items: center; gap: var(--space-6); }
  .termina { display: flex; flex-direction: column; gap: var(--space-8); margin-top: var(--space-6); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--surface-sunken); }
  .recesso { display: flex; align-items: center; gap: var(--space-6); color: var(--cloro-ink); font-weight: 600; }
  .gruppi { display: flex; flex-wrap: wrap; gap: var(--space-6); align-items: center; }
  .tag.gruppo { display: inline-flex; flex-direction: row; align-items: center; gap: var(--space-4); padding-right: var(--space-4); }
  .togli { display: inline-flex; align-items: center; justify-content: center; width: auto; height: 20px; min-height: 0; border: none; background: none;
    color: inherit; cursor: pointer; font: inherit; padding: 0 4px; min-width: 20px; border-radius: var(--radius-pill); }
  .togli:hover { background: var(--surface-sunken); }
  .togli :global(.ic) { width: 14px; height: 14px; stroke-width: 2.2; }
  .scegli-gruppo { width: auto; min-height: 30px; height: 30px; padding: 0 var(--space-8); appearance: auto; font-size: var(--text-sm); }
  .tre { display: grid; grid-template-columns: 110px minmax(0, 1fr) 80px; gap: var(--space-10); }
  @media (max-width: 560px) { .due.stretto { grid-template-columns: 1fr; } }
  .note { min-height: 70px; resize: vertical; font-family: var(--font-body); }
  .modulo { display: flex; flex-direction: column; gap: var(--space-10); }
  select.inp { appearance: auto; }
  .sotto { display: flex; flex-direction: column; gap: var(--space-4); padding-top: var(--space-10); border-top: 1px solid var(--line); }
  .punto { width: 10px; height: 10px; border-radius: 50%; background: var(--ferma); flex-shrink: 0; }
  .punto.v { background: var(--passa); }
</style>
