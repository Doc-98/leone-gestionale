<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Foglio from '../components/Foglio.svelte';
  import { api, eAvviso, messaggioErrore, TIPI_RICHIESTA, type Candidato, type Postazione, type Richiesta, type Turno } from '../lib/api';
  import { aggiungiGiorni, daIso, giornoEsteso, iso, lunedi, ora } from '../lib/date';
  import { formatH, gruppoDi, orePerPersona } from '../lib/gestione';
  import { asse, buchi, giornoCorto, gruppoRichiesta, quandoInviata, type GruppoRichieste } from '../lib/richieste';
  import { areaDi, traQuanto } from '../lib/turni';
  import { sessione } from '../lib/sessione.svelte';
  import { schermo } from '../lib/schermo.svelte';

  /**
   * Le richieste per il coordinatore (ADR 0022, 0023). Sostituzioni e cambi d'orario non si respingono: chi li chiede
   * ha avvisato che non ci sarà. Una sostituzione si propone a un collega (accettando è già approvata), si conferma
   * chi l'ha presa o si lascia scoperta; un cambio orario si approva creando un turno per l'orario lasciato libero
   * («Risolvi») o lasciandolo scoperto. Nemmeno gli scambi si respingono: i due colleghi li hanno concordati, si confermano.
   */
  let { cambiate }: { cambiate?: (n: number) => void } = $props();
  const oggi = iso(new Date());
  const GRUPPI: GruppoRichieste[] = ['Da coprire', 'Da decidere', 'Aspettano il collega'];

  let richieste = $state<Richiesta[]>([]);
  let turni = $state<Turno[]>([]);
  let postazioni = $state<Postazione[]>([]);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let lavoro = $state(false);
  let sceltaId = $state('');
  let nota = $state('');
  let candidati = $state<Candidato[] | null>(null);
  let collega = $state('');
  let mostraOccupati = $state(false);
  let avviso = $state<{ testo: string; conferma: () => void } | null>(null);
  let foglio = $state(false);

  api.postazioni().then((p) => (postazioni = p)).catch(() => {});

  async function carica() {
    caricamento = true;
    try {
      richieste = (await api.richiesteDaGestire()).sort((a, b) => a.turno.data.localeCompare(b.turno.data) || a.turno.inizio.localeCompare(b.turno.inizio));
      cambiate?.(richieste.filter((r) => gruppoRichiesta(r) !== 'Aspettano il collega').length);
      const daLink = location.hash.split('/')[2];
      if (daLink && richieste.some((r) => r.id === daLink) && !sceltaId) scegli(daLink);
      else if (!richieste.some((r) => r.id === sceltaId)) scegli(richieste.find((r) => gruppoRichiesta(r) !== 'Aspettano il collega')?.id ?? richieste[0]?.id ?? '');
      const date = richieste.flatMap((r) => [r.turno.data, r.turnoCollega?.data]).filter((d): d is string => !!d).sort();
      if (date.length) {
        const da = lunedi(date[0]);
        const a = [aggiungiGiorni(lunedi(date.at(-1)!), 6), aggiungiGiorni(da, 90)].sort()[0];
        turni = (await api.turniTutti(da, a)).filter((t) => t.stato === 'PIANIFICATO');
      }
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      caricamento = false;
    }
  }
  carica();

  function scegli(id: string) {
    sceltaId = id;
    nota = '';
    collega = '';
    mostraOccupati = false;
    avviso = null;
    candidati = null;
    const r = richieste.find((x) => x.id === id);
    if (r?.tipo === 'SOSTITUZIONE') api.candidati(id).then((c) => sceltaId === id && (candidati = c.filter((x) => x.persona.id !== r.collega?.id))).catch(() => (candidati = []));
  }

  const scelta = $derived(richieste.find((r) => r.id === sceltaId));
  const gruppi = $derived(GRUPPI.map((g) => ({ nome: g, voci: richieste.filter((r) => gruppoRichiesta(r) === g) })).filter((g) => g.voci.length));
  const nDecidere = $derived(richieste.filter((r) => gruppoRichiesta(r) === 'Da decidere').length);
  const nCoprire = $derived(richieste.filter((r) => gruppoRichiesta(r) === 'Da coprire').length);
  const nome1 = (n: string) => n.split(' ')[0];
  const postDi = (t: Turno) => postazioni.find((p) => p.id === t.postazioneId);
  const dove = (t: Turno) => {
    const p = postDi(t);
    return p ? `${gruppoDi(p).nome} · ${gruppoDi(p).etichetta}` : t.postazione;
  };
  const doveBreve = (t: Turno) => {
    const p = postDi(t);
    return p ? `${gruppoDi(p).nome} ${gruppoDi(p).etichetta.replace(/^postazione\s*/i, '')}` : t.postazione;
  };
  const oreSettimana = (personaId: string, giorno: string) => {
    const da = lunedi(giorno), a = aggiungiGiorni(da, 6);
    return orePerPersona(turni.filter((t) => t.data >= da && t.data <= a).map((t) => ({ id: t.id, giorno: t.data, inizio: t.inizio, fine: t.fine, postazioneId: t.postazioneId, persona: t.persona }))).get(personaId)?.ore ?? 0;
  };

  function titolo(r: Richiesta): string {
    switch (r.tipo) {
      case 'SOSTITUZIONE':
        return r.stato === 'IN_ATTESA_APPROVAZIONE' && r.collega ? `${r.collega.nome} copre ${r.richiedente.nome}`
          : r.collega ? `${r.collega.nome} per ${r.richiedente.nome}` : `${r.richiedente.nome} sarà assente`;
      case 'CAMBIO_ORARIO':
        return ora(r.nuovoInizio!) !== ora(r.turno.inizio) ? `${r.richiedente.nome} inizierà alle ${ora(r.nuovoInizio!)}` : `${r.richiedente.nome} finirà alle ${ora(r.nuovoFine!)}`;
      case 'SCAMBIO':
        return `${r.richiedente.nome} ↔ ${r.collega?.nome ?? '?'}`;
    }
  }
  function breve(r: Richiesta): string {
    if (r.tipo === 'SCAMBIO') return `${nome1(r.richiedente.nome)} ↔ ${nome1(r.collega?.nome ?? '?')}`;
    if (r.tipo === 'SOSTITUZIONE' && r.collega) return `${r.richiedente.nome} → ${r.collega.nome}`;
    return r.richiedente.nome;
  }
  function sotto(r: Richiesta): string {
    const t = r.turno;
    if (r.tipo === 'CAMBIO_ORARIO') return `${giornoCorto(t.data)} · ${ora(t.inizio)}–${ora(t.fine)} → ${ora(r.nuovoInizio!)}–${ora(r.nuovoFine!)}`;
    if (r.tipo === 'SCAMBIO' && r.turnoCollega) return `${giornoCorto(t.data)} ↔ ${giornoCorto(r.turnoCollega.data)}`;
    if (r.propostaDa && r.collega) return `${giornoCorto(t.data)} · proposta da ${r.propostaDa.id === sessione.utente?.personaId ? 'te' : r.propostaDa.nome}`;
    return `${giornoCorto(t.data)} · ${ora(t.inizio)}–${ora(t.fine)} · ${doveBreve(t)}`;
  }
  function inviata(r: Richiesta): string {
    if (r.stato === 'IN_ATTESA_APPROVAZIONE' && r.collega && r.tipo !== 'CAMBIO_ORARIO') return `${nome1(r.collega.nome)} ha accettato`;
    return quandoInviata(r.creataIl, oggi, r.propostaDa ? 'proposta' : 'chiesta');
  }

  /** Linea del tempo dell'area del turno, in quel giorno, come sarebbe con la richiesta. */
  function linea(r: Richiesta) {
    const t = r.turno;
    const p = postDi(t);
    const g = p ? gruppoDi(p).nome : t.mansione;
    const righe = postazioni.filter((x) => (p ? gruppoDi(x).nome === g && x.mansione === p.mansione : x.id === t.postazioneId));
    if (!righe.length) righe.push({ id: t.postazioneId, nome: t.postazione, mansione: t.mansione, impiantoId: t.impiantoId });
    const delGiorno = turni.filter((x) => x.data === t.data && righe.some((y) => y.id === x.postazioneId));
    if (!delGiorno.some((x) => x.id === t.id)) delGiorno.push(t);
    const segmenti = righe.map((x) => {
      const seg: { da: string; a: string; testo: string; cls: string }[] = [];
      for (const y of delGiorno.filter((z) => z.postazioneId === x.id)) {
        const [i, f] = [ora(y.inizio), ora(y.fine)];
        if (y.id !== t.id) { seg.push({ da: i, a: f, testo: y.persona?.nome ?? 'scoperto', cls: y.persona ? '' : 'buco' }); continue; }
        if (r.tipo === 'SOSTITUZIONE') {
          if (r.stato === 'IN_ATTESA_APPROVAZIONE' && r.collega) seg.push({ da: i, a: f, testo: `${r.collega.nome}, al posto di ${nome1(r.richiedente.nome)}`, cls: 'questo' });
          else seg.push({ da: i, a: f, testo: r.collega ? `${r.propostaDa ? 'proposto' : 'chiesto'} a ${nome1(r.collega.nome)}` : `${nome1(r.richiedente.nome)} · assente`, cls: 'buco' });
        } else if (r.tipo === 'CAMBIO_ORARIO') {
          for (const [a, b] of buchi(y.inizio, y.fine, r.nuovoInizio!, r.nuovoFine!)) seg.push({ da: a, a: b, testo: 'scoperta', cls: 'buco' });
          seg.push({ da: ora(r.nuovoInizio!), a: ora(r.nuovoFine!), testo: r.richiedente.nome, cls: 'questo' });
        } else seg.push({ da: i, a: f, testo: `${r.collega?.nome ?? '?'} (era di ${nome1(r.richiedente.nome)})`, cls: 'questo' });
      }
      return { nome: p ? gruppoDi(x).etichetta : x.nome, seg };
    }).filter((x) => x.seg.length || righe.length <= 4);
    const tutti = segmenti.flatMap((x) => x.seg.map((s) => [s.da, s.a] as [string, string]));
    const giorno = giornoEsteso(t.data).split(' ').slice(0, 2).join(' ');
    return { titolo: `${g}, ${giorno}${r.tipo !== 'SOSTITUZIONE' ? ' · se approvi' : ''}`, righe: segmenti, asse: asse(tutti.length ? tutti : [[ora(t.inizio), ora(t.fine)]]), area: areaDi(t.postazione) };
  }

  function effetto(r: Richiesta): string {
    if (r.tipo === 'CAMBIO_ORARIO') {
      const b = buchi(r.turno.inizio, r.turno.fine, r.nuovoInizio!, r.nuovoFine!);
      if (!b.length) return 'Il turno si allunga: non resta nessun buco.';
      const testo = b.map(([a, c]) => `dalle ${a} alle ${c}`).join(' e ');
      const p = postDi(r.turno);
      const post = p && /^postazione/i.test(gruppoDi(p).etichetta) ? gruppoDi(p).etichetta.toLowerCase() : 'postazione';
      return `${testo[0].toUpperCase()}${testo.slice(1)} la ${post} resta senza nessuno. «Risolvi» crea un turno da assegnare per quell'orario; «Lascia scoperto» accetta il buco.`;
    }
    if (r.tipo === 'SCAMBIO' && r.turnoCollega) {
      const c = r.turnoCollega;
      return `${nome1(r.collega?.nome ?? '?')} fa il turno di ${nome1(r.richiedente.nome)} ${giornoEsteso(r.turno.data).split(' ').slice(0, 2).join(' ')}; ${nome1(r.richiedente.nome)} fa quello di ${nome1(r.collega?.nome ?? '?')} ${giornoEsteso(c.data).split(' ').slice(0, 2).join(' ')} (${doveBreve(c)}, ${ora(c.inizio)}–${ora(c.fine)}).`;
    }
    return '';
  }

  async function esegui(fai: () => Promise<unknown>, ok: string) {
    errore = messaggio = '';
    avviso = null;
    lavoro = true;
    try {
      await fai();
      messaggio = ok;
      foglio = false;
      await carica();
    } catch (e) {
      errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }

  async function proponi(r: Richiesta, forza = false) {
    const c = candidati?.find((x) => x.persona.id === collega);
    if (!c) return void (errore = 'Scegli a chi proporla.');
    errore = messaggio = '';
    lavoro = true;
    try {
      await api.proponi(r.id, c.persona.id, forza);
      messaggio = `Proposta inviata a ${nome1(c.persona.nome)}: se accetta, la sostituzione è già confermata.`;
      avviso = null;
      foglio = false;
      await carica();
      scegli(r.id);
    } catch (e) {
      if (eAvviso(e)) avviso = { testo: e.message, conferma: () => proponi(r, true) };
      else errore = messaggioErrore(e);
    } finally {
      lavoro = false;
    }
  }

  const scoperto = (r: Richiesta) => esegui(() => api.senzaSostituto(r.id, nota || undefined), 'Il turno resta da assegnare: lo trovi scoperto in Pianificazione.');
  const notaPredefinita = $derived(`Va bene - ${sessione.utente?.nome ?? ''}`);

  /** Le due azioni della richiesta: [testo, cosa fa, serve scegliere un collega?] */
  function azioni(r: Richiesta): { t: string; f: () => void; collega?: boolean; primaria?: boolean }[] {
    const sceltoNome = candidati?.find((x) => x.persona.id === collega)?.persona.nome;
    const proponiT = sceltoNome ? `Proponi a ${nome1(sceltoNome)}` : 'Proponi a…';
    if (r.tipo === 'SOSTITUZIONE') {
      if (r.stato === 'IN_ATTESA_APPROVAZIONE' && r.collega)
        return [{ t: `Conferma ${nome1(r.collega.nome)}`, f: () => esegui(() => api.approva(r.id, nota || undefined), `Confermata: il turno passa a ${r.collega!.nome}.`) },
          { t: sceltoNome ? proponiT : 'Proponi a un altro', f: () => proponi(r), collega: true }];
      return [{ t: proponiT, f: () => proponi(r), collega: true }, { t: 'Lascia scoperto', f: () => scoperto(r) }];
    }
    if (r.tipo === 'CAMBIO_ORARIO') {
      const b = buchi(r.turno.inizio, r.turno.fine, r.nuovoInizio!, r.nuovoFine!);
      const chi = nome1(r.richiedente.nome);
      if (!b.length) return [{ t: 'Approva', f: () => esegui(() => api.approva(r.id, nota || undefined), 'Approvato: l’orario è aggiornato.') }];
      return [{ t: 'Risolvi', f: () => esegui(() => api.approva(r.id, nota || undefined, true), `Approvato: ${chi} ha il nuovo orario e ${b.map(([a, c]) => `${a}–${c}`).join(', ')} è un turno da assegnare in Pianificazione.`) },
        { t: 'Lascia scoperto', f: () => esegui(() => api.approva(r.id, nota || undefined), `Approvato: ${chi} ha il nuovo orario e ${b.map(([a, c]) => `dalle ${a} alle ${c}`).join(', ')} la postazione resta scoperta.`) }];
    }
    if (r.stato === 'IN_ATTESA_APPROVAZIONE')
      return [{ t: 'Conferma lo scambio', f: () => esegui(() => api.approva(r.id, nota || undefined), 'Scambio confermato: i turni sono aggiornati.') }];
    return [];
  }

  const statoCand = (c: Candidato, r: Richiesta) => (r.collega?.id === c.persona.id ? 'proposta inviata' : c.occupato ? `in turno ${c.occupato}` : 'libero');
  // prima chi è libero, dal meno carico
  const ordinati = $derived(scelta && candidati ? [...candidati].sort((a, b) => Number(!!a.occupato) - Number(!!b.occupato)
    || oreSettimana(a.persona.id, scelta.turno.data) - oreSettimana(b.persona.id, scelta.turno.data) || a.persona.nome.localeCompare(b.persona.nome)) : []);
  const visibili = $derived(mostraOccupati ? ordinati : ordinati.filter((c) => !c.occupato));
  const nOccupati = $derived(candidati?.filter((c) => c.occupato).length ?? 0);
</script>

{#snippet tipo(r: Richiesta)}
  <span class="tipo" class:sost={r.tipo === 'SOSTITUZIONE'}>{TIPI_RICHIESTA[r.tipo]}</span>
{/snippet}

{#snippet sceltaCollega(r: Richiesta)}
  <span class="lbl">{r.collega && r.stato === 'IN_ATTESA_COLLEGA' ? `${r.propostaDa ? 'Proposta' : 'Chiesta'} a ${r.collega.nome}: aspetta la risposta. Vuoi proporla a un altro?`
    : r.collega ? `${nome1(r.collega.nome)} l’ha presa. Confermala, o proponila a un altro:` : `Nessuno l’ha ancora presa: a chi la proponi?`}</span>
  <div class="cand" role="radiogroup" aria-label="A chi proporla">
    {#if !candidati}<span class="muted small vuoto-c">…</span>{/if}
    {#each visibili as c (c.persona.id)}
      {@const o = oreSettimana(c.persona.id, r.turno.data)}
      <button type="button" role="radio" aria-checked={collega === c.persona.id} class:occ={!!c.occupato} onclick={() => ((collega = c.persona.id), (avviso = null))}>
        <span class="n">{c.persona.nome}</span>
        <span class="ore-b"><i style="width: {Math.min(100, (o / 20) * 100)}%"></i></span><span class="m s muted ore">{formatH(o)}</span>
        <span class="s stato" class:libero={!c.occupato}>{statoCand(c, r)}</span>
      </button>
    {:else}
      {#if candidati}<span class="muted small vuoto-c">Tutti quelli che possono lavorare qui sono già in turno.</span>{/if}
    {/each}
  </div>
  {#if nOccupati}<button type="button" class="mini-btn tratteggio" aria-expanded={mostraOccupati} onclick={() => (mostraOccupati = !mostraOccupati)}>
    {mostraOccupati ? 'Nascondi chi è già in turno' : `Mostra anche chi è già in turno (${nOccupati})`}</button>{/if}
  {#if avviso}<div class="avviso" role="alert"><span>{avviso.testo}</span><span class="riga-az"><button type="button" class="mini-btn" onclick={() => (avviso = null)}>Lascia stare</button>
    <button type="button" class="mini-btn pieno-warn" onclick={avviso.conferma}>Proponi comunque</button></span></div>{/if}
  <span class="muted small">Se la proposta viene accettata, la sostituzione è approvata da sola; altrimenti torna aperta a tutti.</span>
{/snippet}

{#snippet dettaglio(r: Richiesta)}
  {@const l = linea(r)}
  {@const e = effetto(r)}
  {@const az = azioni(r)}
  <header class="testa-d">
    <span class="riga">{@render tipo(r)}<span class="grow"></span><span class="muted small">{inviata(r)}</span></span>
    <h2>{titolo(r)}</h2>
    <span class="m sotto-d">{giornoEsteso(r.turno.data).replace(/^./, (c) => c.toUpperCase())} · {ora(r.turno.inizio)}–{ora(r.turno.fine)} · {dove(r.turno)}</span>
  </header>
  {#if r.messaggio}<span class="muted citazione">«{r.messaggio}»</span>{/if}
  <div class="tl-box ar-{l.area}">
    <span class="lbl">{l.titolo}</span>
    <div class="tl">
      <div class="asse-t"><span></span><span class="ore-t">{#each l.asse.ore as o (o)}<span>{o}</span>{/each}</span></div>
      {#each l.righe as riga, i (i)}
        <div class="r"><span class="nome-t">{riga.nome}</span>
          <span class="barra-t">{#each riga.seg as s, j (j)}<span class="seg-t {s.cls}" style="left: {l.asse.pos(s.da)}%; width: {l.asse.pos(s.a) - l.asse.pos(s.da)}%" title={`${s.da}–${s.a} · ${s.testo}`}>{s.testo}</span>{/each}</span></div>
      {/each}
    </div>
  </div>
  {#if e}<span class="effetto">{e}</span>{/if}
  {#if r.tipo === 'SOSTITUZIONE'}{@render sceltaCollega(r)}{/if}
  {#if r.stato === 'IN_ATTESA_APPROVAZIONE' || r.tipo === 'CAMBIO_ORARIO'}
    <label class="field"><span class="lbl">Nota per lo staff (facoltativa)</span><input class="inp" placeholder={notaPredefinita} bind:value={nota} maxlength="500" /></label>
  {/if}
  {#if az.length}
    <div class="azioni">
      {#each az as a, i (i)}<button class="btn {i === 0 ? 'btn-primary' : 'btn-secondary'}" type="button" disabled={lavoro || (!!a.collega && !collega)} onclick={a.f}>{a.t}</button>{/each}
    </div>
  {:else}
    <span class="muted small">Aspetta la risposta di {r.collega?.nome ?? 'un collega'}: non c’è niente da fare per ora.</span>
  {/if}
{/snippet}

<section class="page">
  <span class="muted sotto-p">{nDecidere} da decidere · {nCoprire} da coprire</span>
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}

  {#if schermo.larga}
    <div class="colonne-p">
      <div class="card elenco">
        {#each gruppi as g (g.nome)}
          <div class="gruppo-t lbl">{g.nome}</div>
          {#each g.voci as r (r.id)}
            <button type="button" aria-pressed={sceltaId === r.id} onclick={() => scegli(r.id)}>
              <span class="t"><b>{breve(r)}</b><span>{sotto(r)}</span></span>{@render tipo(r)}
            </button>
          {/each}
        {:else}
          <p class="muted vuoto">{caricamento ? '…' : 'Nessuna richiesta.'}</p>
        {/each}
      </div>
      <section class="card pann dettaglio">
        {#if scelta}{@render dettaglio(scelta)}
        {:else}<p class="muted niente">{caricamento ? '…' : 'Niente da decidere: tutto in ordine.'}</p>{/if}
      </section>
    </div>
  {:else}
    {#each richieste.filter((r) => gruppoRichiesta(r) !== 'Aspettano il collega') as r (r.id)}
      {@const az = azioni(r)}
      <article class="card ri">
        <span class="testa">{@render tipo(r)}<span class="grow"></span><span class="quando-b">{traQuanto(r.turno, oggi, 0)}</span></span>
        <b class="titolo-r">{titolo(r)}</b>
        <span class="mini-t ar-{areaDi(r.turno.postazione)}"><span class="di">{giornoCorto(r.turno.data).split(' ')[0]} {daIso(r.turno.data).getDate()}</span>
          {#if r.tipo === 'CAMBIO_ORARIO'}<b><s class="prima">{ora(r.turno.inizio)}</s> {ora(r.nuovoInizio!)}–{ora(r.nuovoFine!)}</b>{:else}<b>{ora(r.turno.inizio)}–{ora(r.turno.fine)}</b>{/if}
          <span class="dove-t">{doveBreve(r.turno)}</span></span>
        {#if r.tipo === 'SCAMBIO' && r.turnoCollega}
          <span class="mini-t ar-{areaDi(r.turnoCollega.postazione)}"><span class="di">{giornoCorto(r.turnoCollega.data).split(' ')[0]} {daIso(r.turnoCollega.data).getDate()}</span><b>{ora(r.turnoCollega.inizio)}–{ora(r.turnoCollega.fine)}</b><span class="dove-t">{doveBreve(r.turnoCollega)}</span></span>
        {/if}
        {#if r.messaggio}<span class="muted citazione">«{r.messaggio}»</span>{/if}
        {#if effetto(r)}<span class="effetto"><Icona nome="avviso" />{effetto(r)}</span>{/if}
        <div class="due-b">
          {#each az as a, i (i)}
            <button class="btn {i === 0 ? 'btn-primary' : 'btn-secondary'}" type="button" disabled={lavoro}
              onclick={() => { if (sceltaId !== r.id) scegli(r.id); if (a.collega) foglio = true; else a.f(); }}>{a.collega ? (i === 0 ? 'Proponi a…' : 'Proponi a un altro') : a.t}</button>
          {/each}
        </div>
      </article>
    {:else}
      <p class="muted niente">{caricamento ? '…' : 'Niente da decidere: tutto in ordine.'}</p>
    {/each}
    {#if richieste.some((r) => gruppoRichiesta(r) === 'Aspettano il collega')}
      <section class="aspettano">
        <span class="lbl">Aspettano il collega</span>
        <div class="card lista-a">
          {#each richieste.filter((r) => gruppoRichiesta(r) === 'Aspettano il collega') as r (r.id)}
            <button type="button" class="voce-a" onclick={() => (scegli(r.id), (foglio = true))}>
              <span class="t"><b>{giornoCorto(r.turno.data)} · {r.tipo === 'SCAMBIO' ? `scambio ${breve(r)}` : `${r.collega?.nome} per ${r.richiedente.nome}`}</b>
                <span>{r.propostaDa ? 'proposta da te · se accetta è già confermata' : `${nome1(r.collega?.nome ?? '?')} non ha ancora risposto`}</span></span>
              <Icona nome="dx" class="chev" />
            </button>
          {/each}
        </div>
      </section>
    {/if}
  {/if}
</section>

{#if foglio && scelta && !schermo.larga}
  <Foglio titolo={`${TIPI_RICHIESTA[scelta.tipo]} · ${giornoCorto(scelta.turno.data)}`} chiudi={() => (foglio = false)}>
    <div class="nel-foglio">{@render dettaglio(scelta)}</div>
  </Foglio>
{/if}

<style>
  .sotto-p { margin-top: calc(-1 * var(--space-8)); }
  .elenco { flex: 1 1 320px; max-width: 400px; padding: 0; overflow: hidden; display: flex; flex-direction: column; }
  .elenco > button { display: flex; align-items: center; gap: var(--space-12); padding: var(--space-12) var(--space-14); border: none; border-top: 1px solid var(--line-soft);
    background: var(--surface); font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .elenco > button[aria-pressed='true'] { background: var(--cloro-soft); box-shadow: inset 3px 0 0 var(--cloro); }
  .elenco .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  .elenco .t b { font-size: var(--text-md); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .elenco .t span { font-size: var(--text-xs); color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  /* titoli dei gruppi, su una fascia appena più scura */
  .gruppo-t { padding: var(--space-8) var(--space-14); background: var(--surface-sunken); color: var(--seg-fg); border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
  .gruppo-t:first-child { border-top: none; }
  .vuoto { padding: var(--space-16); margin: 0; }
  .tipo { font-size: 11px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; padding: 3px 8px; border-radius: 999px; background: var(--surface-sunken); color: var(--seg-fg); white-space: nowrap; }
  .tipo.sost { background: var(--warn-bg); color: var(--warn); }
  .dettaglio { flex: 999 1 520px; max-width: none; padding: var(--space-20) var(--space-20); gap: var(--space-16); }
  .niente { margin: calc(var(--space-16) * 2) 0; text-align: center; }
  /* intestazione del dettaglio: tipo, titolo, giorno e orario su una fascia appena più scura */
  .testa-d { display: flex; flex-direction: column; gap: var(--space-8); margin: calc(-1 * var(--space-20)) calc(-1 * var(--space-20)) var(--space-4); padding: var(--space-16) var(--space-20);
    background: var(--surface-sunken); border-bottom: 1px solid var(--line); border-radius: var(--radius) var(--radius) 0 0; }
  .nel-foglio .testa-d { margin: 0; border-radius: var(--radius-md); }
  .nel-foglio { display: flex; flex-direction: column; gap: var(--space-14); }
  .testa-d .riga { display: flex; align-items: center; gap: var(--space-10); }
  .testa-d h2 { margin: 0; font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 800; line-height: 1.15; }
  .sotto-d { font-size: var(--text-md); color: var(--seg-fg); }
  .citazione { font-style: italic; }
  .tl-box { display: flex; flex-direction: column; gap: var(--space-8); }
  .tl { display: flex; flex-direction: column; gap: var(--space-6); }
  .asse-t, .tl .r { display: grid; grid-template-columns: 110px 1fr; align-items: center; }
  .ore-t { display: flex; justify-content: space-between; font-family: var(--font-mono); font-size: 11px; color: var(--muted); }
  .nome-t { font-size: var(--text-sm); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding-right: var(--space-8); }
  .barra-t { position: relative; height: 30px; border-radius: 8px; background: var(--surface-sunken); }
  .seg-t { position: absolute; top: 3px; bottom: 3px; border-radius: 6px; background: var(--a-bg); color: var(--a-fg); font-size: var(--text-xs); font-weight: 700; display: flex; align-items: center;
    padding: 0 var(--space-8); overflow: hidden; white-space: nowrap; box-sizing: border-box; }
  .seg-t.buco { background: repeating-linear-gradient(135deg, var(--warn-bg) 0 6px, var(--surface) 6px 10px); color: var(--warn); box-shadow: inset 0 0 0 1.5px var(--warn-line); }
  .seg-t.questo { box-shadow: inset 0 0 0 2px var(--a-fg); }
  .effetto { display: flex; gap: var(--space-8); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn); font-size: var(--text-sm); font-weight: 600; }
  .effetto :global(.ic) { width: 16px; height: 16px; flex-shrink: 0; margin-top: 2px; }
  .cand { display: flex; flex-direction: column; border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; max-height: 340px; overflow-y: auto; }
  .cand button { display: flex; align-items: center; gap: var(--space-10); min-height: 44px; padding: var(--space-8) var(--space-12); border: none; border-top: 1px solid var(--line-soft);
    background: var(--surface); font: inherit; font-size: var(--text-md); color: var(--ink); text-align: left; cursor: pointer; }
  .cand button:first-child { border-top: none; }
  .cand button[aria-checked='true'] { background: var(--cloro-soft); box-shadow: inset 3px 0 0 var(--cloro); }
  .cand button.occ { color: var(--muted); }
  .cand .n { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .cand .s { font-size: var(--text-xs); white-space: nowrap; }
  .cand .ore { width: 46px; text-align: right; }
  .cand .stato { width: 150px; text-align: right; color: var(--muted); overflow: hidden; text-overflow: ellipsis; }
  .cand .stato.libero { color: var(--ok); }
  .vuoto-c { padding: var(--space-12); }
  .ore-b { width: 44px; height: 6px; border-radius: 3px; background: var(--surface-sunken); overflow: hidden; flex-shrink: 0; }
  .ore-b i { display: block; height: 100%; background: var(--cloro); }
  .mini-btn { position: relative; height: 34px; padding: 0 var(--space-12); border-radius: var(--radius-sm); border: 1px solid var(--line); background: var(--surface); font: inherit;
    font-size: var(--text-sm); font-weight: 700; color: var(--ink); cursor: pointer; white-space: nowrap; }
  .mini-btn.tratteggio { align-self: flex-start; border-style: dashed; }
  .mini-btn.pieno-warn { background: var(--warn); border-color: var(--warn); color: var(--surface); }
  .avviso { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-10) var(--space-12); border-radius: var(--radius-md); background: var(--warn-bg); color: var(--warn);
    font-size: var(--text-sm); font-weight: 600; }
  .riga-az { display: flex; gap: var(--space-8); justify-content: flex-end; }
  .azioni { display: flex; gap: var(--space-10); flex-wrap: wrap; }
  .azioni .btn { height: 44px; padding: 0 var(--space-20); }
  /* telefono */
  .ri { display: flex; flex-direction: column; gap: var(--space-10); padding: var(--space-14); }
  .testa { display: flex; align-items: center; gap: var(--space-8); }
  .quando-b { font-size: var(--text-xs); font-weight: 700; color: var(--muted); white-space: nowrap; }
  .titolo-r { font-size: var(--text-lg); }
  /* giorno e orario sempre della stessa larghezza, così il luogo comincia allo stesso punto in ogni riga */
  .mini-t { display: flex; align-items: baseline; gap: var(--space-8); padding: var(--space-8) var(--space-10); white-space: nowrap; line-height: 18px; border-radius: var(--radius-sm);
    background: var(--a-bg); color: var(--a-fg); font-size: var(--text-sm); min-width: 0; }
  .mini-t b { font-family: var(--font-mono); font-weight: 500; color: var(--ink); flex-shrink: 0; }
  .mini-t .di { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; width: 52px; flex-shrink: 0; }
  .mini-t .prima { color: var(--muted); text-decoration-thickness: 1.5px; }
  .dove-t { overflow: hidden; text-overflow: ellipsis; }
  .due-b { display: flex; gap: var(--space-8); }
  .due-b .btn { flex: 1; height: 44px; }
  .aspettano { display: flex; flex-direction: column; gap: var(--space-6); }
  .lista-a { padding: 0; overflow: hidden; }
  .voce-a { display: flex; align-items: center; gap: var(--space-10); width: 100%; padding: var(--space-10) var(--space-14); border: none; border-top: 1px solid var(--line-soft); background: var(--surface);
    font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
  .voce-a:first-child { border-top: none; }
  .voce-a .t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .voce-a .t b { font-size: var(--text-sm); }
  .voce-a .t span { font-size: var(--text-xs); color: var(--muted); }
  @media (max-width: 599px) {
    .cand .stato { width: auto; max-width: 110px; }
    .cand .ore-b { display: none; }
    .asse-t, .tl .r { grid-template-columns: 84px 1fr; }
  }
</style>
