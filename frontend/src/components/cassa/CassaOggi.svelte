<script lang="ts">
  import Icona from '../Icona.svelte';
  import ContaTagli from './ContaTagli.svelte';
  import { untrack } from 'svelte';
  import {
    api, cassaDelComputer, ErroreApi, messaggioErrore, scegliCassaDelComputer, type EsitoConfronto, type PaginaCassa, type SessioneCassa, type TipoMovimentoCassa,
  } from '../../lib/api';
  import { conSegno, esitoDifferenza, lasciatoProposto, MOVIMENTI, numeroDa, oltreTolleranza, serveAprire, spiegaFondo, statoDaMostrare } from '../../lib/cassa';
  import { dataBreve, dataDi, iso, oraDi } from '../../lib/date';
  import { euro } from '../../lib/listino';
  import { sessione } from '../../lib/sessione.svelte';

  /**
   * La cassa di oggi (ADR 0018, parte 11). Aprirla (con il fondo), i soldi tolti o messi a mano, poi la chiusura:
   * - con la conta alla cieca in tre passi: si conta (anche per tagli) e si scrivono gli scontrini del POS senza vedere
   *   gli attesi; il confronto mostra la differenza (oltre la tolleranza propone di ricontare e chiede la nota); si chiude;
   * - senza, in un passo solo, con la differenza che si vede mentre si scrive.
   * Dopo: il riepilogo da stampare, e se serve l'avviso che un responsabile la deve controllare.
   * «versione» cambia quando la pagina ricarica gli incassi.
   */
  let { versione = 0, cambiata }: { versione?: number; cambiata?: () => void } = $props();

  let pagina = $state<PaginaCassa | null>(null);
  let scelta = $state<string | null>(cassaDelComputer());
  let errore = $state('');
  let lavoro = $state(false);
  let fondoContato = $state('');
  let contati = $state('');
  let pezzi = $state<Record<string, number | null>>({});
  let pos = $state<Record<string, string>>({});
  let lasciato = $state('');
  let note = $state('');
  let tagli = $state(false);
  let esito = $state<EsitoConfronto | null>(null);
  let mov = $state<{ tipo: TipoMovimentoCassa; importo: string; descrizione: string } | null>(null);
  let fatta = $state<SessioneCassa | null>(null);
  let riapri = $state<string | null>(null);
  let vediContanti = $state(false);

  async function carica() {
    try {
      pagina = await api.cassa();
      errore = '';
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare la cassa.');
    }
  }
  $effect(() => {
    void versione;
    untrack(carica);
  });

  const imp = $derived(pagina?.impostazioni);
  const stato = $derived(pagina ? statoDaMostrare(pagina, scelta) : undefined);
  const s = $derived(stato?.corrente);
  const c = $derived(s?.conti);
  const cieca = $derived(!!imp?.cieca);
  const daAprire = $derived(!!imp && !!stato && !stato.aperta && serveAprire(imp));
  const contatiN = $derived(numeroDa(contati));
  const lasciatoN = $derived(imp?.fondo === 'FISSO' || !lasciato.trim() ? (contatiN != null && imp ? lasciatoProposto(contatiN, imp) : null) : numeroDa(lasciato));
  // gli attesi: dal confronto (alla cieca) o in chiaro
  const attesi = $derived(esito ? esito.sessione.conti.attesi : (c?.attesi ?? null));
  const diff = $derived(contatiN != null && attesi != null ? Math.round((contatiN - attesi) * 100) / 100 : null);
  const oltre = $derived(diff != null && !!imp && oltreTolleranza(diff, imp.tolleranza));
  // senza cieca i riscontri si calcolano qui dai totali per metodo; alla cieca arrivano dal confronto
  const riscontriVisti = $derived(
    esito ? esito.riscontri
      : (pagina?.daRiscontrare ?? []).map((m) => {
        const t = c?.perMetodo.find((x) => x.metodo === m.nome);
        const atteso = t ? Math.round((t.entrate - t.uscite) * 100) / 100 : 0;
        const d = numeroDa(pos[m.id] ?? '');
        return { metodo: m.nome, atteso, dichiarato: d, differenza: d == null ? 0 : Math.round((d - atteso) * 100) / 100 };
      }),
  );
  const nonTorna = $derived(oltre || riscontriVisti.some((r) => r.dichiarato != null && Math.round(r.differenza * 100) !== 0));
  const fuori = $derived(s?.contanti?.filter((v) => v.aCassaChiusa).length ?? 0);
  const puoAnnullare = $derived(sessione.puo('PAGAMENTI_ANNULLA'));
  const movimenti = $derived(s?.movimenti?.filter((m) => !m.annullato) ?? []);
  const altreAperte = $derived((pagina?.avvisi.aperteDaTroppo ?? []).filter((a) => a.cassaId !== stato?.cassa.id));
  const tipiMovimento = $derived(MOVIMENTI.filter((t) => t.id !== 'SPESA' || imp?.spese));
  const passo = $derived(!cieca ? 'unico' : esito ? 'confronto' : 'conta');

  function scegli(id: string) {
    scegliCassaDelComputer(id);
    scelta = id;
    esito = null;
    cambiata?.();
  }

  async function fai<T>(azione: () => Promise<T>, poi?: (r: T) => void) {
    errore = '';
    lavoro = true;
    try {
      const r = await azione();
      poi?.(r);
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
      if (e instanceof ErroreApi && e.status === 409) {
        esito = null;
        await carica();
      }
    } finally {
      lavoro = false;
    }
  }

  const scritti = () => (pagina?.daRiscontrare ?? []).flatMap((m) => {
    const n = numeroDa(pos[m.id] ?? '');
    return n == null ? [] : [{ metodoId: m.id, importo: n }];
  });

  const apri = () => fai(() => api.apriCassa(stato!.cassa.id, imp?.fondo === 'CONTATO' ? (numeroDa(fondoContato) ?? undefined) : undefined), () => (fondoContato = ''));

  const registraMovimento = () =>
    fai(() => api.movimentoCassa(stato!.cassa.id, mov!.tipo, numeroDa(mov!.importo)!, mov!.descrizione.trim() || undefined), () => (mov = null));

  const confronta = () => fai(() => api.confrontaCassa(stato!.cassa.id, contatiN!, scritti()), (r) => (esito = r));

  /** Riconta: si torna al conteggio con i pezzi già scritti; il primo conteggio resta. */
  function riconta() {
    esito = null;
    tagli = Object.keys(pezzi).length > 0;
  }

  const chiudi = () =>
    fai(() => api.chiudiCassa(stato!.cassa.id, { contati: contatiN!, lasciato: lasciatoN, note: note.trim() || null, attesiVisti: attesi, riscontri: scritti() }), (r) => {
      fatta = r;
      contati = lasciato = note = '';
      pezzi = {};
      pos = {};
      esito = null;
      tagli = false;
    });

  const riapriCassa = (id: string) => fai(() => api.riapriCassa(id, riapri!.trim()), () => { riapri = null; fatta = null; });

  const nomeTipo = (t: TipoMovimentoCassa) => MOVIMENTI.find((m) => m.id === t)!;
  const quando = (i: string) => (dataDi(i) === iso(new Date()) ? `alle ${oraDi(i)}` : `${dataBreve(dataDi(i))} alle ${oraDi(i)}`);
  const nome1 = (n: string | null | undefined) => (n ? n.split(' ')[0] : '');
</script>

<section class="card cassa" aria-label="La cassa">
  <div class="testa">
    <h2 class="grow">{pagina && pagina.stati.length > 1 ? stato?.cassa.nome : 'Cassa'}</h2>
    {#if stato}
      {#if stato.aperta && s?.esplicita}<span class="tag aperta">Aperta{s.apertaDa ? ` da ${nome1(s.apertaDa)}` : ''} {quando(s.apertaIl!)}</span>
      {:else if stato.aperta}<span class="tag aperta">In corso</span>
      {:else if daAprire}<span class="tag chiusa">Da aprire</span>
      {:else}<span class="tag chiusa">Si apre da sola</span>{/if}
    {/if}
  </div>

  {#if pagina && imp?.casse === 'PER_POSTAZIONE' && pagina.stati.length > 1}
    <label class="computer"><span>Questo computer è</span>
      <select class="inp" value={stato?.cassa.id} onchange={(e) => scegli(e.currentTarget.value)} aria-label="La cassa di questo computer">
        {#each pagina.stati as x (x.cassa.id)}<option value={x.cassa.id}>{x.cassa.nome}{x.cassa.principale ? ' (principale)' : ''}</option>{/each}
      </select></label>
  {/if}

  {#if stato?.apertaDaTroppo && s?.apertaIl}
    <p class="avviso-c" role="status"><Icona nome="attesa" /><span>La cassa è aperta {quando(s.apertaIl)}{s.apertaDa ? ` da ${s.apertaDa}` : ''}: se il turno è finito, chiudila prima di cominciare il tuo.</span></p>
  {/if}
  {#each altreAperte as a (a.cassaId)}
    <p class="avviso-c" role="status"><Icona nome="attesa" /><span>«{a.cassa}» è aperta {quando(a.apertaIl)}{a.apertaDa ? ` da ${a.apertaDa}` : ''}.</span></p>
  {/each}

  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if fatta}
    {@const e = esitoDifferenza(fatta.differenza ?? 0)}
    <div class="fatta" role="status">
      <b class="t">Cassa chiusa alle {oraDi(fatta.chiusaIl!)}</b>
      <span class="diff-r {e.tono}"><span>{e.testo}</span><span class="m">{conSegno(fatta.differenza ?? 0, euro)}</span></span>
      <span class="small">Lasciati {euro(fatta.lasciato ?? 0)} per la prossima volta{fatta.versato ? `; ${euro(fatta.versato)} da versare${imp?.banca ? ' (vanno in cassaforte)' : ''}` : ''}.</span>
      {#if fatta.daControllare}
        <p class="avviso-c nomargin"><Icona nome="avviso" /><span>Da controllare: un responsabile la vede tra le chiusure e la segna come controllata.</span></p>
      {:else if imp?.controllo !== 'NESSUNA'}
        <p class="avviso-c ok nomargin"><Icona nome="check" /><span>Entro la tolleranza: non serve il controllo.</span></p>
      {/if}
      <span class="bottoni">
        <a class="btn btn-secondary btn-sm" href={api.urlPdfChiusura(fatta.id!)} target="_blank" rel="noopener"><Icona nome="stampa" />Stampa il riepilogo</a>
        <button type="button" class="btn btn-quieto btn-sm" onclick={() => (fatta = null)}>Va bene</button>
      </span>
    </div>
  {/if}

  {#if !pagina}
    {#if !errore}<p class="muted small nomargin">Caricamento…</p>{/if}
  {:else if stato && s && c && imp}
    {#if cieca && !daAprire && !fatta}
      <div class="passi" aria-label="Passi della chiusura">
        <span class="on"><i></i>1 · Conta</span><span class:on={passo !== 'conta'}><i></i>2 · Confronta</span><span><i></i>3 · Chiudi</span>
      </div>
    {/if}

    {#if daAprire}
      <form class="apri" onsubmit={(e) => { e.preventDefault(); apri(); }}>
        <span class="t"><b>Apri la cassa per il tuo turno</b>
          <span>{imp.fondo === 'CONTATO' ? `Conta il fondo nel cassetto: ${stato.ultimaChiusa ? `alla chiusura sono stati lasciati ${euro(stato.fondoProposto)}` : `di solito ${euro(stato.fondoProposto)}`}.`
            : `Fondo cassa ${euro(stato.fondoProposto)}${stato.ultimaChiusa?.chiusaDa && imp.fondo === 'RIPORTATO' ? `, lasciato da ${nome1(stato.ultimaChiusa.chiusaDa)} ${quando(stato.ultimaChiusa.chiusaIl!)}` : ''}.`}</span></span>
        {#if imp.fondo === 'CONTATO'}
          <input class="inp m" inputmode="decimal" bind:value={fondoContato} placeholder={euro(stato.fondoProposto)} aria-label="Fondo contato" />
        {/if}
        <button class="btn btn-primary" disabled={lavoro || (imp.fondo === 'CONTATO' && numeroDa(fondoContato) == null)}>Apri la cassa</button>
      </form>
    {/if}

    {#if passo !== 'confronto'}
      <!-- il riepilogo del turno: alla cieca senza importi -->
      <div class="turno" aria-label="Riepilogo del turno">
        <div class="turno-t"><span class="lbl">{stato.aperta && s.esplicita ? 'Nel tuo turno' : 'Dall\'ultima chiusura'}</span><span class="muted small">dal {dataBreve(dataDi(s.dal))} {oraDi(s.dal)}</span></div>
        <div class="turno-g">
          <span class="cifra"><b class="m">{euro(c.fondo)}</b><span>fondo di partenza</span></span>
          {#if s.nascosti}
            <span class="cifra"><b class="m">{c.quanteEntrate}</b><span>{c.quanteEntrate === 1 ? 'pagamento in contanti' : 'pagamenti in contanti'}</span></span>
            <span class="cifra"><b class="m">{c.quanteUscite}</b><span>{c.quanteUscite === 1 ? 'restituzione' : 'restituzioni'}</span></span>
            <span class="cifra"><b class="m">{movimenti.length}</b><span>{movimenti.length === 1 ? 'movimento a mano' : 'movimenti a mano'}</span></span>
          {:else}
            <span class="cifra"><b class="m">{euro(c.entrati ?? 0)}</b><span>contanti incassati</span></span>
            <span class="cifra"><b class="m">{euro((c.usciti ?? 0) + c.versamenti + c.spese - c.aggiunte)}</b><span>restituiti e tolti</span></span>
            <span class="cifra tot"><b class="m">{euro(c.attesi ?? 0)}</b><span>attesi in cassa</span></span>
          {/if}
        </div>
        {#each movimenti as m (m.id)}
          <span class="mov"><span class="grow"><b>{nomeTipo(m.tipo).nome}</b>{m.descrizione !== nomeTipo(m.tipo).nome ? ` · ${m.descrizione}` : ''}
            <span class="muted small"> · {oraDi(m.registratoIl)}{m.registratoDa ? `, ${nome1(m.registratoDa)}` : ''}</span></span>
            <span class="m">{nomeTipo(m.tipo).segno}{euro(m.importo)}</span>
            <button type="button" class="mini-x" aria-label={`Annulla ${nomeTipo(m.tipo).nome.toLowerCase()} di ${euro(m.importo)}`} disabled={lavoro}
              onclick={() => fai(() => api.annullaMovimentoCassa(m.id))}><Icona nome="chiudi" /></button></span>
        {/each}
        {#if s.nascosti}
          <span class="spiega-c">Conta alla cieca: i contanti attesi si vedono dopo il conteggio.</span>
        {:else if s.contanti?.length}
          <span class="spiega-c">{s.contanti.length === 1 ? '1 movimento' : `${s.contanti.length} movimenti`} in contanti
            <button type="button" class="link" aria-expanded={vediContanti} onclick={() => (vediContanti = !vediContanti)}>{vediContanti ? 'nascondi' : 'vedi'}</button></span>
        {/if}
      </div>
      {#if fuori}<p class="hint nomargin"><span>{fuori === 1 ? '1 pagamento in contanti è arrivato' : `${fuori} pagamenti in contanti sono arrivati`} a cassa chiusa: sono contati qui.</span></p>{/if}
      {#if vediContanti && s.contanti}
        <ul class="voci">
          {#each s.contanti as v (v.id + v.tipo)}
            <li class:fuori={v.aCassaChiusa}><span class="m ora">{oraDi(v.istante)}</span><span class="grow tronca">{v.chi ?? ''} <span class="muted">· {v.descrizione}{v.operatore ? ` · ${nome1(v.operatore)}` : ''}</span></span>
              <span class="m" class:rosso={v.tipo === 'USCITA'}>{v.tipo === 'USCITA' ? '−' : ''}{euro(v.importo)}</span></li>
          {/each}
        </ul>
      {/if}

      {#if mov}
        <form class="nuovo-mov" onsubmit={(e) => { e.preventDefault(); registraMovimento(); }}>
          <span class="seg" style:grid-template-columns={`repeat(${tipiMovimento.length}, minmax(0, 1fr))`} role="group" aria-label="Che movimento">
            {#each tipiMovimento as t (t.id)}<button type="button" aria-pressed={mov.tipo === t.id} title={t.spiega} onclick={() => (mov!.tipo = t.id)}>{t.nome}</button>{/each}
          </span>
          <span class="muted small">{nomeTipo(mov.tipo).spiega}</span>
          <span class="riga-f">
            <input class="inp m corto" inputmode="decimal" bind:value={mov.importo} placeholder="0,00" aria-label="Importo" />
            <input class="inp grow" bind:value={mov.descrizione} maxlength="200" placeholder={mov.tipo === 'SPESA' ? 'Per cosa (es. cancelleria)' : 'Nota (facoltativa)'} aria-label="Descrizione" />
          </span>
          <span class="bottoni">
            <button class="btn btn-primary btn-sm" disabled={lavoro || !numeroDa(mov.importo)}>Registra</button>
            <button type="button" class="btn btn-secondary btn-sm" onclick={() => (mov = null)}>Annulla</button>
          </span>
        </form>
      {:else if !daAprire}
        <button type="button" class="btn btn-quieto btn-sm sx" onclick={() => (mov = { tipo: 'VERSAMENTO', importo: '', descrizione: '' })}><Icona nome="piu" />Soldi tolti o messi a mano</button>
      {/if}
    {/if}

    {#if !daAprire}
      {#if passo === 'confronto' && esito}
        {@const ce = esito.sessione.conti}
        <!-- 2 · il confronto: adesso si vedono gli attesi -->
        <div class="conti">
          <span class="riga"><span>Fondo cassa</span><span class="m imp">{euro(ce.fondo)}</span></span>
          <span class="riga"><span>+ Contanti incassati</span><span class="m imp">{euro(ce.entrati ?? 0)}</span></span>
          {#if ce.usciti}<span class="riga"><span>− Contanti restituiti</span><span class="m imp">{euro(ce.usciti)}</span></span>{/if}
          {#if ce.versamenti}<span class="riga"><span>− Versamenti</span><span class="m imp">{euro(ce.versamenti)}</span></span>{/if}
          {#if ce.spese}<span class="riga"><span>− Spese</span><span class="m imp">{euro(ce.spese)}</span></span>{/if}
          {#if ce.aggiunte}<span class="riga"><span>+ Aggiunte</span><span class="m imp">{euro(ce.aggiunte)}</span></span>{/if}
          <span class="riga tot"><span>Attesi in cassa</span><span class="m imp">{euro(ce.attesi ?? 0)}</span></span>
          <label class="riga"><span>Contati</span><input class="inp m piccolo" inputmode="decimal" bind:value={contati} aria-label="Contati" /></label>
        </div>
        {#if esito.primoConteggio != null && contatiN != null && Math.round(esito.primoConteggio * 100) !== Math.round(contatiN * 100)}
          <span class="conteggi"><span>Primo conteggio <s>{euro(esito.primoConteggio)}</s></span><span>Adesso <b>{euro(contatiN)}</b></span></span>
        {/if}
      {:else}
        <!-- 1 · il conteggio (o tutto in un passo, senza la conta alla cieca) -->
        <div class="chiudi-c">
          <div class="lbl-r"><span class="lbl">Contati</span>
            <button type="button" class="link" aria-expanded={tagli} onclick={() => (tagli = !tagli)}>{tagli ? 'Scrivi il totale' : 'Conta per tagli'}</button></div>
          {#if tagli}<ContaTagli bind:pezzi totale={(t) => (contati = t ? String(t).replace('.', ',') : '')} />{/if}
          <input class="inp m grande" inputmode="decimal" bind:value={contati} readonly={tagli} placeholder="Quanto c'è nel cassetto" aria-label="Contati" />
        </div>
      {/if}

      {#if pagina.daRiscontrare.length}
        <div class="blocco-c">
          <span class="lbl">Riscontro con il POS</span>
          {#if passo === 'confronto'}
            {#each riscontriVisti as r (r.metodo)}
              {@const torna = r.dichiarato == null || Math.round(r.differenza * 100) === 0}
              <span class="riga piccola"><span>{r.metodo}: atteso {euro(r.atteso)}{r.dichiarato != null ? `, scontrino ${euro(r.dichiarato)}` : ', scontrino non scritto'}</span>
                <span class="pill {r.dichiarato == null ? '' : torna ? 'passa' : 'ferma'}">{r.dichiarato == null ? '—' : torna ? 'Torna' : conSegno(r.differenza, euro)}</span></span>
            {/each}
          {:else}
            {#each pagina.daRiscontrare as m (m.id)}
              {@const r = riscontriVisti.find((x) => x.metodo === m.nome)}
              <label class="pos"><span>{m.nome} · totale dello scontrino di chiusura del POS{#if !cieca && r} <span class="muted">(atteso {euro(r.atteso)})</span>{/if}</span>
                <input class="inp m" inputmode="decimal" bind:value={pos[m.id]} placeholder="0,00" aria-label={`Totale del POS per ${m.nome}`} /></label>
            {/each}
          {/if}
        </div>
      {/if}

      {#if passo === 'conta'}
        <button type="button" class="btn btn-primary passo-b" disabled={lavoro || contatiN == null} onclick={confronta}>
          {lavoro ? 'Un momento…' : 'Confronta con gli attesi'}<Icona nome="freccia" /></button>
      {:else}
        {#if diff != null && esito !== undefined}
          {@const e = esitoDifferenza(diff)}
          <div class="diff {oltre ? (diff > 0 ? 'piu' : 'meno') : 'ok'}" role="status">
            <span class="riga"><span>{oltre ? e.testo : diff === 0 ? 'La cassa torna' : 'Entro la tolleranza'}</span><span class="m">{conSegno(diff, euro)}</span></span>
            {#if oltre}<small>Più della differenza tollerata ({euro(imp.tolleranza)}): riconta, o scrivi cosa è successo.{imp.controllo !== 'NESSUNA' ? ' La chiusura andrà controllata da un responsabile.' : ''}</small>
            {:else if diff !== 0}<small>Entro i {euro(imp.tolleranza)} tollerati{imp.controllo === 'OLTRE' ? ': non serve il controllo' : ''}.</small>{/if}
          </div>
          {#if oltre && cieca}<button type="button" class="btn btn-secondary btn-sm sx" onclick={riconta}><Icona nome="aggiorna" />Riconta</button>{/if}
        {/if}
        {#if contatiN != null}
          <div class="lascia">
            {#if imp.fondo === 'FISSO'}
              <span class="riga"><span>Resta il fondo</span><span class="m imp">{euro(lasciatoN ?? 0)}</span></span>
            {:else}
              <label class="riga"><span>Lasci per la prossima volta</span>
                <input class="inp m piccolo" inputmode="decimal" bind:value={lasciato} placeholder={euro(lasciatoN ?? 0)} aria-label="Lasci in cassa" /></label>
            {/if}
            <span class="riga"><span>Da versare{imp.banca ? ' (in cassaforte)' : ' (banca, cassaforte)'}</span><span class="m imp">{euro(Math.max(0, contatiN - (lasciatoN ?? 0)))}</span></span>
          </div>
          <label class="campo-n"><span class="small">{nonTorna ? 'Cosa è successo (obbligatorio: la cassa non torna)' : 'Nota (facoltativa)'}</span>
            <textarea class="inp nota-i" bind:value={note} maxlength="500" placeholder={nonTorna ? 'Es. resto sbagliato a un cliente alle 18:20' : ''} aria-label="Nota della chiusura"></textarea></label>
        {/if}
        <span class="bottoni fine">
          {#if passo === 'confronto'}<button type="button" class="btn btn-secondary" onclick={() => (esito = null)}>Indietro</button>{/if}
          <button class="btn btn-primary grow" disabled={lavoro || contatiN == null || (lasciatoN != null && lasciatoN > contatiN) || (nonTorna && !note.trim())} onclick={chiudi}>
            {lavoro ? 'Un momento…' : imp.chiusura === 'GIORNATA' ? 'Chiudi la giornata' : 'Chiudi la cassa'}</button>
        </span>
        {#if lasciatoN != null && contatiN != null && lasciatoN > contatiN}<span class="msg err small nomargin">Non puoi lasciare più di quello che hai contato.</span>{/if}
      {/if}
    {/if}

    {#if stato.ultimaChiusa && !fatta && passo !== 'confronto'}
      {@const u = stato.ultimaChiusa}
      {@const e = esitoDifferenza(u.differenza ?? 0)}
      <div class="ultima">
        <span class="small grow">Ultima chiusura {quando(u.chiusaIl!)}{u.chiusaDa ? `, ${u.chiusaDa}` : ''} · <span class="diff-t {e.tono}">{u.differenza ? conSegno(u.differenza, euro) : 'tornava'}</span>
          {#if u.daControllare && !u.controllataIl}<span class="pill warn">Da controllare</span>{/if}</span>
        <a class="link" href={api.urlPdfChiusura(u.id!)} target="_blank" rel="noopener">Riepilogo</a>
        {#if puoAnnullare && !stato.aperta && riapri === null && !u.inBanca}<button type="button" class="link" onclick={() => (riapri = '')}>Riapri</button>{/if}
      </div>
      {#if riapri !== null}
        <form class="riapri" onsubmit={(ev) => { ev.preventDefault(); riapriCassa(u.id!); }}>
          <span class="small">Si riapre l'ultima chiusura (es. contati sbagliati): i soldi passati dopo finiscono in questa. Resta la nota con chi e perché.</span>
          <input class="inp" bind:value={riapri} maxlength="300" placeholder="Perché la riapri" aria-label="Perché riapri la cassa" />
          <span class="bottoni"><button class="btn btn-danger btn-sm" disabled={lavoro || !riapri.trim()}>Riapri</button>
            <button type="button" class="btn btn-secondary btn-sm" onclick={() => (riapri = null)}>Lascia chiusa</button></span>
        </form>
      {/if}
    {/if}
    <span class="muted small">{spiegaFondo(imp, euro)}{pagina.daRiscontrare.length ? '' : ' Carte e Satispay si controllano con l\'estratto del POS.'}</span>
  {:else if pagina}
    <p class="muted small nomargin">Nessuna cassa accesa: si sceglie nelle impostazioni della cassa.</p>
  {/if}
</section>

<style>
  .cassa { padding: var(--space-16) var(--space-18, 18px); display: flex; flex-direction: column; gap: var(--space-12); }
  .testa { display: flex; align-items: center; gap: var(--space-10); }
  .testa h2 { margin: 0; font-size: var(--text-xl); }
  .tag.aperta { background: var(--passa-bg); color: var(--passa-fg); }
  .tag.chiusa { background: var(--surface-sunken); color: var(--seg-fg); }
  .computer { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; font-size: var(--text-sm); color: var(--muted); }
  .computer select { appearance: auto; width: auto; height: 36px; flex: 1 1 160px; }
  .avviso-c { display: flex; gap: var(--space-8); align-items: flex-start; margin: 0; padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn); font-size: var(--text-sm); font-weight: 600; }
  .avviso-c.ok { background: var(--passa-bg); color: var(--passa-fg); }
  .avviso-c :global(.ic) { width: 18px; height: 18px; flex-shrink: 0; margin-top: 1px; }
  .passi { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-6); }
  .passi span { display: flex; flex-direction: column; gap: var(--space-4); font-size: var(--text-xs); font-weight: 600; color: var(--muted); }
  .passi span i { height: 4px; border-radius: 2px; background: var(--surface-sunken); }
  .passi span.on { color: var(--ink); }
  .passi span.on i { background: var(--cloro); }
  .apri { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--cloro-soft); }
  .apri .t { display: flex; flex-direction: column; gap: 2px; }
  .apri .t span { font-size: var(--text-sm); color: var(--muted); }
  .apri .inp { height: 44px; font-size: var(--text-lg); }
  .turno { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md);
    border: 1px solid color-mix(in srgb, var(--cloro) 30%, transparent); background: var(--cloro-soft); color: var(--cloro-ink); }
  .turno-t { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-8); }
  .turno-t .lbl { color: var(--cloro-ink); }
  .turno-g { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-8); }
  .cifra { display: flex; flex-direction: column; gap: 1px; padding: var(--space-8) var(--space-10); border-radius: var(--radius-sm); background: var(--surface); }
  .cifra b { font-size: var(--text-lg); font-weight: 500; color: var(--ink); }
  .cifra span { font-size: var(--text-xs); color: var(--muted); }
  .cifra.tot b { font-weight: 700; }
  .spiega-c { font-size: var(--text-xs); color: var(--cloro-ink); }
  .conti { display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-md); }
  .riga { display: flex; justify-content: space-between; align-items: center; gap: var(--space-10); }
  .riga.tot { padding-top: var(--space-8); border-top: 1px solid var(--line); font-weight: 700; }
  .riga.piccola { font-size: var(--text-sm); }
  .imp { font-weight: 700; white-space: nowrap; }
  .rosso { color: var(--ferma); }
  .link { background: none; border: none; padding: 0 var(--space-4); color: var(--link, var(--cloro)); font: inherit; font-size: var(--text-sm); font-weight: 600; cursor: pointer; text-decoration: underline; }
  .voci { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; font-size: var(--text-sm); max-height: 260px; overflow-y: auto; }
  .voci li { display: flex; gap: var(--space-8); align-items: center; padding: var(--space-6) 0; border-top: 1px solid var(--line-soft); }
  .voci li.fuori { background: var(--warn-bg); }
  .ora { color: var(--muted); min-width: 40px; }
  .tronca { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
  .mov { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-md); padding: var(--space-6) var(--space-10); border-radius: var(--radius-sm); background: var(--surface); color: var(--ink); }
  .mini-x { position: relative; width: 32px; height: 32px; border-radius: 50%; border: none; background: none; color: var(--muted); cursor: pointer; display: grid; place-items: center; flex-shrink: 0; }
  .mini-x::after { content: ''; position: absolute; inset: -6px; }
  .nuovo-mov { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12); border-radius: var(--radius-md); border: 1px solid var(--line); }
  .riga-f { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .riga-f .inp { height: 40px; }
  .corto { width: 110px; }
  .grow { flex: 1 1 auto; min-width: 0; }
  .sx { align-self: flex-start; }
  .chiudi-c { display: flex; flex-direction: column; gap: var(--space-10); padding-top: var(--space-12); border-top: 1px solid var(--line); }
  .blocco-c { display: flex; flex-direction: column; gap: var(--space-8); padding-top: var(--space-12); border-top: 1px solid var(--line); }
  .pos { display: grid; grid-template-columns: minmax(0, 1fr) 120px; gap: var(--space-8); align-items: center; font-size: var(--text-sm); }
  .pos .inp { height: 40px; text-align: right; }
  .lbl-r { display: flex; justify-content: space-between; align-items: center; }
  .grande { font-size: var(--text-xl); height: 48px; }
  .piccolo { width: 120px; height: 36px; text-align: right; }
  .passo-b { align-self: flex-end; min-height: 52px; padding: 0 22px; border-radius: var(--radius-lg); font-size: var(--text-base); gap: var(--space-10); }
  .lascia { display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-md); padding-top: var(--space-12); border-top: 1px solid var(--line); }
  .campo-n { display: flex; flex-direction: column; gap: var(--space-6); }
  .nota-i { min-height: 64px; padding: var(--space-10) var(--space-12); resize: vertical; font: inherit; font-size: var(--text-md); }
  .conteggi { display: flex; gap: var(--space-6); flex-wrap: wrap; font-size: var(--text-sm); }
  .conteggi span { padding: 3px 10px; border-radius: var(--radius-pill); background: var(--surface-sunken); }
  .conteggi s { color: var(--muted); }
  .diff, .diff-r { display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); font-size: var(--text-md); }
  .diff .riga, .diff-r { font-weight: 700; }
  .diff small { font-size: var(--text-sm); font-weight: 500; }
  .diff-r { flex-direction: row; justify-content: space-between; }
  .ok { background: var(--passa-bg); color: var(--passa-fg); }
  .piu { background: var(--warn-bg); color: var(--warn); }
  .meno { background: var(--ferma-bg); color: var(--ferma-fg); }
  .diff-t { background: none; padding: 0; font-weight: 700; }
  .diff-t.ok { color: var(--passa-fg); } .diff-t.piu { color: var(--warn); } .diff-t.meno { color: var(--ferma-fg); }
  .fatta { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); border: 1px solid var(--line); box-shadow: inset 3px 0 0 var(--passa); }
  .fatta .t { font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 800; }
  .bottoni { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .bottoni.fine .btn { min-height: 44px; }
  .bottoni a.btn { text-decoration: none; display: inline-flex; align-items: center; }
  .ultima { display: flex; align-items: center; gap: var(--space-6); flex-wrap: wrap; padding-top: var(--space-10); border-top: 1px solid var(--line-soft); }
  .riapri { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12); border-radius: var(--radius-md); background: var(--ferma-bg); color: var(--ferma-fg); }
  .riapri .inp { height: 40px; }
  .pill { display: inline-flex; align-items: center; height: 22px; padding: 0 9px; border-radius: var(--radius-pill); font-size: var(--text-xs); font-weight: 700; background: var(--surface-sunken); color: var(--seg-fg); white-space: nowrap; }
  .pill.passa { background: var(--passa-bg); color: var(--passa-fg); }
  .pill.ferma { background: var(--ferma-bg); color: var(--ferma-fg); }
  .pill.warn { background: var(--warn-bg); color: var(--warn); margin-left: var(--space-4); }
</style>
