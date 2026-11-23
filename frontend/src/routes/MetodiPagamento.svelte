<script lang="ts">
  import { api, ErroreApi, messaggioErrore, type DatiSocieta, type MetodoPagamento, type PagamentiFamiglia } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';
  import { tipoMetodo, comeIncassa, type TipoMetodo } from '../lib/pagamenti';
  import { contaModifiche } from '../lib/salvataggio';
  import ElencoModificabile from '../components/ElencoModificabile.svelte';
  import RiquadroAcceso from '../components/RiquadroAcceso.svelte';
  import Foglio from '../components/Foglio.svelte';
  import Fumetto from '../components/Fumetto.svelte';
  import BarraSalva from '../components/BarraSalva.svelte';
  import Icona from '../components/Icona.svelte';

  /**
   * I metodi di pagamento della società: si riordinano, si accendono e spengono, si aggiungono, si correggono nel foglio
   * e si eliminano (lo storico ne conserva una copia; finché qualcosa è aperto, il backend risponde 409 col motivo).
   */
  let metodi = $state<MetodoPagamento[]>([]);
  let errore = $state('');
  let messaggio = $state('');
  const puo = $derived(sessione.puo('IMPOSTAZIONI'));

  async function carica() {
    try {
      metodi = await api.metodiPagamento();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }
  carica();

  // come paga una famiglia (sta nei dati della società; si cambia con il permesso «Impostazioni della società»)
  let societa = $state<DatiSocieta | null>(null);
  /** La scelta nel campo: si salva con la barra, come le altre impostazioni di pagina. */
  let sceltaFamiglia = $state<PagamentiFamiglia>('A_SCELTA');
  api.societa().then((d) => { societa = d; sceltaFamiglia = d.pagamentiFamiglia; }).catch(() => {});
  const modificheFamiglia = $derived(societa ? contaModifiche(societa.pagamentiFamiglia, sceltaFamiglia) : 0);
  async function salvaFamiglia() {
    errore = '';
    messaggio = '';
    try {
      societa = await api.modificaSocieta({ ...societa!, pagamentiFamiglia: sceltaFamiglia });
      sceltaFamiglia = societa.pagamentiFamiglia;
      messaggio = 'Salvato: vale dal prossimo pagamento.';
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  /** Il corpo di una modifica: senza `ordine`, che il backend tiene com'è (un riordino in corso non va annullato). */
  const corpo = (m: MetodoPagamento, cambi: { nome?: string; daIncassare?: boolean; contanti?: boolean; attivo?: boolean; riscontro?: boolean }) =>
    ({ nome: m.nome, daIncassare: m.daIncassare, contanti: m.contanti, attivo: m.attivo, ...cambi });
  const sostituisci = (nuovo: MetodoPagamento) => (metodi = metodi.map((x) => (x.id === nuovo.id ? nuovo : x)));

  // Le chiamate che cambiano i metodi partono una alla volta, nell'ordine dei tocchi: le risposte non si scavalcano e il
  // server finisce come l'ultimo tocco. Una risposta superata da un tocco più recente sullo stesso metodo non si applica.
  let coda: Promise<unknown> = Promise.resolve();
  const inCoda = <R,>(f: () => Promise<R>): Promise<R> => {
    const p = coda.then(f);
    coda = p.catch(() => {});
    return p;
  };
  const tocchi: Record<string, number> = {};

  /** Acceso o spento: dalla riga e dal foglio, subito (si vede prima della risposta). Dopo un errore si rilegge l'elenco. */
  async function accendi(m: MetodoPagamento, attivo: boolean) {
    errore = '';
    messaggio = '';
    erroreFoglio = '';
    const n = (tocchi[m.id] = (tocchi[m.id] ?? 0) + 1);
    sostituisci({ ...m, attivo });
    try {
      const risposta = await inCoda(() => api.modificaMetodoPagamento(m.id, corpo(m, { attivo })));
      if (tocchi[m.id] === n) sostituisci(risposta);
    } catch (e) {
      if (tocchi[m.id] !== n) return;
      // il foglio aperto su questo metodo nasconde il messaggio sopra la card: lo dice lui
      if (foglio?.id === m.id) erroreFoglio = messaggioErrore(e);
      else errore = messaggioErrore(e);
      await carica();
    }
  }

  const eElencoCambiato = (e: unknown) => e instanceof ErroreApi && e.status === 400 && /ricarica/i.test(e.message);

  /** Il nuovo ordine in una chiamata sola; se fallisce rilancia l'errore e l'elenco torna da sé a com'era. */
  async function riordina(ids: string[]) {
    errore = '';
    messaggio = '';
    try {
      const nuovi = await inCoda(() => api.ordinaMetodiPagamento(ids));
      const per = new Map(metodi.map((x) => [x.id, x]));
      // lo stato acceso/spento resta quello dei tocchi (la risposta può essere più vecchia); l'ordine è quello nuovo
      if (Array.isArray(nuovi)) metodi = nuovi.map((n) => ({ ...n, attivo: per.get(n.id)?.attivo ?? n.attivo }));
      else metodi = ids.map((id) => per.get(id)).filter((x): x is MetodoPagamento => !!x);
    } catch (e) {
      if (eElencoCambiato(e)) {
        await carica();
        messaggio = "L'elenco era cambiato: l'ho ricaricato.";
      } else errore = messaggioErrore(e);
      throw e;
    }
  }

  /** Elimina: se il backend dice 409 si ferma e il messaggio sta sotto la riga (lo mostra l'elenco). */
  async function elimina(m: MetodoPagamento): Promise<string | undefined> {
    errore = '';
    messaggio = '';
    try {
      await inCoda(() => api.eliminaMetodoPagamento(m.id));
      metodi = metodi.filter((x) => x.id !== m.id);
      messaggio = `${m.nome} eliminato.`;
    } catch (e) {
      if (e instanceof ErroreApi && e.status === 409) return e.message;
      errore = messaggioErrore(e);
    }
  }

  // ── il foglio: «Nuovo metodo» e la modifica di uno esistente ──
  let foglio = $state<{ id: string | null } | null>(null);
  let bozza = $state<{ nome: string; tipo: TipoMetodo; riscontro: boolean }>({ nome: '', tipo: 'subito', riscontro: false });
  /** acceso o spento di un metodo che non c'è ancora (per uno che c'è vale quello dell'elenco, uguale alla riga) */
  let nuovoAcceso = $state(true);
  let erroreFoglio = $state('');
  let inSalvataggio = $state(false);
  const aperto = $derived(foglio?.id ? metodi.find((m) => m.id === foglio!.id) ?? null : null);
  const accesoFoglio = $derived(aperto ? aperto.attivo : nuovoAcceso);

  // il metodo aperto è sparito dall'elenco (eliminato altrove): si chiude il foglio e lo si dice
  $effect(() => {
    if (foglio?.id && !aperto) {
      foglio = null;
      messaggio = 'Il metodo non c\'è più.';
    }
  });

  function nuovoMetodo() {
    bozza = { nome: '', tipo: 'subito', riscontro: false };
    nuovoAcceso = true;
    erroreFoglio = '';
    foglio = { id: null };
  }
  function apri(m: MetodoPagamento) {
    bozza = { nome: m.nome, tipo: tipoMetodo(m), riscontro: m.riscontro };
    erroreFoglio = '';
    foglio = { id: m.id };
  }
  const chiudi = () => (foglio = null);

  function accendiDalFoglio(v: boolean) {
    if (aperto) accendi(aperto, v);
    else nuovoAcceso = v;
  }

  async function salvaFoglio(e: SubmitEvent) {
    e.preventDefault();
    const nome = bozza.nome.trim();
    if (!nome || inSalvataggio) return;
    inSalvataggio = true;
    erroreFoglio = '';
    errore = '';
    try {
      // il riscontro con il POS vale solo per «Incassato subito»: per gli altri tipi si azzera (lo fa anche il backend)
      const come = { ...comeIncassa(bozza.tipo), riscontro: bozza.tipo === 'subito' && bozza.riscontro };
      if (aperto) {
        sostituisci(await inCoda(() => api.modificaMetodoPagamento(aperto.id, corpo(aperto, { nome, ...come }))));
        messaggio = `${nome} salvato.`;
      } else {
        await inCoda(() => api.creaMetodoPagamento({ nome, ...come, attivo: nuovoAcceso }));
        await carica();
        messaggio = `Metodo «${nome}» aggiunto.`;
      }
      chiudi();
    } catch (err) {
      erroreFoglio = messaggioErrore(err);
    } finally {
      inSalvataggio = false;
    }
  }

  const SCELTE: { tipo: TipoMetodo; testo: string }[] = [
    { tipo: 'subito', testo: 'Incassato subito' },
    { tipo: 'cassetto', testo: 'Nel cassetto' },
    { tipo: 'daIncassare', testo: 'Da incassare' },
  ];
</script>

<div class="pagina">
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  <section class="card sez" aria-label="Metodi di pagamento">
    <div class="sez-t"><h2>Metodi</h2>
      <Fumetto tema="i metodi di pagamento">Compaiono in cassa in quest'ordine. «Da incassare» aspetta di arrivare sul conto, «Nel cassetto» conta nei contanti della cassa. Se spento non si propone, ma resta nello storico.</Fumetto>
    </div>
    <div class="elenco">
      <ElencoModificabile
        elementi={metodi}
        modificabile={puo}
        nome={(m) => m.nome}
        nomeElemento="metodo"
        riga={contenuto}
        acceso={(m) => m.attivo}
        haCestino={(m) => !m.borsellino}
        onriordina={riordina}
        onaggiungi={nuovoMetodo}
        onapri={apri}
        onacceso={accendi}
        onelimina={elimina}
      />
    </div>
  </section>

  {#if societa}
    <section class="card sez" id="imp-famiglia" aria-label="Pagamenti di famiglia">
      <div class="sez-t"><h2>Pagamenti di famiglia</h2>
        <Fumetto tema="i pagamenti di famiglia">Quando qualcuno paga insieme per più persone della stessa famiglia, per esempio due fratelli: un pagamento unico per la famiglia, uno per persona, o si sceglie ogni volta al banco. Vale dal prossimo pagamento, dopo il salvataggio.</Fumetto></div>
      <div class="riga-imp"><span class="t"><b>Per più familiari insieme</b><span>Uno solo, uno a testa o si sceglie.</span></span>
        <span class="ctl"><select class="inp" bind:value={sceltaFamiglia} disabled={!puo} aria-label="Come paga una famiglia">
          <option value="A_SCELTA">Si sceglie ogni volta</option>
          <option value="INSIEME">Sempre un pagamento unico</option>
          <option value="PER_PERSONA">Sempre uno per persona</option>
        </select></span></div>
    </section>
  {/if}

  {#if puo}<BarraSalva modifiche={modificheFamiglia} onannulla={() => (sceltaFamiglia = societa!.pagamentiFamiglia)} onsalva={salvaFamiglia} />{/if}
</div>

{#snippet contenuto(m: MetodoPagamento)}
  <strong class="nome" class:tenue={!m.attivo}>{m.nome}</strong>
  {#if m.daIncassare}<span class="tag t-warn">Da incassare</span>{/if}
  {#if m.contanti}<span class="tag t-soft">Nel cassetto</span>{/if}
  {#if m.riscontro}<span class="tag t-soft">Riscontro POS</span>{/if}
  {#if m.borsellino}<span class="tag t-soft">Credito in euro</span>{/if}
{/snippet}

{#snippet pillolaSpento()}
  {#if !accesoFoglio}<span class="pill">spento</span>{/if}
{/snippet}

{#if foglio && puo}
  <Foglio titolo={aperto ? aperto.nome : 'Nuovo metodo'} {chiudi} troncaTitolo dopoTitolo={pillolaSpento}>
    <form class="modulo" onsubmit={salvaFoglio}>
      <RiquadroAcceso acceso={accesoFoglio} onacceso={accendiDalFoglio} nome={aperto?.nome ?? bozza.nome.trim()} />

      {#if aperto?.borsellino}
        <p class="nomargin"><span class="tag t-soft">Credito in euro</span></p>
      {:else}
        <label class="field"><span class="lbl">Nome</span>
          <input class="inp" bind:value={bozza.nome} maxlength="60" autocomplete="off" placeholder="es. buono welfare" />
        </label>
        <div class="field"><span class="lbl" id="come-incassa">Come si incassa</span>
          <div class="seg tre" role="group" aria-labelledby="come-incassa">
            {#each SCELTE as s (s.tipo)}
              <button type="button" aria-pressed={bozza.tipo === s.tipo} onclick={() => (bozza.tipo = s.tipo)}>{s.testo}</button>
            {/each}
          </div>
        </div>
        {#if bozza.tipo === 'subito'}
          <label class="riga-switch riscontro">
            <span class="t"><b>Riscontro con il POS</b><span class="muted small">Si confronta alla chiusura.</span></span>
            <button type="button" class="switch tap44" role="switch" aria-checked={bozza.riscontro} aria-label="Riscontro con il POS" onclick={() => (bozza.riscontro = !bozza.riscontro)}><span></span></button>
          </label>
        {/if}
      {/if}

      {#if erroreFoglio}<p class="msg err nomargin" role="alert">{erroreFoglio}</p>{/if}

      {#if !aperto?.borsellino}
        <div class="azioni-foglio">
          <button type="button" class="btn btn-secondary" onclick={chiudi}>Annulla</button>
          <button type="submit" class="btn btn-accent" disabled={!bozza.nome.trim() || inSalvataggio}>
            {#if aperto}<Icona nome="salva" />Salva{:else}<Icona nome="piu" />Aggiungi il metodo{/if}
          </button>
        </div>
      {/if}
    </form>
  </Foglio>
{/if}

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-14); max-width: 760px; }
  .elenco { padding: var(--space-6) var(--space-16) var(--space-10); }
  .modulo { display: flex; flex-direction: column; gap: var(--space-14); }
  .nome { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .nome.tenue { opacity: 0.7; }
  /* le tre scelte stanno in fila solo se il FOGLIO le contiene intere, senza troncare né andare a capo; altrimenti una sotto l'altra */
  .modulo { container-type: inline-size; }
  .seg.tre { grid-template-columns: repeat(3, auto); }
  .seg.tre button { min-height: 44px; }
  /* telefoni stretti: meno margine ai lati perché i nomi di tutti i giorni stiano interi */
  @media (max-width: 360px) { .elenco { padding-inline: var(--space-8); } }
  @container (max-width: 379px) { .seg.tre { grid-template-columns: 1fr; } }
  .riscontro .t { display: flex; flex-direction: column; gap: var(--space-2); min-width: 0; }
  .azioni-foglio { display: flex; justify-content: flex-end; gap: var(--space-10); flex-wrap: wrap; }
</style>
