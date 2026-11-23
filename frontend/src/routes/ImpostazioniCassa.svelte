<script lang="ts">
  import BarraSalva from '../components/BarraSalva.svelte';
  import ElencoModificabile from '../components/ElencoModificabile.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import Icona from '../components/Icona.svelte';
  import RiquadroAcceso from '../components/RiquadroAcceso.svelte';
  import { api, ErroreApi, messaggioErrore, type Cassa, type ChiusuraModo, type FondoModo, type ImpostazioniCassa, type MetodoPagamento, type PaginaCassa } from '../lib/api';
  import { CONTROLLI, metodiRiscontrati, spiegaFondo, testoRiscontro } from '../lib/cassa';
  import { creaCoda } from '../lib/coda';
  import { impila } from '../lib/impila';
  import { euro, euroColonna, numeroDa, perCampo } from '../lib/numeri';
  import { contaModifiche } from '../lib/salvataggio';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Impostazioni della cassa (ADR 0018, parte 11): una cassa o una per postazione, come funziona il fondo e quando si
   * chiude. Le casse sono un elenco modificabile: ognuna si corregge nel suo foglio, con il suo «Salva la cassa»;
   * interruttore, riordino e cestino agiscono subito. Le scelte della pagina si salvano dalla barra in basso.
   */
  type Bozza = Omit<ImpostazioniCassa, 'importoFondo' | 'tolleranza' | 'oreAvviso'> & { importoFondo: string; tolleranza: string; oreAvviso: string };
  let pagina = $state<PaginaCassa | null>(null);
  let casse = $state<Cassa[]>([]);
  let metodi = $state<MetodoPagamento[]>([]);
  let bozza = $state<Bozza | null>(null);
  let originale = $state('');
  let errore = $state('');
  let messaggio = $state('');
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  async function carica() {
    try {
      [pagina, metodi] = await Promise.all([api.cassa(), api.metodiPagamento()]);
      casse = pagina.casse;
      const i = pagina.impostazioni;
      bozza = { ...i, importoFondo: perCampo(i.importoFondo), tolleranza: perCampo(i.tolleranza), oreAvviso: String(i.oreAvviso) };
      originale = JSON.stringify(bozza);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  /** Rilegge le casse e il loro stato (chi è principale, quali sono aperte); le scelte della barra non salvate restano. */
  async function caricaCasse() {
    const p = await api.cassa();
    pagina = { ...p, impostazioni: pagina?.impostazioni ?? p.impostazioni };
    casse = p.casse;
  }

  const modifiche = $derived(bozza && originale ? contaModifiche(JSON.parse(originale), bozza) : 0);
  const fondoN = $derived(bozza ? numeroDa(bozza.importoFondo) : null);
  const tolleranzaN = $derived(bozza ? numeroDa(bozza.tolleranza) : null);
  const oreN = $derived(bozza && /^\d{1,2}$/.test(bozza.oreAvviso.trim()) ? Number(bozza.oreAvviso) : null);
  const valida = $derived(fondoN != null && fondoN >= 0 && tolleranzaN != null && tolleranzaN >= 0 && oreN != null && oreN <= 72);
  const aperte = $derived(pagina?.stati.filter((s) => s.aperta).map((s) => s.cassa.nome) ?? []);
  const nelCassetto = $derived(metodi.filter((m) => m.contanti && m.attivo).map((m) => m.nome));
  const riscontrati = $derived(metodiRiscontrati(metodi));

  async function salva() {
    if (!bozza) return;
    errore = '';
    messaggio = '';
    try {
      await api.modificaImpostazioniCassa({ ...bozza, importoFondo: fondoN ?? -1, tolleranza: tolleranzaN ?? -1, oreAvviso: oreN ?? -1 });
      await carica();
      messaggio = 'Salvato: vale dalla prossima apertura.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── le casse ──
  const inCoda = creaCoda();
  const tocchi: Record<string, number> = {};
  const sostituisci = (nuova: Cassa) => (casse = casse.map((x) => (x.id === nuova.id ? { ...x, ...nuova } : x)));

  /** Acceso o spento: dalla riga e dal foglio, subito. La principale non si spegne. Dopo un errore si rileggono le casse. */
  async function accendi(c: Cassa, attiva: boolean) {
    if (c.principale) return;
    errore = '';
    messaggio = '';
    erroreFoglio = '';
    const n = (tocchi[c.id] = (tocchi[c.id] ?? 0) + 1);
    sostituisci({ ...c, attiva });
    try {
      const risposta = await inCoda(() => api.modificaCassa(c.id, { attiva }));
      if (tocchi[c.id] === n) sostituisci(risposta);
    } catch (e) {
      if (tocchi[c.id] !== n) return;
      if (foglio?.id === c.id) erroreFoglio = messaggioErrore(e);
      else errore = messaggioErrore(e);
      await caricaCasse().catch(() => {});
    }
  }

  const eElencoCambiato = (e: unknown) => e instanceof ErroreApi && e.status === 400 && /ricarica/i.test(e.message);

  /** Il nuovo ordine in una chiamata sola; se fallisce rilancia l'errore e l'elenco torna da sé a com'era. */
  async function riordina(ids: string[]) {
    errore = '';
    messaggio = '';
    try {
      const nuove = await inCoda(() => api.ordinaCasse(ids));
      const per = new Map(casse.map((x) => [x.id, x]));
      // lo stato acceso/spento resta quello dei tocchi (la risposta può essere più vecchia); l'ordine è quello nuovo
      casse = nuove.map((n) => ({ ...n, attiva: per.get(n.id)?.attiva ?? n.attiva }));
    } catch (e) {
      if (eElencoCambiato(e)) {
        await caricaCasse().catch(() => {});
        messaggio = "L'elenco era cambiato: l'ho ricaricato.";
      } else errore = messaggioErrore(e);
      throw e;
    }
  }

  /** Elimina: se il backend dice 409 si ferma e il messaggio a due righe sta sotto la riga (lo mostra l'elenco). */
  async function elimina(c: Cassa): Promise<string | undefined> {
    errore = '';
    messaggio = '';
    try {
      await inCoda(() => api.eliminaCassa(c.id));
      casse = casse.filter((x) => x.id !== c.id);
      messaggio = `Cassa «${c.nome}» eliminata.`;
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409) return e.message;
      errore = messaggioErrore(e);
    }
  }

  // ── il foglio: «Nuova cassa» e la modifica di una esistente ──
  let foglio = $state<{ id: string | null } | null>(null);
  let bozzaC = $state<{ nome: string; principale: boolean }>({ nome: '', principale: false });
  /** acceso o spento di una cassa che non c'è ancora (per una che c'è vale quello dell'elenco, uguale alla riga) */
  let nuovoAcceso = $state(true);
  let erroreFoglio = $state('');
  let inSalvataggio = $state(false);
  const aperta = $derived(foglio?.id ? casse.find((c) => c.id === foglio!.id) ?? null : null);
  const accesoFoglio = $derived(aperta ? aperta.attiva : nuovoAcceso);

  // la cassa aperta è sparita dall'elenco (eliminata altrove): si chiude il foglio e lo si dice
  $effect(() => {
    if (foglio?.id && !aperta) {
      foglio = null;
      messaggio = 'La cassa non c\'è più.';
    }
  });

  function nuovaCassa() {
    bozzaC = { nome: '', principale: false };
    nuovoAcceso = true;
    erroreFoglio = '';
    foglio = { id: null };
  }
  function apri(c: Cassa) {
    bozzaC = { nome: c.nome, principale: c.principale };
    erroreFoglio = '';
    foglio = { id: c.id };
  }
  const chiudi = () => (foglio = null);
  /** Una principale non si spegne: sceglierla accende la cassa (e il riquadro «Acceso» resta bloccato finché è scelta). */
  function scegliPrincipale() {
    bozzaC.principale = !bozzaC.principale;
    if (!bozzaC.principale) return;
    if (aperta) {
      if (!aperta.attiva) accendi(aperta, true);
    } else nuovoAcceso = true;
  }
  function accendiDalFoglio(v: boolean) {
    if (aperta) accendi(aperta, v);
    else nuovoAcceso = v;
  }

  async function salvaFoglio(e: SubmitEvent) {
    e.preventDefault();
    const nome = bozzaC.nome.trim();
    if (!nome || inSalvataggio) return;
    inSalvataggio = true;
    erroreFoglio = '';
    errore = '';
    let creata: Cassa | null = null;
    try {
      if (aperta) {
        const base = aperta;
        const rendiPrincipale = bozzaC.principale && !base.principale;
        await inCoda(() => api.modificaCassa(base.id, rendiPrincipale ? { nome, principale: true } : { nome }));
        await caricaCasse();
        messaggio = rendiPrincipale ? `«${nome}» è la principale.` : `Cassa «${nome}» salvata.`;
      } else {
        creata = await inCoda(() => api.creaCassa(nome));
        const nuova = creata;
        // nasce accesa e non principale: gli altri valori scelti nel foglio si applicano subito dopo
        if (bozzaC.principale || !nuovoAcceso) await inCoda(() => api.modificaCassa(nuova.id, { ...(nuovoAcceso ? {} : { attiva: false }), ...(bozzaC.principale ? { principale: true } : {}) }));
        await caricaCasse();
        messaggio = `Cassa «${nome}» aggiunta.`;
      }
      chiudi();
    } catch (err) {
      erroreFoglio = messaggioErrore(err);
      // creata ma non ancora a posto: il foglio resta aperto sulla cassa vera, così si riprova senza farne una seconda
      if (creata) {
        await caricaCasse().catch(() => {});
        foglio = { id: creata.id };
      }
    } finally {
      inSalvataggio = false;
    }
  }

  /** Una spiegazione lunga: in pagina una riga corta, il testo intero nel fumetto «i». */
  type Spiega = { corta: string; tema: string; lunga: string };
  type Scelte = { valore: string; opzioni: { id: string; nome: string }[]; scegli: (v: string) => void };
  const seg = <T extends string>(valore: T, opzioni: { id: T; nome: string }[], scegli: (v: T) => void): Scelte => ({ valore, opzioni, scegli: (v) => scegli(v as T) });
  const FONDI: { id: FondoModo; nome: string }[] = [{ id: 'RIPORTATO', nome: 'Riportato' }, { id: 'FISSO', nome: 'Fisso' }, { id: 'CONTATO', nome: 'Contato' }];
  const CHIUSURE: { id: ChiusuraModo; nome: string }[] = [{ id: 'TURNO', nome: 'A fine turno' }, { id: 'GIORNATA', nome: 'A fine giornata' }];
</script>

<div class="pagina">
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if bozza && pagina}
    <div class="colonne">
      <div class="princ">
        <section class="card sez" id="imp-casse" aria-label="Le casse">
          <div class="sez-t"><h2 class="grow">Le casse</h2><span class="spiega">un cassetto con il suo fondo e la sua chiusura</span></div>
          {@render scelte('Quante casse', { corta: 'Una sola o una per postazione.', tema: 'le casse', lunga: 'Una sola per tutta la segreteria, o una per postazione: ogni computer dice di quale cassa è (si sceglie nella pagina Incassi) e chiude la sua.' },
            seg(bozza.casse, [{ id: 'UNICA', nome: 'Una sola' }, { id: 'PER_POSTAZIONE', nome: 'Una per postazione' }], (v) => (bozza!.casse = v)))}
          {#if aperte.length && bozza.casse !== pagina.impostazioni.casse}
            <p class="hint nomargin avviso-r"><span>Per cambiare prima si chiude: {aperte.length === 1 ? `è aperta «${aperte[0]}»` : `sono aperte ${aperte.map((x) => `«${x}»`).join(', ')}`}.</span></p>
          {/if}
          {#if bozza.casse === 'PER_POSTAZIONE'}
            <div class="elenco">
              <ElencoModificabile
                elementi={casse}
                modificabile={puo}
                nome={(c) => c.nome}
                nomeElemento="cassa"
                articolo="una"
                riga={contenuto}
                acceso={(c) => c.attiva}
                haCestino={(c) => !c.principale}
                haInterruttore={(c) => !c.principale}
                onriordina={riordina}
                onaggiungi={nuovaCassa}
                onapri={apri}
                onacceso={accendi}
                onelimina={elimina}
              />
            </div>
          {/if}
        </section>

        <section class="card sez" id="imp-fondo" aria-label="Il fondo cassa">
          <div class="sez-t"><h2 class="grow">Il fondo cassa</h2><span class="spiega">i contanti per dare il resto</span></div>
          {@render scelte('Da dove si parte', { corta: 'Il fondo con cui si apre la cassa.', tema: 'il fondo', lunga: spiegaFondo({ fondo: bozza.fondo, importoFondo: fondoN ?? 0 }, euro) }, seg(bozza.fondo, FONDI, (v) => (bozza!.fondo = v)))}
          <div class="riga-imp"><span class="t"><b>{bozza.fondo === 'FISSO' ? 'Il fondo' : 'Il fondo di solito'}</b>
            {@render spiega(bozza.fondo === 'FISSO'
              ? { corta: 'Resta nel cassetto alla chiusura.', tema: 'il fondo', lunga: 'Alla chiusura resta nel cassetto, il resto si versa.' }
              : { corta: 'Il primo giorno e alla chiusura.', tema: 'il fondo', lunga: 'Il primo giorno, e quello proposto da lasciare alla chiusura.' })}</span>
            <span class="ctl"><input class="inp m corto" inputmode="decimal" bind:value={bozza.importoFondo} disabled={!puo} aria-label="Importo del fondo" aria-invalid={fondoN == null || fondoN < 0} /><span class="unita">€</span></span>
            {#if fondoN == null || fondoN < 0}<span class="msg err" role="alert">Scrivi il fondo in euro, es. 100 o 100,50</span>{/if}</div>
        </section>

        <section class="card sez" id="imp-chiusura" aria-label="La chiusura">
          <div class="sez-t"><h2 class="grow">La chiusura</h2><span class="spiega">contati, differenza, quanto si lascia</span></div>
          {@render scelte('Quando si chiude', { corta: 'A fine turno o a fine giornata.', tema: 'la chiusura', lunga: bozza.chiusura === 'TURNO'
            ? 'Chi entra in cassa la apre, e la chiude a fine turno: ogni chiusura ha il suo nome. Più chiusure al giorno.'
            : 'Una chiusura a fine giornata, senza apertura (tranne con il fondo da contare). I pagamenti dopo la chiusura vanno nel giorno dopo.' },
            seg(bozza.chiusura, CHIUSURE, (v) => (bozza!.chiusura = v)))}
          <div class="riga-imp"><span class="t"><b>Cosa finisce nel cassetto</b>
            <span class="corta"><span class="t-c">{#if nelCassetto.length}{nelCassetto.join(', ')}{:else}<strong class="manca">Nessun metodo è «nel cassetto».</strong>{/if}</span><Fumetto tema="il cassetto">Contano nei contanti attesi solo i metodi segnati «nel cassetto».{#if !nelCassetto.length}{' '}<strong class="manca">Nessuno lo è.</strong>{/if}</Fumetto></span></span>
            <span class="ctl"><a class="btn btn-secondary btn-sm" href="#/metodi"><Icona nome="freccia" />Scegli i metodi</a></span></div>
        </section>

        <section class="card sez" id="imp-conteggio" aria-label="Il conteggio e il controllo">
          <div class="sez-t"><h2 class="grow">Il conteggio e il controllo</h2><span class="spiega">come si usa nel settore</span></div>
          <div class="riga-imp"><span class="t"><b>Conta alla cieca</b>
            {@render spiega({ corta: 'Chi chiude non vede gli attesi.', tema: 'il conteggio alla cieca', lunga: 'Chi chiude conta senza vedere i contanti attesi; il confronto arriva dopo. È la pratica consigliata: il conto non si «aggiusta» sul numero atteso.' })}</span>
            <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.cieca} aria-label="Conta alla cieca" disabled={!puo} onclick={() => (bozza!.cieca = !bozza!.cieca)}><span></span></button></span></div>
          <div class="riga-imp"><span class="t"><b>Differenza tollerata</b>
            {@render spiega({ corta: 'Fino a qui si chiude e basta.', tema: 'la differenza tollerata', lunga: 'Fino a questa cifra, in più o in meno, si chiude e basta. Oltre: si propone di ricontare, la nota diventa obbligatoria e la chiusura può andare controllata. 0 = ogni differenza.' })}</span>
            <span class="ctl"><input class="inp m corto" inputmode="decimal" bind:value={bozza.tolleranza} disabled={!puo} aria-label="Differenza tollerata" aria-invalid={tolleranzaN == null || tolleranzaN < 0} /><span class="unita">€</span></span>
            {#if tolleranzaN == null || tolleranzaN < 0}<span class="msg err" role="alert">Scrivi la differenza in euro, es. 2 o 0,50 (0 = ogni differenza)</span>{/if}</div>
          {@render scelte('Chiusure da controllare', { corta: 'Le segna chi annulla i pagamenti.', tema: 'il controllo delle chiusure', lunga: 'Chi può annullare i pagamenti le segna come controllate (non chi le ha chiuse); sul riepilogo stampato compare chi e quando.' },
            seg(bozza.controllo, CONTROLLI, (v) => (bozza!.controllo = v)))}
          <div class="riga-imp"><span class="t"><b>Riscontro con il POS</b>
            <span class="corta"><span class="t-c">{testoRiscontro(riscontrati)}</span><Fumetto tema="il riscontro con il POS">Alla chiusura si scrive il totale dello scontrino del POS e si vede se torna con i pagamenti registrati. Si sceglie nel foglio di ogni metodo, in Prezzi e pagamenti › Metodi di pagamento.</Fumetto></span></span>
            <span class="ctl"><a class="btn btn-secondary btn-sm" href="#/metodi"><Icona nome="freccia" />Metodi di pagamento</a></span></div>
        </section>

        <section class="card sez" id="imp-tolti" aria-label="I soldi tolti dal cassetto">
          <div class="sez-t"><h2 class="grow">I soldi tolti dal cassetto</h2><span class="spiega">spese, cassaforte, banca</span></div>
          <div class="riga-imp"><span class="t"><b>Spese pagate con la cassa</b>
            {@render spiega({ corta: 'Spento, le spese stanno fuori cassa.', tema: 'le spese', lunga: 'Spento, il cassetto serve solo per incassi e resto: le piccole spese passano da una cassa a parte (come si consiglia di solito).' })}</span>
            <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.spese} aria-label="Spese pagate con la cassa" disabled={!puo} onclick={() => (bozza!.spese = !bozza!.spese)}><span></span></button></span></div>
          <div class="riga-imp"><span class="t"><b>Conto di cassaforte e banca</b>
            {@render spiega({ corta: 'Dalla cassaforte alla banca.', tema: 'il conto della cassaforte e della banca', lunga: 'Quello versato alle chiusure resta «in cassaforte» finché qualcuno non segna che è stato portato in banca, con il giorno e la distinta. Il conto parte da quando si accende.' })}</span>
            <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.banca} aria-label="Conto della cassaforte e della banca" disabled={!puo} onclick={() => (bozza!.banca = !bozza!.banca)}><span></span></button></span></div>
          <div class="riga-imp"><span class="t"><b>Avvisa se una cassa resta aperta</b>
            {@render spiega({ corta: 'Dopo quante ore dall\'apertura. 0 = mai.', tema: 'l\'avviso di cassa aperta', lunga: 'Dopo queste ore dall\'apertura, un avviso a chi entra in cassa e, sulla voce Incassi, a chi usa la cassa. 0 = mai.' })}</span>
            <span class="ctl"><input class="inp m corto" inputmode="numeric" bind:value={bozza.oreAvviso} disabled={!puo} aria-label="Ore dopo cui avvisare" /><span class="unita">ore</span></span></div>
        </section>
      </div>

      <aside class="lato">
        <section class="card pann">
          <span class="lbl">Come si fanno i conti</span>
          <div class="conto" aria-label="Esempio di chiusura">
            <span class="r"><span>Fondo cassa</span><span class="m">{euroColonna(fondoN ?? 0)}</span></span>
            <span class="r"><span>+ contanti incassati</span><span class="m">{euroColonna(420)}</span></span>
            <span class="r"><span>− contanti restituiti</span><span class="m">{euroColonna(20)}</span></span>
            <span class="r"><span>− versamenti e spese</span><span class="m">{euroColonna(200)}</span></span>
            <span class="r tot"><span>Attesi</span><span class="m">{euroColonna((fondoN ?? 0) + 200)}</span></span>
            <span class="r"><span>Contati</span><span class="m">{euroColonna((fondoN ?? 0) + 198)}</span></span>
            <span class="r meno"><span>Differenza</span><span class="m">−{euroColonna(2)}</span></span>
          </div>
          <div class="cosa">
            {@render spiega({ corta: bozza.cieca ? 'Si conta alla cieca.' : 'Gli attesi si vedono mentre si conta.', tema: 'il conteggio',
              lunga: bozza.cieca ? 'Si conta alla cieca: gli attesi compaiono dopo il conteggio, e resta il primo conteggio se si riconta.' : 'Gli attesi si vedono mentre si conta.' })}
            {@render spiega({ corta: tolleranzaN ? `Fino a ${euro(tolleranzaN)} si chiude e basta.` : 'Ogni differenza chiede la nota.', tema: 'la differenza',
              lunga: `${tolleranzaN ? `Fino a ${euro(tolleranzaN)} di differenza si chiude e basta; oltre serve la nota.` : 'Ogni differenza chiede la nota.'}${bozza.controllo === 'TUTTE' ? ' Ogni chiusura va controllata da un responsabile.' : bozza.controllo === 'OLTRE' ? ' Quelle oltre vanno controllate da un responsabile.' : ''}` })}
            {@render spiega({ corta: 'Quanto si lascia e il riepilogo.', tema: 'la chiusura',
              lunga: 'Alla chiusura si dice quanto si lascia per la volta dopo; il resto è da versare. Il riepilogo si stampa da firmare. L\'ultima chiusura si può riaprire, con il motivo, da chi può annullare i pagamenti.' })}
            {@render spiega({ corta: 'Non sostituisce il registratore telematico.', tema: 'il registratore telematico',
              lunga: 'Il gestionale non sostituisce il registratore telematico: il riepilogo della chiusura è un documento interno.' })}
          </div>
        </section>
      </aside>
    </div>

    {#if puo}<BarraSalva {modifiche} {valida} onannulla={() => (bozza = JSON.parse(originale))} onsalva={salva} />{/if}
  {:else if !errore}
    <p class="muted small">Caricamento…</p>
  {/if}
</div>

{#snippet spiega(s: Spiega)}<span class="corta"><span class="t-c">{s.corta}</span><Fumetto tema={s.tema}>{s.lunga}</Fumetto></span>{/snippet}

{#snippet scelte(titolo: string, spiega_: Spiega, s: Scelte)}
  <div class="riga-imp"><span class="t"><b>{titolo}</b>{@render spiega(spiega_)}</span>
    <span class="ctl"><span class="seg" style:--n={s.opzioni.length} use:impila role="group" aria-label={titolo}>
      {#each s.opzioni as o (o.id)}<button type="button" aria-pressed={s.valore === o.id} disabled={!puo} onclick={() => s.scegli(o.id)}>{o.nome}</button>{/each}
    </span></span></div>
{/snippet}

{#snippet contenuto(c: Cassa)}
  <span class="col">
    <span class="nome-r">
      <strong class="nome" class:tenue={!c.attiva}>{c.nome}</strong>
      {#if c.principale}<span class="tag t-soft">Principale</span>{/if}
    </span>
    {#if c.principale}<span class="sotto">Per i computer che non hanno scelto</span>{/if}
  </span>
{/snippet}

{#snippet pillolaSpento()}
  {#if !accesoFoglio}<span class="pill">spento</span>{/if}
{/snippet}

{#if foglio && puo}
  <Foglio titolo={aperta ? aperta.nome : 'Nuova cassa'} {chiudi} troncaTitolo dopoTitolo={pillolaSpento}>
    <form class="modulo" onsubmit={salvaFoglio}>
      {#if !aperta?.principale}<RiquadroAcceso acceso={accesoFoglio} onacceso={accendiDalFoglio} nome={aperta?.nome ?? bozzaC.nome.trim()} disabled={bozzaC.principale} />{/if}

      <label class="field"><span class="lbl">Nome</span>
        <input class="inp" bind:value={bozzaC.nome} maxlength="40" autocomplete="off" placeholder="es. Segreteria piscina" />
      </label>

      <div class="riga-sw"><span class="lbl-i"><b>Cassa principale</b>
        <Fumetto tema="la cassa principale">{aperta?.principale ? 'È la principale: prende i pagamenti dei computer che non hanno scelto la loro cassa. Per cambiarla, rendi principale un\'altra cassa.' : 'La principale prende i pagamenti dei computer che non hanno scelto la loro cassa. Ce n\'è una sola: scegliendo questa, la principale di ora diventa una cassa come le altre.'}</Fumetto></span>
        <button type="button" class="switch tap44" role="switch" aria-checked={bozzaC.principale} aria-label="Cassa principale" disabled={!!aperta?.principale} onclick={scegliPrincipale}><span></span></button></div>

      {#if erroreFoglio}<p class="msg err nomargin" role="alert">{erroreFoglio}</p>{/if}

      <div class="azioni-foglio">
        <button type="button" class="btn btn-secondary" onclick={chiudi}>Annulla</button>
        <button type="submit" class="btn btn-accent" disabled={!bozzaC.nome.trim() || inSalvataggio}>
          {#if aperta}<Icona nome="salva" />Salva la cassa{:else}<Icona nome="piu" />Aggiungi la cassa{/if}
        </button>
      </div>
    </form>
  </Foglio>
{/if}

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); }
  .colonne { display: flex; flex-wrap: wrap; gap: var(--space-20); align-items: flex-start; }
  .princ { flex: 999 1 600px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-14); }
  .lato { flex: 1 1 300px; min-width: 0; max-width: 380px; position: sticky; top: var(--space-16); }
  /* quando i controlli vanno a capo sotto il testo, 20 px tra le righe: l'area di tocco della «i» e quella dell'interruttore non si sovrappongono */
  .pagina :global(.riga-imp) { row-gap: var(--space-20); }
  .ctl .inp { height: 40px; }
  .corto { width: 110px; text-align: right; }
  /* i segmenti: stessa larghezza per tutte le voci di un selettore, alti 44; se non ci stanno in fila vanno uno sotto l'altro */
  .seg { grid-template-columns: repeat(var(--n), var(--col, 1fr)); }
  .seg button { min-height: 44px; }
  .seg:global([data-impilato]) { grid-template-columns: 1fr; }
  .cosa { display: flex; flex-direction: column; gap: var(--space-8); font-size: var(--text-sm); }
  .btn-sm { text-decoration: none; }
  .manca { color: var(--warn); font-weight: 600; }
  /* la riga corta: non va a capo, la «i» sta in fondo e non allarga la riga */
  .corta { display: flex; align-items: center; gap: var(--space-4); white-space: nowrap; min-width: 0; }
  /* a 320 px il testo si accorcia con i puntini (il testo intero resta nel fumetto), la «i» si vede sempre */
  .t-c { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  .avviso-r { margin: 0 var(--space-16) var(--space-12); }
  .elenco { padding: var(--space-6) var(--space-16) var(--space-10); border-top: 1px solid var(--line-soft); }
  .col { display: flex; flex-direction: column; gap: var(--space-2); min-width: 0; }
  .nome-r { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-4) var(--space-8); min-width: 0; }
  .nome { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .nome.tenue { opacity: 0.7; }
  .sotto { font-size: var(--text-sm); color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-14); }
  .modulo .inp { height: 44px; }
  .lbl-i { display: flex; align-items: center; gap: var(--space-4); font-weight: 600; }
  .riga-sw { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); min-height: 44px; }
  .azioni-foglio { display: flex; justify-content: flex-end; gap: var(--space-10); flex-wrap: wrap; }
  .grow { flex: 1 1 auto; min-width: 0; }
  .pann { gap: var(--space-10); } /* un po' più stretto del .pann globale (12px) */
  .conto { display: flex; flex-direction: column; gap: var(--space-6); padding: var(--space-14); border-radius: var(--radius-md); background: var(--surface-sunken); font-size: var(--text-sm); }
  .conto .r { display: flex; justify-content: space-between; gap: var(--space-10); }
  .conto .tot { border-top: 1px solid var(--line); padding-top: var(--space-6); font-weight: 700; }
  .conto .meno { color: var(--ferma-fg); font-weight: 700; }
</style>
