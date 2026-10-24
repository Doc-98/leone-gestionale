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
    colonneSpazi, colore, disponi, etichettaSpazi, finestraOraria, inAttivita, inFascia, intervalliColonne, minuti, nomeCategoria,
    orarioFascia, raggruppaFasce, STATI, fasceDefiniteIl, definitePerVasca, type Banda, type Fascia, type Intestazione,
  } from '../lib/piano';
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

  const oggi = iso(new Date());
  let vista = $state<'giorno' | 'settimana'>('giorno');
  /** nel giorno, sul PC: le fasce con la pianta della vasca, oppure la griglia oraria per corsia */
  let modoGiorno = $state<'fasce' | 'orario'>('fasce');
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
  const colSpazi = $derived(colonneSpazi(spazi));
  const giorni = $derived(Array.from({ length: 7 }, (_, i) => aggiungiGiorni(lunedi(giorno), i)));

  // ── Vista giorno: una colonna per corsia (o vasca senza corsie), più «Altro» se serve ──
  const delGiorno = $derived(occorrenze.filter((o) => o.data === giorno));
  const senzaColonna = $derived(delGiorno.filter((o) => o.categoria !== 'CHIUSURA' && !intervalliColonne(o.spazi, colSpazi).length));
  const colonneGiorno = $derived<Intestazione[]>([
    ...colSpazi.map((c) => ({ chiave: c.id, titolo: c.id === c.vascaId ? 'Tutta la vasca' : c.nome.replace(/^Corsia\s*/i, 'Corsia '), sopra: c.vasca })),
    ...(senzaColonna.length ? [{ chiave: 'altro', titolo: 'Senza spazio', sopra: 'Altro' }] : []),
  ]);
  const riquadriGiorno = $derived(
    disponi(
      delGiorno
        .filter((o) => o.categoria !== 'CHIUSURA')
        .flatMap((o) => {
          const iv = intervalliColonne(o.spazi, colSpazi);
          return iv.length ? iv.map(([x, y]) => ({ occorrenza: o, colonna: x, ampiezza: y - x })) : [{ occorrenza: o, colonna: colSpazi.length, ampiezza: 1 }];
        }),
    ),
  );
  const bandeGiorno = $derived<Banda[]>(
    delGiorno
      .filter((o) => o.categoria === 'CHIUSURA' && o.stato !== 'ANNULLATO')
      .flatMap((o) => {
        const iv = o.spazi.length ? intervalliColonne(o.spazi, colSpazi) : [[0, colonneGiorno.length] as [number, number]];
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

  function inCorsia(data: string, fascia: Fascia, spazio: string) {
    messaggio = '';
    if (!corsi.length) api.corsi().then((c) => (corsi = c)).catch(() => {});
    pannello = { tipo: 'inCorsia', data, fascia, spazio, cosa: 'turno' };
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
</script>

<section class="page">
  <div class="barra">
    <div class="seg vista" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Vista">
      <button aria-pressed={vista === 'giorno'} onclick={() => (vista = 'giorno')}>Giorno</button>
      <button aria-pressed={vista === 'settimana'} onclick={() => (vista = 'settimana')}>Settimana</button>
    </div>
    <div class="nav">
      <button class="icon-btn" aria-label="Indietro" onclick={() => sposta(-1)}><Icona nome="sx" /></button>
      <div class="d quando">{titoloCap}</div>
      <button class="icon-btn" aria-label="Avanti" onclick={() => sposta(1)}><Icona nome="dx" /></button>
      <Azione cornice icona="oggi" etichetta="Torna a oggi" disabled={vista === 'giorno' ? giorno === oggi : lunedi(giorno) === lunedi(oggi)} onclick={() => (giorno = oggi)} />
    </div>
    {#if schermo.larga && vista === 'giorno'}
      <div class="seg modo" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Come mostrare il giorno">
        <button aria-pressed={modoGiorno === 'fasce'} onclick={() => (modoGiorno = 'fasce')}>Vasca per fasce</button>
        <button aria-pressed={modoGiorno === 'orario'} onclick={() => (modoGiorno = 'orario')}>Orario</button>
      </div>
    {/if}
    <span class="grow"></span>
    {#if gestore && schermo.larga}
      <button class="btn btn-secondary compatto" onclick={() => ((pannello = { tipo: 'fasce' }), (messaggio = ''))}>Fasce…</button>
    {/if}
    {#if gestore}
      <button class="btn btn-primary compatto" onclick={() => ((pannello = { tipo: 'evento', evento: null, data: giorno }), (messaggio = ''))}><Icona nome="piu" />Nuovo evento…</button>
    {/if}
  </div>

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
      {:else if schermo.larga}
        <div class="giorno-fasce">
          <nav class="fasce card" aria-label="Fasce del giorno">
            {#each fasceGiorno.fasce as f (f.chiave)}
              {@const dentro = contaInFascia(f)}
              <button class="fascia" class:on={fasciaCorrente?.chiave === f.chiave} aria-current={fasciaCorrente?.chiave === f.chiave}
                      onclick={() => (fasciaScelta = f.chiave)}>
                <span class="m orario-f">{orarioFascia(f)}</span>
                <span class="pallini">
                  {#each dentro.slice(0, 8) as o (o.chiave)}<span class="pallino {colore(o.categoria)}" style={o.colore ? `background: ${o.colore}` : ''}></span>{/each}
                </span>
                <span class="muted small">{dentro.length} {dentro.length === 1 ? 'attività' : 'attività'}{dentro.some((o) => o.conflitti.length) ? ' · sovrapposizioni' : ''}</span>
              </button>
            {/each}
          </nav>
          {#if fasciaCorrente}
            {@const f = fasciaCorrente}
            {@const i = fasceGiorno.fasce.indexOf(f)}
            <div class="dettaglio-fascia">
              <div class="testa-fascia">
                <button class="icon-btn" aria-label="Fascia precedente" disabled={i <= 0} onclick={() => (fasciaScelta = fasceGiorno.fasce[i - 1].chiave)}><Icona nome="sx" /></button>
                <h2 class="d">{orarioFascia(f)}</h2>
                <button class="icon-btn" aria-label="Fascia successiva" disabled={i >= fasceGiorno.fasce.length - 1} onclick={() => (fasciaScelta = fasceGiorno.fasce[i + 1].chiave)}><Icona nome="dx" /></button>
              </div>
              <PiantaVasche {spazi} occorrenze={inFascia(f, delGiorno, fasceGiorno.fasciaDi)} fascia={f}
                definite={definitePerVasca(definiteGiorno, f)}
                apri={(o) => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))}
                iscritti={apriIscritti} vedeIscritti={vediIscritti}
                cambiaAssetto={gestore ? cambiaAssetto : undefined}
                libera={gestore ? (spazio) => inCorsia(giorno, f, spazio) : undefined} />
            </div>
          {/if}
        </div>
      {:else}
        {#each fasceGiorno.fasce as f (f.chiave)}
          {@const dentro = inFascia(f, delGiorno, fasceGiorno.fasciaDi)}
          <details class="card fascia-tel" open={fasciaCorrente?.chiave === f.chiave}>
            <summary>
              <span class="m orario-f">{orarioFascia(f)}</span>
              <span class="nomi">{dentro.filter((o) => o.categoria !== 'CHIUSURA').map((o) => o.titolo).join(' · ')}</span>
            </summary>
            <PiantaVasche {spazi} occorrenze={dentro} fascia={f} compatta definite={definitePerVasca(definiteGiorno, f)}
              iscritti={apriIscritti} vedeIscritti={vediIscritti}
              apri={(o) => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))} />
          </details>
        {/each}
      {/if}
    {:else if vista === 'giorno'}
      <OrarioGriglia colonne={colonneGiorno} riquadri={riquadriGiorno} bande={bandeGiorno} {finestra} {adesso}
        etichetta="Piano vasca del giorno per spazio" sotto={sottoGiorno}
        apri={(o) => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))}
        vuoto={gestore ? (c, m) => {
          const spazio = colonneGiorno[c]?.chiave;
          pannello = { tipo: 'evento', evento: null, data: giorno, inizio: hhmm(m), spazio: spazio === 'altro' ? '' : spazio };
          messaggio = '';
        } : undefined} />
    {:else if schermo.larga}
      <!-- Settimana: una riga per fascia, una colonna per giorno; in ogni casella i corsi di quella fascia -->
      <div class="card tabella-sett">
        <table>
          <thead>
            <tr><th class="col-fascia">Fascia</th>
              {#each giorni as g, n (g)}
                {@const ch = occorrenze.find((o) => o.data === g && o.categoria === 'CHIUSURA' && o.stato !== 'ANNULLATO')}
                <th class:oggi={g === oggi}>{colonneGiorni([g])[0].etichetta} {colonneGiorni([g])[0].numero}{#if ch}<span class="tag t-altro chiuso" title={ch.titolo}>chiuso</span>{/if}</th>
              {/each}
            </tr>
          </thead>
          <tbody>
            {#each fasceSettimana.fasce as f (f.chiave)}
              <tr>
                <th class="col-fascia m">{orarioFascia(f)}</th>
                {#each giorni as g (g)}
                  {@const qui = occorrenze.filter((o) => o.data === g && fasceSettimana.fasciaDi.get(o.chiave) === f.chiave).sort(perCorsia)}
                  <td class:oggi={g === oggi}>
                    {#if qui.length}
                      <button class="cella" onclick={() => ((pannello = { tipo: 'fascia', data: g, fascia: f }), (messaggio = ''))}
                              aria-label="{giornoEsteso(g)}, {orarioFascia(f)}: apri la vasca">
                        {#each qui as o (o.chiave)}
                          <span class="corso-cella {colore(o.categoria)}" class:spento={!inAttivita(o)} class:conflitto={o.conflitti.length > 0}>
                            <span class="pallino" style={o.colore ? `background: ${o.colore}` : ''}></span>
                            <span class="nome-c">{o.titolo}</span>
                            <span class="dove-c muted">{etichettaCorta(o)}{o.iscritti != null ? ` · ${o.iscritti}${o.posti != null ? '/' + o.posti : ''}` : ''}</span>
                          </span>
                        {/each}
                      </button>
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
                {#each qui as o (o.chiave)}<span class="nome-chip {colore(o.categoria)}" class:spento={!inAttivita(o)}><span class="pallino" style={o.colore ? `background: ${o.colore}` : ''}></span>{o.titolo}</span>{/each}
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
  .giorno-fasce { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: var(--space-20); align-items: start; }
  .fasce { display: flex; flex-direction: column; padding: var(--space-6); position: sticky; top: var(--space-16); max-height: calc(100vh - 200px); overflow-y: auto; }
  .fascia { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-4); padding: var(--space-8) var(--space-10); border: none; border-radius: var(--radius-sm); background: none; color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .fascia:hover { background: var(--carta); }
  .fascia.on { background: var(--cloro-soft); color: var(--cloro-ink); }
  .orario-f { font-weight: 600; font-size: var(--text-md); }
  .pallini { display: flex; gap: 3px; flex-wrap: wrap; }
  .pallini .pallino { width: 9px; height: 9px; }
  .dettaglio-fascia { display: flex; flex-direction: column; gap: var(--space-12); max-width: 980px; }
  .testa-fascia { display: flex; align-items: center; gap: var(--space-10); }
  .testa-fascia h2 { margin: 0; font-size: var(--text-2xl); font-weight: 800; min-width: 150px; text-align: center; }
  .testa-fascia .icon-btn { font-size: 20px; }
  .fascia-tel { padding: var(--space-10) var(--space-12); }
  .fascia-tel summary { display: flex; align-items: baseline; gap: var(--space-10); cursor: pointer; list-style: none; }
  .fascia-tel summary::-webkit-details-marker { display: none; }
  .fascia-tel[open] summary { margin-bottom: var(--space-12); }
  .nomi { font-size: var(--text-sm); color: var(--muted); min-width: 0; display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-8); }
  .fascia-tel .nomi { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
  .fascia-sett { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-6); width: 100%; padding: var(--space-10) var(--space-12); color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .nome-chip { display: inline-flex; align-items: center; gap: var(--space-4); color: var(--ink); }
  .tabella-sett { overflow: auto; max-height: calc(100vh - 200px); }
  .tabella-sett table { width: 100%; border-collapse: separate; border-spacing: 0; table-layout: fixed; min-width: 1000px; }
  .tabella-sett th { position: sticky; top: 0; z-index: 2; background: var(--surface); text-align: left; font-size: var(--text-sm); padding: var(--space-8) var(--space-10); border-bottom: 1px solid var(--line); }
  .tabella-sett th.oggi { color: var(--cloro); }
  .tabella-sett tbody th { position: sticky; left: 0; z-index: 1; font-size: var(--text-sm); vertical-align: top; border-bottom: 1px solid var(--line-soft); }
  .col-fascia { width: 108px; }
  .tabella-sett td { vertical-align: top; padding: var(--space-4); border-bottom: 1px solid var(--line-soft); border-left: 1px solid var(--line-soft); }
  .tabella-sett td.oggi { background: color-mix(in srgb, var(--cloro-soft) 30%, transparent); }
  .chiuso { height: 18px; margin-left: var(--space-6); }
  .cella { display: flex; flex-direction: column; gap: 2px; width: 100%; padding: var(--space-4); border: 1px solid transparent; border-radius: var(--radius-xs); background: none; color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
  .cella:hover { border-color: var(--line-strong); background: var(--surface); }
  .corso-cella { display: grid; grid-template-columns: 10px minmax(0, 1fr) auto; align-items: center; gap: var(--space-6); font-size: var(--text-xs); line-height: 1.3; }
  .corso-cella .pallino { width: 8px; height: 8px; }
  .nome-c { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .dove-c { font-family: var(--font-mono); font-size: var(--text-2xs); white-space: nowrap; }
  .corso-cella.spento { opacity: 0.5; }
  .corso-cella.spento .nome-c { text-decoration: line-through; }
  .corso-cella.conflitto .nome-c { color: var(--danger); }
  .giorno-ag { display: flex; flex-direction: column; gap: var(--space-6); }
  .giorno-ag h2 { margin: 0; }
  .giorno-ag h2.oggi { color: var(--cloro); }
</style>
