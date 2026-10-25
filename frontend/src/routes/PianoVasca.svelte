<script lang="ts">
  import Icona from '../components/Icona.svelte';
  import Azione from '../components/Azione.svelte';
  import { api, messaggioErrore, type Corso, type Evento, type FasciaOraria, type Occorrenza, type PersonaBreve, type Piano } from '../lib/api';
  import { sessione } from '../lib/sessione.svelte';
  import Iscritti from './Iscritti.svelte';
  import GestioneFasce from './GestioneFasce.svelte';
  import LezioneForm from './LezioneForm.svelte';
  import { aggiungiGiorni, daIso, GIORNI_ESTESI, giornoEsteso, iso, lunedi, MESI, ora } from '../lib/date';
  import {
    colonneDelGiorno, colore, disponi, etichettaSpazi, finestraOraria, inAttivita, inFascia, intervalliColonne, minuti, nomeCategoria,
    orarioFascia, pianta, raggruppaFasce, STATI, fasceDefiniteIl, definitePerVasca, type Banda, type Fascia, type Intestazione,
  } from '../lib/piano';
  import { leggiPreferenza, salvaPreferenza } from '../lib/preferenze';
  import PiantaVasche from '../components/PiantaVasche.svelte';
  import { colonneGiorni } from '../lib/griglia';
  import { schermo } from '../lib/schermo.svelte';
  import OrarioGriglia from '../components/OrarioGriglia.svelte';
  import Foglio from '../components/Foglio.svelte';
  import DettaglioOccorrenza from '../components/DettaglioOccorrenza.svelte';
  import EccezioneForm from './EccezioneForm.svelte';
  import EventoForm from './EventoForm.svelte';

  type Pannello =
    | { tipo: 'dettaglio'; o: Occorrenza }
    | { tipo: 'eccezione'; o: Occorrenza }
    | { tipo: 'evento'; evento: Evento | null; data?: string; inizio?: string; spazio?: string }
    | { tipo: 'fascia'; data: string; fascia: Fascia }
    | { tipo: 'iscritti'; o: Occorrenza }
    | { tipo: 'fasce' }
    | { tipo: 'inCorsia'; data: string; fascia: Fascia; spazio: string; cosa: 'turno' | 'evento' };

  type ModoGiorno = 'fasce' | 'orario';
  type ModoSettimana = 'riquadri' | 'corsie';

  const oggi = iso(new Date());
  let vista = $state<'giorno' | 'settimana'>('giorno');
  /**
   * Nel giorno, sul PC: una fascia alla volta con la pianta della vasca, oppure tutta la giornata per corsia.
   * Nella settimana: i corsi in riquadri, oppure ogni casella come una piccola vasca con le corsie sempre allo stesso
   * posto. La scelta resta per la volta dopo; la prima volta: le fasce, e i riquadri per la segreteria, le corsie per
   * chi coordina.
   */
  let modoGiorno = $state<ModoGiorno>(leggiPreferenza<ModoGiorno>('piano.giorno', 'fasce', ['fasce', 'orario']));
  let modoSettimana = $state<ModoSettimana>(
    leggiPreferenza<ModoSettimana>('piano.settimana', sessione.ha('COORDINATORE', 'ADMIN') ? 'corsie' : 'riquadri', ['riquadri', 'corsie']));
  let vascaSettimana = $state<string>(leggiPreferenza<string>('piano.vasca', ''));
  $effect(() => salvaPreferenza('piano.giorno', modoGiorno));
  $effect(() => salvaPreferenza('piano.settimana', modoSettimana));
  let fasciaScelta = $state('');
  let giorno = $state(oggi);
  let piano = $state<Piano | null>(null);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let avvisi = $state<string[]>([]);
  let pannello = $state<Pannello | null>(null);
  let personale = $state<PersonaBreve[]>([]);
  let corsi = $state<Corso[]>([]);

  const da = $derived(vista === 'giorno' ? giorno : lunedi(giorno));
  const a = $derived(vista === 'giorno' ? giorno : aggiungiGiorni(lunedi(giorno), 6));

  function carica() {
    caricamento = true;
    return api.piano(da, a)
      .then((p) => {
        piano = p;
        errore = '';
        if (p.impiantiGestiti.length && !personale.length) api.personale().then((s) => (personale = s)).catch(() => {});
      })
      .catch((e) => (errore = messaggioErrore(e, 'Non riesco a caricare il piano.')))
      .finally(() => (caricamento = false));
  }
  $effect(() => {
    void da;
    void a;
    carica();
  });

  const gestore = $derived(!!piano?.impiantiGestiti.length);
  const spazi = $derived(piano?.spazi ?? []);
  const occorrenze = $derived(piano?.occorrenze ?? []);
  const giorni = $derived(Array.from({ length: 7 }, (_, i) => aggiungiGiorni(lunedi(giorno), i)));

  // ── Vista giorno: una colonna per corsia (o vasca senza corsie), più «Altro» se serve ──
  const delGiorno = $derived(occorrenze.filter((o) => o.data === giorno));
  // nel giorno solo le corsie degli assetti in uso (la vasca grande non compare due volte, a 6 e a 5 corsie)
  const colDelGiorno = $derived(colonneDelGiorno(spazi, delGiorno.filter((o) => o.categoria !== 'CHIUSURA'), fasceDefiniteIl(piano?.fasce ?? [], giorno)));
  const senzaColonna = $derived(delGiorno.filter((o) => o.categoria !== 'CHIUSURA' && !intervalliColonne(o.spazi, colDelGiorno).length));
  const colonneGiorno = $derived<Intestazione[]>([
    ...colDelGiorno.map((c) => ({ chiave: c.id, titolo: c.id === c.vascaId ? 'Tutta la vasca' : c.nome.replace(/^Corsia\s*/i, 'Corsia '), sopra: c.vasca })),
    ...(senzaColonna.length ? [{ chiave: 'altro', titolo: 'Senza spazio', sopra: 'Altro' }] : []),
  ]);
  const riquadriGiorno = $derived(
    disponi(
      delGiorno
        .filter((o) => o.categoria !== 'CHIUSURA')
        .flatMap((o) => {
          const iv = intervalliColonne(o.spazi, colDelGiorno);
          return iv.length ? iv.map(([x, y]) => ({ occorrenza: o, colonna: x, ampiezza: y - x })) : [{ occorrenza: o, colonna: colDelGiorno.length, ampiezza: 1 }];
        }),
    ),
  );
  const bandeGiorno = $derived<Banda[]>(
    delGiorno
      .filter((o) => o.categoria === 'CHIUSURA' && o.stato !== 'ANNULLATO')
      .flatMap((o) => {
        const iv = o.spazi.length ? intervalliColonne(o.spazi, colDelGiorno) : [[0, colonneGiorno.length] as [number, number]];
        return iv.map(([x, y]) => ({ chiave: o.chiave + x, colonna: x, ampiezza: y - x, inizio: minuti(o.inizio), fine: minuti(o.fine), titolo: o.titolo }));
      }),
  );

  // ── Vista settimana: una colonna per giorno ──
  const riquadriSettimana = $derived(
    disponi(occorrenze.filter((o) => o.categoria !== 'CHIUSURA').map((o) => ({ occorrenza: o, colonna: giorni.indexOf(o.data), ampiezza: 1 })).filter((v) => v.colonna >= 0)),
  );
  const bandeSettimana = $derived<Banda[]>(
    occorrenze
      .filter((o) => o.categoria === 'CHIUSURA' && o.stato !== 'ANNULLATO')
      .map((o) => ({ chiave: o.chiave, colonna: giorni.indexOf(o.data), ampiezza: 1, inizio: minuti(o.inizio), fine: minuti(o.fine), titolo: o.titolo }))
      .filter((b) => b.colonna >= 0),
  );

  // ── Fasce ──
  const definiteGiorno = $derived(fasceDefiniteIl(piano?.fasce ?? [], giorno));
  const definiteSettimana = $derived(giorni.flatMap((g) => fasceDefiniteIl(piano?.fasce ?? [], g)));
  const fasceGiorno = $derived(raggruppaFasce(delGiorno, definiteGiorno));
  const fasceSettimana = $derived(raggruppaFasce(occorrenze, definiteSettimana));
  const dow = (g: string) => ((new Date(`${g}T12:00:00`).getDay() + 6) % 7) + 1;
  /** La lista degli iscritti la vedono segreteria e coordinatori, e l'istruttore del turno. */
  const vediIscritti = (o: Occorrenza) => !!o.lezioneId && (!!piano?.impiantiIscrizioni.length || o.istruttore?.id === sessione.utente?.personaId);
  const apriIscritti = (o: Occorrenza) => ((pannello = { tipo: 'iscritti', o }), (messaggio = ''));

  async function cambiaAssetto(f: FasciaOraria, assetto: number) {
    try {
      const r = await api.cambiaAssetto(f.id, assetto);
      fatto(`Fascia ${ora(f.inizio)}–${ora(f.fine)} a ${assetto} corsie${r.turniAggiornati ? `: ${r.turniAggiornati} corsi riportati sulle corsie con lo stesso numero` : ''}.`);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  function inCorsia(data: string, fascia: Fascia, spazio: string, cosa: 'turno' | 'evento' = 'turno') {
    messaggio = '';
    rapido = null;
    if (!corsi.length) api.corsi().then((c) => (corsi = c)).catch(() => {});
    pannello = { tipo: 'inCorsia', data, fascia, spazio, cosa };
  }
  const hhmm = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  /** La fascia da mostrare: quella scelta, altrimenti quella in corso (o la prossima) se è oggi, altrimenti la prima. */
  const fasciaCorrente = $derived.by(() => {
    const lista = fasceGiorno.fasce;
    const scelta = lista.find((f) => f.chiave === fasciaScelta);
    if (scelta) return scelta;
    if (giorno === oggi) {
      const d = new Date();
      const ora = d.getHours() * 60 + d.getMinutes();
      return lista.find((f) => f.fine > ora) ?? lista[lista.length - 1];
    }
    return lista[0];
  });
  const contaInFascia = (f: Fascia) => inFascia(f, delGiorno, fasceGiorno.fasciaDi).filter((o) => o.categoria !== 'CHIUSURA');
  const chiusureDelGiorno = $derived(delGiorno.filter((o) => o.categoria === 'CHIUSURA' && o.stato !== 'ANNULLATO'));
  $effect(() => {
    void giorno;
    fasciaScelta = '';
  });

  const finestra = $derived(finestraOraria(vista === 'giorno' ? delGiorno : occorrenze));
  const adesso = $derived.by(() => {
    if (vista !== 'giorno' || giorno !== oggi) return null;
    const d = new Date();
    return d.getHours() * 60 + d.getMinutes();
  });

  const legenda = $derived.by(() => {
    const viste = new Map<string, string>();
    for (const o of occorrenze) if (!viste.has(o.categoria)) viste.set(o.categoria, nomeCategoria(o));
    return [...viste.entries()];
  });
  const sovrapposte = $derived(new Set((vista === 'giorno' ? delGiorno : occorrenze).filter((o) => o.conflitti.length).map((o) => o.chiave)).size);

  const titolo = $derived.by(() => {
    if (vista === 'giorno') return giornoEsteso(giorno);
    const x = daIso(giorni[0]);
    const y = daIso(giorni[6]);
    return x.getMonth() === y.getMonth() ? `${x.getDate()}–${y.getDate()} ${MESI[x.getMonth()]}` : `${x.getDate()} ${MESI[x.getMonth()]} – ${y.getDate()} ${MESI[y.getMonth()]}`;
  });

  function sposta(verso: number) {
    giorno = aggiungiGiorni(giorno, verso * (vista === 'giorno' ? 1 : 7));
  }

  function fatto(testo: string, nuoviAvvisi: string[] = []) {
    pannello = null;
    messaggio = testo;
    avvisi = nuoviAvvisi;
    carica();
  }

  async function apriEvento(o: Occorrenza) {
    if (!o.eventoId) return;
    try {
      pannello = { tipo: 'evento', evento: await api.evento(o.eventoId) };
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  async function ripristina(o: Occorrenza) {
    if (!o.eccezioneId) return;
    try {
      await api.togliEccezione(o.eccezioneId);
      fatto(`${o.titolo}: torna al suo giorno e orario.`);
    } catch (e) {
      errore = messaggioErrore(e);
    }
  }

  const sottoGiorno = (o: Occorrenza) => o.istruttore?.nome ?? o.dettaglio ?? '';
  /** Per ordinare: la prima corsia occupata (la vasca piccola e il resto dopo). */
  function primaCorsia(o: Occorrenza): number {
    const numeri = o.spazi.map((id) => spazi.find((x) => x.id === id)).filter((x) => x?.padreId).map((x) => Number(/(\d+)\s*$/.exec(x!.nome)?.[1] ?? 99));
    return numeri.length ? Math.min(...numeri) : o.spazi.length ? 50 : 99;
  }
  const perCorsia = (a: Occorrenza, b: Occorrenza) => primaCorsia(a) - primaCorsia(b) || a.titolo.localeCompare(b.titolo);

  /** «C3», «C4–5», «V. piccola», «V. grande» */
  function etichettaCorta(o: Occorrenza): string {
    if (!o.spazi.length) return '';
    return etichettaSpazi(o.spazi, spazi)
      .replace(/Corsi[ae] ([\d–, ]+) · [^+]+/g, (_m, n: string) => `C${n.trim()}`)
      .replace(/Vasca (\w+)[^+]*/g, 'V. $1')
      .replace(/ \+ /g, ' + ');
  }
  const titoloCap = $derived(titolo.charAt(0).toUpperCase() + titolo.slice(1));

  // ── Fascia scelta: riassunto ──
  const nellaFascia = $derived(fasciaCorrente ? contaInFascia(fasciaCorrente) : []);
  const iscrittiFascia = $derived(nellaFascia.reduce((n, o) => n + (o.iscritti ?? 0), 0));
  const istruttoriFascia = $derived(new Set(nellaFascia.map((o) => o.istruttore?.id).filter(Boolean)).size);
  const sovrapposteFascia = $derived(nellaFascia.filter((o) => o.conflitti.length).length);
  // la fascia scelta resta visibile nella striscia
  let striscia = $state<HTMLElement>();
  $effect(() => {
    void fasciaCorrente;
    striscia?.querySelector('[aria-pressed="true"]')?.scrollIntoView({ block: 'nearest', inline: 'center' });
  });

  // ── Settimana: quale vasca ──
  const vasche = $derived(spazi.filter((x) => !x.padreId && x.tipo === 'VASCA'));
  const vascaScelta = $derived(vascaSettimana === 'tutte' || vasche.some((v) => v.id === vascaSettimana) ? vascaSettimana : (vasche[0]?.id ?? 'tutte'));
  function scegliVasca(id: string) {
    vascaSettimana = id;
    salvaPreferenza('piano.vasca', id);
  }
  const inVasca = (o: Occorrenza, vascaId: string) =>
    vascaId === 'tutte' || o.spazi.includes(vascaId) || o.spazi.some((id) => spazi.find((x) => x.id === id)?.padreId === vascaId);
  const nellaCella = (g: string, f: Fascia) =>
    occorrenze.filter((o) => o.data === g && o.categoria !== 'CHIUSURA' && fasceSettimana.fasciaDi.get(o.chiave) === f.chiave && inVasca(o, vascaScelta)).sort(perCorsia);
  /** dove sta, in breve: con una vasca scelta basta la corsia */
  const doveBreve = (o: Occorrenza) => {
    const e = etichettaCorta(o);
    return vascaScelta !== 'tutte' && e.startsWith('V.') ? '' : e;
  };
  /** Le fasce della settimana che hanno qualcosa nella vasca scelta. */
  const fasceVisibili = $derived(fasceSettimana.fasce.filter((f) => giorni.some((g) => nellaCella(g, f).length)));
  /** Le vasche viste dall'alto in una casella della settimana (giorno e fascia), con l'assetto delle corsie di quel giorno. */
  function piantaCella(g: string, f: Fascia) {
    const occ = nellaCella(g, f);
    if (!occ.length) return null;
    const definite = definitePerVasca(fasceDefiniteIl(piano?.fasce ?? [], g), f);
    const assetti = new Map([...definite].filter(([, d]) => d.assetto != null).map(([v, d]) => [v, d.assetto!]));
    return pianta(spazi, occ, assetti).vasche.filter((v) => vascaScelta === 'tutte' ? v.pezzi.length || v.corsie.length : v.id === vascaScelta);
  }

  // ── Menu rapido su una corsia libera (settimana per corsie) ──
  let rapido = $state<{ chiave: string; data: string; fascia: Fascia; spazio: string; nome: string } | null>(null);
  function chiudiRapido(e: MouseEvent) {
    if (rapido && !(e.target as HTMLElement).closest('.rapido, .slot.libero')) rapido = null;
  }
</script>

<section class="page">
  <div class="barra">
    <div class="seg vista" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Vista">
      <button aria-pressed={vista === 'giorno'} onclick={() => (vista = 'giorno')}>Giorno</button>
      <button aria-pressed={vista === 'settimana'} onclick={() => (vista = 'settimana')}>Settimana</button>
    </div>
    <div class="nav">
      <Azione cornice icona="sx" etichetta={vista === 'giorno' ? 'Giorno precedente' : 'Settimana precedente'} onclick={() => sposta(-1)} />
      <div class="d quando">{titoloCap}</div>
      <Azione cornice icona="dx" etichetta={vista === 'giorno' ? 'Giorno successivo' : 'Settimana successiva'} onclick={() => sposta(1)} />
      <Azione cornice icona="oggi" etichetta={vista === 'giorno' ? 'Torna a oggi' : 'Torna a questa settimana'} disabled={vista === 'giorno' ? giorno === oggi : lunedi(giorno) === lunedi(oggi)} onclick={() => (giorno = oggi)} />
    </div>
    {#if schermo.larga && vista === 'giorno'}
      <div class="seg modo" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Come mostrare il giorno">
        <button aria-pressed={modoGiorno === 'fasce'} onclick={() => (modoGiorno = 'fasce')}>Una fascia</button>
        <button aria-pressed={modoGiorno === 'orario'} onclick={() => (modoGiorno = 'orario')}>Tutto il giorno</button>
      </div>
    {:else if schermo.larga}
      <div class="seg modo" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Come mostrare la settimana">
        <button aria-pressed={modoSettimana === 'riquadri'} onclick={() => (modoSettimana = 'riquadri')}>Riquadri</button>
        <button aria-pressed={modoSettimana === 'corsie'} onclick={() => (modoSettimana = 'corsie')}>Per corsia</button>
      </div>
    {/if}
    <span class="grow"></span>
    {#if gestore && schermo.larga}
      <Azione cornice icona="regole" etichetta={`Fasce del ${GIORNI_ESTESI[dow(giorno)].toLowerCase()}`} onclick={() => ((pannello = { tipo: 'fasce' }), (messaggio = ''))} />
    {/if}
    {#if gestore}
      <button class="btn btn-primary compatto" onclick={() => ((pannello = { tipo: 'evento', evento: null, data: giorno }), (messaggio = ''))}><Icona nome="piu" />Nuovo evento…</button>
    {/if}
  </div>

  {#if schermo.larga && vista === 'settimana' && vasche.length > 1}
    <div class="seg quale" style="grid-template-columns: repeat({vasche.length + 1}, auto)" role="group" aria-label="Quale vasca">
      {#each vasche as v (v.id)}<button aria-pressed={vascaScelta === v.id} onclick={() => scegliVasca(v.id)}>{v.nome}</button>{/each}
      <button aria-pressed={vascaScelta === 'tutte'} onclick={() => scegliVasca('tutte')}>Tutte</button>
    </div>
  {/if}

  {#if schermo.larga && (vista === 'settimana' || modoGiorno === 'orario') && legenda.length}
    <div class="legenda" aria-label="Legenda">
      {#each legenda as [cat, nome] (cat)}<span class="voce-leg {colore(cat)}"><span class="pallino"></span>{nome}</span>{/each}
    </div>
  {/if}
  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#each avvisi as av (av)}<p class="msg err nomargin">Attenzione: {av}</p>{/each}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  {#if sovrapposte && !caricamento}
    <p class="muted small nomargin sovr"><span class="avviso-ico" aria-hidden="true"></span>{sovrapposte === 1 ? 'Una attività si sovrappone' : `${sovrapposte} attività si sovrappongono`} ad altre sugli stessi spazi.</p>
  {/if}

  {#if piano}
    {#if vista === 'giorno' && (modoGiorno === 'fasce' || !schermo.larga)}
      {#each chiusureDelGiorno as c (c.chiave)}<p class="chiusura-g">{c.titolo}{c.nota ? ` · ${c.nota}` : ''}</p>{/each}
      {#if !fasceGiorno.fasce.length}
        <p class="muted">{caricamento ? '' : 'Niente in programma in questo giorno.'}</p>
      {:else}
        <div class="striscia" role="group" aria-label="Fasce del giorno" bind:this={striscia}>
          {#each fasceGiorno.fasce as f (f.chiave)}
            {@const dentro = contaInFascia(f)}
            {@const conf = dentro.filter((o) => o.conflitti.length).length}
            <button class="fascia-chip" aria-pressed={fasciaCorrente?.chiave === f.chiave} onclick={() => (fasciaScelta = f.chiave)}
                    aria-label="{orarioFascia(f)}: {dentro.length} attività{conf ? ', con sovrapposizioni' : ''}">
              <span class="m ora-f">{orarioFascia(f)}</span>
              <span class="punti" aria-hidden="true">
                {#each dentro.slice(0, 8) as o (o.chiave)}<span class="pallino {colore(o.categoria)}"></span>{/each}
              </span>
              <span class="quante">{dentro.length} {schermo.larga ? 'attività' : ''}{#if conf}<span class="warn-t"> · sovrapp.</span>{/if}</span>
            </button>
          {/each}
        </div>
        {#if fasciaCorrente}
          {@const f = fasciaCorrente}
          <section class="card fascia-scelta">
            <div class="testa-fascia">
              <h2 class="d">{orarioFascia(f)}</h2>
              <span class="muted small">{nellaFascia.length} attività{iscrittiFascia ? ` · ${iscrittiFascia} iscritti` : ''}{istruttoriFascia ? ` · ${istruttoriFascia} ${istruttoriFascia === 1 ? 'istruttore' : 'istruttori'} in vasca` : ''}</span>
              <span class="grow"></span>
              {#if sovrapposteFascia}<span class="tag t-warn"><Icona nome="avviso" />{sovrapposteFascia} attività si sovrappongono</span>{/if}
            </div>
            <PiantaVasche {spazi} occorrenze={inFascia(f, delGiorno, fasceGiorno.fasciaDi)} fascia={f} compatta={!schermo.larga}
              definite={definitePerVasca(definiteGiorno, f)}
              apri={(o) => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))}
              iscritti={apriIscritti} vedeIscritti={vediIscritti}
              cambiaAssetto={gestore && schermo.larga ? cambiaAssetto : undefined}
              libera={gestore && giorno >= oggi ? (spazio) => inCorsia(giorno, f, spazio) : undefined} />
          </section>
        {/if}
      {/if}
    {:else if vista === 'giorno'}
      <OrarioGriglia colonne={colonneGiorno} riquadri={riquadriGiorno} bande={bandeGiorno} {finestra} {adesso} comprimi
        etichetta="Piano vasca del giorno per spazio" sotto={sottoGiorno}
        apri={(o) => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))}
        vuoto={gestore && giorno >= oggi ? (c, m) => {
          const spazio = colonneGiorno[c]?.chiave;
          pannello = { tipo: 'evento', evento: null, data: giorno, inizio: hhmm(m), spazio: spazio === 'altro' ? '' : spazio };
          messaggio = '';
        } : undefined} />
    {:else if schermo.larga}
      <!-- Settimana: una riga per fascia, una colonna per giorno. In ogni casella i corsi in riquadri, oppure una piccola vasca -->
      <div class="card tabella-sett">
        <table>
          <thead>
            <tr><th class="col-fascia">Fascia</th>
              {#each giorni as g (g)}
                {@const ch = occorrenze.find((o) => o.data === g && o.categoria === 'CHIUSURA' && o.stato !== 'ANNULLATO')}
                <th class:oggi={g === oggi}>{colonneGiorni([g])[0].etichetta} {colonneGiorni([g])[0].numero}{#if ch}<span class="tag t-altro chiuso" title={ch.titolo}>chiuso</span>{/if}</th>
              {/each}
            </tr>
          </thead>
          <tbody>
            {#each fasceVisibili as f (f.chiave)}
              <tr>
                <th class="col-fascia m">{orarioFascia(f)}</th>
                {#each giorni as g (g)}
                  <td class:oggi={g === oggi}>
                    {#if modoSettimana === 'riquadri'}
                      {@const qui = nellaCella(g, f)}
                      {#if qui.length}
                        <button class="cella" onclick={() => ((pannello = { tipo: 'fascia', data: g, fascia: f }), (messaggio = ''))}
                                aria-label="{giornoEsteso(g)}, {orarioFascia(f)}: apri la vasca">
                          {#each qui as o (o.chiave)}
                            {@const pieno = o.posti != null && o.iscritti != null && o.iscritti >= o.posti}
                            <span class="box-s {colore(o.categoria)}" class:spento={!inAttivita(o)} class:conflitto={o.conflitti.length > 0}>
                              <span class="nome-c">{#if o.colore}<span class="livello" style="background: {o.colore}"></span>{/if}{o.titolo}</span>
                              <span class="dove-c" class:pieno>{[doveBreve(o), o.iscritti != null ? `${o.iscritti}${o.posti != null ? '/' + o.posti : ''}` : ''].filter(Boolean).join(' · ')}{o.conflitti.length ? ' · si sovrappone' : ''}</span>
                            </span>
                          {/each}
                        </button>
                      {/if}
                    {:else}
                      {@const vv = piantaCella(g, f)}
                      {#if vv}
                        <div class="mini">
                          {#each vv as v (v.id)}
                            {@const righe = Math.max(v.corsie.length, v.pezzi.length, 1)}
                            {@const col = Math.max(1, ...v.pezzi.map((p) => p.colonne))}
                            <div class="mini-vasca" style="grid-template-rows: repeat({righe}, 26px); grid-template-columns: repeat({col}, minmax(0, 1fr))"
                                 title={vascaScelta === 'tutte' ? v.nome : undefined}>
                              {#if v.corsie.length}
                                {#each v.corsie as c, i (c.id)}
                                  {#if !v.pezzi.some((p) => i >= p.da && i < p.da + p.quante)}
                                    {@const chiave = `${g}|${f.chiave}|${c.id}`}
                                    {#if gestore && g >= oggi}
                                      <button class="slot libero" style="grid-row: {i + 1}; grid-column: 1 / -1" aria-expanded={rapido?.chiave === chiave}
                                              aria-label="Corsia {c.numero} libera, {giornoEsteso(g)} {orarioFascia(f)}: aggiungi"
                                              onclick={() => (rapido = rapido?.chiave === chiave ? null : { chiave, data: g, fascia: f, spazio: c.id, nome: `Corsia ${c.numero}` })}>
                                        <span class="n">{c.numero}</span><Icona nome="piu" />
                                      </button>
                                    {:else}
                                      <span class="slot libero" style="grid-row: {i + 1}; grid-column: 1 / -1"><span class="n">{c.numero}</span></span>
                                    {/if}
                                    {#if rapido?.chiave === chiave}{@render menuRapido(i + 1)}{/if}
                                  {/if}
                                {/each}
                              {/if}
                              {#each v.pezzi as p (p.occorrenza.chiave + ':' + p.da)}
                                {@const o = p.occorrenza}
                                {@const pieno = o.posti != null && o.iscritti != null && o.iscritti >= o.posti}
                                <button class="slot {colore(o.categoria)}" class:spento={!inAttivita(o)} class:conflitto={o.conflitti.length > 0}
                                        style="grid-row: {(v.corsie.length ? p.da : p.colonna) + 1} / span {v.corsie.length ? p.quante : 1}; grid-column: {v.corsie.length && p.colonne > 1 ? `${p.colonna + 1} / span 1` : '1 / -1'}"
                                        title="{o.titolo} · {doveBreve(o) || v.nome}{o.iscritti != null ? ` · ${o.iscritti}${o.posti != null ? '/' + o.posti : ''} iscritti` : ''}{o.conflitti.length ? ' · si sovrappone' : ''}"
                                        onclick={() => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))}>
                                  <span class="n">{v.corsie.length ? (p.quante > 1 ? `${v.corsie[p.da].numero}–${v.corsie[p.da + p.quante - 1].numero}` : v.corsie[p.da].numero) : 'VP'}</span>
                                  <span class="nome-c">{o.titolo}</span>
                                  {#if o.iscritti != null}<span class="dove-c" class:pieno>{o.iscritti}{o.posti != null ? `/${o.posti}` : ''}</span>{/if}
                                </button>
                              {/each}
                            </div>
                          {/each}
                        </div>
                      {/if}
                    {/if}
                  </td>
                {/each}
              </tr>
            {:else}
              <tr><td colspan="8" class="muted">{caricamento ? '' : 'Niente in programma questa settimana.'}</td></tr>
            {/each}
          </tbody>
        </table>
      </div>
    {:else}
      {#each giorni as g (g)}
        {@const delG = occorrenze.filter((o) => o.data === g)}
        {@const fg = raggruppaFasce(delG)}
        <div class="giorno-ag">
          <h2 class="lbl" class:oggi={g === oggi}>{giornoEsteso(g)}{g === oggi ? ' · oggi' : ''}</h2>
          {#each fg.fasce as f (f.chiave)}
            {@const qui = delG.filter((o) => fg.fasciaDi.get(o.chiave) === f.chiave).sort(perCorsia)}
            <button class="card fascia-sett" onclick={() => (pannello = { tipo: 'fascia', data: g, fascia: f })}>
              <span class="m orario-f">{orarioFascia(f)}</span>
              <span class="nomi">
                {#each qui as o (o.chiave)}<span class="nome-chip {colore(o.categoria)}" class:spento={!inAttivita(o)} class:conflitto={o.conflitti.length > 0}>{#if o.colore}<span class="livello" style="background: {o.colore}"></span>{/if}{o.titolo}</span>{/each}
              </span>
            </button>
          {:else}
            <p class="muted small nomargin">{delG.some((o) => o.categoria === 'CHIUSURA') ? 'Chiuso' : 'Niente in programma.'}</p>
          {/each}
        </div>
      {/each}
    {/if}
  {/if}
</section>

{#snippet menuRapido(riga: number)}
  {@const r = rapido!}
  <div class="rapido" role="menu" aria-label="Aggiungi in {r.nome}" style="grid-row: {riga + 1}">
    <span class="lbl">{r.nome} · {orarioFascia(r.fascia)}</span>
    <button role="menuitem" onclick={() => inCorsia(r.data, r.fascia, r.spazio, 'turno')}><Icona nome="corsi" />Turno di un corso…</button>
    <button role="menuitem" onclick={() => inCorsia(r.data, r.fascia, r.spazio, 'evento')}><Icona nome="oggi" />Evento…</button>
  </div>
{/snippet}

<svelte:window onclick={chiudiRapido} onkeydown={(e) => e.key === 'Escape' && (rapido = null)} />

{#if pannello?.tipo === 'dettaglio'}
  {@const o = pannello.o}
  <Foglio titolo={o.tipo === 'LEZIONE' ? 'Lezione' : 'Evento'} chiudi={() => (pannello = null)}>
    <DettaglioOccorrenza occorrenza={o} {spazi} {gestore} vediIscritti={vediIscritti(o)} azioni={{
      iscritti: () => apriIscritti(o),
      saltaOSposta: () => (pannello = { tipo: 'eccezione', o }),
      ripristina: () => ripristina(o),
      modificaEvento: () => apriEvento(o),
      apriCorso: () => (location.hash = `#/corsi/${o.corsoId}`),
    }} />
  </Foglio>
{:else if pannello?.tipo === 'eccezione'}
  <Foglio titolo="Salta o sposta · {pannello.o.titolo}" chiudi={() => (pannello = null)}>
    <EccezioneForm occorrenza={pannello.o} {fatto} />
  </Foglio>
{:else if pannello?.tipo === 'fascia'}
  {@const p = pannello}
  {@const delG = occorrenze.filter((o) => o.data === p.data)}
  <Foglio titolo="{giornoEsteso(p.data)} · {orarioFascia(p.fascia)}" chiudi={() => (pannello = null)}>
    <PiantaVasche {spazi} occorrenze={inFascia(p.fascia, delG, fasceSettimana.fasciaDi)} fascia={p.fascia} compatta
      definite={definitePerVasca(fasceDefiniteIl(piano?.fasce ?? [], p.data), p.fascia)}
      iscritti={apriIscritti} vedeIscritti={vediIscritti}
      apri={(o) => (pannello = { tipo: 'dettaglio', o })} />
  </Foglio>
{:else if pannello?.tipo === 'iscritti'}
  {@const o = pannello.o}
  <Foglio titolo="Iscritti · {o.titolo}" chiudi={() => (pannello = null)}>
    <Iscritti lezioneId={o.lezioneId!} data={o.data} cambiato={carica} />
  </Foglio>
{:else if pannello?.tipo === 'fasce' && piano}
  <Foglio titolo="Fasce · {GIORNI_ESTESI[dow(giorno)]}" chiudi={() => (pannello = null)}>
    <GestioneFasce giornoSettimana={dow(giorno)} fasce={piano.fasce} {spazi} cambiato={(m) => { messaggio = m; avvisi = []; carica(); }} />
  </Foglio>
{:else if pannello?.tipo === 'inCorsia' && piano}
  {@const p = pannello}
  <Foglio titolo="{giornoEsteso(p.data)} · {orarioFascia(p.fascia)}" chiudi={() => (pannello = null)}>
    <div class="seg" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Cosa aggiungere">
      <button aria-pressed={p.cosa === 'turno'} onclick={() => (pannello = { ...p, cosa: 'turno' })}>Turno di un corso</button>
      <button aria-pressed={p.cosa === 'evento'} onclick={() => (pannello = { ...p, cosa: 'evento' })}>Evento</button>
    </div>
    {#if p.cosa === 'turno'}
      {#key corsi.length}
        <LezioneForm corso={null} {corsi} lezione={null} {spazi} {personale} {fatto}
          proposta={{ giornoSettimana: dow(p.data), inizio: hhmm(p.fascia.inizio), fine: hhmm(p.fascia.fine), spazio: p.spazio, dal: p.data }} />
      {/key}
    {:else}
      <EventoForm evento={null} impiantoId={piano.impiantiGestiti[0]} {spazi} {personale} data={p.data} inizio={hhmm(p.fascia.inizio)} spazio={p.spazio}
        {fatto} annullato={() => fatto('Evento annullato.')} />
    {/if}
  </Foglio>
{:else if pannello?.tipo === 'evento' && piano}
  <Foglio titolo={pannello.evento ? 'Modifica evento' : 'Nuovo evento'} chiudi={() => (pannello = null)}>
    <EventoForm evento={pannello.evento} impiantoId={pannello.evento?.impiantoId ?? piano.impiantiGestiti[0]} {spazi} {personale}
      data={pannello.data ?? giorno} inizio={pannello.inizio ?? '17:00'} spazio={pannello.spazio ?? ''}
      {fatto} annullato={() => fatto('Evento annullato.')} />
  </Foglio>
{/if}

<style>
  .barra { display: flex; align-items: center; gap: var(--space-12) var(--space-16); flex-wrap: wrap; }
  .vista { width: 220px; }
  .nav { display: flex; align-items: center; gap: var(--space-8); }
  .quando { font-size: var(--text-xl); font-weight: 800; min-width: 200px; text-align: center; }
  .legenda { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-14); margin-top: calc(-1 * var(--space-6)); }
  .voce-leg { display: inline-flex; align-items: center; gap: var(--space-6); font-size: var(--text-xs); color: var(--muted); }
  .compatto { height: 40px; font-size: var(--text-md); padding: 0 var(--space-14); }
  .sovr { display: flex; align-items: center; gap: var(--space-6); }
  .avviso-ico { width: 14px; height: 14px; background: var(--danger); flex-shrink: 0; -webkit-mask: var(--icona-avviso) center / contain no-repeat; mask: var(--icona-avviso) center / contain no-repeat; }
  /* telefono: agenda */
  @media (max-width: 1023px) {
    .vista { width: 100%; }
    .nav { width: 100%; justify-content: space-between; }
    .quando { min-width: 0; font-size: var(--text-lg); }
    .barra .btn { width: 100%; }
  }
  .modo { width: 260px; }
  .chiusura-g { margin: 0; padding: var(--space-10) var(--space-14); border-radius: var(--radius-md); border: 1px dashed var(--line-dashed); font-weight: 700; color: var(--muted);
    background: repeating-linear-gradient(135deg, var(--surface-sunken) 0 8px, transparent 8px 16px); }
  /* Giorno: la striscia delle fasce e la fascia scelta */
  .striscia { display: flex; gap: var(--space-8); overflow-x: auto; padding: 2px 2px var(--space-6); scrollbar-width: thin; }
  .fascia-chip { flex: 0 0 auto; min-width: 116px; display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-8) var(--space-12);
    border-radius: var(--radius-md); border: 1px solid var(--line); background: var(--surface); color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .fascia-chip:hover { border-color: var(--line-strong); }
  .ora-f { font-size: var(--text-md); }
  .punti { display: flex; gap: 3px; height: 10px; align-items: center; }
  .punti .pallino { width: 8px; height: 8px; }
  .quante { font-size: var(--text-xs); color: var(--muted); }
  .warn-t { color: var(--warn); font-weight: 600; }
  .fascia-chip[aria-pressed='true'] { background: var(--fill); border-color: var(--fill); color: var(--on-fill); }
  .fascia-chip[aria-pressed='true'] .quante { color: var(--on-fill); }
  .fascia-chip[aria-pressed='true'] .warn-t { color: var(--on-fill); text-decoration: underline; }
  .fascia-scelta { display: flex; flex-direction: column; gap: var(--space-14); padding: var(--space-16) var(--space-20) var(--space-20); }
  .testa-fascia { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-4) var(--space-14); }
  .testa-fascia h2 { margin: 0; font-size: var(--text-2xl); font-weight: 800; }
  .testa-fascia .tag :global(.ic) { width: 14px; height: 14px; }
  @media (max-width: 1023px) {
    .fascia-scelta { padding: var(--space-12); }
    .fascia-chip { min-width: 100px; }
  }
  .quale { align-self: flex-start; }
  .quale button { padding: 0 var(--space-14); }
  .nomi { min-width: 0; display: flex; flex-wrap: wrap; gap: var(--space-4); }
  .fascia-sett { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-6); width: 100%; padding: var(--space-10) var(--space-12); color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .nome-chip { display: inline-flex; align-items: center; gap: var(--space-4); padding: 2px var(--space-8); border-radius: 8px; background: var(--c-bg); color: var(--ink); font-size: var(--text-sm); font-weight: 600; }
  .nome-chip.spento { opacity: 0.5; text-decoration: line-through; }
  .nome-chip.conflitto { box-shadow: inset 0 0 0 2px var(--warn); }
  .tabella-sett { overflow: auto; max-height: calc(100vh - 200px); }
  .tabella-sett table { width: 100%; border-collapse: separate; border-spacing: 0; table-layout: fixed; min-width: 1000px; }
  .tabella-sett th { position: sticky; top: 0; z-index: 2; background: var(--surface); text-align: left; font-size: var(--text-sm); padding: var(--space-8) var(--space-10); border-bottom: 1px solid var(--line); }
  .tabella-sett th.oggi { color: var(--cloro); }
  .tabella-sett tbody th { position: sticky; left: 0; z-index: 1; font-size: var(--text-sm); vertical-align: top; border-bottom: 1px solid var(--line-soft); }
  .col-fascia { width: 108px; }
  .tabella-sett td { vertical-align: top; padding: var(--space-4); border-bottom: 1px solid var(--line-soft); border-left: 1px solid var(--line-soft); }
  .tabella-sett td.oggi { background: color-mix(in srgb, var(--cloro-soft) 30%, transparent); }
  .chiuso { height: 18px; margin-left: var(--space-6); }
  .cella { display: flex; flex-direction: column; gap: 3px; width: 100%; padding: 2px; border: 1px solid transparent; border-radius: var(--radius-sm); background: none; color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .cella:hover { border-color: var(--line-strong); background: var(--surface); }
  /* Riquadri: un corso, nella tinta della sua categoria */
  .box-s { display: flex; flex-direction: column; gap: 1px; padding: var(--space-4) var(--space-8); border-radius: 8px; background: var(--c-bg); color: var(--c-fg); }
  .nome-c { display: flex; align-items: center; gap: var(--space-4); font-size: var(--text-xs); font-weight: 700; color: var(--ink); line-height: 1.25; overflow-wrap: anywhere; }
  .dove-c { font-family: var(--font-mono); font-size: var(--text-2xs); white-space: nowrap; }
  .dove-c.pieno { color: var(--warn); font-weight: 600; }
  .livello { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; box-shadow: 0 0 0 1px rgba(16, 33, 43, 0.25); }
  .box-s.spento, .slot.spento { opacity: 0.5; }
  .box-s.spento .nome-c, .slot.spento .nome-c { text-decoration: line-through; }
  .box-s.conflitto, .slot.conflitto { box-shadow: inset 0 0 0 2px var(--warn); }
  /* Per corsia: ogni casella è una piccola vasca, la stessa corsia sta alla stessa altezza ogni giorno */
  .mini { display: flex; flex-direction: column; gap: var(--space-4); }
  .mini-vasca { position: relative; display: grid; gap: 2px; }
  .slot { display: flex; align-items: center; gap: var(--space-6); min-width: 0; padding: 0 var(--space-6); border: none; border-radius: 6px;
    background: var(--c-bg); color: var(--c-fg); font: inherit; text-align: left; cursor: pointer; }
  .slot .nome-c { flex: 1; min-width: 0; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .slot .n { font-family: var(--font-mono); font-size: var(--text-2xs); color: var(--muted); min-width: 1.4em; }
  .slot:not(.libero) .n { color: var(--c-fg); }
  .slot:hover:not(.libero) { filter: brightness(0.97); }
  .slot.libero { background: none; border: 1px dashed var(--line-dashed); color: var(--muted); cursor: default; }
  button.slot.libero { cursor: pointer; }
  button.slot.libero :global(.ic) { width: 14px; height: 14px; opacity: 0; }
  button.slot.libero:hover, button.slot.libero[aria-expanded='true'] { border-color: var(--cloro); color: var(--cloro-ink); background: var(--cloro-soft); }
  button.slot.libero:hover :global(.ic), button.slot.libero[aria-expanded='true'] :global(.ic), button.slot.libero:focus-visible :global(.ic) { opacity: 1; }
  .rapido { position: absolute; left: 0; z-index: 5; grid-column: 1 / -1; display: flex; flex-direction: column; min-width: 210px; padding: var(--space-6);
    background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); box-shadow: 0 6px 20px rgba(16, 33, 43, 0.14); }
  .rapido .lbl { padding: var(--space-4) var(--space-8); }
  .rapido button { display: flex; align-items: center; gap: var(--space-8); min-height: 40px; padding: 0 var(--space-8); border: none; background: none; border-radius: var(--radius-sm);
    font: inherit; font-size: var(--text-md); color: var(--ink); cursor: pointer; text-align: left; white-space: nowrap; }
  .rapido button:hover { background: var(--carta); }
  .rapido button :global(.ic) { width: 18px; height: 18px; color: var(--cloro); }
  .giorno-ag { display: flex; flex-direction: column; gap: var(--space-6); }
  .giorno-ag h2 { margin: 0; }
  .giorno-ag h2.oggi { color: var(--cloro); }
</style>
