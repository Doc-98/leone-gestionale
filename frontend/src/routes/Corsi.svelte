<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Foglio from '../components/Foglio.svelte';
  import { onMount } from 'svelte';
  import { api, messaggioErrore, type Corso, type FasciaOraria, type PersonaBreve, type SpazioPiano, type TipoCorso } from '../lib/api';
  import { iso } from '../lib/date';
  import { colore, eta, TIPI_CORSO } from '../lib/piano';
  import { doveBreve, riempimento, turniAttivi, turniPerGiorno } from '../lib/corsi';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import { schermo } from '../lib/schermo.svelte';
  import CorsoForm from './CorsoForm.svelte';
  import SchedaCorso from './SchedaCorso.svelte';

  /** L'elenco dei corsi, in schede per categoria o in righe; «#/corsi/<id>» apre la scheda del corso. */

  type Vista = 'categorie' | 'elenco';
  type Ordine = 'nome' | 'pieni' | 'liberi';

  let corsi = $state<Corso[]>([]);
  let spazi = $state<SpazioPiano[]>([]);
  let fasce = $state<FasciaOraria[]>([]);
  let personale = $state<PersonaBreve[]>([]);
  let gestiti = $state<string[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let nuovo = $state(false);

  let vista = $state<Vista>(leggiPreferenza<Vista>('corsi.vista', 'categorie', ['categorie', 'elenco']));
  $effect(() => salvaPreferenza('corsi.vista', vista));
  let soloAttivi = $state(true);
  let cerca = $state('');
  let categoria = $state<TipoCorso | ''>('');
  let istruttore = $state('');
  let soloAttesa = $state(false);
  let ordine = $state<Ordine>('nome');

  const oggi = iso(new Date());
  const ORDINE_CATEGORIE: TipoCorso[] = ['SCUOLA_NUOTO', 'ACQUATICITA', 'MASTER', 'AGONISMO', 'ACQUAGYM', 'CURRICOLARE', 'NUOTO_LIBERO', 'ALTRO'];
  const GIORNI = ['L', 'M', 'M', 'G', 'V', 'S', 'D'];
  const GIORNI_LUNGHI = ['lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato', 'domenica'];

  // ── Corso aperto dall'indirizzo ──
  const daIndirizzo = () => location.hash.split('/')[2] ?? null;
  let apertoId = $state<string | null>(daIndirizzo());
  onMount(() => {
    const f = () => (apertoId = daIndirizzo());
    window.addEventListener('hashchange', f);
    return () => window.removeEventListener('hashchange', f);
  });
  const aperto = $derived(apertoId ? corsi.find((c) => c.id === apertoId) ?? null : null);
  const apri = (c: Corso) => (location.hash = `#/corsi/${c.id}`);

  function carica() {
    return api.corsi().then((c) => (corsi = c));
  }

  onMount(() => {
    Promise.all([carica(), api.piano(oggi, oggi), api.personale()])
      .then(([, p, s]) => {
        spazi = p.spazi;
        fasce = p.fasce;
        gestiti = p.impiantiGestiti;
        personale = s;
      })
      .catch((e) => (errore = messaggioErrore(e)))
      .finally(() => (caricamento = false));
  });

  // ── Riassunto di ogni corso ──
  interface Riga {
    corso: Corso;
    giorni: number[];
    turni: number;
    iscritti: number;
    posti: number | null;
    percentuale: number | null;
    inAttesa: number;
    dove: string;
    istruttori: string;
    dettaglio: string;
  }

  const righe = $derived<Riga[]>(corsi.map((c) => {
    const attivi = turniAttivi(c.lezioni, oggi);
    const r = riempimento(attivi);
    const ist = c.istruttori.length ? c.istruttori : c.istruttore ? [c.istruttore] : [];
    return {
      corso: c, giorni: turniPerGiorno(attivi), turni: attivi.length, iscritti: r.iscritti, posti: r.posti, percentuale: r.percentuale,
      inAttesa: r.inAttesa, dove: doveBreve([...new Set(attivi.flatMap((l) => l.spazi))], spazi),
      istruttori: ist.length > 2 ? `${ist[0].nome} e altri ${ist.length - 1}` : ist.map((p) => p.nome).join(' e '),
      dettaglio: [c.livello, eta(c.etaDa, c.etaA)].filter(Boolean).join(' · '),
    };
  }));

  const istruttoriTutti = $derived.by(() => {
    const m = new Map<string, string>();
    corsi.forEach((c) => [...c.istruttori, ...(c.istruttore ? [c.istruttore] : [])].forEach((p) => m.set(p.id, p.nome)));
    return [...m].map(([id, nome]) => ({ id, nome })).sort((a, b) => a.nome.localeCompare(b.nome));
  });

  const filtrate = $derived.by(() => {
    const t = cerca.trim().toLowerCase();
    return righe.filter((r) => {
      const c = r.corso;
      if (soloAttivi && !c.attivo) return false;
      if (istruttore && !c.istruttori.some((p) => p.id === istruttore) && c.istruttore?.id !== istruttore) return false;
      if (soloAttesa && !r.inAttesa) return false;
      if (t && ![c.nome, c.livello ?? '', r.istruttori, TIPI_CORSO[c.tipo]].some((x) => x.toLowerCase().includes(t))) return false;
      return true;
    });
  });

  const conteggi = $derived.by(() => {
    const m = new Map<TipoCorso, number>();
    filtrate.forEach((r) => m.set(r.corso.tipo, (m.get(r.corso.tipo) ?? 0) + 1));
    return m;
  });

  const ordina = (a: Riga, b: Riga) =>
    ordine === 'pieni' ? (b.percentuale ?? -1) - (a.percentuale ?? -1)
      : ordine === 'liberi' ? (a.percentuale ?? 101) - (b.percentuale ?? 101)
        : a.corso.nome.localeCompare(b.corso.nome);

  const gruppi = $derived(ORDINE_CATEGORIE
    .filter((t) => !categoria || t === categoria)
    .map((t) => ({ tipo: t, righe: filtrate.filter((r) => r.corso.tipo === t).sort(ordina) }))
    .filter((g) => g.righe.length));

  const totale = $derived.by(() => {
    const attivi = righe.filter((r) => r.corso.attivo);
    return { corsi: attivi.length, turni: attivi.reduce((a, r) => a + r.turni, 0), iscritti: attivi.reduce((a, r) => a + r.iscritti, 0) };
  });
  const conAttesa = $derived(righe.filter((r) => (!soloAttivi || r.corso.attivo) && r.inAttesa).length);
  const impiantoNuovo = $derived(gestiti[0] ?? '');
  const vistaEffettiva = $derived<Vista>(schermo.larga ? vista : 'categorie');

  const quantiCorsi = (n: number) => (n === 1 ? '1 corso' : `${n} corsi`);
  const settimanaTesto = (g: number[]) => g.map((n, i) => (n ? `${GIORNI_LUNGHI[i]} ${n}` : null)).filter(Boolean).join(', ') || 'nessun turno';
</script>

{#if apertoId}
  {#if aperto}
    <SchedaCorso corso={aperto} {corsi} {spazi} {fasce} {personale} gestore={gestiti.includes(aperto.impiantoId)} ricarica={carica} />
  {:else}
    <section class="page"><p class="muted">{caricamento ? 'Caricamento…' : 'Corso non trovato.'}</p>
      {#if !caricamento}<a href="#/corsi">→ Torna ai corsi</a>{/if}</section>
  {/if}
{:else}
<section class="page larga-c">
  <div class="testa">
    <p class="muted nomargin grow">{totale.corsi} corsi attivi · {totale.turni} turni · {totale.iscritti.toLocaleString('it-IT')} iscrizioni</p>
    {#if impiantoNuovo}
      <button class="btn btn-primary compatto" onclick={() => ((nuovo = true), (messaggio = ''))}><Icona nome="piu" />Nuovo corso…</button>
    {/if}
  </div>
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  <div class="strumenti">
    <label class="cerca">
      <span class="sr">Cerca un corso</span>
      <Icona nome="cerca" />
      <input class="inp" type="search" placeholder="Corso, livello o istruttore" bind:value={cerca} />
    </label>
    <div class="seg filtro" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Quali corsi">
      <button aria-pressed={soloAttivi} onclick={() => (soloAttivi = true)}>Attivi</button>
      <button aria-pressed={!soloAttivi} onclick={() => (soloAttivi = false)}>Tutti</button>
    </div>
    <select class="sel" aria-label="Istruttore" bind:value={istruttore}>
      <option value="">Tutti gli istruttori</option>
      {#each istruttoriTutti as p (p.id)}<option value={p.id}>{p.nome}</option>{/each}
    </select>
    <span class="grow"></span>
    {#if schermo.larga}
      {#if vista === 'elenco'}
        <label class="ordina muted">Ordina per
          <select class="sel" bind:value={ordine}><option value="nome">nome</option><option value="pieni">i più pieni</option><option value="liberi">i più liberi</option></select>
        </label>
      {/if}
      <div class="seg modo" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Come mostrare i corsi">
        <button aria-pressed={vista === 'categorie'} onclick={() => (vista = 'categorie')}>Categorie</button>
        <button aria-pressed={vista === 'elenco'} onclick={() => (vista = 'elenco')}>Elenco</button>
      </div>
    {/if}
  </div>

  <div class="filtri" role="group" aria-label="Categoria">
    <button class="chip" aria-pressed={!categoria} onclick={() => (categoria = '')}>Tutte <span class="quanti">{filtrate.length}</span></button>
    {#each ORDINE_CATEGORIE.filter((t) => conteggi.get(t)) as t (t)}
      <button class="chip {colore(t)}" aria-pressed={categoria === t} onclick={() => (categoria = categoria === t ? '' : t)}>
        <span class="pallino"></span>{TIPI_CORSO[t]} <span class="quanti">{conteggi.get(t)}</span>
      </button>
    {/each}
    {#if conAttesa}
      <span class="sep" aria-hidden="true"></span>
      <button class="chip" aria-pressed={soloAttesa} onclick={() => (soloAttesa = !soloAttesa)}><Icona nome="attesa" />Con lista d'attesa <span class="quanti">{conAttesa}</span></button>
    {/if}
  </div>

  {#if vistaEffettiva === 'categorie'}
    {#each gruppi as g (g.tipo)}
      <section class="gruppo-c">
        <h2 class="lbl">{TIPI_CORSO[g.tipo]} <span class="m">{quantiCorsi(g.righe.length)}</span></h2>
        <div class="schede-c">
          {#each g.righe as r (r.corso.id)}
            <button class="corso-c {colore(r.corso.tipo)}" class:spento={!r.corso.attivo} onclick={() => apri(r.corso)}>
              <span class="testa-c">
                {#if r.corso.colore}<span class="livello" style="background: {r.corso.colore}"></span>{/if}
                <span class="nome">{r.corso.nome}</span>
                {#if r.inAttesa}<span class="attesa-t"><Icona nome="attesa" />{r.inAttesa} in attesa</span>{/if}
                {#if !r.corso.attivo}<span class="tag t-altro">non attivo</span>{/if}
              </span>
              <span class="sub">{r.dettaglio || TIPI_CORSO[r.corso.tipo]}{#if r.istruttori}<br />{r.istruttori}{/if}</span>
              <span class="sett7" aria-label="Turni: {settimanaTesto(r.giorni)}">
                {#each r.giorni as n, i (i)}<span class="g" class:no={!n}><span class="d">{GIORNI[i]}</span><span class="n">{n || '–'}</span></span>{/each}
              </span>
              {#if r.posti != null}
                <span class="riemp"><span class="barra-r"><span class:quasi={(r.percentuale ?? 0) >= 95} style="width: {Math.min(100, r.percentuale ?? 0)}%"></span></span><span class="m">{r.iscritti}/{r.posti}</span> iscritti</span>
              {:else if r.turni}
                <span class="riemp">Senza limite di posti{r.iscritti ? ` · ${r.iscritti} iscritti` : ''}</span>
              {/if}
              <span class="piede-c"><span><span class="m">{r.turni}</span> {r.turni === 1 ? 'turno' : 'turni'}</span><span>{r.dove}</span></span>
            </button>
          {/each}
        </div>
      </section>
    {:else}
      <p class="muted">{caricamento ? 'Caricamento…' : 'Nessun corso con questi filtri.'}</p>
    {/each}
  {:else}
    <div class="card tabella">
      <table class="tab-c">
        <thead>
          <tr>
            <th>Corso</th><th>Istruttori</th>
            <th><span class="testa-g">{#each GIORNI as d, i (i)}<span>{d}</span>{/each}</span></th>
            <th class="num">Turni</th><th>Iscritti</th><th>In attesa</th><th><span class="sr">Apri</span></th>
          </tr>
        </thead>
        <tbody>
          {#each gruppi as g (g.tipo)}
            <tr class="cat {colore(g.tipo)}"><td colspan="7"><span class="etichetta-c">{TIPI_CORSO[g.tipo]} · {quantiCorsi(g.righe.length)}</span></td></tr>
            {#each g.righe as r (r.corso.id)}
              <tr class="riga {colore(r.corso.tipo)}" class:spento={!r.corso.attivo} onclick={() => apri(r.corso)}>
                <td>
                  <a class="nome-r" href="#/corsi/{r.corso.id}">{#if r.corso.colore}<span class="livello" style="background: {r.corso.colore}"></span>{/if}{r.corso.nome}</a>
                  <div class="sub-r">{[r.dettaglio, r.dove].filter(Boolean).join(' · ')}</div>
                </td>
                <td>{r.istruttori || '—'}</td>
                <td><span class="sett7r" aria-label="Turni: {settimanaTesto(r.giorni)}">{#each r.giorni as n, i (i)}<span class:no={!n} title="{GIORNI_LUNGHI[i]}: {n ? `${n} ${n === 1 ? 'turno' : 'turni'}` : 'nessun turno'}">{n || '–'}</span>{/each}</span></td>
                <td class="m num">{r.turni}</td>
                <td>
                  {#if r.posti != null}
                    <span class="mini-r"><span class="barra-r"><span class:quasi={(r.percentuale ?? 0) >= 95} style="width: {Math.min(100, r.percentuale ?? 0)}%"></span></span><span class="m">{r.iscritti}/{r.posti}</span></span>
                  {:else}<span class="muted small">senza limite</span>{/if}
                </td>
                <td>{#if r.inAttesa}<span class="attesa-t">{r.inAttesa} in attesa</span>{/if}</td>
                <td class="chev"><Icona nome="dx" /></td>
              </tr>
            {/each}
          {:else}
            <tr><td colspan="7" class="muted">{caricamento ? 'Caricamento…' : 'Nessun corso con questi filtri.'}</td></tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}
</section>
{/if}

{#if nuovo}
  <Foglio titolo="Nuovo corso" chiudi={() => (nuovo = false)}>
    <CorsoForm corso={null} impiantoId={impiantoNuovo} {personale} fatto={async (c, testo) => { await carica(); nuovo = false; messaggio = testo; apri(c); }} />
  </Foglio>
{/if}

<style>
  .larga-c { gap: var(--space-14); }
  .testa { display: flex; align-items: center; gap: var(--space-16); flex-wrap: wrap; }
  .compatto { height: 40px; font-size: var(--text-md); padding: 0 var(--space-14); }
  .sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

  .strumenti { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-8) var(--space-12); }
  .cerca { position: relative; flex: 0 1 340px; min-width: 220px; }
  .cerca :global(.ic) { position: absolute; left: var(--space-14); top: 50%; transform: translateY(-50%); width: 18px; height: 18px; color: var(--muted); pointer-events: none; }
  .cerca .inp { padding-left: 42px; height: 44px; }
  .filtro { width: 180px; }
  .modo { width: 220px; }
  .sel { appearance: auto; height: 40px; padding: 0 var(--space-10); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); font: inherit; font-size: var(--text-sm); color: var(--ink); }
  .ordina { display: inline-flex; align-items: center; gap: var(--space-8); font-size: var(--text-sm); }
  .filtri { display: flex; flex-wrap: wrap; gap: var(--space-6); align-items: center; }
  .filtri .chip { display: inline-flex; align-items: center; gap: var(--space-6); }
  .filtri .chip :global(.ic) { width: 16px; height: 16px; }
  .quanti { font-family: var(--font-mono); font-size: var(--text-2xs); opacity: 0.8; }
  .filtri .pallino { width: 9px; height: 9px; }
  .sep { width: 1px; height: 24px; background: var(--line); margin: 0 var(--space-4); }

  .gruppo-c { display: flex; flex-direction: column; gap: var(--space-10); }
  .gruppo-c h2 { margin: var(--space-4) 0 0; display: flex; align-items: baseline; gap: var(--space-8); }
  .gruppo-c h2 .m { text-transform: none; letter-spacing: 0; }
  .schede-c { display: grid; grid-template-columns: repeat(auto-fill, minmax(268px, 1fr)); gap: var(--space-10); }
  .corso-c { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14) var(--space-14) var(--space-12); border: none; border-radius: var(--radius); background: var(--c-bg); color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .corso-c:hover { box-shadow: inset 0 0 0 1.5px var(--c-line); }
  .testa-c { display: flex; align-items: flex-start; gap: var(--space-8); }
  .testa-c .livello { margin-top: 5px; }
  .corso-c .nome { flex: 1; min-width: 0; font-size: var(--text-lg); font-weight: 700; line-height: 1.25; }
  .corso-c .sub { font-size: var(--text-sm); color: var(--c-fg); line-height: 1.35; }
  .livello { display: inline-block; width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; box-shadow: 0 0 0 1px rgba(16, 33, 43, 0.25); }
  .attesa-t { display: inline-flex; align-items: center; gap: var(--space-4); flex-shrink: 0; padding: 2px var(--space-8); border-radius: var(--radius-pill); background: var(--warn-bg); color: var(--warn); font-size: var(--text-xs); font-weight: 600; white-space: nowrap; }
  .attesa-t :global(.ic) { width: 14px; height: 14px; }
  .sett7 { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 3px; }
  .sett7 .g { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 34px; border-radius: 7px; background: var(--surface); }
  .sett7 .d { font-size: 10px; font-weight: 600; letter-spacing: 0.04em; text-transform: uppercase; color: var(--muted); line-height: 1.2; }
  .sett7 .n { font-family: var(--font-mono); font-size: var(--text-sm); font-weight: 500; line-height: 1.2; }
  .sett7 .g.no { background: none; box-shadow: inset 0 0 0 1px var(--line-strong); opacity: 0.6; }
  .riemp { display: flex; align-items: center; gap: var(--space-8); font-size: var(--text-xs); color: var(--c-fg); }
  .riemp .m { color: var(--ink); white-space: nowrap; }
  .barra-r { flex: 1; height: 6px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 12%, transparent); overflow: hidden; }
  .barra-r span { display: block; height: 100%; border-radius: inherit; background: var(--c-line); }
  .barra-r span.quasi { background: var(--warn); }
  .piede-c { display: flex; justify-content: space-between; gap: var(--space-8); font-size: var(--text-xs); color: var(--c-fg); }
  .spento { opacity: 0.6; }

  .tabella { padding: var(--space-4) var(--space-8) var(--space-8); overflow-x: auto; }
  .tab-c { width: 100%; border-collapse: separate; border-spacing: 0; font-size: var(--text-md); }
  .tab-c th { text-align: left; font-size: var(--text-2xs); font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); padding: var(--space-10) var(--space-12); border-bottom: 1px solid var(--line); }
  .tab-c td { padding: var(--space-8) var(--space-12); border-bottom: 1px solid var(--line-soft); vertical-align: middle; }
  .tab-c .num { text-align: right; }
  tr.riga { cursor: pointer; }
  tr.riga:hover td { background: var(--carta); }
  tr.cat td { padding: var(--space-14) var(--space-12) var(--space-6); border-bottom: none; }
  .etichetta-c { display: inline-flex; padding: 3px var(--space-10); border-radius: var(--radius-pill); background: var(--c-bg); color: var(--c-fg); font-size: var(--text-xs); font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; }
  .nome-r { display: inline-flex; align-items: center; gap: var(--space-8); font-weight: 600; color: var(--ink); text-decoration: none; }
  .sub-r { font-size: var(--text-xs); color: var(--muted); margin-left: 18px; }
  .sett7r, .testa-g { display: grid; grid-template-columns: repeat(7, 26px); gap: 3px; }
  .testa-g span { text-align: center; }
  .sett7r span { display: grid; place-items: center; height: 26px; border-radius: 6px; font-family: var(--font-mono); font-size: var(--text-xs); font-weight: 500; background: var(--c-bg); color: var(--c-fg); }
  .sett7r span.no { background: var(--surface-sunken); color: var(--muted); }
  .mini-r { display: flex; align-items: center; gap: var(--space-8); min-width: 150px; }
  .mini-r .barra-r { background: var(--line-soft); }
  .mini-r .m { font-size: var(--text-xs); white-space: nowrap; }
  .chev { width: 24px; color: var(--chevron); }
  .chev :global(.ic) { width: 18px; height: 18px; }

  @media (max-width: 1023px) {
    .cerca { flex: 1 1 100%; }
    .filtro { width: 100%; }
    .testa .btn { width: 100%; }
  }
</style>
