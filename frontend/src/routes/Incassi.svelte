<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import CassaOggi from '../components/cassa/CassaOggi.svelte';
  import ChiusuraRiga from '../components/cassa/ChiusuraRiga.svelte';
  import CassaforteBox from '../components/cassa/Cassaforte.svelte';
  import DaIncassareLista from '../components/cassa/DaIncassare.svelte';
  import { api, messaggioErrore, type AvvisiCassa, type DaIncassare, type Incassi, type SessioneCassa } from '../lib/api';
  import { andamento, coloreMetodo, conSegno, contanti, perPersona, registro, statoChiusura, VISTE_INCASSI, type VistaIncassi } from '../lib/cassa';
  import { aggiungiGiorni, dataBreve, daIso, GIORNI, iso, lunedi, MESI, oraDi } from '../lib/date';
  import { euro } from '../lib/listino';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';

  /**
   * Gli incassi, in quattro viste a scelta (dalla rotella; si ricorda per account): la cassa del giorno con la
   * chiusura (oggi) o le chiusure fatte (un altro giorno), il periodo con l'andamento giorno per giorno, il registro di
   * entrate e uscite come un libro cassa, lo storico delle chiusure di cassa. Nelle prime tre ci sono i pagamenti da
   * incassare (bonifici) da segnare quando arrivano.
   */
  const oggi = iso(new Date());
  let vista = $state<VistaIncassi>(leggiPreferenza<VistaIncassi>('incassi.vista', 'cassa', VISTE_INCASSI.map((v) => v.id)));
  let scegliVista = $state(false);
  $effect(() => salvaPreferenza('incassi.vista', vista));

  let da = $state(oggi);
  let a = $state(oggi);
  let dati = $state<Incassi | null>(null);
  let aperti = $state<DaIncassare[]>([]);
  let chiusure = $state<SessioneCassa[]>([]);
  let avvisi = $state<AvvisiCassa | null>(null);
  let soloDaControllare = $state(false);
  let versioneCassa = $state(0);
  let errore = $state('');
  let messaggio = $state('');

  function cambiaVista(v: VistaIncassi) {
    vista = v;
    if (v === 'cassa') da = a; // la cassa è di un giorno: l'ultimo del periodo
    scegliVista = false;
  }

  const PERIODI = [
    { nome: 'Oggi', da: () => oggi, a: () => oggi },
    { nome: 'Ieri', da: () => aggiungiGiorni(oggi, -1), a: () => aggiungiGiorni(oggi, -1) },
    { nome: 'Questa settimana', da: () => lunedi(oggi), a: () => oggi },
    { nome: 'Questo mese', da: () => oggi.slice(0, 8) + '01', a: () => oggi },
    { nome: 'Mese scorso', da: () => aggiungiGiorni(oggi.slice(0, 8) + '01', -1).slice(0, 8) + '01', a: () => aggiungiGiorni(oggi.slice(0, 8) + '01', -1) },
  ];

  async function carica() {
    errore = '';
    const conChiusure = vista === 'chiusure' || (vista === 'cassa' && da !== oggi);
    try {
      [dati, aperti, chiusure] = await Promise.all([
        vista === 'chiusure' ? Promise.resolve(dati ?? { da, a, totale: 0, perMetodo: [], pagamenti: [], uscite: [] }) : api.incassi(da, a),
        api.daIncassare(),
        conChiusure ? api.chiusureCassa(da, a) : Promise.resolve([]),
      ]);
      if (vista === 'chiusure') avvisi = await api.avvisiCassa().catch(() => null);
      versioneCassa++;
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare gli incassi.');
    }
  }
  $effect(() => {
    void vista;
    if (da && a && a >= da) carica();
  });

  /** CSV delle chiusure per l'amministrazione: una per riga. */
  function esportaChiusure() {
    const q = (v: string | number | null) => `"${String(v ?? '').replaceAll('"', '""')}"`;
    const n = (v: number | null) => (v ?? 0).toFixed(2).replace('.', ',');
    const righe = [['Chiusa il', 'Cassa', 'Chiusa da', 'Fondo', 'Contanti incassati', 'Restituiti', 'Versamenti e spese', 'Attesi', 'Primo conteggio', 'Contati', 'Differenza', 'Lasciato', 'In cassaforte', 'Stato', 'Controllata da', 'Note'].map(q).join(';')];
    for (const s of chiusure)
      righe.push([`${s.chiusaIl!.slice(0, 10)} ${oraDi(s.chiusaIl!)}`, s.cassa, s.chiusaDa, n(s.fondo), n(s.conti.entrati), n(s.conti.usciti), n(s.conti.versamenti + s.conti.spese),
        n(s.attesi), n(s.primoConteggio ?? s.contati), n(s.contati), n(s.differenza), n(s.lasciato), n(s.perCassaforte ?? s.versato), statoChiusura(s).testo,
        s.controllataDa, s.note].map(q).join(';'));
    const url = URL.createObjectURL(new Blob(['﻿' + righe.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
    const link = Object.assign(document.createElement('a'), { href: url, download: `chiusure-${da}${a !== da ? '-' + a : ''}.csv` });
    link.click();
    URL.revokeObjectURL(url);
  }
  const differenze = $derived(chiusure.reduce((t, s) => t + Math.round((s.differenza ?? 0) * 100), 0) / 100);
  const nonTornano = $derived(chiusure.filter((s) => Math.round((s.differenza ?? 0) * 100) !== 0).length);
  const versato = $derived(chiusure.reduce((t, s) => t + Math.round((s.versato ?? 0) * 100), 0) / 100);
  const piuCasse = $derived(new Set(chiusure.map((s) => s.cassaId)).size > 1);
  const daControllare = $derived(chiusure.filter((s) => statoChiusura(s).tono === 'warn'));
  const visteChiusure = $derived(soloDaControllare ? daControllare : chiusure);
  const persone = $derived(perPersona(chiusure));
  function chiusuraCambiata(n: SessioneCassa) {
    chiusure = chiusure.map((s) => (s.id === n.id ? { ...s, ...n, contanti: null, movimenti: null } : s));
    api.avvisiCassa().then((x) => (avvisi = x)).catch(() => {});
  }

  async function incassa(x: DaIncassare) {
    messaggio = '';
    try {
      await api.incassa(x.id);
      messaggio = `${x.metodo} di ${euro(x.importo)} da ${x.pagante} segnato arrivato.`;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  /** CSV per l'amministrazione: un pagamento per riga, con i metodi e per chi. */
  function esporta() {
    const d = dati!;
    const q = (v: string | number | null) => `"${String(v ?? '').replaceAll('"', '""')}"`;
    const righe = [['Data', 'Importo', 'Chi paga', 'Metodi', 'Per', 'Note', 'Annullato'].map(q).join(';')];
    for (const p of d.pagamenti)
      righe.push([p.data, p.importo.toFixed(2).replace('.', ','), p.pagante, p.metodi.map((m) => `${m.metodo} ${m.importo.toFixed(2).replace('.', ',')}${m.daIncassare && !m.incassatoIl ? ' (da incassare)' : ''}`).join(' + '),
        p.voci.map((v) => `${v.persona} · ${v.descrizione}`).join(' + '), p.note, p.annullatoIl ? (p.motivoAnnullamento ?? 'sì') : ''].map(q).join(';'));
    const url = URL.createObjectURL(new Blob(['﻿' + righe.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
    const link = Object.assign(document.createElement('a'), { href: url, download: `incassi-${d.da}${d.a !== d.da ? '-' + d.a : ''}.csv` });
    link.click();
    URL.revokeObjectURL(url);
  }

  // ── comuni ──
  const metodi = $derived(dati ? dati.perMetodo.filter((m) => m.importo > 0 || m.uscite > 0) : []);
  const colore = (nome: string) => coloreMetodo(Math.max(0, dati?.perMetodo.findIndex((m) => m.metodo === nome) ?? 0));
  const validi = $derived(dati?.pagamenti.filter((p) => !p.annullatoIl) ?? []);
  const cassa = $derived(dati ? contanti(dati) : { entrati: 0, usciti: 0 });
  const restituiti = $derived(dati?.uscite.reduce((t, u) => t + Math.round(u.importo * 100), 0) ?? 0);
  const daIncassareTot = $derived(aperti.reduce((t, x) => t + Math.round(x.importo * 100), 0) / 100);
  const per = (p: Incassi['pagamenti'][number]) => p.voci.map((v) => `${v.persona && v.persona !== p.pagante ? v.persona + ' · ' : ''}${v.descrizione}`).join(' · ');
  const nomeGiorno = (d: string) => { const x = daIso(d); return `${GIORNI[x.getDay()]} ${x.getDate()} ${MESI[x.getMonth()]}`; };
  const periodo = $derived(da === a ? nomeGiorno(da) : `dal ${dataBreve(da)} al ${dataBreve(a)}`);

  // ── cassa del giorno ──
  let filtroMetodo = $state('');
  const pagamentiFiltrati = $derived(dati ? dati.pagamenti.filter((p) => !filtroMetodo || p.metodi.some((m) => m.metodo === filtroMetodo)) : []);
  function sposta(n: number) {
    const d = aggiungiGiorni(da, n);
    if (d > oggi) return;
    da = a = d;
  }

  // ── periodo ──
  let raggruppa = $state<'giorno' | 'settimana'>('giorno');
  const colonne = $derived(dati && da !== a ? andamento(dati.pagamenti, dati.perMetodo.map((m) => m.metodo), da, a, raggruppa) : []);
  const massimo = $derived(Math.max(1, ...colonne.map((c) => c.totale)));
  const clienti = $derived(new Set(validi.map((p) => p.paganteId)).size);
  let quanti = $state(50);

  // ── registro ──
  let filtroRegistro = $state<'tutto' | 'entrate' | 'uscite'>('tutto');
  let quantiRegistro = $state(150);
  const righeRegistro = $derived(dati ? registro(dati).filter((r) => filtroRegistro === 'tutto' || (filtroRegistro === 'entrate' ? r.entrata != null : r.uscita != null)) : []);
</script>

<div class="pagina">
  <div class="barra">
    {#if vista === 'cassa'}
      <div class="giorno-nav">
        <Azione icona="sx" etichetta="Giorno prima" onclick={() => sposta(-1)} />
        <span class="giorno-t">{nomeGiorno(da)}</span>
        <Azione icona="dx" etichetta="Giorno dopo" disabled={da >= oggi} onclick={() => sposta(1)} />
      </div>
      <button type="button" class="chip" aria-pressed={da === oggi} onclick={() => (da = a = oggi)}>Oggi</button>
      <input class="inp data" type="date" value={da} max={oggi} aria-label="Giorno" onchange={(e) => { const v = e.currentTarget.value; if (v) da = a = v; }} />
    {:else}
      <div class="chips" role="group" aria-label="Periodo">
        {#each PERIODI as p (p.nome)}
          <button type="button" class="chip" aria-pressed={da === p.da() && a === p.a()} onclick={() => { da = p.da(); a = p.a(); }}>{p.nome}</button>
        {/each}
      </div>
      <label class="data-l"><span class="sr-testo">Dal</span><input class="inp data" type="date" bind:value={da} max={oggi} aria-label="Dal" /></label>
      <label class="data-l"><span class="sr-testo">al</span><input class="inp data" type="date" bind:value={a} min={da} max={oggi} aria-label="Al" /></label>
    {/if}
    <span class="grow"></span>
    {#if vista === 'registro'}
      <div class="seg tre" role="group" aria-label="Cosa mostrare">
        <button type="button" aria-pressed={filtroRegistro === 'tutto'} onclick={() => (filtroRegistro = 'tutto')}>Tutto</button>
        <button type="button" aria-pressed={filtroRegistro === 'entrate'} onclick={() => (filtroRegistro = 'entrate')}>Entrate</button>
        <button type="button" aria-pressed={filtroRegistro === 'uscite'} onclick={() => (filtroRegistro = 'uscite')}>Uscite</button>
      </div>
    {/if}
    {#if vista === 'chiusure'}
      {#if chiusure.length}<button class="btn btn-secondary" onclick={esportaChiusure}><Icona nome="scarica" />Esporta (CSV)</button>{/if}
    {:else if dati?.pagamenti.length}<button class="btn btn-secondary" onclick={esporta}><Icona nome="scarica" />Esporta (CSV)</button>{/if}
    <Azione icona="impostazioni" etichetta="Scegli come mostrare gli incassi" premuto={scegliVista} onclick={() => (scegliVista = !scegliVista)} />
  </div>

  {#if scegliVista}
    <section class="card viste" aria-label="Come mostrare gli incassi">
      <span class="lbl">Come mostrare gli incassi</span>
      <div class="chips">
        {#each VISTE_INCASSI as v (v.id)}<button type="button" class="chip" aria-pressed={vista === v.id} title={v.spiega} onclick={() => cambiaVista(v.id)}>{v.nome}</button>{/each}
      </div>
      <p class="muted small nomargin">{VISTE_INCASSI.find((v) => v.id === vista)?.spiega}. La scelta resta per il tuo account su questo dispositivo.</p>
    </section>
  {/if}
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if !dati}
    {#if !errore}<p class="muted">Caricamento…</p>{/if}
  {:else if vista === 'cassa'}
    <div class="colonne">
      <div class="sx">
        <div class="hero">
          <div class="hero-testa">
            <span class="col"><span class="lbl chiaro">Incassato {da === oggi ? 'oggi' : nomeGiorno(da)}</span><span class="grande">{euro(dati.totale)}</span></span>
            <span class="chiaro small">{validi.length === 1 ? '1 pagamento' : `${validi.length} pagamenti`}{restituiti ? ` · ${euro(restituiti / 100)} restituiti` : ''}</span>
          </div>
          {#if dati.totale > 0}
            <div class="barra-metodi" role="img" aria-label={metodi.map((m) => `${m.metodo} ${euro(m.importo)}`).join(', ')}>
              {#each metodi as m (m.metodoId)}<span style:width="{(m.importo / dati.totale) * 100}%" style:background={colore(m.metodo)}></span>{/each}
            </div>
            <div class="legenda chiaro">
              {#each metodi as m (m.metodoId)}<span><i style:background={colore(m.metodo)}></i>{m.metodo} <b class="m">{euro(m.importo)}</b>{#if m.daIncassare}<span class="tenue"> · {euro(m.daIncassare)} da incassare</span>{/if}</span>{/each}
            </div>
          {/if}
        </div>

        <section class="blocco">
          <div class="b-testa">
            <h2 class="lbl">Pagamenti · i più recenti per primi</h2>
            <span class="grow"></span>
            {#if metodi.length > 1}
              <div class="chips" role="group" aria-label="Quali pagamenti">
                <button type="button" class="chip" aria-pressed={!filtroMetodo} onclick={() => (filtroMetodo = '')}>Tutti</button>
                {#each metodi as m (m.metodoId)}<button type="button" class="chip" aria-pressed={filtroMetodo === m.metodo} onclick={() => (filtroMetodo = m.metodo)}>{m.metodo}</button>{/each}
              </div>
            {/if}
          </div>
          <ul class="card pagamenti">
            {#each pagamentiFiltrati as p (p.id)}
              <li class:annullato={p.annullatoIl}>
                <span class="m ora">{oraDi(p.registratoIl)}</span>
                <span class="m imp">{euro(p.importo)}</span>
                <span class="col"><strong class="tronca">{p.pagante}</strong><span class="muted small tronca">{per(p)}{p.note ? ` · ${p.note}` : ''}</span></span>
                <span class="tags">
                  {#each p.metodi as m (m.id)}<span class="tag-m" class:da={m.daIncassare && !m.incassatoIl}><i style:background={colore(m.metodo)}></i>{m.metodo}{p.metodi.length > 1 ? ` ${euro(m.importo)}` : ''}</span>{/each}
                  {#if p.annullatoIl}<span class="tag-m" title={p.motivoAnnullamento ?? ''}>annullato</span>{/if}
                </span>
              </li>
            {:else}
              <li class="muted small vuoto">Nessun pagamento {da === oggi ? 'oggi' : 'in questo giorno'}.</li>
            {/each}
          </ul>
        </section>
        {#if dati.uscite.length}
          <section class="blocco">
            <h2 class="lbl">Soldi restituiti</h2>
            <ul class="card pagamenti">
              {#each dati.uscite as u (u.id)}
                <li><span class="m ora">{oraDi(u.registratoIl)}</span><span class="m imp rosso">−{euro(u.importo)}</span>
                  <span class="col"><strong>{u.persona}</strong><span class="muted small">{u.descrizione}</span></span>
                  <span class="tags"><span class="tag-m"><i style:background={colore(u.metodo)}></i>{u.metodo}</span></span></li>
              {/each}
            </ul>
          </section>
        {/if}
      </div>
      <aside class="dx">
        {#if da === oggi}
          <CassaOggi versione={versioneCassa} cambiata={carica} />
        {:else}
          <section class="card chiusure-g" aria-label="Le chiusure di cassa del giorno">
            <h2 class="lbl">Chiusure di cassa · {nomeGiorno(da)}</h2>
            {#if chiusure.length}
              <ul class="lista-ch">{#each chiusure as s (s.id)}<ChiusuraRiga chiusura={s} conCassa={piuCasse} />{/each}</ul>
            {:else}
              <p class="muted small nomargin">Nessuna chiusura in questo giorno. Contanti del giorno: {euro(cassa.entrati - cassa.usciti)} (entrati meno restituiti, senza il fondo).</p>
            {/if}
          </section>
        {/if}
        <DaIncassareLista {aperti} {oggi} onincassa={incassa} />
      </aside>
    </div>
  {:else if vista === 'periodo'}
    <div class="tiles cinque">
      <div class="tile hero-t"><span class="lbl">{periodo}</span><span class="v">{euro(dati.totale)}</span><span class="n">{restituiti ? `${euro(restituiti / 100)} restituiti` : 'incassati'}</span></div>
      <div class="tile"><span class="lbl">Pagamenti</span><span class="v">{validi.length}</span><span class="n">{clienti === 1 ? 'di 1 cliente' : `di ${clienti} clienti diversi`}</span></div>
      <div class="tile"><span class="lbl">In media</span><span class="v">{validi.length ? euro(dati.totale / validi.length) : '–'}</span><span class="n">a pagamento</span></div>
      <div class="tile" class:arancio={aperti.length > 0}><span class="lbl">Da incassare</span><span class="v">{euro(daIncassareTot)}</span><span class="n">{aperti.length === 1 ? '1 in attesa' : `${aperti.length} in attesa`}</span></div>
      <div class="tile"><span class="lbl">Restituiti</span><span class="v">{euro(restituiti / 100)}</span><span class="n">cauzioni e crediti</span></div>
    </div>

    {#if colonne.length > 1}
      <section class="card grafico">
        <div class="g-testa">
          <h2>{raggruppa === 'giorno' ? 'Giorno per giorno' : 'Settimana per settimana'}</h2>
          <div class="legenda">{#each metodi as m (m.metodoId)}<span><i style:background={colore(m.metodo)}></i>{m.metodo}</span>{/each}</div>
          <span class="grow"></span>
          <div class="seg due" role="group" aria-label="Raggruppa">
            <button type="button" aria-pressed={raggruppa === 'giorno'} onclick={() => (raggruppa = 'giorno')}>Per giorno</button>
            <button type="button" aria-pressed={raggruppa === 'settimana'} onclick={() => (raggruppa = 'settimana')}>Per settimana</button>
          </div>
        </div>
        <div class="barre" style:gap={colonne.length > 40 ? '2px' : '6px'} role="img" aria-label="Incassi {periodo}, {raggruppa === 'giorno' ? 'per giorno' : 'per settimana'}">
          {#each colonne as c (c.da)}
            <span class="colonna" class:oggi={c.a === oggi} style:height="{(c.totale / massimo) * 100}%" title="{c.da === c.a ? dataBreve(c.da) : `${dataBreve(c.da)} – ${dataBreve(c.a)}`}: {euro(c.totale)}">
              {#each c.pezzi as x, i (i)}{#if x > 0}<span style:height="{(x / c.totale) * 100}%" style:background={coloreMetodo(i)}></span>{/if}{/each}
            </span>
          {/each}
        </div>
        <div class="etichette" style:gap={colonne.length > 40 ? '2px' : '6px'}>{#each colonne as c (c.da)}<span class="m">{colonne.length > 40 ? '' : c.etichetta}</span>{/each}</div>
      </section>
    {/if}

    <div class="colonne">
      <div class="sx">
        <section class="blocco">
          <h2 class="lbl">Pagamenti · {validi.length}</h2>
          <div class="card tabella">
            <table>
              <thead><tr><th class="lbl">Data</th><th class="lbl">Chi paga</th><th class="lbl">Per</th><th class="lbl">Metodo</th><th class="lbl dx-t">Importo</th></tr></thead>
              <tbody>
                {#each dati.pagamenti.slice(0, quanti) as p (p.id)}
                  <tr class:annullato={p.annullatoIl}>
                    <td class="m muted">{dataBreve(p.data)} {oraDi(p.registratoIl)}</td>
                    <td><strong>{p.pagante}</strong></td>
                    <td class="muted">{per(p)}</td>
                    <td>{#each p.metodi as m (m.id)}<span class="tag-m" class:da={m.daIncassare && !m.incassatoIl}><i style:background={colore(m.metodo)}></i>{m.metodo}</span> {/each}</td>
                    <td class="m imp dx-t">{euro(p.importo)}</td>
                  </tr>
                {:else}
                  <tr><td colspan="5" class="muted">Nessun pagamento in questo periodo.</td></tr>
                {/each}
              </tbody>
            </table>
          </div>
          {#if dati.pagamenti.length > quanti}<button class="btn btn-secondary btn-sm centro" onclick={() => (quanti = dati!.pagamenti.length)}>Mostra tutti i {dati.pagamenti.length} pagamenti</button>{/if}
        </section>
      </div>
      <aside class="dx">
        <section class="card metodi">
          <h2 class="lbl">Per metodo</h2>
          {#each metodi as m (m.metodoId)}
            <div class="m-riga">
              <span class="m-nome"><i style:background={colore(m.metodo)}></i>{m.metodo}</span>
              <span class="traccia"><span style:width="{dati.totale ? (m.importo / dati.totale) * 100 : 0}%" style:background={colore(m.metodo)}></span></span>
              <span class="m imp">{euro(m.importo)}</span>
            </div>
          {:else}
            <span class="muted small">Nessun incasso.</span>
          {/each}
        </section>
        <DaIncassareLista {aperti} {oggi} onincassa={incassa} />
      </aside>
    </div>
  {:else if vista === 'chiusure'}
    <div class="tiles">
      <div class="tile hero-t"><span class="lbl">{periodo}</span><span class="v">{chiusure.length}</span><span class="n">{chiusure.length === 1 ? 'chiusura di cassa' : 'chiusure di cassa'}</span></div>
      {#if avvisi?.daControllare}
        <div class="tile arancio"><span class="lbl">Da controllare</span><span class="v">{avvisi.daControllare}</span><span class="n">{daControllare.length === avvisi.daControllare ? 'in questo periodo' : `${daControllare.length} in questo periodo`}</span></div>
      {:else}
        <div class="tile" class:arancio={nonTornano > 0}><span class="lbl">Non tornano</span><span class="v">{nonTornano}</span><span class="n">{nonTornano ? 'con una differenza' : 'tutte tornano'}</span></div>
      {/if}
      <div class="tile"><span class="lbl">Differenze</span><span class="v">{conSegno(differenze, euro)}</span><span class="n">in più e in meno, sommate</span></div>
      {#if avvisi?.inCassaforte != null}
        <div class="tile"><span class="lbl">In cassaforte</span><span class="v">{euro(avvisi.inCassaforte)}</span><span class="n">da portare in banca</span></div>
      {:else}
        <div class="tile"><span class="lbl">Versato</span><span class="v">{euro(versato)}</span><span class="n">tolto dal cassetto alle chiusure</span></div>
      {/if}
    </div>
    <div class="colonne-ch">
      <section class="blocco princ-ch">
        <div class="filtri-ch" role="group" aria-label="Quali chiusure">
          <button type="button" class="chip" aria-pressed={!soloDaControllare} onclick={() => (soloDaControllare = false)}>Tutte</button>
          <button type="button" class="chip" aria-pressed={soloDaControllare} onclick={() => (soloDaControllare = true)}>Da controllare{#if daControllare.length}<span class="n-ch">{daControllare.length}</span>{/if}</button>
        </div>
        <ul class="card lista-ch">
          {#each visteChiusure as s (s.id)}<ChiusuraRiga chiusura={s} conCassa={piuCasse} cambiata={chiusuraCambiata} />
          {:else}<li class="muted small vuoto">{soloDaControllare ? 'Niente da controllare in questo periodo.' : 'Nessuna chiusura in questo periodo.'}</li>{/each}
        </ul>
        <p class="muted small nomargin">Il riepilogo di ogni chiusura si stampa da firmare e conservare. Per correggere l'ultima chiusura (es. contati sbagliati) si riapre dalla cassa di oggi.</p>
      </section>
      <aside class="lato-ch">
        {#if avvisi?.aperteDaTroppo.length}
          <section class="card pann-ch">
            <span class="lbl">Avvisi</span>
            {#each avvisi.aperteDaTroppo as x (x.cassaId)}
              <p class="avviso-ch"><Icona nome="attesa" /><span>«{x.cassa}» è aperta dal {dataBreve(x.apertaIl.slice(0, 10))} alle {oraDi(x.apertaIl)}{x.apertaDa ? `, da ${x.apertaDa}` : ''}.</span></p>
            {/each}
          </section>
        {/if}
        {#if avvisi?.inCassaforte != null}<CassaforteBox cambiata={carica} />{/if}
        {#if persone.length}
          <section class="card pann-ch">
            <span class="lbl">Chi ha chiuso · {periodo.toLowerCase()}</span>
            <div class="chi-t" role="table" aria-label="Chi ha chiuso">
              <div class="r-ch th" role="row"><span role="columnheader">Persona</span><span role="columnheader" class="n">Chiusure</span><span role="columnheader" class="n">Differenze</span></div>
              {#each persone as p (p.chi)}
                <div class="r-ch" role="row"><span role="cell">{p.chi}</span><span role="cell" class="n">{p.chiusure}</span><span role="cell" class="n" class:meno={p.differenze < 0}>{conSegno(p.differenze, euro)}</span></div>
              {/each}
            </div>
            <span class="muted small">Per vedere se le differenze si ripetono: anche i soldi in più sono un segnale (un resto sbagliato a un cliente).</span>
          </section>
        {/if}
      </aside>
    </div>
  {:else}
    <div class="tiles">
      <div class="tile"><span class="lbl">Entrate</span><span class="v">{euro(dati.totale)}</span><span class="n">{validi.length === 1 ? '1 pagamento' : `${validi.length} pagamenti`}</span></div>
      <div class="tile"><span class="lbl">Uscite</span><span class="v">{restituiti ? '−' : ''}{euro(restituiti / 100)}</span><span class="n">{dati.uscite.length === 1 ? '1 restituzione' : `${dati.uscite.length} restituzioni`}</span></div>
      <div class="tile hero-t"><span class="lbl">Contanti {da === a ? 'del giorno' : 'del periodo'}</span><span class="v">{euro(cassa.entrati - cassa.usciti)}</span><span class="n">entrati meno restituiti, senza il fondo</span></div>
      <div class="tile" class:arancio={aperti.length > 0}><span class="lbl">Da incassare</span><span class="v">{euro(daIncassareTot)}</span><span class="n">{aperti.length === 1 ? '1 in attesa' : `${aperti.length} in attesa`}</span></div>
    </div>
    <div class="colonne">
      <div class="sx">
        <section class="blocco">
          <h2 class="lbl">Registro · {periodo}</h2>
          <div class="card tabella">
            <table>
              <thead><tr><th class="lbl">{da === a ? 'Ora' : 'Quando'}</th><th class="lbl">Cosa</th><th class="lbl">Metodo</th><th class="lbl dx-t">Entrata</th><th class="lbl dx-t">Uscita</th>
                <th class="lbl dx-t" title="Contanti entrati meno restituiti fino a questa riga">Contanti</th></tr></thead>
              <tbody>
                {#each righeRegistro.slice(0, quantiRegistro) as r (r.id)}
                  <tr class:annullato={r.annullato}>
                    <td class="m muted">{da === a ? '' : dataBreve(r.istante.slice(0, 10)) + ' '}{oraDi(r.istante)}</td>
                    <td class="cosa-c"><span class="col"><strong>{r.chi}</strong><span class="muted small">{r.per}{r.annullato && r.motivo ? ` · annullato: ${r.motivo}` : r.annullato ? ' · annullato' : ''}</span></span></td>
                    <td><span class="tag-m"><i style:background={colore(r.metodo.split(' + ')[0])}></i>{r.metodo}</span></td>
                    <td class="m imp dx-t verde">{r.entrata != null ? euro(r.entrata) : ''}</td>
                    <td class="m imp dx-t rosso">{r.uscita != null ? `−${euro(r.uscita)}` : ''}</td>
                    <td class="m dx-t muted">{r.contanti != null ? euro(r.contanti) : ''}</td>
                  </tr>
                {:else}
                  <tr><td colspan="6" class="muted">Nessun movimento in questo periodo.</td></tr>
                {/each}
              </tbody>
            </table>
          </div>
          {#if righeRegistro.length > quantiRegistro}<button class="btn btn-secondary btn-sm centro" onclick={() => (quantiRegistro = righeRegistro.length)}>Mostra tutte le {righeRegistro.length} righe</button>{/if}
          <p class="muted small nomargin">La colonna «Contanti» segue solo i contanti, senza il fondo cassa. Gli annullati restano, barrati, con il motivo.</p>
        </section>
      </div>
      <aside class="dx">
        <DaIncassareLista {aperti} {oggi} onincassa={incassa} />
        <section class="card metodi">
          <h2 class="lbl">Per metodo</h2>
          {#each metodi as m (m.metodoId)}
            <span class="riga-m"><span class="m-nome"><i style:background={colore(m.metodo)}></i>{m.metodo}</span><span class="m imp">{euro(m.importo)}</span></span>
          {/each}
          <span class="riga-m tot"><span>Totale</span><span class="m imp">{euro(dati.totale)}</span></span>
          <button class="btn btn-primary" onclick={() => { da = a = oggi; cambiaVista('cassa'); }}>Vai alla cassa di oggi</button>
        </section>
      </aside>
    </div>
  {/if}
</div>

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-16); }
  .barra { display: flex; flex-wrap: wrap; gap: var(--space-10); align-items: center; }
  .chips { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .giorno-nav { display: flex; align-items: center; gap: var(--space-4); }
  .giorno-t { font-weight: 700; font-size: var(--text-lg); min-width: 180px; text-align: center; }
  .data { width: 160px; height: 40px; }
  .sr-testo { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
  .seg.tre { grid-template-columns: repeat(3, auto); }
  .seg.tre button, .seg.due button { padding: 0 var(--space-12); }
  .seg.due { grid-template-columns: 1fr 1fr; }
  .viste { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-16); }
  .lbl { margin: 0; }

  .colonne { display: flex; flex-wrap: wrap; gap: var(--space-20); align-items: flex-start; }
  .sx { flex: 7 1 520px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-16); }
  .dx { flex: 4 1 320px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-16); }
  .blocco { display: flex; flex-direction: column; gap: var(--space-8); }
  .b-testa { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; min-height: 32px; }
  .col { display: flex; flex-direction: column; min-width: 0; line-height: 1.3; }
  .tronca { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .imp { font-weight: 700; white-space: nowrap; }
  .rosso { color: var(--ferma); }
  .verde { color: var(--passa); }
  .annullato { opacity: 0.5; text-decoration: line-through; }

  .hero { display: flex; flex-direction: column; gap: var(--space-14); padding: var(--space-20) 22px; border-radius: var(--radius-xl); background: var(--hero); color: var(--on-ink); }
  .hero-testa { display: flex; align-items: flex-end; gap: var(--space-16); flex-wrap: wrap; }
  .chiaro { color: var(--on-ink-soft); }
  .tenue { color: var(--on-ink-faint); }
  .grande { font-family: var(--font-display); font-size: 44px; font-weight: 800; line-height: 1; color: var(--on-ink); }
  .barra-metodi { display: flex; height: 12px; border-radius: 6px; overflow: hidden; background: var(--on-ink-track); }
  .legenda { display: flex; flex-wrap: wrap; gap: var(--space-6) var(--space-16); }
  .legenda span { display: inline-flex; align-items: center; gap: var(--space-6); font-size: var(--text-sm); }
  .legenda.chiaro span { color: var(--on-ink); }
  .legenda i, .m-nome i, .tag-m i { width: 10px; height: 10px; border-radius: 3px; display: inline-block; flex-shrink: 0; }

  .pagamenti { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .pagamenti li { display: grid; grid-template-columns: 48px 96px minmax(0, 1fr) auto; gap: var(--space-14); align-items: center; padding: var(--space-10) var(--space-16); border-top: 1px solid var(--line-soft); }
  .pagamenti li:first-child { border-top: none; }
  .pagamenti .vuoto { display: block; padding: var(--space-16); }
  .ora { font-size: var(--text-sm); color: var(--muted); }
  .tags { display: flex; gap: var(--space-6); align-items: center; justify-content: flex-end; flex-wrap: wrap; }
  .tag-m { display: inline-flex; align-items: center; gap: var(--space-6); height: 22px; padding: 0 9px; border-radius: var(--radius-pill); background: var(--surface-sunken);
    color: var(--seg-fg); font-size: var(--text-xs); font-weight: 600; white-space: nowrap; text-decoration: none; }
  .tag-m.da { background: var(--warn-bg); color: var(--warn); }
  .tag-m i { width: 8px; height: 8px; border-radius: 2px; }

  .tiles { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-10); }
  .tiles.cinque { grid-template-columns: repeat(5, minmax(0, 1fr)); }
  .tile { display: flex; flex-direction: column; gap: 2px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line); min-width: 0; }
  .tile .v { font-family: var(--font-display); font-size: 26px; font-weight: 800; line-height: 1.1; white-space: nowrap; }
  .tile .n { font-size: var(--text-xs); color: var(--muted); }
  .tile.arancio { background: var(--warn-bg); border-color: transparent; } .tile.arancio .v { color: var(--warn); }
  .tile.hero-t { background: var(--hero); color: var(--on-ink); border-color: transparent; }
  .tile.hero-t .lbl, .tile.hero-t .n { color: var(--on-ink-muted); }

  .grafico { padding: var(--space-16) var(--space-18, 18px); display: flex; flex-direction: column; gap: var(--space-12); }
  .g-testa { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; }
  .g-testa h2 { margin: 0; font-size: var(--text-lg); }
  .barre { display: flex; align-items: flex-end; height: 200px; border-bottom: 1px solid var(--line); }
  .colonna { flex: 1 1 0; min-width: 0; display: flex; flex-direction: column-reverse; border-radius: 4px 4px 0 0; overflow: hidden; }
  .colonna.oggi { box-shadow: 0 0 0 2px var(--hero); }
  .etichette { display: flex; }
  .etichette span { flex: 1 1 0; min-width: 0; text-align: center; font-size: 11px; color: var(--muted); }

  .tabella { padding: 0; overflow-x: auto; }
  table { width: 100%; border-collapse: collapse; font-size: var(--text-md); min-width: 680px; }
  th { text-align: left; padding: var(--space-10) var(--space-12); border-bottom: 1px solid var(--line); }
  td { padding: var(--space-10) var(--space-12); border-top: 1px solid var(--line-soft); vertical-align: middle; }
  th:first-child, td:first-child { padding-left: var(--space-16); }
  th:last-child, td:last-child { padding-right: var(--space-16); }
  td.m { font-size: var(--text-sm); white-space: nowrap; }
  .dx-t { text-align: right; }
  .cosa-c { min-width: 260px; width: 45%; }
  .centro { align-self: center; }

  .metodi { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); }
  .m-riga { display: grid; grid-template-columns: 140px minmax(0, 1fr) 100px; gap: var(--space-12); align-items: center; }
  .m-nome { display: inline-flex; align-items: center; gap: var(--space-8); font-size: var(--text-md); font-weight: 600; }
  .traccia { height: 8px; border-radius: 4px; background: var(--surface-sunken); overflow: hidden; }
  .traccia span { display: block; height: 100%; }
  .m-riga .imp { text-align: right; font-size: var(--text-md); }
  .riga-m { display: flex; justify-content: space-between; font-size: var(--text-md); }
  .riga-m.tot { padding-top: var(--space-8); border-top: 1px solid var(--line); font-weight: 700; }
  .chiusure-g { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); }
  .chiusure-g .lista-ch { margin: 0 calc(-1 * var(--space-16)); }
  .lista-ch { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .colonne-ch { display: flex; flex-wrap: wrap; gap: var(--space-18, 18px); align-items: flex-start; }
  .princ-ch { flex: 999 1 600px; min-width: 0; }
  .lato-ch { flex: 1 1 300px; max-width: 380px; display: flex; flex-direction: column; gap: var(--space-14); }
  .filtri-ch { display: flex; gap: var(--space-8); flex-wrap: wrap; }
  .n-ch { margin-left: var(--space-6); min-width: 20px; height: 20px; padding: 0 6px; border-radius: 999px; background: var(--warn-bg); color: var(--warn); font-size: var(--text-xs); display: inline-grid; place-items: center; }
  .pann-ch { padding: var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); }
  .avviso-ch { display: flex; gap: var(--space-8); align-items: flex-start; margin: 0; padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn); font-size: var(--text-sm); font-weight: 600; }
  .avviso-ch :global(.ic) { width: 18px; height: 18px; flex-shrink: 0; }
  .chi-t { display: flex; flex-direction: column; font-size: var(--text-sm); }
  .r-ch { display: grid; grid-template-columns: minmax(0, 1fr) 72px 96px; gap: var(--space-8); padding: 7px 0; border-top: 1px solid var(--line-soft); }
  .r-ch.th { border-top: none; padding-top: 0; font-size: 11px; font-weight: 700; letter-spacing: .07em; text-transform: uppercase; color: var(--muted); }
  .r-ch .n { text-align: right; }
  .r-ch:not(.th) .n { font-family: var(--font-mono); }
  .r-ch .meno { color: var(--ferma-fg); }
  .lista-ch .vuoto { display: block; padding: var(--space-16); }

  @media (max-width: 900px) {
    .tiles, .tiles.cinque { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .pagamenti li { grid-template-columns: 44px minmax(0, 1fr) auto; }
    .pagamenti li .col { grid-column: 1 / -1; grid-row: 2; }
  }
</style>
