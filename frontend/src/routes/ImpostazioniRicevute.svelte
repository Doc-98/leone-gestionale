<script lang="ts">
  import BarraSalva from '../components/BarraSalva.svelte';
  import ElencoModificabile from '../components/ElencoModificabile.svelte';
  import ErroreCampo from '../components/ErroreCampo.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import Icona from '../components/Icona.svelte';
  import RiquadroAcceso from '../components/RiquadroAcceso.svelte';
  import { api, ErroreApi, messaggioErrore, type ImpostazioniRicevute, type ModificaSerieRicevute, type PaginaRicevute, type RiparteSerie, type SerieRicevute, type TipoSerie } from '../lib/api';
  import { creaCoda } from '../lib/coda';
  import { impila } from '../lib/impila';
  import { euro, euroColonna, importoNonValido, numeroDa, perCampo } from '../lib/numeri';
  import { anteprimaNumero, FORMATI_PRONTI, predefinitiSerie, riepilogoSerie, RIPARTE, serieDiBase, serieRichiesta } from '../lib/ricevute';
  import { contaModifiche } from '../lib/salvataggio';
  import { cifreNonValide as cifreKo, MSG_CIFRE } from '../lib/validita';
  import { sessione } from '../lib/sessione.svelte';

  /**
   * Impostazioni delle ricevute (ADR 0018, parte 10). Le serie sono un elenco modificabile: ognuna si corregge nel suo
   * foglio, con il suo «Salva la serie»; interruttore, riordino e cestino agiscono subito. Ciò che riguarda tutta la pagina
   * (cosa c'è scritto, come si consegna) si salva dalla barra in basso.
   */
  type Bozza = Omit<ImpostazioniRicevute, 'bolloSoglia' | 'bolloImporto'> & { bolloSoglia: string; bolloImporto: string };
  let pagina = $state<PaginaRicevute | null>(null);
  let serie = $state<SerieRicevute[]>([]);
  let bozza = $state<Bozza | null>(null);
  let originale = $state('');
  let errore = $state('');
  let messaggio = $state('');
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  async function carica() {
    try {
      pagina = await api.impostazioniRicevute();
      serie = pagina.serie;
      const i = pagina.impostazioni;
      bozza = { ...i, bolloSoglia: perCampo(i.bolloSoglia), bolloImporto: perCampo(i.bolloImporto) };
      originale = JSON.stringify(bozza);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  /** Dopo un errore o una creazione: rilegge solo le serie, le scelte della barra non salvate restano. */
  async function caricaSerie() {
    const p = await api.impostazioniRicevute();
    serie = p.serie;
    if (pagina) pagina = { ...pagina, mancaIntestazione: p.mancaIntestazione };
  }

  const erroreBollo = $derived.by(() => {
    if (!bozza) return '';
    const { bollo, bolloSoglia, bolloImporto } = bozza;
    if (importoNonValido(bolloSoglia) || importoNonValido(bolloImporto) || (bollo && (numeroDa(bolloSoglia) == null || numeroDa(bolloImporto) == null)))
      return 'Scrivi la soglia e l\'importo della marca in euro, es. 77,47 e 2';
    return '';
  });
  const modifiche = $derived(bozza ? contaModifiche(JSON.parse(originale), bozza) : 0);

  async function salva() {
    if (!bozza) return;
    errore = '';
    messaggio = '';
    try {
      await api.modificaImpostazioniRicevute({ ...bozza, bolloSoglia: numeroDa(bozza.bolloSoglia) ?? 0, bolloImporto: numeroDa(bozza.bolloImporto) ?? 0 });
      await carica();
      messaggio = 'Salvato: vale dalla prossima ricevuta.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  // ── le serie ──
  const anno = new Date().getFullYear();
  /** La stagione per l'anteprima: quella del periodo della serie, se la serie riparte a ogni stagione. */
  const stagione = $derived(serie.find((s) => s.riparte === 'STAGIONE')?.periodo ?? `${anno}/${String(anno + 1).slice(2)}`);
  type Campi = { formato: string; cifre: number | string | null; riparte: RiparteSerie; prossimoNumero: number | null };
  /** Il numero della prossima ricevuta: quello del server se la serie non è stata toccata, altrimenti come uscirebbe. */
  function anteprima(c: Campi, base?: SerieRicevute): string {
    const emesse = base?.emesse ?? 0;
    if (base && emesse > 0 && c.formato === base.formato && Number(c.cifre) === base.cifre && c.riparte === base.riparte) return base.prossima;
    const cifre = cifreKo(c.cifre) ? 4 : Number(c.cifre);
    return anteprimaNumero(c.formato, cifre, stagione, anno, (emesse > 0 ? null : c.prossimoNumero) ?? emesse + 1);
  }

  const inCoda = creaCoda();
  const tocchi: Record<string, number> = {};
  /** La fonte è il server (`richiesta`); senza, il conteggio locale delle serie accese di ricevute. */
  const richiesta = (s: SerieRicevute) => s.richiesta ?? serieRichiesta(serie, s.id);
  /** La serie «di base»: la prima di ricevute accesa dell'elenco. Cambia con l'ordine e con gli interruttori. */
  const diBase = $derived(serieDiBase(serie));
  const sostituisci = (nuova: SerieRicevute) => (serie = serie.map((x) => (x.id === nuova.id ? nuova : x)));
  /** Il corpo di una modifica: tutti i campi della serie com'è adesso, con i cambi. */
  const corpo = (s: SerieRicevute, cambi: Partial<ModificaSerieRicevute> = {}): ModificaSerieRicevute =>
    ({ nome: s.nome, attiva: s.attiva, formato: s.formato, cifre: s.cifre, riparte: s.riparte, prossimoNumero: s.prossimoNumero, ...cambi });

  /** Acceso o spento: dalla riga e dal foglio, subito. Dopo un errore si rileggono le serie. */
  async function accendi(s: SerieRicevute, attiva: boolean) {
    if (!attiva && richiesta(s)) return;
    errore = '';
    messaggio = '';
    erroreFoglio = '';
    const n = (tocchi[s.id] = (tocchi[s.id] ?? 0) + 1);
    sostituisci({ ...s, attiva });
    try {
      const risposta = await inCoda(() => api.modificaSerieRicevute(s.id, corpo(s, { attiva })));
      if (tocchi[s.id] === n) {
        sostituisci(risposta);
        await caricaSerie(); // accendere una serie cambia quale è «richiesta»
      }
    } catch (e) {
      if (tocchi[s.id] !== n) return;
      if (foglio?.id === s.id) erroreFoglio = messaggioErrore(e);
      else errore = messaggioErrore(e);
      await caricaSerie().catch(() => {});
    }
  }

  const eElencoCambiato = (e: unknown) => e instanceof ErroreApi && e.status === 400 && /ricarica/i.test(e.message);

  /** Il nuovo ordine in una chiamata sola; se fallisce rilancia l'errore e l'elenco torna da sé a com'era. */
  async function riordina(ids: string[]) {
    errore = '';
    messaggio = '';
    try {
      const nuove = await inCoda(() => api.ordinaSerieRicevute(ids));
      const per = new Map(serie.map((x) => [x.id, x]));
      // lo stato acceso/spento resta quello dei tocchi (la risposta può essere più vecchia); l'ordine è quello nuovo
      serie = nuove.map((n) => ({ ...n, attiva: per.get(n.id)?.attiva ?? n.attiva }));
    } catch (e) {
      if (eElencoCambiato(e)) {
        await caricaSerie().catch(() => {});
        messaggio = "L'elenco era cambiato: l'ho ricaricato.";
      } else errore = messaggioErrore(e);
      throw e;
    }
  }

  /** Elimina: se il backend dice 409 si ferma e il messaggio a due righe sta sotto la riga (lo mostra l'elenco). */
  async function elimina(s: SerieRicevute): Promise<string | undefined> {
    errore = '';
    messaggio = '';
    try {
      await inCoda(() => api.eliminaSerieRicevute(s.id));
      serie = serie.filter((x) => x.id !== s.id);
      messaggio = `Serie «${s.nome}» eliminata.`;
      await caricaSerie().catch(() => {}); // una serie può essere diventata «richiesta»: perde subito cestino e interruttore
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409) return e.message;
      errore = messaggioErrore(e);
    }
  }

  // ── il foglio: «Nuova serie» e la modifica di una esistente ──
  let foglio = $state<{ id: string | null } | null>(null);
  type BozzaSerie = { nome: string; tipo: TipoSerie; formato: string; cifre: number | string | null; riparte: RiparteSerie; prossimoNumero: number | null };
  let bozzaS = $state<BozzaSerie>({ nome: '', tipo: 'RICEVUTA', ...predefinitiSerie('RICEVUTA'), prossimoNumero: null });
  /** acceso o spento di una serie che non c'è ancora (per una che c'è vale quello dell'elenco, uguale alla riga) */
  let nuovoAcceso = $state(false);
  let erroreFoglio = $state('');
  let inSalvataggio = $state(false);
  const aperta = $derived(foglio?.id ? serie.find((s) => s.id === foglio!.id) ?? null : null);
  const accesoFoglio = $derived(aperta ? aperta.attiva : nuovoAcceso);
  const cifreSbagliate = $derived(cifreKo(bozzaS.cifre));

  // la serie aperta è sparita dall'elenco (eliminata altrove): si chiude il foglio e lo si dice
  $effect(() => {
    if (foglio?.id && !aperta) {
      foglio = null;
      messaggio = 'La serie non c\'è più.';
    }
  });

  function nuovaSerie() {
    bozzaS = { nome: '', tipo: 'RICEVUTA', ...predefinitiSerie('RICEVUTA'), prossimoNumero: null };
    nuovoAcceso = false;
    erroreFoglio = '';
    foglio = { id: null };
  }
  function apri(s: SerieRicevute) {
    bozzaS = { nome: s.nome, tipo: s.tipo, formato: s.formato, cifre: s.cifre, riparte: s.riparte, prossimoNumero: s.prossimoNumero };
    erroreFoglio = '';
    foglio = { id: s.id };
  }
  const chiudi = () => (foglio = null);
  /** Cambiare il tipo di una serie nuova porta con sé il formato e la ripartenza giusti per quel tipo. */
  function scegliTipo(tipo: TipoSerie) {
    bozzaS = { ...bozzaS, tipo, ...predefinitiSerie(tipo) };
  }
  function accendiDalFoglio(v: boolean) {
    if (aperta) accendi(aperta, v);
    else nuovoAcceso = v;
  }

  async function salvaFoglio(e: SubmitEvent) {
    e.preventDefault();
    const nome = bozzaS.nome.trim();
    if (!nome || cifreSbagliate || inSalvataggio) return;
    inSalvataggio = true;
    erroreFoglio = '';
    errore = '';
    const campi = { formato: bozzaS.formato.trim(), cifre: Number(bozzaS.cifre), riparte: bozzaS.riparte, prossimoNumero: bozzaS.prossimoNumero };
    let creata: SerieRicevute | null = null;
    try {
      if (aperta) {
        const base = aperta;
        sostituisci(await inCoda(() => api.modificaSerieRicevute(base.id, corpo(base, { nome, ...campi, prossimoNumero: base.emesse > 0 ? base.prossimoNumero : campi.prossimoNumero }))));
        messaggio = `Serie «${nome}» salvata.`;
      } else {
        // la serie nasce con i valori del server; poi si applica quanto scritto nel foglio
        creata = await inCoda(() => api.creaSerieRicevute(nome, bozzaS.tipo));
        const nuova = creata;
        await inCoda(() => api.modificaSerieRicevute(nuova.id, { nome, attiva: nuovoAcceso, ...campi }));
        await caricaSerie();
        messaggio = `Serie «${nome}» aggiunta${nuovoAcceso ? '' : ', spenta: accendila quando è pronta'}.`;
      }
      chiudi();
    } catch (err) {
      erroreFoglio = messaggioErrore(err);
      // creata ma non ancora a posto: il foglio resta aperto sulla serie vera, così si riprova senza farne una seconda
      if (creata) {
        await caricaSerie().catch(() => {});
        foglio = { id: creata.id };
      }
    } finally {
      inSalvataggio = false;
    }
  }

  /** Il pezzo che dà più peso all'anteprima: la serie aperta nel foglio o, senza, la prima accesa di ricevute. */
  const inAnteprima = $derived(aperta ?? serie.find((s) => s.attiva && s.tipo === 'RICEVUTA') ?? serie[0]);
  const tipoAnteprima = $derived(foglio ? bozzaS.tipo : inAnteprima?.tipo);
  const numeroAnteprima = $derived(foglio ? anteprima(bozzaS, aperta ?? undefined) : inAnteprima ? inAnteprima.prossima : '');

  /** Una spiegazione lunga: in pagina una riga corta, il testo intero nel fumetto «i». */
  type Spiega = { corta: string; tema: string; lunga: string };
  type Scelte = { valore: string; opzioni: { id: string; nome: string }[]; scegli: (v: string) => void };
  const seg = <T extends string>(valore: T, opzioni: { id: T; nome: string }[], scegli: (v: T) => void): Scelte => ({ valore, opzioni, scegli: (v) => scegli(v as T) });
</script>

<div class="pagina">
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if bozza && pagina}
    <div class="colonne">
      <div class="princ">
        <section class="card sez" id="imp-serie" aria-label="Serie e numerazione">
          <div class="sez-t"><h2>Serie e numerazione</h2>
            <Fumetto tema="le serie">Ogni serie ha la sua numerazione. Un numero usato non torna mai libero, nemmeno se la ricevuta si annulla. Una serie che ha già emesso ricevute si spegne ma non si elimina. La prima serie di ricevute accesa dell'elenco è quella «di base»: la usano i nuovi pagamenti, quindi l'ordine conta.</Fumetto>
          </div>
          <div class="elenco">
            <ElencoModificabile
              elementi={serie}
              modificabile={puo}
              nome={(s) => s.nome || 'Senza nome'}
              nomeElemento="serie"
              articolo="una"
              riga={contenuto}
              acceso={(s) => s.attiva}
              haCestino={(s) => !richiesta(s)}
              haInterruttore={(s) => !richiesta(s)}
              onriordina={riordina}
              onaggiungi={nuovaSerie}
              onapri={apri}
              onacceso={accendi}
              onelimina={elimina}
            />
          </div>
        </section>

        <section class="card sez" id="imp-scritto" aria-label="Cosa c'è scritto">
          <div class="sez-t"><h2 class="grow">Cosa c'è scritto</h2><span class="spiega">l'intestazione viene dai dati della società</span></div>
          <div class="riga-imp"><span class="t"><b>Intestazione</b>
            <span class="corta"><span class="t-c">{#if pagina.mancaIntestazione.length}<strong class="manca">Manca: {pagina.mancaIntestazione.join(', ')}.</strong>{:else}Ragione sociale, partita IVA, indirizzo.{/if}</span><Fumetto tema="l'intestazione">Ragione sociale, partita IVA o codice fiscale, indirizzo: vengono dai dati della società.{#if pagina.mancaIntestazione.length}{' '}Manca: {pagina.mancaIntestazione.join(', ')}.{/if}</Fumetto></span></span>
            <span class="ctl"><a class="btn btn-secondary btn-sm" href="#/societa"><Icona nome="freccia" />Dati, stagioni e gruppi</a></span></div>
          {@render scelte('Intestata a', { corta: 'A chi è intestata la ricevuta.', tema: 'a chi è intestata', lunga: 'Per un minore paga quasi sempre un genitore: la ricevuta è sua, con il nome del figlio. Intestate a chi frequenta, sono una per persona.' },
            seg(bozza.intestata, [{ id: 'PAGANTE', nome: 'A chi paga' }, { id: 'BENEFICIARIO', nome: 'A chi frequenta' }], (v) => (bozza!.intestata = v)))}
          <div class="riga-imp"><span class="t"><b>Codici fiscali</b>
            {@render spiega({ corta: 'Per detrazioni e rimborsi welfare.', tema: 'i codici fiscali', lunga: 'Di chi paga e di chi frequenta: servono per le detrazioni e per i rimborsi welfare.' })}</span>
            <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.codiciFiscali} aria-label="Codici fiscali sulla ricevuta" disabled={!puo}
              onclick={() => (bozza!.codiciFiscali = !bozza!.codiciFiscali)}><span></span></button></span></div>
          <div class="riga-imp"><span class="t"><b>Marca da bollo</b>
            {@render spiega({ corta: 'Sopra la soglia, il riquadro per la marca.', tema: 'la marca da bollo', lunga: 'Sopra la soglia la ricevuta ha il riquadro per la marca.' })}</span>
            <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.bollo} aria-label="Marca da bollo" disabled={!puo} onclick={() => (bozza!.bollo = !bozza!.bollo)}><span></span></button>
              <span class="pezzo"><span class="unita">sopra</span><input class="inp m corto" type="text" inputmode="decimal" bind:value={bozza.bolloSoglia} aria-invalid={!!erroreBollo} disabled={!puo || !bozza.bollo} aria-label="Soglia della marca da bollo in euro" /><span class="unita">€</span></span>
              <span class="pezzo"><span class="unita">da</span><input class="inp m corto" type="text" inputmode="decimal" bind:value={bozza.bolloImporto} aria-invalid={!!erroreBollo} disabled={!puo || !bozza.bollo} aria-label="Importo della marca da bollo in euro" /><span class="unita">€</span></span></span>
            {#if erroreBollo}<span class="msg err" role="alert">{erroreBollo}</span>{/if}</div>
          <div class="riga-imp colonna-imp"><span class="t"><b>Dicitura in fondo</b>
            {@render spiega({ corta: 'Il testo per le detrazioni o per l\'ente.', tema: 'la dicitura', lunga: `Il testo per le detrazioni o per l'ente. Con {beneficiario} e {nascita} si inseriscono nome e data di nascita di chi frequenta.` })}</span>
            <textarea class="inp dicitura" rows="3" maxlength="1000" value={bozza.dicitura ?? ''} disabled={!puo} aria-label="Dicitura in fondo alla ricevuta"
              oninput={(e) => (bozza!.dicitura = e.currentTarget.value || null)}></textarea></div>
          <div class="riga-imp"><span class="t"><b>Spazio per la firma</b>
            {@render spiega({ corta: 'Una riga «Per la società» in fondo.', tema: 'lo spazio per la firma', lunga: 'Una riga «Per la società» in fondo, per firmare la copia stampata.' })}</span>
            <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.firma} aria-label="Spazio per la firma" disabled={!puo} onclick={() => (bozza!.firma = !bozza!.firma)}><span></span></button></span></div>
        </section>

        <section class="card sez" id="imp-consegna" aria-label="Come si consegna">
          <div class="sez-t"><h2 class="grow">Come si consegna</h2><span class="spiega">quando si registra un pagamento</span></div>
          {@render scelte('Dopo ogni pagamento', { corta: 'Cosa succede appena si paga.', tema: 'cosa succede dopo il pagamento', lunga: 'Chiedi: compare il pannello con stampa, PDF ed email. Stampa subito: la ricevuta si fa e si apre per la stampa. Niente: si fa quando serve, dal pagamento.' },
            seg(bozza.dopo, [{ id: 'CHIEDI', nome: 'Chiedi' }, { id: 'STAMPA', nome: 'Stampa subito' }, { id: 'NIENTE', nome: 'Niente' }], (v) => (bozza!.dopo = v)))}
          <div class="riga-imp"><span class="t"><b>Manda per email a chi paga</b>
            {@render spiega({ corta: 'In allegato, all\'indirizzo nella scheda.', tema: 'l\'invio per email', lunga: 'Appena fatta, in allegato, all\'indirizzo nella scheda. Senza email si stampa.' })}</span>
            <span class="ctl"><button type="button" class="switch tap44" role="switch" aria-checked={bozza.email} aria-label="Manda per email" disabled={!puo} onclick={() => (bozza!.email = !bozza!.email)}><span></span></button></span></div>
          {@render scelte('Un genitore paga per più figli', { corta: 'Una ricevuta o una per figlio.', tema: 'il pagamento per più figli', lunga: 'Una ricevuta con una riga per figlio, o una per figlio (comodo per le detrazioni di ciascuno).' },
            seg(bozza.famiglia, [{ id: 'CHIEDI', nome: 'Chiedi' }, { id: 'FAMIGLIA', nome: 'Per famiglia' }, { id: 'PERSONA', nome: 'Per figlio' }], (v) => (bozza!.famiglia = v)))}
        </section>
      </div>

      <aside class="lato">
        <section class="card pann">
          <span class="lbl">Anteprima</span>
          <div class="mini-r" aria-label="Anteprima della ricevuta">
            <span class="rs">{pagina.mancaIntestazione.includes('ragione sociale') ? 'Nome della società' : 'Intestazione della società'}</span>
            {#if pagina.mancaIntestazione.length}<span class="manca">Manca: {pagina.mancaIntestazione.join(', ')}</span>{/if}
            <span class="n"><span>{tipoAnteprima === 'FATTURA' ? 'Fattura' : 'Ricevuta'} n.</span><b class="m">{numeroAnteprima}</b></span>
            <span class="riga"><span>Ricevuto da</span><b>{bozza.intestata === 'PAGANTE' ? 'il genitore che paga' : 'chi frequenta'}</b></span>
            {#if bozza.codiciFiscali}<span class="riga"><span>C.F.</span><span>sì</span></span>{/if}
            <span class="riga"><span>Corso di nuoto · 1ª rata</span><span>{euroColonna(320)}</span></span>
            <span class="tot"><span>Totale</span><span>{euroColonna(320)}</span></span>
            {#if bozza.bollo && 320 > (numeroDa(bozza.bolloSoglia) ?? 0)}<span class="bollo">marca da bollo {euro(numeroDa(bozza.bolloImporto) ?? 0)}</span>{/if}
            {#if bozza.dicitura}<span class="dic">{bozza.dicitura}</span>{/if}
            {#if bozza.firma}<span class="firma">Per la società</span>{/if}
          </div>
        </section>
      </aside>
    </div>

    {#if puo}<BarraSalva {modifiche} valida={!erroreBollo} onannulla={() => (bozza = JSON.parse(originale))} onsalva={salva} />{/if}
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

{#snippet contenuto(s: SerieRicevute)}
  <span class="col">
    <span class="nome-r">
      <strong class="nome" class:tenue={!s.attiva}>{s.nome || 'Senza nome'}</strong>
      {#if s.tipo === 'FATTURA'}<span class="tag t-soft">Fatture</span>{/if}
      {#if richiesta(s)}<span class="tag t-soft">Richiesta dal sistema</span>{:else if s.id === diBase}<span class="tag t-soft">Di base</span>{/if}
    </span>
    <span class="sotto">{riepilogoSerie(s)}</span>
  </span>
{/snippet}

{#snippet pillolaSpento()}
  {#if !accesoFoglio}<span class="pill">spento</span>{/if}
{/snippet}

{#if foglio && puo}
  <Foglio titolo={aperta ? aperta.nome || 'Senza nome' : 'Nuova serie'} {chiudi} troncaTitolo dopoTitolo={pillolaSpento}>
    <form class="modulo" onsubmit={salvaFoglio}>
      <RiquadroAcceso acceso={accesoFoglio} onacceso={accendiDalFoglio} nome={aperta?.nome ?? bozzaS.nome.trim()} disabled={!!aperta && richiesta(aperta)} />
      <span class="corta"><span class="t-c muted small">Spenta, non fa ricevute.</span><Fumetto tema="lo stato della serie">Una serie spenta non fa ricevute; quelle già fatte restano.</Fumetto></span>
      {#if aperta && richiesta(aperta)}<p class="muted small nomargin">È l'ultima serie accesa di ricevute: serve al sistema. Per spegnerla accendine prima un'altra.</p>{/if}

      <label class="field"><span class="lbl">Nome</span>
        <input class="inp" bind:value={bozzaS.nome} maxlength="60" autocomplete="off" placeholder="es. Ricevute del campo estivo" />
      </label>
      {#if aperta}
        <p class="nomargin"><span class="tag t-soft">{aperta.tipo === 'FATTURA' ? 'Fatture' : 'Ricevute'}</span></p>
      {:else}
        <label class="field"><span class="lbl">Tipo</span>
          <select class="inp" value={bozzaS.tipo} onchange={(e) => scegliTipo(e.currentTarget.value as TipoSerie)}><option value="RICEVUTA">Ricevute</option><option value="FATTURA">Fatture</option></select>
        </label>
      {/if}

      <div class="field"><span class="lbl-i"><label class="lbl" for="formato-serie">Formato del numero</label>
        <Fumetto tema="il formato del numero">Con {'{stagione}'} o {'{anno}'} il numero dice anche quando è stata fatta; {'{numero}'} non può mancare.</Fumetto></span>
        <span class="chips" role="group" aria-label="Formati pronti">
          {#each FORMATI_PRONTI as f (f)}<button type="button" class="chip-f m" aria-pressed={bozzaS.formato === f} onclick={() => (bozzaS.formato = f)}>{f}</button>{/each}
        </span>
        <input class="inp m" id="formato-serie" bind:value={bozzaS.formato} maxlength="40" />
      </div>

      <div class="field"><span class="lbl-i"><span class="lbl" id="riparte-serie">Il numero riparte da 1</span>
        <Fumetto tema="quando riparte il numero">Di solito a ogni stagione, come il tesseramento.</Fumetto></span>
        <span class="seg" style:--n={RIPARTE.length} use:impila role="group" aria-labelledby="riparte-serie">
          {#each RIPARTE as r (r.id)}<button type="button" aria-pressed={bozzaS.riparte === r.id} onclick={() => (bozzaS.riparte = r.id)}>{r.nome}</button>{/each}
        </span>
      </div>

      <div class="field"><span class="lbl-i"><label class="lbl" for="cifre-serie">Cifre del numero</label>
        <Fumetto tema="le cifre del numero">Con 4 cifre: 0001, 0002…</Fumetto></span>
        <span class="riga-c"><input class="inp m corto" id="cifre-serie" type="number" min="1" max="8" bind:value={bozzaS.cifre} aria-invalid={cifreSbagliate} aria-describedby={cifreSbagliate ? 'err-cifre' : undefined} /><span class="unita">cifre</span></span>
        {#if cifreSbagliate}<ErroreCampo id="err-cifre">{MSG_CIFRE}</ErroreCampo>{/if}
      </div>

      <div class="field"><span class="lbl-i"><label class="lbl" for="primo-serie">Partire da un numero</label>
        <Fumetto tema="il numero di partenza">Per continuare la numerazione del gestionale di prima. Vuoto: da 1. Si sceglie solo finché il periodo non ha ricevute.</Fumetto></span>
        <input class="inp m corto" id="primo-serie" aria-describedby={aperta && aperta.emesse > 0 ? 'motivo-primo' : undefined} type="number" min="1" placeholder="1" value={bozzaS.prossimoNumero ?? ''} disabled={!!aperta && aperta.emesse > 0}
          oninput={(e) => (bozzaS.prossimoNumero = e.currentTarget.value ? Number(e.currentTarget.value) : null)} />
        {#if aperta && aperta.emesse > 0}<span class="muted small" id="motivo-primo">Ci sono già ricevute: la numerazione continua da sé.</span>{/if}
      </div>

      <div class="riga-c"><span class="lbl">La prossima sarà</span><span class="esempio m">{anteprima(bozzaS, aperta ?? undefined)}</span></div>

      {#if erroreFoglio}<p class="msg err nomargin" role="alert">{erroreFoglio}</p>{/if}

      <div class="azioni-foglio">
        <button type="button" class="btn btn-secondary" onclick={chiudi}>Annulla</button>
        <button type="submit" class="btn btn-accent" disabled={!bozzaS.nome.trim() || cifreSbagliate || inSalvataggio}>
          {#if aperta}<Icona nome="salva" />Salva la serie{:else}<Icona nome="piu" />Aggiungi la serie{/if}
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
  .elenco { padding: var(--space-6) var(--space-16) var(--space-10); }
  select.inp { appearance: auto; width: auto; align-self: flex-start; }
  .col { display: flex; flex-direction: column; gap: var(--space-2); min-width: 0; max-width: 100%; }
  .nome-r { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-4) var(--space-8); min-width: 0; }
  .nome { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .nome.tenue { opacity: 0.7; }
  .sotto { font-size: var(--text-sm); color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
  .modulo { display: flex; flex-direction: column; gap: var(--space-14); }
  .lbl-i { display: flex; align-items: center; gap: var(--space-4); }
  .riga-c { display: flex; align-items: center; gap: var(--space-8); }
  .modulo .inp { height: 44px; }
  .azioni-foglio { display: flex; justify-content: flex-end; gap: var(--space-10); flex-wrap: wrap; }
  .grow { flex: 1 1 auto; min-width: 0; }
  /* la riga corta: non va a capo, la «i» sta in fondo e non allarga la riga; a 320 px il testo si accorcia con i puntini */
  .corta { display: flex; align-items: center; gap: var(--space-4); white-space: nowrap; min-width: 0; }
  .t-c { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  .ctl .inp { height: 40px; }
  .corto { width: 96px; }
  /* «sopra [importo] €» e «da [importo] €» vanno a capo come gruppi interi, mai spezzati né tagliati */
  .pezzo { display: inline-flex; align-items: center; gap: var(--space-8); white-space: nowrap; }
  .chips { gap: var(--space-12) var(--space-6); } /* 32 px di chip + 12 di spazio tra le righe: le aree di tocco da 44 non si sovrappongono */ /* più stretto del .chips globale (8px) */
  .chip-f { position: relative; min-height: 32px; padding: 0 var(--space-10); border-radius: var(--radius-pill); border: 1px solid var(--line); background: var(--surface); font-size: var(--text-xs);
    color: var(--seg-fg); cursor: pointer; }
  .chip-f::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 44px; transform: translateY(-50%); }
  .chip-f[aria-pressed='true'] { background: var(--fill); border-color: var(--fill); color: var(--on-fill); }
  /* i segmenti: stessa larghezza per tutte le voci di un selettore, alti 44; se non ci stanno in fila vanno uno sotto l'altro */
  .seg { grid-template-columns: repeat(var(--n), var(--col, 1fr)); }
  .seg button { min-height: 44px; }
  .seg:global([data-impilato]) { grid-template-columns: 1fr; }
  .btn-sm { text-decoration: none; }
  .esempio { font-size: var(--text-base); font-weight: 500; padding: var(--space-6) var(--space-10); border-radius: var(--radius-sm); background: var(--carta); }
  .manca { color: var(--warn); font-weight: 600; }
  .colonna-imp { flex-direction: column; align-items: stretch; }
  /* in colonna la spiegazione non deve allungarsi (fuori colonna ha una base di 240px) */
  .colonna-imp > .t { flex: none; }
  .dicitura { width: 100%; box-sizing: border-box; min-height: 90px; font: inherit; font-size: var(--text-md); line-height: 1.45; padding: var(--space-10) var(--space-12); resize: vertical; }
  .pann { gap: var(--space-10); } /* un po' più stretto del .pann globale (12px) */
  /* la ricevuta in piccolo: carta bianca anche nel tema scuro, come sarà stampata */
  .mini-r { display: flex; flex-direction: column; gap: var(--space-6); padding: var(--space-16); border-radius: var(--radius-md); background: #fff; color: #1d2a30; border: 1px solid var(--line);
    box-shadow: 0 2px 10px rgba(16, 33, 43, 0.08); font-size: var(--text-xs); }
  .mini-r .rs { font-family: var(--font-display); font-size: var(--text-md); font-weight: 800; }
  .mini-r .manca { color: #8a4b00; }
  .mini-r .n { display: flex; justify-content: space-between; align-items: baseline; border-top: 1px solid #e3ddd1; padding-top: var(--space-8); margin-top: var(--space-4); }
  .mini-r .riga, .mini-r .tot { display: flex; justify-content: space-between; gap: var(--space-10); }
  .mini-r .tot { border-top: 1px dashed #cfc8bb; padding-top: var(--space-6); font-weight: 700; }
  .mini-r .bollo { align-self: flex-end; width: 64px; height: 64px; border: 1.5px dashed #b9b1a3; border-radius: 6px; display: grid; place-items: center; text-align: center; color: #5d655f; line-height: 1.2; }
  .mini-r .dic { color: #4a524c; line-height: 1.35; white-space: pre-line; }
  .mini-r .firma { align-self: flex-start; min-width: 140px; border-top: 1px solid #1d2a30; padding-top: 2px; margin-top: var(--space-12); color: #5d655f; }
</style>
