<script lang="ts">
  import Azione from '../components/Azione.svelte';
  import Icona from '../components/Icona.svelte';
  import { api, messaggioErrore, type Scadenze, type VoceScadenza } from '../lib/api';
  import {
    nelFiltro, perGiornoScadenze, promemoriaPossibile, quando, RIMEDI, settimane, tipoBreve, urgenza, URGENZE,
    type FiltroTipo, type TipoBreve, type VistaScadenze,
  } from '../lib/cassa';
  import { aggiungiGiorni, dataBreve, dataDi, daIso, GIORNI, MESI } from '../lib/date';
  import type { NomeIcona } from '../lib/icone';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { schermo } from '../lib/schermo.svelte';

  /**
   * Le scadenze per la segreteria: chi frequenta e ha il certificato, l'abbonamento, il carnet o il tesseramento
   * scaduto, mancante o in scadenza. Due viste a scelta (si ricorda per account): l'elenco per urgenza, con la scelta
   * di più persone per mandare i promemoria insieme, e il calendario delle prossime cinque settimane.
   */
  let vista = $state<VistaScadenze>(leggiPreferenza<VistaScadenze>('scadenze.vista', 'elenco', ['elenco', 'calendario']));
  $effect(() => salvaPreferenza('scadenze.vista', vista));

  let entro = $state(30);
  let dati = $state<Scadenze | null>(null);
  let filtro = $state<FiltroTipo>('tutti');
  let cerca = $state('');
  let errore = $state('');
  let messaggio = $state('');
  let inviando = $state('');
  let scelte = $state<Record<string, boolean>>({});
  /** quante righe per gruppo: i gruppi lunghi si aprono a richiesta */
  let aperti = $state<Record<string, number>>({});
  const PASSO = 25;
  const eta = (n: number | null) => (n == null ? '' : n === 1 ? '1 anno' : `${n} anni`);

  // il calendario mostra cinque settimane: chiede le scadenze fino alla fine dell'ultima
  const giorniRichiesti = $derived(vista === 'calendario' ? 36 : entro);
  async function carica() {
    errore = '';
    try {
      dati = await api.scadenze(giorniRichiesti);
    } catch (e) {
      errore = messaggioErrore(e, 'Non riesco a caricare le scadenze.');
    }
  }
  $effect(() => {
    void giorniRichiesti;
    carica();
  });

  const chiave = (v: VoceScadenza) => `${v.tipo}|${v.personaId}|${v.riferimento}`;
  const tutte = $derived(dati ? [...dati.certificati, ...dati.abbonamenti, ...dati.tesseramento] : []);
  const cercate = $derived.by(() => {
    const q = cerca.trim().toLowerCase();
    return tutte.filter((v) => !q || `${v.nome} ${v.cognome}`.toLowerCase().includes(q) || `${v.cognome} ${v.nome}`.toLowerCase().includes(q));
  });
  const filtrate = $derived(cercate.filter((v) => nelFiltro(v, filtro)).sort((a, b) => (a.giorni ?? -9999) - (b.giorni ?? -9999)));
  const gruppi = $derived(URGENZE.map((u) => ({ ...u, voci: filtrate.filter((v) => urgenza(v) === u.id) })).filter((g) => g.voci.length));
  const conta = (u: string) => tutte.filter((v) => urgenza(v) === u).length;
  const FILTRI: { id: FiltroTipo; nome: string }[] = [
    { id: 'tutti', nome: 'Tutte' }, { id: 'cert', nome: 'Certificati' }, { id: 'abb', nome: 'Abbonamenti e carnet' }, { id: 'tess', nome: 'Tesseramento' },
  ];
  const ICONE: Record<TipoBreve, NomeIcona> = { cert: 'certificato', abb: 'euro', carnet: 'carnet', tess: 'tesserino' };

  // ── selezione e promemoria ──
  const selezionate = $derived(tutte.filter((v) => scelte[chiave(v)]));
  const daInviare = $derived(selezionate.filter(promemoriaPossibile));
  function scegli(v: VoceScadenza) {
    scelte = { ...scelte, [chiave(v)]: !scelte[chiave(v)] };
  }
  function scegliGruppo(voci: VoceScadenza[]) {
    const s = { ...scelte };
    voci.forEach((v) => (s[chiave(v)] = true));
    scelte = s;
  }

  function esito(v: VoceScadenza, stato: string, err: string | null): string {
    return stato === 'INVIATA' ? `Promemoria inviato a ${v.email}.`
      : stato === 'NON_INVIATA' ? 'Promemoria registrato, ma non partito: il server di posta non è configurato.'
      : stato === 'SENZA_EMAIL' ? `${v.nome} ${v.cognome} non ha un'email nella scheda.`
      : `Invio non riuscito: ${err ?? 'errore del server di posta'}.`;
  }
  async function invia(v: VoceScadenza) {
    inviando = chiave(v);
    errore = messaggio = '';
    try {
      const r = await api.inviaPromemoria({ personaId: v.personaId, tipo: v.tipo, riferimento: v.riferimento });
      messaggio = esito(v, r.stato, r.errore);
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      inviando = '';
    }
  }
  async function inviaScelte() {
    const lista = daInviare;
    inviando = 'tutte';
    errore = messaggio = '';
    let ok = 0, ko = 0;
    for (const v of lista) {
      try {
        const r = await api.inviaPromemoria({ personaId: v.personaId, tipo: v.tipo, riferimento: v.riferimento });
        if (r.stato === 'INVIATA' || r.stato === 'NON_INVIATA') ok++; else ko++;
      } catch {
        ko++;
      }
    }
    messaggio = `${ok === 1 ? '1 promemoria registrato' : `${ok} promemoria registrati`}${ko ? `, ${ko} non riusciti` : ''}${dati && !dati.postaConfigurata ? ' (il server di posta non è configurato: non partono)' : ''}.`;
    scelte = {};
    inviando = '';
    await carica();
  }

  /** L'elenco mostrato in CSV (per le telefonate, o per Excel). */
  function scarica() {
    const righe = [['Cognome', 'Nome', 'Età', 'Cosa', 'Scadenza', 'Email', 'Ultimo promemoria']];
    for (const v of filtrate)
      righe.push([v.cognome, v.nome, v.eta == null ? '' : String(v.eta), v.descrizione, v.scadenza ? dataBreve(v.scadenza) : '', v.email ?? '',
        v.ultimoPromemoria ? dataBreve(dataDi(v.ultimoPromemoria)) : '']);
    const csv = righe.map((r) => r.map((c) => `"${c.replaceAll('"', '""')}"`).join(';')).join('\r\n');
    const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    const a = Object.assign(document.createElement('a'), { href: url, download: `scadenze-${dati?.oggi ?? ''}.csv` });
    a.click();
    URL.revokeObjectURL(url);
  }

  // ── calendario ──
  const oggi = $derived(dati?.oggi ?? '');
  const righe = $derived(oggi ? settimane(oggi, 5) : []);
  const perGiorno = $derived(perGiornoScadenze(filtrate));
  let giorno = $state('');
  const giornoScelto = $derived(giorno || oggi);
  const vociGiorno = $derived(perGiorno.get(giornoScelto) ?? []);
  const scadute = $derived(filtrate.filter((v) => urgenza(v) === 'scaduti'));
  const nomeGiorno = (d: string) => { const x = daIso(d); return `${GIORNI[x.getDay()]} ${x.getDate()} ${MESI[x.getMonth()]}`; };
  const numero = (d: string) => { const x = daIso(d); return x.getDate() === 1 ? `1 ${MESI[x.getMonth()].slice(0, 3)}` : String(x.getDate()); };
</script>

<div class="pagina">
  <div class="barra">
    <div class="seg viste" role="group" aria-label="Come mostrare le scadenze">
      <button type="button" aria-pressed={vista === 'elenco'} onclick={() => (vista = 'elenco')}>Elenco</button>
      <button type="button" aria-pressed={vista === 'calendario'} onclick={() => (vista = 'calendario')}>Calendario</button>
    </div>
    {#if vista === 'elenco'}
      <div class="seg entro" role="group" aria-label="Entro quanti giorni">
        {#each [7, 15, 30, 60] as g (g)}<button type="button" aria-pressed={entro === g} onclick={() => (entro = g)}>{g} giorni</button>{/each}
      </div>
    {/if}
    <span class="grow"></span>
    <button class="btn btn-secondary" onclick={scarica} disabled={!filtrate.length}><Icona nome="scarica" />Scarica elenco</button>
    <a class="btn btn-secondary" href="#/promemoria"><Icona nome="promemoria" />Promemoria email…</a>
  </div>

  {#if dati && !dati.postaConfigurata}
    <p class="hint nomargin">Il server di posta non è ancora configurato: i promemoria si registrano (si vedono nella scheda e nei promemoria) ma non partono.</p>
  {/if}
  {#if messaggio}<p class="msg ok nomargin" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err nomargin" role="alert">{errore}</p>{/if}

  {#if vista === 'elenco'}
    <div class="riep">
      <div class="tile rosso"><span class="lbl">Scaduti o mancanti</span><span class="v">{conta('scaduti')}</span><span class="n">non passano il tornello</span></div>
      <div class="tile arancio"><span class="lbl">Scadono oggi</span><span class="v">{conta('oggi')}</span><span class="n">da domani rossi al tornello</span></div>
      <div class="tile"><span class="lbl">Entro una settimana</span><span class="v">{conta('settimana')}</span><span class="n">fino a {oggi ? dataBreve(aggiungiGiorni(oggi, 7)) : '…'}</span></div>
      <div class="tile"><span class="lbl">Più avanti</span><span class="v">{conta('avanti')}</span><span class="n">entro {entro} giorni</span></div>
    </div>
  {/if}

  <div class="barra">
    <div class="filtri" role="group" aria-label="Quali scadenze">
      {#each FILTRI as f (f.id)}
        <button type="button" class="chip" aria-pressed={filtro === f.id} onclick={() => (filtro = f.id)}>{f.nome}<span class="m conto">{cercate.filter((v) => nelFiltro(v, f.id)).length}</span></button>
      {/each}
    </div>
    <span class="grow"></span>
    <input class="inp cerca" type="search" bind:value={cerca} placeholder="Cerca per nome" aria-label="Cerca per nome" />
  </div>

  {#if !dati}
    {#if !errore}<p class="muted">Caricamento…</p>{/if}
  {:else if vista === 'elenco'}
    {#if selezionate.length}
      <div class="bulk" role="region" aria-label="Azioni sulle scadenze scelte">
        <strong>{selezionate.length === 1 ? '1 scelta' : `${selezionate.length} scelte`}</strong>
        <span class="bulk-n">{daInviare.length} con l'email{selezionate.length > daInviare.length ? ' · le altre vanno chiamate' : ''}</span>
        <span class="grow"></span>
        <button type="button" class="b-chiaro" disabled={!daInviare.length || inviando === 'tutte'} onclick={inviaScelte}>
          <Icona nome="promemoria" />{inviando === 'tutte' ? 'Invio…' : `Invia ${daInviare.length} promemoria`}</button>
        <button type="button" class="b-vuoto" onclick={() => (scelte = {})}>Deseleziona</button>
      </div>
    {/if}

    {#each gruppi as g (g.id)}
      <section class="gruppo">
        <div class="g-testa">
          <h2 class="lbl {g.id}">{g.nome} · {g.voci.length}</h2>
          {#if g.spiega}<span class="muted small">{g.spiega}</span>{/if}
          <span class="grow"></span>
          <button type="button" class="link-btn" onclick={() => scegliGruppo(g.voci)}>Scegli il gruppo</button>
        </div>
        <ul class="card elenco">
          {#each g.voci.slice(0, aperti[g.id] ?? PASSO) as v (chiave(v))}
            {@const q = quando(v)}
            {@const t = tipoBreve(v.tipo)}
            <li class="voce" class:largo={schermo.larga} class:scelta={scelte[chiave(v)]}>
              <input type="checkbox" checked={!!scelte[chiave(v)]} onchange={() => scegli(v)} aria-label="Scegli {v.nome} {v.cognome}" />
              <span class="ico-t {t}" aria-hidden="true"><Icona nome={ICONE[t]} /></span>
              <span class="col">
                <a href={`#/clienti/${v.personaId}`} class="nome">{v.nome} {v.cognome}</a>
                <span class="sotto">{[eta(v.eta), v.corso].filter(Boolean).join(' · ')}</span>
              </span>
              <span class="col">
                <span class="cosa">{v.descrizione}</span>
                <span class="sotto">{v.scadenza ? `${(v.giorni ?? 0) < 0 ? 'scaduto' : 'scade'} il ${dataBreve(v.scadenza)}` : v.tipo === 'CARNET' ? 'finito' : 'mai registrato'}</span>
              </span>
              <span><span class="quando {q.classe}">{q.testo}</span></span>
              <span class="col contatto">
                {#if v.email}<span class="tronca">{v.email}</span>{:else}<span class="manca">Nessuna email</span>{/if}
                <span class="sotto">{v.ultimoPromemoria ? `promemoria il ${dataBreve(dataDi(v.ultimoPromemoria))}` : 'nessun promemoria'}</span>
              </span>
              <span class="azioni">
                <a class="link-r" href={`#/clienti/${v.personaId}`}>{RIMEDI[t]} →</a>
                <Azione icona="promemoria" etichetta={promemoriaPossibile(v) ? `Invia un promemoria a ${v.nome} ${v.cognome}` : v.email ? 'Per il certificato mai consegnato non c’è un promemoria' : "Aggiungi l'email nella scheda"}
                        disabled={!promemoriaPossibile(v) || inviando === chiave(v)} onclick={() => invia(v)} />
              </span>
            </li>
          {/each}
        </ul>
        {#if g.voci.length > (aperti[g.id] ?? PASSO)}
          <button type="button" class="btn btn-secondary btn-sm altri-btn" onclick={() => (aperti = { ...aperti, [g.id]: (aperti[g.id] ?? PASSO) + 100 })}>
            Mostra altre {Math.min(100, g.voci.length - (aperti[g.id] ?? PASSO))} di {g.voci.length - (aperti[g.id] ?? PASSO)}</button>
        {/if}
      </section>
    {:else}
      <p class="muted vuoto card">{cerca ? 'Nessuno con questo nome.' : 'Niente in scadenza: tutti in regola.'}</p>
    {/each}
    <p class="muted small nomargin">I promemoria automatici partono ogni mattina (Impostazioni › Promemoria email). Quelli inviati da qui si vedono nella scheda del cliente.</p>
  {:else}
    <div class="calendario">
      <section class="scadute">
        <h2 class="lbl scaduti">Già scaduti o mancanti · {scadute.length}</h2>
        <ul class="card mini">
          {#each scadute.slice(0, 12) as v (chiave(v))}
            {@const t = tipoBreve(v.tipo)}
            <li>
              <span class="ico-t {t} piccola" aria-hidden="true"><Icona nome={ICONE[t]} /></span>
              <span class="col"><a href={`#/clienti/${v.personaId}`} class="nome">{v.nome} {v.cognome}</a><span class="rosso-t">{v.descrizione} · {quando(v).testo.toLowerCase()}</span></span>
            </li>
          {:else}
            <li class="muted small">Nessuno.</li>
          {/each}
          {#if scadute.length > 12}<li><button type="button" class="link-btn" onclick={() => (vista = 'elenco')}>E altri {scadute.length - 12}: vedili nell'elenco →</button></li>{/if}
        </ul>
        <p class="muted small nomargin">Chi è qui prende rosso al tornello e finisce tra i «da sistemare» del banco.</p>
      </section>

      <section class="mese">
        <div class="legenda">
          <span><i class="cert"></i>Certificati</span><span><i class="abb"></i>Abbonamenti</span><span><i class="carnet"></i>Carnet</span><span><i class="tess"></i>Tesseramento</span>
        </div>
        <div class="griglia intest" aria-hidden="true">{#each ['lun', 'mar', 'mer', 'gio', 'ven', 'sab', 'dom'] as d (d)}<span class="lbl">{d}</span>{/each}</div>
        {#each righe as s (s[0])}
          <div class="griglia">
            {#each s as d, i (d)}
              {@const voci = perGiorno.get(d) ?? []}
              <button type="button" class="giorno" class:passato={d < oggi} class:oggi={d === oggi} aria-pressed={d === giornoScelto}
                      aria-label="{nomeGiorno(d)}: {voci.length === 1 ? '1 scadenza' : `${voci.length} scadenze`}" onclick={() => (giorno = d)}>
                <span class="g-testa"><span class="m num" class:festivo={i >= 5}>{numero(d)}</span>{#if d === oggi}<span class="oggi-t">oggi</span>{/if}
                  <span class="grow"></span>{#if voci.length}<span class="m conto">{voci.length}</span>{/if}</span>
                {#each voci.slice(0, 3) as v (chiave(v))}<span class="nome-g {tipoBreve(v.tipo)}"><i></i>{v.nome} {v.cognome}</span>{/each}
                {#if voci.length > 3}<span class="altri">+ altre {voci.length - 3}</span>{/if}
              </button>
            {/each}
          </div>
        {/each}

        <div class="card dettaglio">
          <div class="d-testa">
            <h2>{giornoScelto ? nomeGiorno(giornoScelto) : ''}</h2>
            <span class="muted small">{vociGiorno.length === 1 ? '1 scadenza' : `${vociGiorno.length} scadenze`}</span>
            <span class="grow"></span>
            {#if vociGiorno.filter(promemoriaPossibile).length > 1}
              <button class="btn btn-secondary btn-sm" disabled={inviando === 'tutte'}
                      onclick={() => { const s: Record<string, boolean> = {}; vociGiorno.forEach((v) => (s[chiave(v)] = true)); scelte = s; inviaScelte(); }}>
                <Icona nome="promemoria" />Promemoria a tutti</button>
            {/if}
          </div>
          {#each vociGiorno as v (chiave(v))}
            {@const t = tipoBreve(v.tipo)}
            <div class="d-riga">
              <span class="ico-t {t}" aria-hidden="true"><Icona nome={ICONE[t]} /></span>
              <span class="col"><a href={`#/clienti/${v.personaId}`} class="nome">{v.nome} {v.cognome}</a><span class="sotto">{eta(v.eta)}</span></span>
              <span class="cosa">{v.descrizione}</span>
              <span class="azioni">
                <a class="link-r" href={`#/clienti/${v.personaId}`}>{RIMEDI[t]} →</a>
                <Azione icona="promemoria" etichetta={promemoriaPossibile(v) ? `Invia un promemoria a ${v.nome} ${v.cognome}` : "Aggiungi l'email nella scheda"}
                        disabled={!promemoriaPossibile(v) || inviando === chiave(v)} onclick={() => invia(v)} />
              </span>
            </div>
          {:else}
            <span class="muted small">Niente in scadenza questo giorno.</span>
          {/each}
        </div>
      </section>
    </div>
  {/if}
</div>

<style>
  .pagina { display: flex; flex-direction: column; gap: var(--space-16); }
  .barra { display: flex; flex-wrap: wrap; gap: var(--space-10); align-items: center; }
  .barra a.btn { text-decoration: none; display: inline-flex; align-items: center; }
  .viste { grid-template-columns: 1fr 1fr; min-width: 220px; }
  .entro { grid-template-columns: repeat(4, auto); }
  .entro button { padding: 0 var(--space-12); }
  .filtri { display: flex; gap: var(--space-6); flex-wrap: wrap; }
  .conto { margin-left: var(--space-6); opacity: 0.65; }
  .cerca { width: 240px; }

  .riep { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-10); }
  .tile { display: flex; flex-direction: column; gap: 2px; padding: var(--space-12) var(--space-14); border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line); }
  .tile .v { font-family: var(--font-display); font-size: 28px; font-weight: 800; line-height: 1.1; }
  .tile .n { font-size: var(--text-xs); color: var(--muted); }
  .tile.rosso { background: var(--ferma-bg); border-color: transparent; } .tile.rosso .v { color: var(--ferma); }
  .tile.arancio { background: var(--warn-bg); border-color: transparent; } .tile.arancio .v { color: var(--warn); }

  .bulk { display: flex; align-items: center; gap: var(--space-12); flex-wrap: wrap; padding: var(--space-10) var(--space-14); border-radius: var(--radius-md); background: var(--hero); color: var(--on-fill); }
  .bulk-n { font-size: var(--text-sm); opacity: 0.75; }
  .b-chiaro, .b-vuoto { height: 36px; padding: 0 var(--space-14); border-radius: var(--radius-md); font: inherit; font-size: var(--text-sm); font-weight: 700; cursor: pointer;
    display: inline-flex; align-items: center; gap: var(--space-6); }
  .b-chiaro { border: none; background: var(--surface); color: var(--ink); }
  .b-chiaro:disabled { opacity: 0.6; cursor: default; }
  .b-vuoto { border: 1px solid color-mix(in srgb, var(--on-fill) 35%, transparent); background: transparent; color: var(--on-fill); font-weight: 600; }

  .gruppo { display: flex; flex-direction: column; gap: var(--space-8); }
  .g-testa { display: flex; align-items: center; gap: var(--space-10); min-height: 28px; }
  .g-testa h2 { margin: 0; }
  .lbl.scaduti { color: var(--ferma); }
  .lbl.oggi { color: var(--warn); }
  .link-btn { border: none; background: none; padding: 0; font: inherit; font-size: var(--text-sm); font-weight: 600; color: var(--cloro); cursor: pointer; }
  .elenco { list-style: none; margin: 0; padding: 0; overflow: hidden; }
  .voce { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-8) var(--space-12); padding: var(--space-10) var(--space-14); border-top: 1px solid var(--line-soft); }
  .voce.largo { display: grid; grid-template-columns: 22px 32px minmax(0, 1.3fr) minmax(0, 1.2fr) 150px minmax(0, 1fr) 236px; gap: var(--space-14); }
  .voce:first-child { border-top: none; }
  .voce.scelta { background: var(--cloro-soft); }
  .voce input { width: 18px; height: 18px; margin: 0; accent-color: var(--cloro); }
  .col { display: flex; flex-direction: column; min-width: 0; line-height: 1.3; }
  .nome { font-weight: 700; color: var(--ink); text-decoration: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .nome:hover { text-decoration: underline; }
  .sotto { font-size: var(--text-xs); color: var(--muted); }
  .cosa { font-size: var(--text-md); font-weight: 600; }
  .contatto { font-size: var(--text-xs); }
  .tronca { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .manca { color: var(--warn); font-weight: 600; }
  .azioni { display: flex; align-items: center; gap: var(--space-12); }
  .azioni .link-r { flex: 1; }
  .link-r { font-size: var(--text-sm); font-weight: 700; color: var(--cloro); text-decoration: none; white-space: nowrap; }
  .link-r:hover { text-decoration: underline; }
  .quando { display: inline-flex; align-items: center; height: 24px; padding: 0 var(--space-10); border-radius: var(--radius-pill); font-size: var(--text-xs); font-weight: 700; white-space: nowrap; }
  .quando.rosso { background: var(--ferma-bg); color: var(--ferma-fg); }
  .quando.arancio { background: var(--warn-bg); color: var(--warn); }
  .quando.neutro { background: var(--surface-sunken); color: var(--seg-fg); }
  .vuoto { padding: var(--space-20); text-align: center; margin: 0; }
  .altri-btn { align-self: center; }

  .ico-t { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; flex-shrink: 0; }
  .ico-t :global(.ic) { width: 17px; height: 17px; }
  .ico-t.piccola { width: 28px; height: 28px; border-radius: 8px; }
  .cert { --t-bg: var(--area-m-bg); --t-fg: var(--area-m-fg); --t: var(--area-m); }
  .abb { --t-bg: var(--cloro-soft); --t-fg: var(--cloro-ink); --t: var(--cloro); }
  .carnet { --t-bg: var(--area-p-bg); --t-fg: var(--area-p-fg); --t: var(--area-p); }
  .tess { --t-bg: var(--area-a-bg); --t-fg: var(--area-a-fg); --t: var(--area-a); }
  .ico-t { background: var(--t-bg); color: var(--t-fg); }

  /* calendario */
  .calendario { display: flex; flex-wrap: wrap; gap: var(--space-20); align-items: flex-start; }
  .scadute { flex: 1 1 260px; max-width: 320px; display: flex; flex-direction: column; gap: var(--space-8); }
  .scadute h2 { margin: 0; min-height: 28px; display: flex; align-items: center; }
  .mini { list-style: none; margin: 0; padding: 4px var(--space-14); }
  .mini li { display: flex; align-items: center; gap: var(--space-10); padding: 9px 0; border-top: 1px solid var(--line-soft); }
  .mini li:first-child { border-top: none; }
  .rosso-t { font-size: var(--text-xs); color: var(--ferma); font-weight: 600; }
  .mese { flex: 999 1 620px; min-width: 0; display: flex; flex-direction: column; gap: var(--space-6); }
  .legenda { display: flex; flex-wrap: wrap; gap: var(--space-6) var(--space-16); min-height: 28px; align-items: center; }
  .legenda span { display: inline-flex; align-items: center; gap: var(--space-6); font-size: var(--text-sm); }
  .legenda i { width: 10px; height: 10px; border-radius: 3px; background: var(--t); }
  .griglia { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: var(--space-6); }
  .griglia.intest .lbl { padding: 0 6px; }
  .giorno { min-height: 100px; display: flex; flex-direction: column; align-items: stretch; gap: 4px; padding: 7px 7px 8px; border: none; border-radius: var(--radius-md);
    background: var(--surface); box-shadow: inset 0 0 0 1px var(--line); font: inherit; text-align: left; cursor: pointer; min-width: 0; }
  .giorno.passato { background: transparent; opacity: 0.5; }
  .giorno.oggi { box-shadow: inset 0 0 0 2px var(--hero); }
  .giorno[aria-pressed='true'] { background: var(--cloro-soft); box-shadow: inset 0 0 0 2px var(--cloro); opacity: 1; }
  .giorno .g-testa { display: flex; align-items: center; gap: var(--space-6); min-height: 0; }
  .num { font-size: var(--text-sm); font-weight: 700; }
  .num.festivo { color: var(--muted); }
  .oggi-t { font-size: 11px; font-weight: 700; color: var(--cloro); }
  .giorno .conto { font-size: 11px; color: var(--muted); margin: 0; opacity: 1; }
  .nome-g { display: flex; align-items: center; gap: 5px; padding: 2px 6px; border-radius: 6px; background: var(--t-bg); color: var(--t-fg); font-size: 11px; font-weight: 600;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .nome-g i { width: 6px; height: 6px; border-radius: 2px; flex-shrink: 0; background: var(--t); }
  .altri { font-size: 11px; color: var(--muted); font-weight: 600; }
  .dettaglio { padding: var(--space-14) var(--space-16); display: flex; flex-direction: column; gap: var(--space-10); margin-top: var(--space-6); }
  .d-testa { display: flex; align-items: center; gap: var(--space-10); }
  .d-testa h2 { margin: 0; font-size: var(--text-lg); }
  .d-riga { display: grid; grid-template-columns: 32px minmax(0, 1fr) minmax(0, 1fr) 236px; gap: var(--space-12); align-items: center; padding-top: var(--space-10); border-top: 1px solid var(--line-soft); }

  @media (max-width: 900px) {
    .riep { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .cerca { width: 100%; }
    .scadute { max-width: none; }
    .d-riga { grid-template-columns: 32px minmax(0, 1fr); }
    .giorno { min-height: 64px; }
    .nome-g { display: none; }
  }
</style>
