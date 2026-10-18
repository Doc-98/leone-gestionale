<script lang="ts">
  import { api, messaggioErrore, type Evento, type Occorrenza, type PersonaBreve, type Piano } from '../lib/api';
  import { aggiungiGiorni, daIso, giornoEsteso, iso, lunedi, MESI, ora } from '../lib/date';
  import {
    colonneSpazi, colore, disponi, etichettaSpazi, finestraOraria, inAttivita, intervalliColonne, minuti, nomeCategoria, STATI,
    type Banda, type Intestazione,
  } from '../lib/piano';
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
    | { tipo: 'evento'; evento: Evento | null; data?: string; inizio?: string; spazio?: string };

  const oggi = iso(new Date());
  let vista = $state<'giorno' | 'settimana'>('giorno');
  let giorno = $state(oggi);
  let piano = $state<Piano | null>(null);
  let caricamento = $state(true);
  let errore = $state('');
  let messaggio = $state('');
  let avvisi = $state<string[]>([]);
  let pannello = $state<Pannello | null>(null);
  let personale = $state<PersonaBreve[]>([]);

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
  const sottoSettimana = (o: Occorrenza) => etichettaSpazi(o.spazi, spazi).replace(' · Vasca grande', '').replace('Vasca ', 'V. ');
  const titoloCap = $derived(titolo.charAt(0).toUpperCase() + titolo.slice(1));
</script>

<section class="page">
  <div class="barra">
    <div class="seg vista" style="grid-template-columns: 1fr 1fr" role="group" aria-label="Vista">
      <button aria-pressed={vista === 'giorno'} onclick={() => (vista = 'giorno')}>Giorno</button>
      <button aria-pressed={vista === 'settimana'} onclick={() => (vista = 'settimana')}>Settimana</button>
    </div>
    <div class="nav">
      <button class="icon-btn" aria-label="Indietro" onclick={() => sposta(-1)}>‹</button>
      <div class="d quando">{titoloCap}</div>
      <button class="icon-btn" aria-label="Avanti" onclick={() => sposta(1)}>›</button>
      {#if (vista === 'giorno' && giorno !== oggi) || (vista === 'settimana' && lunedi(giorno) !== lunedi(oggi))}
        <button class="link small" onclick={() => (giorno = oggi)}>Oggi</button>
      {/if}
    </div>
    {#if schermo.larga}
      <div class="legenda" aria-label="Legenda">
        {#each legenda as [cat, nome] (cat)}<span class="voce-leg {colore(cat)}"><span class="pallino"></span>{nome}</span>{/each}
      </div>
    {/if}
    {#if gestore}
      <button class="btn btn-primary compatto" onclick={() => ((pannello = { tipo: 'evento', evento: null, data: giorno }), (messaggio = ''))}>Nuovo evento…</button>
    {/if}
  </div>

  {#if messaggio}<p class="msg ok" role="status">{messaggio}</p>{/if}
  {#each avvisi as av (av)}<p class="msg err nomargin">Attenzione: {av}</p>{/each}
  {#if errore}<p class="msg err" role="alert">{errore}</p>{/if}
  {#if sovrapposte && !caricamento}
    <p class="muted small nomargin sovr"><span class="avviso-ico" aria-hidden="true"></span>{sovrapposte === 1 ? 'Una attività si sovrappone' : `${sovrapposte} attività si sovrappongono`} ad altre sugli stessi spazi.</p>
  {/if}

  {#if piano}
    {#if schermo.larga}
      {#if vista === 'giorno'}
        <OrarioGriglia colonne={colonneGiorno} riquadri={riquadriGiorno} bande={bandeGiorno} {finestra} {adesso}
          etichetta="Piano vasca del giorno per spazio" sotto={sottoGiorno}
          apri={(o) => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))}
          vuoto={gestore ? (c, m) => {
            const spazio = colonneGiorno[c]?.chiave;
            pannello = { tipo: 'evento', evento: null, data: giorno, inizio: `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`, spazio: spazio === 'altro' ? '' : spazio };
            messaggio = '';
          } : undefined} />
      {:else}
        <OrarioGriglia colonne={colonneGiorni(giorni).map((c) => ({ chiave: c.chiave, titolo: `${c.etichetta} ${c.numero}`, oggi: c.oggi }))}
          riquadri={riquadriSettimana} bande={bandeSettimana} {finestra}
          etichetta="Piano vasca della settimana" sotto={sottoSettimana}
          apri={(o) => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))}
          vuoto={gestore ? (c, m) => {
            pannello = { tipo: 'evento', evento: null, data: giorni[c], inizio: `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}` };
            messaggio = '';
          } : undefined} />
      {/if}
    {:else}
      {#each vista === 'giorno' ? [giorno] : giorni as g (g)}
        {@const lista = occorrenze.filter((o) => o.data === g)}
        <div class="giorno-ag">
          {#if vista === 'settimana'}<h2 class="lbl" class:oggi={g === oggi}>{giornoEsteso(g)}{g === oggi ? ' · oggi' : ''}</h2>{/if}
          {#each lista as o (o.chiave)}
            <button class="card voce-ag {colore(o.categoria)}" class:spenta={!inAttivita(o)} class:conflitto={o.conflitti.length > 0}
                    onclick={() => ((pannello = { tipo: 'dettaglio', o }), (messaggio = ''))}>
              <span class="m orario">{o.categoria === 'CHIUSURA' && ora(o.inizio) === '00:00' ? 'tutto il giorno' : `${ora(o.inizio)}–${ora(o.fine)}`}</span>
              <span class="corpo">
                <span class="tit">{o.titolo}</span>
                <span class="muted small">{STATI[o.stato] || etichettaSpazi(o.spazi, spazi)}{o.istruttore && inAttivita(o) ? ` · ${o.istruttore.nome}` : ''}</span>
              </span>
              {#if o.conflitti.length}<span class="avviso-ico" aria-label="si sovrappone"></span>{/if}
            </button>
          {:else}
            <p class="muted small nomargin">{caricamento ? '' : 'Niente in programma.'}</p>
          {/each}
        </div>
      {/each}
    {/if}
  {/if}
</section>

{#if pannello?.tipo === 'dettaglio'}
  {@const o = pannello.o}
  <Foglio titolo={o.tipo === 'LEZIONE' ? 'Lezione' : 'Evento'} chiudi={() => (pannello = null)}>
    <DettaglioOccorrenza occorrenza={o} {spazi} {gestore} azioni={{
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
  .nav .icon-btn { font-size: 22px; }
  .quando { font-size: var(--text-xl); font-weight: 800; min-width: 200px; text-align: center; }
  .legenda { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-12); flex: 1; justify-content: flex-end; }
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
  .giorno-ag { display: flex; flex-direction: column; gap: var(--space-6); }
  .giorno-ag h2 { margin: 0; }
  .giorno-ag h2.oggi { color: var(--cloro); }
  .voce-ag {
    display: flex; align-items: center; gap: var(--space-12); width: 100%; min-height: 56px; padding: var(--space-8) var(--space-12);
    border-left: 4px solid var(--c-line); color: var(--ink); font: inherit; text-align: left; cursor: pointer;
  }
  .voce-ag .orario { font-size: var(--text-sm); flex-shrink: 0; min-width: 92px; }
  .corpo { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .tit { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .spenta { opacity: 0.6; }
  .spenta .tit { text-decoration: line-through; }
  .conflitto { box-shadow: inset 0 0 0 1px var(--danger); }
</style>
