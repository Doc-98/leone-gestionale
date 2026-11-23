<script lang="ts">
  import { tick } from 'svelte';
  import Icona from '../components/Icona.svelte';
  import Azione from '../components/Azione.svelte';
  import BarraSalva from '../components/BarraSalva.svelte';
  import ElencoModificabile from '../components/ElencoModificabile.svelte';
  import EliminazionePesante from '../components/EliminazionePesante.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import RiquadroAcceso from '../components/RiquadroAcceso.svelte';
  import LineaStagione from '../components/impostazioni/LineaStagione.svelte';
  import { api, ErroreApi, messaggioErrore, type DatiSocieta, type GruppoClienti, type MembroGruppo, type NuovaStagione, type Stagione } from '../lib/api';
  import { righeBlocco } from '../lib/blocco';
  import { creaCoda } from '../lib/coda';
  import { rinumeraPrecedenze } from '../lib/gruppi';
  import { classePillStagione, ordinaStagioni, stagioneIniziale, statoStagione } from '../lib/stagioni';
  import { dataBreve, iso } from '../lib/date';
  import { chiudiNovita, dataIndice, novitaChiusa } from '../lib/impostazioni';
  import { euro, euroColonna, importoNonValido, numeroDa, perCampo } from '../lib/numeri';
  import { quanti } from '../lib/plurali';
  import { contaModifiche } from '../lib/salvataggio';
  import { giorniAnticipoNonValidi, MSG_GIORNI_ANTICIPO } from '../lib/validita';
  import ErroreCampo from '../components/ErroreCampo.svelte';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Dati, stagioni e gruppi (ADR 0018, 0024): chi è la società (compare su ricevute ed email), le sue stagioni con la
   * quota associativa e i gruppi di clienti. Le modifiche ai dati e alla stagione scelta si salvano tutte insieme.
   */
  let dati = $state<DatiSocieta | null>(null);
  let originale = $state('');
  let stagioni = $state<Stagione[]>([]);
  let offerte = $state<Record<string, number>>({});
  let gruppi = $state<GruppoClienti[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  const oggi = iso(new Date());

  type BozzaStagione = { nome: string; dal: string; al: string; quota: string; quotaRimborsabile: boolean };
  let scelta = $state<string | null>(null);
  let bozza = $state<BozzaStagione | null>(null);
  let bozzaOriginale = '';
  let tutte = $state(false);
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));
  /** Perché una stagione non si elimina (il testo a due righe del backend o `motivoBlocco`), sotto la riga di nome e date. */
  let bloccoStagione = $state('');

  const NOVITA = 'impostazioni-2026-11';
  let novita = $state(!novitaChiusa(NOVITA, sessione.utente?.accountId ?? ''));

  async function carica() {
    try {
      let d: DatiSocieta;
      [d, stagioni, gruppi] = await Promise.all([api.societa(), api.stagioni(), api.gruppi()]);
      dati = d;
      originale = JSON.stringify(d);
      if (!scelta || !stagioni.some((s) => s.id === scelta)) scegli(stagioni.find((s) => s.id === stagioneIniziale(stagioni, oggi)) ?? null);
      else scegli(stagioni.find((s) => s.id === scelta) ?? null);
      // quante offerte ha il listino di ogni stagione (se si può vedere)
      if (sessione.puo('IMPOSTAZIONI', 'CLIENTI'))
        for (const s of stagioni) api.listino(s.id).then((l) => (offerte[s.id] = l.offerte.length)).catch(() => {});
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le impostazioni.');
    }
  }
  carica();

  function scegli(s: Stagione | null) {
    scelta = s?.id ?? null;
    bloccoStagione = '';
    bozza = s ? { nome: s.nome, dal: s.dal, al: s.al, quota: s.quota == null ? '' : perCampo(s.quota), quotaRimborsabile: s.quotaRimborsabile } : null;
    bozzaOriginale = JSON.stringify(bozza);
  }

  const datiCambiati = $derived(!!dati && JSON.stringify(dati) !== originale);
  const stagioneCambiata = $derived(!!bozza && JSON.stringify(bozza) !== bozzaOriginale);
  const quotaNonValida = $derived(!!bozza && importoNonValido(bozza.quota));
  const daJson = (s: string): unknown => (s ? JSON.parse(s) : null);
  const giorniNonValidi = $derived(!!dati && giorniAnticipoNonValidi(dati.giorniAnticipo));
  const modifiche = $derived(contaModifiche(daJson(originale), dati) + contaModifiche(daJson(bozzaOriginale), bozza));

  async function salva() {
    errore = '';
    messaggio = '';
    try {
      if (datiCambiati) await api.modificaSocieta({ ...dati!, giorniAnticipo: Number(dati!.giorniAnticipo), giorniRispostaAttesa: Number(dati!.giorniRispostaAttesa) });
      if (stagioneCambiata && bozza) {
        const corpo: NuovaStagione = { nome: bozza.nome, dal: bozza.dal, al: bozza.al, quota: numeroDa(bozza.quota), quotaRimborsabile: bozza.quotaRimborsabile };
        if (scelta === 'nuova') {
          const s = await api.creaStagione(corpo);
          scelta = s.id;
        } else if (scelta) await api.modificaStagione(scelta, corpo);
      }
      await carica();
      messaggio = 'Modifiche salvate.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  function annulla() {
    dati = JSON.parse(originale);
    scegli(stagioni.find((s) => s.id === scelta) ?? stagioni[0] ?? null);
  }

  // ── Stagioni ──────────────────────────────────────────────────────────
  const ordinate = $derived(ordinaStagioni(stagioni));
  /** Si vedono la stagione finita più recente, quella in corso e le future (tutte, a richiesta). */
  const mostrate = $derived.by(() => {
    if (tutte) return ordinate;
    const i = Math.max(0, ordinate.findIndex((s) => s.al >= oggi) - 1);
    return ordinate.slice(i, i + 3);
  });
  const inCorso = $derived(stagioni.find((s) => s.dal <= oggi && oggi <= s.al) ?? null);
  /** Lo stato, la classe della pillola («futura» azzurra) e quella della tessera. */
  const stato = (s: Stagione) => {
    const nome = statoStagione(s, oggi);
    return { nome, cls: classePillStagione(nome), card: nome === 'finita' ? 'finita' : nome === 'in corso' ? 'ora' : '' };
  };
  /** Il giorno da cui si propone il tesseramento della stagione. */
  const tesseramentoDa = (s: Stagione) => dataBreve(dataIndice(s.dal, -Number(dati?.giorniAnticipo ?? 0)));

  const piuUnGiorno = (d: string) => dataIndice(d, 1);
  const piuUnAnno = (d: string) => `${Number(d.slice(0, 4)) + 1}${d.slice(4)}`.replace(/-02-29$/, '-02-28');
  function nomeSuccessivo(n: string): string {
    const m = /^(\d{4})\/(\d{2})$/.exec(n);
    if (m) return `${Number(m[1]) + 1}/${String((Number(m[2]) + 1) % 100).padStart(2, '0')}`;
    return /^\d{4}$/.test(n) ? String(Number(n) + 1) : '';
  }
  const ultima = $derived(ordinate.at(-1));
  const nomeNuova = $derived(ultima ? nomeSuccessivo(ultima.nome) : '');
  function nuovaStagione() {
    scelta = 'nuova';
    if (ultima) bozza = { nome: nomeNuova, dal: piuUnGiorno(ultima.al), al: piuUnAnno(ultima.al), quota: ultima.quota == null ? '' : perCampo(ultima.quota), quotaRimborsabile: ultima.quotaRimborsabile };
    else {
      const a = new Date().getMonth() >= 8 ? new Date().getFullYear() : new Date().getFullYear() - 1;
      bozza = { nome: `${a}/${String((a + 1) % 100).padStart(2, '0')}`, dal: `${a}-09-01`, al: `${a + 1}-08-31`, quota: '', quotaRimborsabile: false };
    }
    bozzaOriginale = '';
  }
  /**
   * Il cestino della stagione (secondo tocco). Se il server ha già detto che non si può (`eliminabile === false`) si mostra
   * il motivo senza chiamarlo; altrimenti `DELETE`, e un 409 si mostra a due righe sotto la riga.
   */
  async function eliminaStagione() {
    const s = stagioni.find((x) => x.id === scelta);
    if (!s) return;
    errore = '';
    messaggio = '';
    bloccoStagione = '';
    if (s.eliminabile === false) {
      bloccoStagione = s.motivoBlocco || 'Non si può eliminare: la stagione è già stata usata.';
      return;
    }
    try {
      await api.eliminaStagione(s.id);
      scelta = null;
      await carica();
      messaggio = `Stagione ${s.nome} eliminata.`;
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409) bloccoStagione = e.message;
      else errore = messaggioErrore(e);
    }
  }

  // ── Gruppi ────────────────────────────────────────────────────────────
  // L'ordine dell'elenco (maniglia) è anche l'ordine delle precedenze. Interruttore, riordino e cestino agiscono subito;
  // il foglio di un gruppo si salva da sé («Salva il gruppo»), senza la barra.
  const inCoda = creaCoda();
  type BozzaGruppo = { nome: string; descrizione: string; conPrecedenza: boolean; giorni: string; dichiarabile: boolean; frase: string };
  const bozzaVuota = (): BozzaGruppo => ({ nome: '', descrizione: '', conPrecedenza: false, giorni: '', dichiarabile: false, frase: '' });
  const bozzaDi = (g: GruppoClienti): BozzaGruppo => ({
    nome: g.nome, descrizione: g.descrizione ?? '', conPrecedenza: g.precedenza != null,
    giorni: g.giorniPerPagare == null ? '' : String(g.giorniPerPagare), dichiarabile: g.dichiarabile, frase: g.fraseDichiarazione ?? '',
  });
  let foglio = $state<{ id: string | null } | null>(null);
  let bozzaG = $state<BozzaGruppo>(bozzaVuota());
  /** acceso o spento di un gruppo che non c'è ancora (per uno che c'è vale quello dell'elenco) */
  let nuovoAcceso = $state(true);
  let erroreFoglio = $state('');
  let inSalvataggio = $state(false);
  const aperta = $derived(foglio?.id ? gruppi.find((g) => g.id === foglio!.id) ?? null : null);
  const accesoFoglio = $derived(aperta ? aperta.attivo : nuovoAcceso);
  const frasiMancante = $derived(bozzaG.dichiarabile && !bozzaG.frase.trim());
  let mostraPersone = $state(false);
  let membri = $state<MembroGruppo[]>([]);
  /** La finestra dell'eliminazione pesante, con le conseguenze del server. */
  let pesante = $state<{ gruppo: GruppoClienti; conseguenze: string[] } | null>(null);

  const ricaricaGruppi = async () => (gruppi = await api.gruppi());

  // il gruppo aperto è sparito dall'elenco (eliminato altrove): si chiude il foglio e lo si dice
  $effect(() => {
    if (foglio?.id && !aperta) {
      foglio = null;
      messaggio = 'Il gruppo non c\'è più.';
    }
  });

  function nuovo() {
    bozzaG = bozzaVuota();
    nuovoAcceso = true;
    erroreFoglio = '';
    mostraPersone = false;
    membri = [];
    foglio = { id: null };
  }
  function apri(g: GruppoClienti) {
    bozzaG = bozzaDi(g);
    erroreFoglio = '';
    mostraPersone = false;
    membri = [];
    foglio = { id: g.id };
  }
  const chiudi = () => (foglio = null);

  /** Il nuovo ordine in una chiamata sola, con i tag delle precedenze già aggiornati; se fallisce l'elenco torna com'era. */
  async function riordina(ids: string[]) {
    errore = '';
    messaggio = '';
    const prima = gruppi;
    const per = new Map(gruppi.map((g) => [g.id, g]));
    gruppi = rinumeraPrecedenze(ids.map((id) => per.get(id)).filter((g): g is GruppoClienti => !!g));
    try {
      gruppi = await inCoda(() => api.ordinaGruppi(ids));
    } catch (e) {
      gruppi = prima;
      errore = messaggioErrore(e);
      throw e;
    }
  }

  /** Acceso o spento: dalla riga e dal foglio, subito, sui dati salvati (senza i campi dei vantaggi, che restano com'erano). */
  async function accendi(g: GruppoClienti, attivo: boolean) {
    errore = '';
    messaggio = '';
    erroreFoglio = '';
    gruppi = gruppi.map((x) => (x.id === g.id ? { ...x, attivo } : x));
    try {
      await inCoda(() => api.modificaGruppo(g.id, { nome: g.nome, descrizione: g.descrizione, attivo }));
    } catch (e) {
      if (foglio?.id === g.id) erroreFoglio = messaggioErrore(e);
      else errore = messaggioErrore(e);
      await ricaricaGruppi().catch(() => {});
    }
  }
  function accendiDalFoglio(v: boolean) {
    if (aperta) accendi(aperta, v);
    else nuovoAcceso = v;
  }

  /** Il cestino di una riga: due tocchi se non ci sono conseguenze, altrimenti la finestra (`cestinoApre`). Restituisce il motivo se non si può. */
  async function elimina(g: GruppoClienti): Promise<string | undefined> {
    errore = '';
    messaggio = '';
    try {
      if (g.eliminazionePesante) {
        const e = await api.eliminazioneGruppo(g.id);
        if (e.bloccata) return e.motivoBlocco ?? 'Non si può eliminare.';
        if (e.pesante) {
          pesante = { gruppo: g, conseguenze: e.conseguenze };
          return;
        }
        // nel frattempo il gruppo si è svuotato: non si elimina con un tocco solo; ora il cestino torna a due tocchi
        await ricaricaGruppi().catch(() => {});
        return 'Ora è vuoto: tocca di nuovo per eliminarlo.';
      } else if (g.eliminabile === false) return g.motivoBlocco ?? 'Non si può eliminare.';
      await api.eliminaGruppo(g.id);
      gruppi = gruppi.filter((x) => x.id !== g.id);
      messaggio = `Gruppo «${g.nome}» eliminato.`;
      await ricaricaGruppi().catch(() => {});
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409) {
        await ricaricaGruppi().catch(() => {}); // nel frattempo ha persone: ora il cestino apre la finestra
        return e.message;
      }
      errore = messaggioErrore(e);
    }
  }
  /** «Elimina il gruppo» dalla finestra: se fallisce la finestra resta aperta e mostra il messaggio. */
  async function eliminaPesante() {
    const { gruppo: g } = pesante!;
    const ids = gruppi.map((x) => x.id);
    const i = ids.indexOf(g.id);
    const vicino = ids[i + 1] ?? ids[i - 1];
    await api.eliminaGruppo(g.id, true);
    pesante = null;
    messaggio = `Gruppo «${g.nome}» eliminato.`;
    await ricaricaGruppi().catch(() => {});
    await tick();
    // il fuoco passa alla riga dopo (o prima, o a «Aggiungi un gruppo…»), come dopo ogni eliminazione dell'elenco
    const riga = vicino ? [...document.querySelectorAll<HTMLElement>('#imp-gruppi li[data-riga]')].find((li) => li.dataset.id === vicino) : null;
    (riga?.querySelector<HTMLElement>('button.maniglia, button.apri, button') ?? document.querySelector<HTMLElement>('#imp-gruppi .aggiungi'))?.focus();
  }

  async function salvaFoglio(e: SubmitEvent) {
    e.preventDefault();
    const nome = bozzaG.nome.trim();
    if (!nome || frasiMancante || inSalvataggio) return;
    inSalvataggio = true;
    erroreFoglio = '';
    errore = '';
    const descrizione = bozzaG.descrizione.trim() || null;
    const vantaggi = {
      conPrecedenza: bozzaG.conPrecedenza, giorniPerPagare: bozzaG.giorni.trim() ? Number(bozzaG.giorni) : null,
      dichiarabile: bozzaG.dichiarabile, fraseDichiarazione: bozzaG.dichiarabile ? bozzaG.frase.trim() || null : null,
    };
    try {
      if (aperta) {
        await api.modificaGruppo(aperta.id, { nome, descrizione, attivo: aperta.attivo, ...vantaggi });
        messaggio = `Gruppo «${nome}» salvato.`;
      } else {
        await api.creaGruppo({ nome, descrizione, attivo: nuovoAcceso, ...vantaggi });
        messaggio = `Gruppo «${nome}» aggiunto.`;
      }
      await ricaricaGruppi();
      chiudi();
    } catch (err) {
      erroreFoglio = messaggioErrore(err);
    } finally {
      inSalvataggio = false;
    }
  }

  async function alternaPersone() {
    if (!aperta) return;
    mostraPersone = !mostraPersone;
    if (!mostraPersone || membri.length) return;
    try {
      membri = await api.membriGruppo(aperta.id);
    } catch (e) {
      erroreFoglio = messaggioErrore(e);
    }
  }

  // ── Ricevuta ──────────────────────────────────────────────────────────
  const luogo = $derived(dati ? [[dati.cap, dati.citta].filter(Boolean).join(' '), dati.provincia ? `(${dati.provincia})` : ''].filter(Boolean).join(' ') : '');
</script>

{#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
{#if messaggio && !modifiche}<p class="msg ok" role="status">{messaggio}</p>{/if}

{#if novita}
  <div class="novita" role="status">
    <span class="t"><b>Novità dell'ultimo aggiornamento</b>
      <span class="corta"><span class="t-c">Impostazioni riordinate.</span>
        <Fumetto tema="le novità">Le impostazioni sono state riordinate. «Lista d'attesa» ora sta nei Promemoria email, «una famiglia paga per più persone» nei Metodi di pagamento, e Account e ruoli sono insieme. La ricerca in alto trova tutto da qualsiasi pagina.</Fumetto></span></span>
    <Azione icona="chiudi" etichetta="Chiudi l'avviso" onclick={() => { chiudiNovita(NOVITA, sessione.utente?.accountId ?? ''); novita = false; }} />
  </div>
{/if}

{#if dati}
  <section class="card sez" id="imp-stagioni" aria-label="Stagioni">
    <div class="sez-t"><h2>Stagioni</h2>
      <Fumetto tema="le stagioni">Le date e la quota associativa le decide la società; due stagioni non si accavallano. Una stagione già usata, con vendite dal listino, tesseramenti o esiti dei livelli, non si elimina.</Fumetto>
      <span class="grow"></span>
      {#if ordinate.length > 3}<button class="btn btn-sm btn-quieto" type="button" aria-expanded={tutte} onclick={() => (tutte = !tutte)}><Icona nome={tutte ? 'su' : 'giu'} />{tutte ? 'Solo le ultime' : 'Mostra tutte'}</button>{/if}</div>
    <div class="stagioni">
      {#each mostrate as s (s.id)}
        {@const st = stato(s)}
        <button type="button" class="stag {st.card}" aria-pressed={scelta === s.id} onclick={() => scegli(s)}>
          <span class="testa-s"><b class="n">{s.nome}</b><span class="grow"></span><span class={st.cls}>{st.nome}</span></span>
          <span class="date">{dataBreve(s.dal)} – {dataBreve(s.al)}</span>
          <ul>
            <li><span class="k">Quota associativa</span><b>{s.quota == null ? 'da decidere' : euro(s.quota)}</b></li>
            <li><span class="k">Tesseramento da</span><span>{tesseramentoDa(s)}</span></li>
            {#if s.id in offerte}<li><span class="k">Listino</span><span class:manca={!offerte[s.id]}>{offerte[s.id] ? quanti(offerte[s.id], 'offerta', 'offerte') : 'da preparare'}</span></li>{/if}
          </ul>
        </button>
      {/each}
      <button type="button" class="stag nuova" aria-pressed={scelta === 'nuova'} onclick={nuovaStagione}><Icona nome="piu" />{nomeNuova ? `Nuova stagione ${nomeNuova}` : 'Nuova stagione'}</button>
    </div>
    {#if inCorso}<div class="linea"><LineaStagione dal={inCorso.dal} al={inCorso.al} /></div>{/if}
    {#if bozza}
      <div class="riga-imp sopra">
        <span class="t"><b>{scelta === 'nuova' ? 'Nuova stagione' : `Stagione ${bozza.nome}`}</b>
          {@render spiega('Nome e date.', 'nome e date', 'Nome e date di inizio e fine. Due stagioni non si accavallano.')}</span>
        <span class="ctl">
          <input class="inp nome-s" bind:value={bozza.nome} maxlength="20" aria-label="Nome della stagione" />
          <input class="inp m data" type="date" bind:value={bozza.dal} aria-label="Dal" /><span class="unita">→</span><input class="inp m data" type="date" bind:value={bozza.al} min={bozza.dal} aria-label="Al" />
          {#if scelta !== 'nuova' && puo}<Azione icona="cestino" tono="danger" conferma="Elimina?" etichetta={`Elimina la stagione ${bozza.nome}`} onclick={eliminaStagione} />{/if}
        </span>
        {#if bloccoStagione}{@render blocco(bloccoStagione)}{/if}
      </div>
      <div class="riga-imp">
        <span class="t"><b>Quota associativa</b>
          {@render spiega('Il tesseramento annuale.', 'la quota associativa', 'Il tesseramento annuale; senza, il tornello lo segnala (se il controllo è acceso).')}</span>
        <span class="ctl"><input class="inp num" type="text" inputmode="decimal" bind:value={bozza.quota} placeholder="—" aria-label="Quota associativa" aria-invalid={quotaNonValida} /><span class="unita">€</span>
          <label class="riga-switch corto"><span>rimborsabile</span><button type="button" class="switch tap44" role="switch" aria-checked={bozza.quotaRimborsabile} aria-label="Quota rimborsabile"
            onclick={() => (bozza!.quotaRimborsabile = !bozza!.quotaRimborsabile)}><span></span></button></label></span>
        {#if quotaNonValida}<span class="msg err" role="alert">Scrivi la quota in euro, es. 35 o 35,50</span>{/if}
      </div>
      <div class="riga-imp">
        <span class="t"><b>Proponi il tesseramento della stagione nuova</b>
          {@render spiega('Da quando compare «da rinnovare».', 'la proposta del tesseramento', 'Al banco e nella scheda compare «da rinnovare» da questo giorno. Vale per tutte le stagioni.')}</span>
        <span class="ctl"><input class="inp num" type="number" min="0" max="365" bind:value={dati.giorniAnticipo} aria-label="Giorni prima" aria-invalid={giorniNonValidi} aria-describedby={giorniNonValidi ? 'err-giorni' : undefined} /><span class="unita">giorni prima del suo inizio</span></span>
        {#if giorniNonValidi}<ErroreCampo id="err-giorni">{MSG_GIORNI_ANTICIPO}</ErroreCampo>{/if}
      </div>
    {/if}
  </section>

  <div class="colonne-p">
    <div class="princ">
      <section class="card sez" id="imp-dati" aria-label="La società">
        <div class="sez-t"><h2>La società</h2><Fumetto tema="i dati della società">Il nome compare nelle email; il resto sulle ricevute.</Fumetto></div>
        <div class="dati">
          <label class="field c3"><span class="lbl">Nome</span><input class="inp" bind:value={dati.nome} required /></label>
          <label class="field c3"><span class="lbl">Ragione sociale</span><input class="inp" bind:value={dati.ragioneSociale} placeholder="Es. Società Sportiva S.S.D. a r.l." /></label>
          <label class="field c3"><span class="lbl">Partita IVA</span><input class="inp m" bind:value={dati.partitaIva} inputmode="numeric" maxlength="13" placeholder="da completare" /></label>
          <label class="field c3"><span class="lbl">Codice fiscale</span><input class="inp m" bind:value={dati.codiceFiscale} maxlength="16" placeholder="da completare" /></label>
          <label class="field c6"><span class="lbl">Indirizzo</span><input class="inp" bind:value={dati.indirizzo} placeholder="da completare" /></label>
          <label class="field c1"><span class="lbl">CAP</span><input class="inp m" bind:value={dati.cap} maxlength="10" /></label>
          <label class="field c4"><span class="lbl">Città</span><input class="inp" bind:value={dati.citta} /></label>
          <label class="field c1"><span class="lbl">Prov.</span><input class="inp m" bind:value={dati.provincia} maxlength="2" /></label>
          <label class="field c3"><span class="lbl">Email</span><input class="inp" type="email" bind:value={dati.email} /></label>
          <label class="field c3"><span class="lbl">Telefono</span><input class="inp m" type="tel" bind:value={dati.telefono} placeholder="da completare" /></label>
        </div>
      </section>

      <section class="card sez" id="imp-gruppi" aria-label="Gruppi di clienti">
        <div class="sez-t"><h2>Gruppi di clienti</h2>
          <Fumetto tema="i gruppi di clienti">Etichette per prezzi diversi e precedenza nei rinnovi; si danno dalla scheda del cliente. L'ordine dell'elenco è anche l'ordine delle precedenze: trascina un gruppo per cambiarlo.</Fumetto></div>
        <div class="elenco">
          <ElencoModificabile
            elementi={gruppi}
            modificabile={puo}
            nome={(g) => g.nome}
            nomeElemento="gruppo"
            riga={contenutoGruppo}
            sotto={tagGruppo}
            acceso={(g) => g.attivo}
            cestinoApre={(g) => !!g.eliminazionePesante}
            onriordina={riordina}
            onaggiungi={nuovo}
            onapri={apri}
            onacceso={accendi}
            onelimina={elimina}
          />
        </div>
      </section>
    </div>

    <aside class="lato-p">
      <section class="card pann">
        <span class="lbl">Come compare su una ricevuta</span>
        <div class="ricevuta">
          <span class="rs">{dati.ragioneSociale || dati.nome}</span>
          {#if dati.partitaIva || dati.codiceFiscale}<span>{dati.partitaIva ? `P. IVA ${dati.partitaIva}` : `C.F. ${dati.codiceFiscale}`}</span>{:else}<span class="mancante">P. IVA da completare</span>{/if}
          {#if dati.indirizzo}<span>{dati.indirizzo}</span>{:else}<span class="mancante">Indirizzo da completare</span>{/if}
          <span>{[luogo, dati.email, dati.telefono].filter(Boolean).join(' · ')}</span>
          <span class="riga-r"><span>Ricevuta n. 128 · {dataBreve(oggi)}</span><b class="m">{euroColonna(520)}</b></span>
        </div>
        {@render spiega('Servono partita IVA e indirizzo.', 'i dati per le ricevute', 'Per le ricevute servono partita IVA (o codice fiscale) e indirizzo.')}
      </section>
    </aside>
  </div>

  <BarraSalva {modifiche} valida={!quotaNonValida && !giorniNonValidi} onannulla={annulla} onsalva={salva} />
{:else if !errore}
  <p class="muted">Caricamento…</p>
{/if}

{#snippet spiega(corta: string, tema: string, lunga: string)}
  <span class="corta"><span class="t-c">{corta}</span><Fumetto {tema}>{lunga}</Fumetto></span>
{/snippet}

{#snippet blocco(testo: string)}
  <p class="blocco" role="alert">{#each righeBlocco(testo) as r, k (k)}<span class="riga-b">{#if r.attacco}<b>{r.attacco}</b>{' '}{/if}{r.resto}{' '}</span>{/each}</p>
{/snippet}

{#snippet contenutoGruppo(g: GruppoClienti)}
  <span class="col">
    <strong class="nome" class:tenue={!g.attivo}>{g.nome}</strong>
  </span>
{/snippet}

{#snippet tagGruppo(g: GruppoClienti)}
  <span class="col">
    <span class="tags">
      {#if g.precedenza != null}<span class="tag t-soft">{g.precedenza}ª precedenza</span>{/if}
      {#if g.daVerificare}<span class="tag t-warn">{g.daVerificare} da verificare</span>{/if}
      <span class="tag t-soft">{quanti(g.persone, 'persona', 'persone')}</span>
    </span>
  </span>
{/snippet}

{#snippet pillolaSpento()}
  {#if !accesoFoglio}<span class="pill">spento</span>{/if}
{/snippet}

{#if foglio && puo}
  <Foglio titolo={aperta ? aperta.nome : 'Nuovo gruppo'} {chiudi} troncaTitolo dopoTitolo={pillolaSpento}>
    <form class="modulo" onsubmit={salvaFoglio}>
      <RiquadroAcceso acceso={accesoFoglio} onacceso={accendiDalFoglio} nome={aperta?.nome ?? bozzaG.nome.trim()} />
      {#if !accesoFoglio}<span class="corta"><span class="t-c muted small">Spento, non si aggiungono persone.</span><Fumetto tema="lo stato del gruppo">Un gruppo spento resta sulle persone che ci sono, ma non se ne aggiungono altre.</Fumetto></span>{/if}

      <label class="field"><span class="lbl">Nome</span><input class="inp" bind:value={bozzaG.nome} maxlength="80" autocomplete="off" /></label>
      <label class="field"><span class="lbl">Descrizione</span><input class="inp" bind:value={bozzaG.descrizione} maxlength="300" /></label>

      <div class="riga-sw">
        <span class="lbl-i"><span id="lbl-prec">Precedenza nelle graduatorie</span>
          <Fumetto tema="la precedenza">L'ordine è quello dell'elenco: per cambiarlo trascina il gruppo nell'elenco.</Fumetto></span>
        <button type="button" class="switch tap44" role="switch" aria-checked={bozzaG.conPrecedenza} aria-labelledby="lbl-prec" onclick={() => (bozzaG.conPrecedenza = !bozzaG.conPrecedenza)}><span></span></button>
      </div>

      <div class="riga-sw">
        <span class="lbl-i"><span id="lbl-dich">Le famiglie lo possono dichiarare</span>
          <Fumetto tema="la dichiarazione">Lo segnano nella richiesta; vale solo dopo la verifica della segreteria.</Fumetto></span>
        <button type="button" class="switch tap44" role="switch" aria-checked={bozzaG.dichiarabile} aria-labelledby="lbl-dich" onclick={() => (bozzaG.dichiarabile = !bozzaG.dichiarabile)}><span></span></button>
      </div>
      {#if bozzaG.dichiarabile}
        <label class="field"><span class="lbl">Cosa leggono le famiglie</span><input class="inp" bind:value={bozzaG.frase} maxlength="160" placeholder="es. Frequenta la scuola dell'Istituto" /></label>
      {/if}

      <label class="field"><span class="lbl">Giorni per pagare il posto offerto</span>
        <span class="riga-c"><input class="inp m giorni-c" type="number" min="1" max="60" bind:value={bozzaG.giorni} placeholder="di base" /><span class="unita">giorni</span></span></label>

      {#if aperta && aperta.persone > 0}
        <div class="persone">
          <button type="button" class="btn btn-sm btn-quieto sx" aria-expanded={mostraPersone} onclick={alternaPersone}><Icona nome={mostraPersone ? 'su' : 'giu'} />{mostraPersone ? 'Nascondi' : 'Mostra'} {aperta.persone === 1 ? 'la persona' : `le ${aperta.persone} persone`}</button>
          {#if mostraPersone}
            <div class="membri small">
              {#each membri as m (m.appartenenzaId)}<a href={`#/clienti/${m.personaId}`}>{m.cognome} {m.nome}</a>{:else}<span class="muted">Carico…</span>{/each}
            </div>
          {/if}
        </div>
      {/if}

      {#if erroreFoglio}<p class="msg err nomargin" role="alert">{erroreFoglio}</p>{/if}

      <div class="azioni-foglio">
        <button type="button" class="btn btn-secondary" onclick={chiudi}>Annulla</button>
        <button type="submit" class="btn btn-accent" disabled={!bozzaG.nome.trim() || frasiMancante || inSalvataggio}>
          {#if aperta}<Icona nome="salva" />Salva il gruppo{:else}<Icona nome="piu" />Aggiungi il gruppo{/if}
        </button>
      </div>
    </form>
  </Foglio>
{/if}

{#if pesante}
  <EliminazionePesante
    titolo={`Eliminare il gruppo «${pesante.gruppo.nome}»?`}
    conseguenze={pesante.conseguenze}
    azione="Elimina il gruppo"
    onconferma={eliminaPesante}
    onannulla={() => (pesante = null)}
  />
{/if}

<style>
  .stagioni { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--space-10); padding: var(--space-16) var(--space-16) var(--space-6); }
  .stag { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-14); border-radius: var(--radius-md); border: 1px solid var(--line); background: var(--surface);
    font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .testa-s { display: flex; align-items: center; gap: var(--space-8); }
  .stag b.n { font-family: var(--font-display); font-size: 22px; font-weight: 800; line-height: 1; }
  .stag .date { font-size: var(--text-xs); color: var(--muted); font-family: var(--font-mono); }
  .stag ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; font-size: var(--text-sm); }
  .stag li { display: flex; gap: var(--space-6); }
  .stag .k { color: var(--muted); flex: 1; }
  .stag.ora { border: 2px solid var(--cloro); background: var(--cloro-soft); }
  .stag.finita { background: var(--carta); color: var(--muted); }
  .stag[aria-pressed='true'] { box-shadow: 0 0 0 3px color-mix(in srgb, var(--cloro) 35%, transparent); }
  .stag.nuova { border: 1.5px dashed var(--line-dashed); background: none; align-items: center; justify-content: center; flex-direction: row; color: var(--cloro); font-weight: 700; min-height: 100px; }
  .stag.nuova :global(.ic) { width: 18px; height: 18px; }
  .manca { color: var(--warn); font-weight: 600; }
  .linea { padding: var(--space-6) var(--space-16) var(--space-14); }
  .sopra { border-top: 1px solid var(--line) !important; }
  .nome-s { width: 110px; height: 40px; }
  .data { width: 160px; height: 40px; }
  .corto { min-height: 40px; gap: var(--space-8); font-size: var(--text-md); }
  .dati { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: var(--space-12) var(--space-14); padding: var(--space-16); }
  .c1 { grid-column: span 1; } .c3 { grid-column: span 3; } .c4 { grid-column: span 4; } .c6 { grid-column: span 6; }
  /* la riga di un gruppo: nome e, sotto, i tag; il nome non va a capo */
  .elenco { padding: var(--space-6) var(--space-16) var(--space-10); }
  @media (max-width: 639px) { .elenco { padding-inline: var(--space-10); } }
  .col { display: flex; flex-direction: column; gap: var(--space-4); min-width: 0; max-width: 100%; }
  .nome { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .nome.tenue { opacity: 0.7; }
  /* sul telefono stretto un nome lungo può usare due righe; i puntini restano l'ultima difesa */
  @media (max-width: 479px) { .nome { white-space: normal; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow-wrap: anywhere; } }
  .tags { display: flex; flex-wrap: wrap; gap: var(--space-4); min-width: 0; }
  .tags .tag { color: inherit; }
  .tags .t-soft { color: var(--cloro-ink); }
  .tags .t-warn { color: var(--warn); }
  .modulo { display: flex; flex-direction: column; gap: var(--space-14); }
  .modulo .inp { height: 44px; }
  .lbl-i { display: flex; align-items: center; gap: var(--space-4); font-weight: 500; white-space: nowrap; }
  /* l'etichetta non va a capo: se non ci sta con l'interruttore, è questo a scendere sotto */
  .riga-sw { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-4) var(--space-12); min-height: 44px; }
  .riga-c { display: flex; align-items: center; gap: var(--space-8); }
  .giorni-c { width: 120px; }
  .persone { display: flex; flex-direction: column; gap: var(--space-8); }
  .membri { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-12); }
  .membri a { overflow-wrap: anywhere; }
  .azioni-foglio { display: flex; justify-content: flex-end; gap: var(--space-10); flex-wrap: wrap; }
  .grow { flex: 1 1 auto; min-width: 0; }
  /* la riga corta: non va a capo, la «i» sta in fondo e non allarga la riga; a 320 px il testo si accorcia con i puntini */
  .corta { display: flex; align-items: center; gap: var(--space-4); white-space: nowrap; min-width: 0; }
  .t-c { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  /* il motivo per cui una stagione non si elimina: sotto la riga, a tutta larghezza, due righe */
  .blocco { flex: 1 1 100%; margin: 0; padding: var(--space-8) var(--space-12); border-radius: var(--radius-md); background: var(--ferma-bg); color: var(--ferma-fg); font-size: var(--text-sm); display: flex; flex-direction: column; gap: var(--space-4); }
  .blocco b { color: inherit; font-weight: 700; }
  .ricevuta { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-6); background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md);
    font-size: var(--text-sm); box-shadow: 0 1px 2px rgba(16, 33, 43, 0.06); }
  .ricevuta .rs { font-family: var(--font-display); font-size: 17px; font-weight: 800; }
  .colonne-p > .lato-p { min-width: 0; }
  .riga-r { display: flex; justify-content: space-between; flex-wrap: wrap; gap: var(--space-4) var(--space-8); border-top: 1px dashed var(--line); padding-top: var(--space-6); margin-top: var(--space-4); }
  .mancante { color: var(--warn); font-weight: 600; }
  @media (max-width: 700px) {
    .dati { grid-template-columns: 1fr 1fr; }
    .c1, .c3, .c4, .c6 { grid-column: span 2; }
  }
</style>
