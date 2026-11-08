<script lang="ts">
  import Icona from '../Icona.svelte';
  import ContaTagli from './ContaTagli.svelte';
  import { untrack } from 'svelte';
  import { api, cassaDelComputer, ErroreApi, messaggioErrore, scegliCassaDelComputer, type PaginaCassa, type SessioneCassa, type TipoMovimentoCassa } from '../../lib/api';
  import { conSegno, esitoDifferenza, lasciatoProposto, MOVIMENTI, numeroDa, serveAprire, spiegaFondo, statoDaMostrare } from '../../lib/cassa';
  import { dataBreve, dataDi, oraDi } from '../../lib/date';
  import { euro } from '../../lib/listino';
  import { sessione } from '../../lib/sessione.svelte';

  /**
   * La cassa di oggi (ADR 0018, parte 11): aprirla (con il fondo), i contanti attesi che crescono con i pagamenti, i
   * soldi tolti o messi a mano, la chiusura con i contati (anche per tagli), la differenza e quanto si lascia per la
   * volta dopo; poi il riepilogo da stampare. «versione» cambia quando la pagina ricarica gli incassi.
   */
  let { versione = 0, cambiata }: { versione?: number; cambiata?: () => void } = $props();

  let pagina = $state<PaginaCassa | null>(null);
  let scelta = $state<string | null>(cassaDelComputer());
  let errore = $state('');
  let lavoro = $state(false);
  let fondoContato = $state('');
  let contati = $state('');
  let lasciato = $state('');
  let note = $state('');
  let tagli = $state(false);
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
  const daAprire = $derived(!!imp && !!stato && !stato.aperta && serveAprire(imp));
  const contatiN = $derived(numeroDa(contati));
  const lasciatoN = $derived(imp?.fondo === 'FISSO' || !lasciato.trim() ? (contatiN != null && imp ? lasciatoProposto(contatiN, imp) : null) : numeroDa(lasciato));
  const diff = $derived(contatiN != null && c?.attesi != null ? Math.round((contatiN - c.attesi) * 100) / 100 : null);
  const esito = $derived(diff == null ? null : esitoDifferenza(diff));
  const fuori = $derived(s?.contanti?.filter((v) => v.aCassaChiusa).length ?? 0);
  const puoAnnullare = $derived(sessione.puo('PAGAMENTI_ANNULLA'));
  const movimenti = $derived(s?.movimenti?.filter((m) => !m.annullato) ?? []);

  function scegli(id: string) {
    scegliCassaDelComputer(id);
    scelta = id;
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
      if (e instanceof ErroreApi && e.status === 409) await carica();
    } finally {
      lavoro = false;
    }
  }

  const apri = () => fai(() => api.apriCassa(stato!.cassa.id, imp?.fondo === 'CONTATO' ? (numeroDa(fondoContato) ?? undefined) : undefined), () => (fondoContato = ''));

  const registraMovimento = () =>
    fai(() => api.movimentoCassa(stato!.cassa.id, mov!.tipo, numeroDa(mov!.importo)!, mov!.descrizione.trim() || undefined), () => (mov = null));

  const chiudi = () =>
    fai(() => api.chiudiCassa(stato!.cassa.id, { contati: contatiN!, lasciato: lasciatoN, note: note.trim() || null, attesiVisti: c?.attesi ?? null }), (r) => {
      fatta = r;
      contati = lasciato = note = '';
      tagli = false;
    });

  const riapriCassa = (id: string) => fai(() => api.riapriCassa(id, riapri!.trim()), () => { riapri = null; fatta = null; });

  const nomeTipo = (t: TipoMovimentoCassa) => MOVIMENTI.find((m) => m.id === t)!;
  const quando = (i: string) => `${dataBreve(dataDi(i))} alle ${oraDi(i)}`;
</script>

<section class="card cassa" aria-label="La cassa">
  <div class="testa">
    <h2 class="grow">{pagina && pagina.stati.length > 1 ? stato?.cassa.nome : 'Cassa'}</h2>
    {#if stato}
      {#if stato.aperta && s?.esplicita}<span class="tag aperta">Aperta{s.apertaDa ? ` da ${s.apertaDa.split(' ')[0]}` : ''} alle {oraDi(s.apertaIl!)}</span>
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

  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if fatta}
    {@const e = esitoDifferenza(fatta.differenza ?? 0)}
    <div class="fatta" role="status">
      <b>Cassa chiusa alle {oraDi(fatta.chiusaIl!)}</b>
      <span class="diff-r {e.tono}"><span>{e.testo}</span><span class="m">{conSegno(fatta.differenza ?? 0, euro)}</span></span>
      <span class="small">Lasciati {euro(fatta.lasciato ?? 0)} per la prossima volta{fatta.versato ? `, da versare ${euro(fatta.versato)}` : ''}.</span>
      <span class="bottoni">
        <a class="btn btn-secondary btn-sm" href={api.urlPdfChiusura(fatta.id!)} target="_blank" rel="noopener"><Icona nome="stampa" />Stampa il riepilogo</a>
        <button type="button" class="btn btn-quieto btn-sm" onclick={() => (fatta = null)}>Va bene</button>
      </span>
    </div>
  {/if}

  {#if !pagina}
    {#if !errore}<p class="muted small nomargin">Caricamento…</p>{/if}
  {:else if stato && s && c && imp}
    {#if daAprire}
      <form class="apri" onsubmit={(e) => { e.preventDefault(); apri(); }}>
        <span class="t"><b>Apri la cassa per il tuo turno</b>
          <span>{imp.fondo === 'CONTATO' ? `Conta il fondo nel cassetto: ${stato.ultimaChiusa ? `alla chiusura sono stati lasciati ${euro(stato.fondoProposto)}` : `di solito ${euro(stato.fondoProposto)}`}.`
            : `Fondo cassa ${euro(stato.fondoProposto)}${stato.ultimaChiusa?.chiusaDa && imp.fondo === 'RIPORTATO' ? `, lasciato da ${stato.ultimaChiusa.chiusaDa.split(' ')[0]} ${quando(stato.ultimaChiusa.chiusaIl!)}` : ''}.`}</span></span>
        {#if imp.fondo === 'CONTATO'}
          <input class="inp m" inputmode="decimal" bind:value={fondoContato} placeholder={euro(stato.fondoProposto)} aria-label="Fondo contato" />
        {/if}
        <button class="btn btn-primary" disabled={lavoro || (imp.fondo === 'CONTATO' && numeroDa(fondoContato) == null)}>Apri la cassa</button>
      </form>
    {/if}

    <div class="conti">
      <span class="riga"><span>Fondo cassa</span><span class="m imp">{euro(c.fondo)}</span></span>
      {#if s.fondoAtteso != null && Math.round((s.fondoAtteso - s.fondo) * 100) !== 0}
        <span class="riga nota-r"><span>alla chiusura prima ne erano stati lasciati {euro(s.fondoAtteso)}</span><span class="m">{conSegno(s.fondo - s.fondoAtteso, euro)}</span></span>
      {/if}
      <span class="riga"><span>+ Contanti incassati</span><span class="m imp">{euro(c.entrati)}</span></span>
      {#if c.usciti}<span class="riga"><span>− Contanti restituiti</span><span class="m imp">{euro(c.usciti)}</span></span>{/if}
      {#if c.versamenti}<span class="riga"><span>− Versamenti</span><span class="m imp">{euro(c.versamenti)}</span></span>{/if}
      {#if c.spese}<span class="riga"><span>− Spese</span><span class="m imp">{euro(c.spese)}</span></span>{/if}
      {#if c.aggiunte}<span class="riga"><span>+ Aggiunte</span><span class="m imp">{euro(c.aggiunte)}</span></span>{/if}
      <span class="riga tot"><span>Attesi in cassa</span><span class="m imp">{euro(c.attesi ?? 0)}</span></span>
      <span class="muted small">Dal {quando(s.dal)}{s.contanti?.length ? ` · ${s.contanti.length === 1 ? '1 movimento' : `${s.contanti.length} movimenti`} in contanti` : ''}
        {#if s.contanti?.length}<button type="button" class="link" aria-expanded={vediContanti} onclick={() => (vediContanti = !vediContanti)}>{vediContanti ? 'nascondi' : 'vedi'}</button>{/if}</span>
      {#if fuori}<p class="hint nomargin"><span>{fuori === 1 ? '1 pagamento in contanti è arrivato' : `${fuori} pagamenti in contanti sono arrivati`} a cassa chiusa: sono contati qui.</span></p>{/if}
      {#if vediContanti && s.contanti}
        <ul class="voci">
          {#each s.contanti as v (v.id + v.tipo)}
            <li class:fuori={v.aCassaChiusa}><span class="m ora">{oraDi(v.istante)}</span><span class="grow tronca">{v.chi ?? ''} <span class="muted">· {v.descrizione}</span></span>
              <span class="m" class:rosso={v.tipo === 'USCITA'}>{v.tipo === 'USCITA' ? '−' : ''}{euro(v.importo)}</span></li>
          {/each}
        </ul>
      {/if}
    </div>

    <div class="movimenti">
      {#each movimenti as m (m.id)}
        <span class="mov"><span class="grow"><b>{nomeTipo(m.tipo).nome}</b>{m.descrizione !== nomeTipo(m.tipo).nome ? ` · ${m.descrizione}` : ''}
          <span class="muted small"> · {oraDi(m.registratoIl)}{m.registratoDa ? `, ${m.registratoDa.split(' ')[0]}` : ''}</span></span>
          <span class="m">{nomeTipo(m.tipo).segno}{euro(m.importo)}</span>
          <button type="button" class="mini-x" aria-label={`Annulla ${nomeTipo(m.tipo).nome.toLowerCase()} di ${euro(m.importo)}`} disabled={lavoro}
            onclick={() => fai(() => api.annullaMovimentoCassa(m.id))}><Icona nome="chiudi" /></button></span>
      {/each}
      {#if mov}
        <form class="nuovo-mov" onsubmit={(e) => { e.preventDefault(); registraMovimento(); }}>
          <span class="seg tre" role="group" aria-label="Che movimento">
            {#each MOVIMENTI as t (t.id)}<button type="button" aria-pressed={mov.tipo === t.id} title={t.spiega} onclick={() => (mov!.tipo = t.id)}>{t.nome}</button>{/each}
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
    </div>

    {#if !daAprire}
      <form class="chiudi" onsubmit={(e) => { e.preventDefault(); chiudi(); }}>
        <div class="lbl-r"><span class="lbl">Contati</span>
          <button type="button" class="link" aria-expanded={tagli} onclick={() => (tagli = !tagli)}>{tagli ? 'Scrivi il totale' : 'Conta per tagli'}</button></div>
        {#if tagli}<ContaTagli totale={(t) => (contati = t ? String(t).replace('.', ',') : '')} />{/if}
        <input class="inp m grande" inputmode="decimal" bind:value={contati} readonly={tagli} placeholder="Quanto c'è nel cassetto" aria-label="Contati" />
        {#if esito && diff != null}
          <div class="diff {esito.tono}" role="status"><span>{esito.testo}</span><span class="grow"></span><span class="m">{conSegno(diff, euro)}</span></div>
        {/if}
        {#if contatiN != null}
          <div class="lascia">
            {#if imp.fondo === 'FISSO'}
              <span class="riga"><span>Resta il fondo</span><span class="m imp">{euro(lasciatoN ?? 0)}</span></span>
            {:else}
              <label class="riga"><span>Lasci per la prossima volta</span>
                <input class="inp m piccolo" inputmode="decimal" bind:value={lasciato} placeholder={euro(lasciatoN ?? 0)} aria-label="Lasci in cassa" /></label>
            {/if}
            <span class="riga"><span>Da versare (banca, cassaforte)</span><span class="m imp">{euro(Math.max(0, contatiN - (lasciatoN ?? 0)))}</span></span>
          </div>
          <input class="inp" bind:value={note} maxlength="500" placeholder={diff ? 'Perché non torna? (facoltativo)' : 'Nota (facoltativa)'} aria-label="Nota della chiusura" />
        {/if}
        <button class="btn btn-primary" disabled={lavoro || contatiN == null || (lasciatoN != null && lasciatoN > contatiN)}>
          {lavoro ? 'Un momento…' : imp.chiusura === 'GIORNATA' ? 'Chiudi la giornata' : 'Chiudi la cassa'}</button>
        {#if lasciatoN != null && contatiN != null && lasciatoN > contatiN}<span class="msg err small nomargin">Non puoi lasciare più di quello che hai contato.</span>{/if}
      </form>
    {/if}

    {#if stato.ultimaChiusa && !fatta}
      {@const u = stato.ultimaChiusa}
      {@const e = esitoDifferenza(u.differenza ?? 0)}
      <div class="ultima">
        <span class="small grow">Ultima chiusura {quando(u.chiusaIl!)}{u.chiusaDa ? `, ${u.chiusaDa}` : ''} · <span class="diff-t {e.tono}">{u.differenza ? conSegno(u.differenza, euro) : 'tornava'}</span></span>
        <a class="link" href={api.urlPdfChiusura(u.id!)} target="_blank" rel="noopener">Riepilogo</a>
        {#if puoAnnullare && !stato.aperta && riapri === null}<button type="button" class="link" onclick={() => (riapri = '')}>Riapri</button>{/if}
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
    <span class="muted small">{spiegaFondo(imp, euro)} Carte e Satispay si controllano con l'estratto del POS.</span>
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
  .apri { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--cloro-soft); }
  .apri .t { display: flex; flex-direction: column; gap: 2px; }
  .apri .t span { font-size: var(--text-sm); color: var(--muted); }
  .apri .inp { height: 44px; font-size: var(--text-lg); }
  .conti { display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-md); }
  .riga { display: flex; justify-content: space-between; align-items: center; gap: var(--space-10); }
  .riga.tot { padding-top: var(--space-8); border-top: 1px solid var(--line); font-weight: 700; }
  .nota-r { font-size: var(--text-sm); color: var(--muted); }
  .imp { font-weight: 700; white-space: nowrap; }
  .rosso { color: var(--ferma); }
  .link { background: none; border: none; padding: 0 var(--space-4); color: var(--link, var(--cloro)); font: inherit; font-size: var(--text-sm); font-weight: 600; cursor: pointer; text-decoration: underline; }
  .voci { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; font-size: var(--text-sm); max-height: 260px; overflow-y: auto; }
  .voci li { display: flex; gap: var(--space-8); align-items: center; padding: var(--space-6) 0; border-top: 1px solid var(--line-soft); }
  .voci li.fuori { background: var(--warn-bg); }
  .ora { color: var(--muted); min-width: 40px; }
  .tronca { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
  .movimenti { display: flex; flex-direction: column; gap: var(--space-6); }
  .mov { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-md); padding: var(--space-6) var(--space-10); border-radius: var(--radius-sm); background: var(--surface-sunken); }
  .mini-x { position: relative; width: 32px; height: 32px; border-radius: 50%; border: none; background: none; color: var(--muted); cursor: pointer; display: grid; place-items: center; flex-shrink: 0; }
  .mini-x::after { content: ''; position: absolute; inset: -6px; }
  .nuovo-mov { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12); border-radius: var(--radius-md); border: 1px solid var(--line); }
  .nuovo-mov .seg { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .riga-f { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .riga-f .inp { height: 40px; }
  .corto { width: 110px; }
  .grow { flex: 1 1 auto; min-width: 0; }
  .sx { align-self: flex-start; }
  .chiudi { display: flex; flex-direction: column; gap: var(--space-10); padding-top: var(--space-12); border-top: 1px solid var(--line); }
  .lbl-r { display: flex; justify-content: space-between; align-items: center; }
  .grande { font-size: var(--text-xl); height: 48px; }
  .piccolo { width: 120px; height: 36px; text-align: right; }
  .lascia { display: flex; flex-direction: column; gap: var(--space-6); font-size: var(--text-md); }
  .diff, .diff-r { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); font-weight: 700; font-size: var(--text-md); }
  .ok { background: var(--passa-bg); color: var(--passa-fg); }
  .piu { background: var(--warn-bg); color: var(--warn); }
  .meno { background: var(--ferma-bg); color: var(--ferma-fg); }
  .diff-r { justify-content: space-between; }
  .diff-t { background: none; padding: 0; font-weight: 700; }
  .diff-t.ok { color: var(--passa-fg); } .diff-t.piu { color: var(--warn); } .diff-t.meno { color: var(--ferma-fg); }
  .fatta { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); border: 1px solid var(--line); box-shadow: inset 3px 0 0 var(--passa); }
  .bottoni { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .bottoni a.btn { text-decoration: none; display: inline-flex; align-items: center; }
  .chiudi > .btn-primary { min-height: 44px; }
  .ultima { display: flex; align-items: center; gap: var(--space-6); flex-wrap: wrap; padding-top: var(--space-10); border-top: 1px solid var(--line-soft); }
  .riapri { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-12); border-radius: var(--radius-md); background: var(--ferma-bg); color: var(--ferma-fg); }
  .riapri .inp { height: 40px; }
</style>
